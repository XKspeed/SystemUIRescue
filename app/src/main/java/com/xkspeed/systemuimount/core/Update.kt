package com.xkspeed.systemuimount.core

import com.xkspeed.systemuimount.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * 仓库根目录的 update.json
 *
 * 字段都给了默认值 少写几个也能解析 不至于因为仓库里改格式就崩
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val url: String = "",
    val changelog: String = "",
)

/**
 * 更新检查
 *
 * 只读 GitHub 上的 update.json 不碰 release API：一份静态文件就够
 * 启动时后台查一次 10 秒没回就当没更新 不能让网络拖住启动
 */
object Update {

    /** 仓库主分支上的清单 换仓库只改这一行 */
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/XKspeed/SystemUIRescue/main/update.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val currentCode: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    /** 超时返回 null 与「没有更新」区分开：调用方可以区分"没查到"和"已最新" */
    suspend fun check(timeoutMs: Long = 10_000): UpdateInfo? =
        withTimeoutOrNull(timeoutMs) { fetch() }

    fun isNewer(info: UpdateInfo?): Boolean = info != null && info.versionCode > currentCode

    /** 变更按行拆开 空行丢掉 列表里好排版 */
    fun changelogLines(info: UpdateInfo): List<String> =
        info.changelog.lines()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() }

    /** 显示用的版本号：清单里没写 name 就退回 code */
    fun label(info: UpdateInfo): String =
        info.versionName.ifBlank { info.versionCode.toString() }

    private suspend fun fetch(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 6_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                // 走 CDN 不能吃到旧清单 否则刚发的版本永远查不到
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    CrashLog.log("更新", "update.json HTTP ${conn.responseCode}")
                    return@runCatching null
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val info = json.decodeFromString<UpdateInfo>(text)
                CrashLog.log(
                    "更新",
                    "远端 ${info.versionName}(${info.versionCode}) 本地 ${currentName}($currentCode)",
                )
                info
            } finally {
                runCatching { conn.disconnect() }
            }
        }.getOrNull()
    }
}
