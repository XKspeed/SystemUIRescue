package com.xkspeed.systemuimount.ui

import android.widget.Toast
import java.io.File
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xkspeed.systemuimount.core.CrashLog
import com.xkspeed.systemuimount.core.DaemonService
import com.xkspeed.systemuimount.core.Module
import com.xkspeed.systemuimount.core.ModuleState
import com.xkspeed.systemuimount.core.Root
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as kyantRememberLayerBackdrop
import com.xkspeed.systemuimount.ui.component.glass.GlassBottomNav
import com.xkspeed.systemuimount.ui.component.glass.GlassNavItem
import com.xkspeed.systemuimount.ui.components.MiuixDialog
import com.xkspeed.systemuimount.ui.util.LocalIsWideScreen
import com.xkspeed.systemuimount.ui.util.shouldShowSplitPane
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object Logs : Route

    @Serializable
    data object UiSettings : Route

    @Serializable
    data object VolumeCheck : Route

    @Serializable
    data object CrashLogs : Route

    @Serializable
    data object ThirdPartyLicenses : Route

    @Serializable
    data class Picker(val target: PickerTarget) : Route
}

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 返回（含预测性返回手势）由 NavDisplay 内部统一处理，这里不再注册 handler：
    // 两层 handler 会互相抢事件，反而让手势失效。
    val backStack = rememberNavBackStack<Route>(Route.Main)
    var state by remember { mutableStateOf(ModuleState()) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyTitle by remember { mutableStateOf<String?>(null) }
    var busySub by remember { mutableStateOf("") }
    var showRootDialog by remember { mutableStateOf(false) }
    var crashText by remember { mutableStateOf<String?>(null) }
    // 记录当前弹窗对应的是哪份崩溃文件，关闭时用来标记「已看过」
    var crashFile by remember { mutableStateOf<File?>(null) }

    // 关闭启动崩溃弹窗：标记这份崩溃已展示过，下次启动不再弹（只提醒一次）
    fun dismissCrash() {
        crashText = null
        val f = crashFile
        crashFile = null
        if (f != null) CrashLog.markCrashShown(f)
    }

    // 启动时先看上次有没有崩：把堆栈直接摊给用户看，
    // 不用去抓 logcat（没 root 抓不到）。同一份崩溃只弹一次。
    LaunchedEffect(Unit) {
        val file = CrashLog.pendingCrash()
        if (file != null) {
            CrashLog.log("启动", "检测到上次崩溃记录，长度 ${file.length()}")
            crashText = file.readText()
            crashFile = file
        } else {
            // 能走到这里说明这次启动没崩（或崩溃已展示过），清掉连续计数
            CrashLog.markAlive()
        }
    }

    // 启动时拿不到 root 就直接提示，别让用户点进一堆"操作失败"
    LaunchedEffect(rootOk) {
        if (rootOk == false) showRootDialog = true
    }

    suspend fun refresh() {
        state = Module.readState()
    }

    // 首次进入：先要 root，再读状态
    LaunchedEffect(Unit) {
        rootOk = Root.available()
        if (rootOk == true) runCatching { refresh() }
    }

    // 守护运行中定时刷新（PID / 救砖次数会变）
    LaunchedEffect(state.daemonRunning) {
        while (state.daemonRunning) {
            delay(2000)
            runCatching { refresh() }
        }
    }

    // 守护状态与前台服务保持一致：运行就挂服务，停了就撤，避免进程被冻结
    LaunchedEffect(state.daemonRunning) {
        if (state.daemonRunning) DaemonService.start(context) else DaemonService.stop(context)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        // 转场固定用 MiuixDefault：关掉「预测性返回」只去掉手势预览的圆角裁剪与遮罩，
        // 普通返回的滑动动画必须保留 —— 之前这里退化成 NavTransitions.None，
        // 结果连普通返回的动画也一起砍掉了，返回变成硬切。
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(
            enableCornerClip = UiConfigState.predictiveBack,
            cornerClipRadius = 32.dp,
            cornerClipMode = NavCornerClipMode.All,
            dimAmount = if (UiConfigState.predictiveBack) 0.5f else 0f,
            blockInputDuringTransition = false,
            backdropColor = MiuixTheme.colorScheme.surface,
        ),
    ) {
        entry<Route.Main> {
            MainScaffold(
                state = state,
                rootOk = rootOk,
                busy = busy,
                onPickRescue = { backStack.add(Route.Picker(PickerTarget.RESCUE)) },
                onPickTarget = { backStack.add(Route.Picker(PickerTarget.TARGET)) },
                onStart = start@{
                    val target = state.targetPath
                    if (target.isBlank()) {
                        toast(context, "请先选择待测界面")
                        return@start
                    }
                    busy = true
                    busyTitle = "启动测试"
                    busySub = "正在准备救砖文件…"
                    scope.launch {
                        val out = Module.testRun(target)
                        if (out.contains("OK")) {
                            busySub = "已启动"
                            runCatching { refresh() }
                            DaemonService.start(context)
                        } else {
                            busyTitle = "启动失败"
                            busySub = firstLine(out)
                        }
                        delay(700)
                        busy = false
                        busyTitle = null
                    }
                },
                onStop = {
                    busy = true
                    busyTitle = "停止守护"
                    busySub = "正在结束进程…"
                    scope.launch {
                        val out = Module.stop()
                        runCatching { refresh() }
                        DaemonService.stop(context)
                        busyTitle = null
                        busy = false
                        toast(context, firstLine(out).ifBlank { "已停止" })
                    }
                },
                onOpenLogs = { backStack.add(Route.Logs) },
                onOpenUiSettings = { backStack.add(Route.UiSettings) },
                onOpenVolumeCheck = { backStack.add(Route.VolumeCheck) },
                onOpenCrashLog = { backStack.add(Route.CrashLogs) },
                onOpenThirdPartyLicenses = { backStack.add(Route.ThirdPartyLicenses) },
                onSetConf = { k, v ->
                    scope.launch {
                        Module.setConf(k, v)
                        runCatching { refresh() }
                    }
                },
                onRescueToggle = { off ->
                    scope.launch {
                        val out = Module.setRescueOff(off)
                        runCatching { refresh() }
                        if (!out.contains("OK")) toast(context, firstLine(out))
                    }
                },
            )
        }

        entry<Route.Logs> {
            LogPage(
                running = state.daemonRunning,
                onBack = { backStack.removeLastOrNull() },
            )
        }

        entry<Route.UiSettings> {
            UiSettingsPage(onBack = { backStack.removeLastOrNull() })
        }

        entry<Route.VolumeCheck> {
            VolumeCheckPage(
                onBack = { backStack.removeLastOrNull() },
                onFinished = { scope.launch { runCatching { refresh() } } },
            )
        }

        entry<Route.CrashLogs> {
            CrashLogPage(onBack = { backStack.removeLastOrNull() })
        }

        entry<Route.ThirdPartyLicenses> {
            ThirdPartyLicensesPage(onBack = { backStack.removeLastOrNull() })
        }

        entry<Route.Picker> { r ->
            PickerPage(
                target = r.target,
                onCancel = { backStack.removeLastOrNull() },
                onPicked = { path ->
                    backStack.removeLastOrNull()
                    scope.launch {
                        if (r.target == PickerTarget.RESCUE) {
                            // 复制到模块根并统一改名 MiuiSystemUI.apk
                            busy = true
                            busyTitle = "救砖界面至模块根目录"
                            busySub = "正在复制…"
                            val out = Module.prepare(path)
                            if (out.contains("OK")) {
                                busySub = "已完成"
                                runCatching { refresh() }
                            } else {
                                busyTitle = "复制失败"
                                busySub = firstLine(out)
                            }
                            delay(600)
                            busy = false
                            busyTitle = null
                        } else {
                            Module.setConf("TARGET_APK_PATH", path)
                            runCatching { refresh() }
                            toast(context, "已选择待测界面")
                        }
                    }
                },
            )
        }
    }

    // 等待框：复制几十兆 APK 时要看得见状态，且不允许误触关闭
    busyTitle?.let { title ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                shape = RoundedCornerShape(22.dp),
                color = MiuixTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        title,
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (busySub.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            busySub,
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }

    // 上次崩溃的现场：直接把堆栈摊出来，用户复制走就能贴给我
    crashText?.let { text ->
        MiuixDialog(
            title = "上次启动时崩溃了",
            onDismiss = { dismissCrash() },
        ) {
            if (UiConfigState.safeMode) {
                Text(
                    "已连续崩溃多次，已自动关闭玻璃效果与预测性返回（安全模式）。",
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.subtitle,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text.take(1200),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 18,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "文件位置：" + (CrashLog.externalCrashFile()?.absolutePath
                    ?: CrashLog.lastCrashFile()?.absolutePath ?: "未知"),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = { dismissCrash() }) { Text("关闭") }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        cm?.setPrimaryClip(
                            android.content.ClipData.newPlainText("crash", text)
                        )
                        toast(context, "已复制堆栈")
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("复制堆栈") }
            }
        }
    }

    // 没有 root：直接给两个选择，别让用户点进一堆"操作失败"
    if (showRootDialog) {
        MiuixDialog(
            title = "需要 ROOT 权限",
            onDismiss = { showRootDialog = false },
        ) {
            Text(
                "本应用通过 su 调用模块里的 webctl.sh 和 action.sh。" +
                    "没有 root 就读不到模块目录，也起不了救砖守护。",
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "请在 su 授权框里选「允许」后重新打开；" +
                    "如果之前点过拒绝，去 root 管理器的授权列表里改回来。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = { showRootDialog = false }) { Text("忽略") }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { (context as? android.app.Activity)?.finish() },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("退出") }
            }
        }
    }
}

