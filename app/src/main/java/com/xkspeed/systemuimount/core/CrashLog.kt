package com.xkspeed.systemuimount.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志落盘
 *
 * 为什么要自己写：系统弹「应用已停止运行」只给一句 FATAL EXCEPTION
 * 真正有用的堆栈在 logcat 里 而没有 root / 连不上 adb 时根本看不到
 * 所以在 [install] 里挂一个 UncaughtExceptionHandler 把现场写到磁盘：
 *
 * - 内部目录 `filesDir/logs/`：session.log（本次运行流水）+ crash-*.log（每次崩溃）
 * - 外部目录 `外部存储/Android/data/<包名>/files/logs/`：同一份副本
 *   用手机文件管理器就能直接看 也能 `adb pull` 出来
 *
 * 另外记录崩溃次数：连续崩溃达到阈值时置「安全模式」
 * 由 [com.xkspeed.systemuimount.ui.UiConfigState.safeMode] 消费 ——
 * 关掉液态玻璃与预测性返回 让应用至少能打开（否则纯玻璃崩溃会变成打不开的死循环）
 */
object CrashLog {

    private const val TAG = "SuiRescue"
    private const val DIR = "logs"
    private const val PREFS = "crash_log"

    /** 记录「已经向用户展示过」的最近崩溃文件绝对路径 避免每次启动都弹窗 */
    private const val KEY_LAST_SHOWN = "last_shown_crash"

    /** 连续崩溃多少次后进入安全模式 */
    private const val SAFE_MODE_THRESHOLD = 2

    /** 多久以内的崩溃算「连续」（毫秒） */
    private const val CONTIGUOUS_WINDOW_MS = 10 * 60 * 1000L

    private lateinit var appContext: Context

    /** install 之前的兜底上下文：万一定时器/异常路径在 install 前就调了这里的函数 不该再崩一次 */
    private var fallbackContext: Context? = null

    /** 是否已安装（避免重复安装把旧 handler 链断掉） */
    private var installed = false

    /** 崩溃时正在执行的动作描述 写进崩溃文件头部 */
    @Volatile
    private var stage: String = "启动"

