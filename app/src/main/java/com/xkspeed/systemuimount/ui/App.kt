package com.xkspeed.systemuimount.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xkspeed.systemuimount.core.DaemonService
import com.xkspeed.systemuimount.core.Module
import com.xkspeed.systemuimount.core.ModuleState
import com.xkspeed.systemuimount.core.Root
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

sealed interface Route {
    data object Main : Route
    data object Logs : Route
    data class Picker(val target: PickerTarget) : Route
}

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var route by remember { mutableStateOf<Route>(Route.Main) }
    var state by remember { mutableStateOf(ModuleState()) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyTitle by remember { mutableStateOf<String?>(null) }
    var busySub by remember { mutableStateOf("") }

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

    when (val r = route) {
        Route.Main -> MainScaffold(
            state = state,
            rootOk = rootOk,
            busy = busy,
            onPickRescue = { route = Route.Picker(PickerTarget.RESCUE) },
            onPickTarget = { route = Route.Picker(PickerTarget.TARGET) },
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
            onOpenLogs = { route = Route.Logs },
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

        Route.Logs -> LogPage(
            running = state.daemonRunning,
            onBack = { route = Route.Main },
        )

        is Route.Picker -> PickerPage(
            target = r.target,
            onCancel = { route = Route.Main },
            onPicked = { path ->
                route = Route.Main
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
    onSetConf: (String, String) -> Unit,
    onRescueToggle: (Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    var selected by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selected = pagerState.currentPage
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selected == 0,
                    onClick = {
                        selected = 0
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = MiuixIcons.Home,
                    label = "主页",
                )
                NavigationBarItem(
                    selected = selected == 1,
                    onClick = {
                        selected = 1
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = MiuixIcons.Settings,
                    label = "设置",
                )
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> HomePage(
                    padding = padding,
                    state = state,
                    rootOk = rootOk,
                    busy = busy,
                    onPickRescue = onPickRescue,
                    onPickTarget = onPickTarget,
                    onStart = onStart,
                    onStop = onStop,
                    onOpenLogs = onOpenLogs,
                )

                else -> SettingsPage(
                    padding = padding,
                    state = state,
                    onSetConf = onSetConf,
                    onRescueToggle = onRescueToggle,
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