@Composable
private fun MainScaffold(
    state: ModuleState,
    rootOk: Boolean?,
    busy: Boolean,
    onPickRescue: () -> Unit,
    onPickTarget: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenUiSettings: () -> Unit,
    onOpenVolumeCheck: () -> Unit,
    onOpenCrashLog: () -> Unit,
    onOpenThirdPartyLicenses: () -> Unit,
    onSetConf: (String, String) -> Unit,
    onRescueToggle: (Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selected by remember { mutableIntStateOf(0) }
    val navItems = remember {
        listOf(
            GlassNavItem("主页", MiuixIcons.Home),
            GlassNavItem("设置", MiuixIcons.Settings),
            GlassNavItem("关于", MiuixIcons.Info),
        )
    }
    val scope = rememberCoroutineScope()

    // 渲染阶段埋点：崩了能知道是第几个 tab / 哪一层出的问题
    LaunchedEffect(selected) {
        CrashLog.stage("主界面 tab=${listOf("主页", "设置", "关于").getOrElse(selected) { "?" }}")
    }

    // 用 settledPage（动画结束后的落点）而不是 currentPage：
    // 底栏点「关于」时 pager 从 0 滚到 2，currentPage 中途会路过 1（设置），
    // 若用它同步 selected，底栏会在动画中途把选中态跳到「设置」，
    // 反过来又触发一次 animateScrollToPage(1) —— 结果永远停在设置页，
    // 这正是「关于页必须在设置页才能点底栏进入」的根因。
    LaunchedEffect(pagerState.settledPage) {
        selected = pagerState.settledPage
    }

    // 系统返回键：在「设置」「关于」上按返回 → 回到主页，而不是退出应用
    // （底栏三页是同一个 pager，不是三条路由，所以要在这一层自己吃掉返回）。
    val mainBackState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = mainBackState,
        isBackEnabled = pagerState.currentPage != 0,
        onBackCompleted = { scope.launch { pagerState.animateScrollToPage(0) } },
    )

    // 底栏采样源：kyant backdrop，采样整个页面内容（含滚动中的列表）
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = kyantRememberLayerBackdrop()
    CompositionLocalProvider(LocalIsWideScreen provides shouldShowSplitPane()) {
        // 底栏是浮在内容上的（内容延伸到它后面，玻璃采样才有东西），
        // 所以不走 Scaffold 的 bottomBar —— 那会把内容顶开，玻璃后面就是空背景
        val pagePadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues()
                .calculateBottomPadding() + 70.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
        ) {
            // 采样层只包住页面内容。底栏必须放在这个 Box 之外 ——
            // 否则底栏自己被录进它要采样的那一层，形成自采样递归，绘制时直接崩。
            // （Sonify 的 NavPageVisual / NavBottomVisual 同样是两个平级节点。）
            Box(
                Modifier
                    .fillMaxSize()
                    .kyantLayerBackdrop(backdrop),
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> HomePage(
                            padding = pagePadding,
                            state = state,
                            rootOk = rootOk,
                            busy = busy,
                            onPickRescue = onPickRescue,
                            onPickTarget = onPickTarget,
                            onStart = onStart,
                            onStop = onStop,
                            onOpenLogs = onOpenLogs,
                            onOpenVolumeCheck = onOpenVolumeCheck,
                            onOpenCrashLog = onOpenCrashLog,
                        )

                        1 -> SettingsPage(
                            padding = pagePadding,
                            state = state,
                            onSetConf = onSetConf,
                            onRescueToggle = onRescueToggle,
                            onOpenUiSettings = onOpenUiSettings,
                        )

                        // 关于页是一级 tab（底栏页），无左上角返回键；
                        // 第三方许可证走二级界面，从「第三方许可证」入口进入。
                        else -> AboutPage(
                            padding = pagePadding,
                            state = state,
                            onOpenThirdPartyLicenses = onOpenThirdPartyLicenses,
                        )
                    }
                }
            }

            // 底栏浮层：垫在导航栏之上、宽度照原版收窄到 93%
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                GlassBottomNav(
                    selectedIndex = { selected },
                    onTabSelected = { idx ->
                        selected = idx
                        scope.launch { pagerState.animateScrollToPage(idx) }
                    },
                    items = navItems,
                    backdrop = backdrop,
                )
            }
        }
    }
}

/** webctl 的输出首行去掉 FAIL 前缀，拿来当提示语 */
private fun firstLine(out: String): String =
    out.lineSequence().firstOrNull { it.isNotBlank() }
        ?.removePrefix("FAIL")?.trim().orEmpty()

private fun toast(context: android.content.Context, msg: String) {
    if (msg.isNotBlank()) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
