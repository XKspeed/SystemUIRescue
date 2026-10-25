package com.xkspeed.systemuimount.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xkspeed.systemuimount.core.ModuleState
import com.xkspeed.systemuimount.ui.components.InfoRow
import com.xkspeed.systemuimount.ui.components.SectionCard
import com.xkspeed.systemuimount.ui.components.StepperRow
import com.xkspeed.systemuimount.ui.util.BlurredBar
import com.xkspeed.systemuimount.ui.util.LocalIsWideScreen
import com.xkspeed.systemuimount.ui.util.pageContentPadding
import com.xkspeed.systemuimount.ui.util.pageScrollModifiers
import com.xkspeed.systemuimount.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LOG_MODE_LABELS = listOf("仅记录日志", "日志 + 按键救砖", "日志 + 自动救砖")
private val LOG_MODE_SUMMARIES = listOf(
    "只写日志，出问题自己按音量键处理",
    "监听崩溃日志，音量+ 触发救砖、音量- 取消",
    "崩溃次数或日志特征达标后自动救砖，不用按键",
)

@Composable
fun SettingsPage(
    padding: PaddingValues,
    state: ModuleState,
    onSetConf: (String, String) -> Unit,
    onRescueToggle: (Boolean) -> Unit,
    onOpenUiSettings: () -> Unit = {},
) {
    val isWideScreen = LocalIsWideScreen.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    val scrollProgress by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else 0f }
    }
    val backdrop = rememberBlurBackdrop()
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) {
        derivedStateOf { backdrop != null && scrollProgress == 1f }
    }

    val logModeIndex = ((state.logMode.toIntOrNull() ?: 1) - 1).coerceIn(0, 2)
    val keyTimeout = state.keyTimeout.toIntOrNull() ?: 30
    val fatalThreshold = state.fatalThreshold.toIntOrNull() ?: 3

    Scaffold(
        topBar = {
            val barColor = when {
                blurActive -> Color.Transparent
                collapsed -> MiuixTheme.colorScheme.surface
                else -> Color.Transparent
            }
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    title = "设置",
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val scrollPadding = pageContentPadding(innerPadding, padding, isWideScreen)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = scrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = scrollPadding.calculateTopPadding(),
                    start = scrollPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = scrollPadding.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = scrollPadding.calculateBottomPadding(),
                ),
            ) {
                item { SmallTitle("运行") }
                item {
                    SectionCard {
                        OverlayDropdownPreference(
                            title = "运行模式",
                            summary = LOG_MODE_SUMMARIES[logModeIndex],
                            items = LOG_MODE_LABELS,
                            selectedIndex = logModeIndex,
                            onSelectedIndexChange = { idx ->
                                onSetConf("LOG_MODE", (idx + 1).toString())
                            },
                        )
                        HorizontalDivider(
                            color = MiuixTheme.colorScheme.dividerLine,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        StepperRow(
                            title = "等待超时",
                            summary = "替换界面后等 SystemUI 重启的秒数",
                            value = keyTimeout,
                            unit = "秒",
                            range = 5..120,
                            step = 5,
                            onChange = { onSetConf("KEY_TIMEOUT", it.toString()) },
                        )
                        HorizontalDivider(
                            color = MiuixTheme.colorScheme.dividerLine,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        StepperRow(
                            title = "崩溃阈值",
                            summary = "累计多少条 fatal 日志后自动救砖",
                            value = fatalThreshold,
                            unit = "次",
                            range = 1..20,
                            onChange = { onSetConf("FATAL_THRESHOLD", it.toString()) },
                        )
                    }
                }

                item { SmallTitle("外观") }
                item {
                    SectionCard {
                        ArrowPreference(
                            title = "界面与主题",
                            summary = "主题模式 · 莫奈取色 · 底栏模糊 · 悬浮底栏",
                            onClick = onOpenUiSettings,
                        )
                    }
                }

                item { SmallTitle("系统") }
                item {
                    SectionCard {
                        SwitchPreference(
                            checked = state.rescueOff,
                            onCheckedChange = { onRescueToggle(it) },
                            title = "屏蔽系统救援",
                            summary = "把 AMS 的进程崩溃次数限制拉高，避免系统抢在前面杀掉 SystemUI",
                        )
                        HorizontalDivider(
                            color = MiuixTheme.colorScheme.dividerLine,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SwitchPreference(
                            checked = state.debug,
                            onCheckedChange = { onSetConf("DEBUG", if (it) "true" else "false") },
                            title = "调试日志",
                            summary = "在 config/debug.log 里记录每一步动作",
                        )
                    }
                }

                item { SmallTitle("运行数据") }
                item {
                    SectionCard {
                        InfoRow("已救砖", "${state.protectCount} 次")
                        InfoRow("SystemUI", if (state.pid.isNotBlank()) "PID ${state.pid}" else "未运行")
                        InfoRow("崩溃上限", state.rescueLimitText)
                        InfoRow("日志目录", state.logDir)
                    }
                }

                item { SmallTitle("关于") }
                item {
                    SectionCard {
                        InfoRow("版本", if (state.version.isNotBlank()) "v${state.version}" else "—")
                        InfoRow("模块路径", "/data/adb/modules/systemui_mount")
                    }
                }
            }
        }
    }
}