    fun install(context: Context) {
        if (installed) return
        installed = true
        appContext = context.applicationContext
        fallbackContext = context.applicationContext

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "写崩溃日志失败", t)
            }
            // 交给系统处理（弹崩溃框、杀进程） 别吞掉
            previous?.uncaughtException(thread, throwable)
        }

        log("应用启动", "pid=${android.os.Process.myPid()} ${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE}")
    }

    /** 标记当前阶段 崩溃时一起记录（例："渲染主页"、"切换到关于页"） */
    fun stage(name: String) {
        stage = name
        log("阶段", name)
    }

    /** 追加一条运行流水 */
    fun log(tag: String, msg: String) {
        if (!installed) return
        try {
            val line = "${ts()} [$tag] $msg\n"
            appendTo(sessionFile(), line)
            val ext = externalDir()
            if (ext != null) appendTo(ext, line)
        } catch (_: Throwable) {
            // 日志失败不能反过来把 App 搞崩
        }
    }

    /** 记录一次可恢复的错误（不杀进程） */
    fun warn(tag: String, msg: String, t: Throwable? = null) {
        log(tag, msg + (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val header = buildString {
            appendLine("===== 崩溃时间 ${ts()} =====")
            appendLine("线程：${thread.name}")
            appendLine("阶段：$stage")
            appendLine("系统：${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("设备：${Build.DEVICE} ${Build.FINGERPRINT}")
            appendLine()
        }
        val body = header + sw.toString()
        val ctx = ctx() ?: return
        val f = crashFile()
        f.parentFile?.mkdirs()
        f.writeText(body)
        // 同步一份到 cache 目录（/data/user/0/<包名>/cache）
        val cache = File(ctx.cacheDir, DIR)
        cache.mkdirs()
        runCatching { File(cache, f.name).writeText(body) }
        val ext = externalDir()
        if (ext != null) {
            ext.parentFile?.mkdirs()
            ext.writeText(body)
        }
        Log.e(TAG, "崩溃已记录：${f.absolutePath}\n$body")
        noteCrash()
    }

    /** 最近一次崩溃文件（按修改时间最新的那个） */
    fun lastCrashFile(): File? {
        val ctx = ctx() ?: return null
        val dir = File(ctx.filesDir, DIR)
        val files = dir.listFiles { f -> f.name.startsWith("crash-") } ?: return null
        return files.maxByOrNull { it.lastModified() }
    }

    /** 最近一次崩溃的内容 没有则空串 */
    fun lastCrashText(): String =
        lastCrashFile()?.takeIf { it.length() > 0 }?.readText().orEmpty()

    /**
     * 最近一次崩溃文件 但**仅当它还从未向用户展示过**才返回
     * 用于启动弹窗：同一份崩溃只提醒一次 之后启动不再 nag
     * 崩溃文件本身不会被删除 仍可在「崩溃日志」页里查阅
     */
    fun pendingCrash(): File? {
        val file = lastCrashFile() ?: return null
        if (!file.exists() || file.length() == 0L) return null
        val shown = ctx()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_LAST_SHOWN, null)
        if (file.absolutePath == shown) return null
        return file
    }

    /** 标记某次崩溃已经向用户展示过（下次启动不再弹） */
    fun markCrashShown(file: File) {
        val ctx = ctx() ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_SHOWN, file.absolutePath)
            .apply()
    }

    /** 外部目录里的崩溃副本（给用户看的那个） */
    fun externalCrashFile(): File? {
        val ext = externalDir() ?: return null
        return ext.takeIf { it.exists() }
    }

    /** 当前是否处于安全模式（连续崩溃超阈值） */
    fun safeMode(): Boolean {
        val ctx = ctx() ?: return false
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = p.getInt("count", 0)
        val last = p.getLong("last", 0L)
        return count >= SAFE_MODE_THRESHOLD && System.currentTimeMillis() - last < CONTIGUOUS_WINDOW_MS
    }

    /** 成功启动一次就清零计数（说明能跑起来） */
    fun markAlive() {
        val ctx = ctx() ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("count", 0)
            .putLong("last", System.currentTimeMillis())
            .apply()
    }

    /** 记一次崩溃（返回是否进入安全模式） */
    private fun noteCrash() {
        val ctx = ctx() ?: return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = p.getLong("last", 0L)
        val count = if (now - last < CONTIGUOUS_WINDOW_MS) p.getInt("count", 0) + 1 else 1
        p.edit().putInt("count", count).putLong("last", now).apply()
    }

    /** 拿上下文；install 没跑过就用兜底 再没有就 null（调用方各自降级） */
    private fun ctx(): Context? =
        if (::appContext.isInitialized) appContext else fallbackContext

    private fun sessionFile(): File {
        val ctx = ctx() ?: return File(System.getProperty("java.io.tmpdir"), "sui-session.log")
        return File(File(ctx.filesDir, DIR), "session.log")
    }

    private fun crashFile(): File {
        val ctx = ctx()
            ?: return File(System.getProperty("java.io.tmpdir"), "sui-crash.log")
        val name = "crash-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.log"
        return File(File(ctx.filesDir, DIR), name)
    }

    /** 外部可读目录；拿不到外部存储就返回 null */
    private fun externalDir(): File? {
        val ctx = ctx() ?: return null
        val ext = ctx.getExternalFilesDir(null) ?: return null
        return File(File(ext, DIR), "crash-latest.log")
    }

    private fun appendTo(f: File, text: String) {
        f.parentFile?.mkdirs()
        // 追加写 单文件上限 512KB 防止无限膨胀
        if (f.length() > 512 * 1024) f.writeText("")
        f.appendText(text)
    }

    private fun ts(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
