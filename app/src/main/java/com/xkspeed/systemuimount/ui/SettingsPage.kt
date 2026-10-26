package com.xkspeed.systemuimount.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xkspeed.systemuimount.BuildConfig
import kotlin.math.roundToInt
import com.xkspeed.systemuimount.core.ModuleState
import com.xkspeed.systemuimount.ui.component.glass.ScrollPage
import com.xkspeed.systemuimount.ui.components.InfoRow
import com.xkspeed.systemuimount.ui.components.SectionCard
import com.xkspeed.systemuimount.ui.util.LocalIsWideScreen
import com.xkspeed.systemuimount.ui.util.pageContentPadding
import com.xkspeed.systemuimount.ui.util.pageScrollModifiers
import com.xkspeed.systemuimount.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LOG_MODE_LABELS = listOf("完全禁用", "仅记录日志", "日志 + 按键救砖", "日志 + 自动救砖")
private val LOG_MODE_SUMMARIES = listOf(
    "不记录日志，也不启动守护",
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

    val backdrop = rememberBlurBackdrop()

    // 拖动过程中只改本地值，松手才落盘，避免每帧一次 sed
    var logMode by remember(state.logMode) {
        mutableFloatStateOf((state.logMode.toIntOrNull() ?: 1).coerceIn(0, 3).toFloat())
    }
    var keyTimeout by remember(state.keyTimeout) {
        mutableFloatStateOf((state.keyTimeout.toIntOrNull() ?: 30).coerceIn(10, 60).toFloat())
    }
    var fatalThreshold by remember(state.fatalThreshold) {
        mutableFloatStateOf((state.fatalThreshold.toIntOrNull() ?: 3).coerceIn(1, 10).toFloat())
    }
    val logModeIndex = logMode.roundToInt().coerceIn(0, 3)

    ScrollPage(
        title = "设置",
        // 与主页/关于一致：底栏高度（70dp）已由外层 pagePadding 给出，这里只加一点呼吸
        bottomPadding = padding.calculateBottomPadding() + 16.dp,
    ) {
                item { SmallTitle("运行") }
                item {
                    SectionCard {
                        // 4 档：0 禁用 / 1 仅日志 / 2 按键救砖 / 3 自动救砖
                        SliderPreference(
                            title = "运行模式",
                            summary = LOG_MODE_SUMMARIES[logModeIndex],
                            value = logMode,
                            onValueChange = { logMode = it },
                            onValueChangeFinished = {
                                onSetConf("LOG_MODE", logModeIndex.toString())
                            },
                            valueRange = 0f..3f,
                            steps = 2,
                            showKeyPoints = true,
                            keyPoints = listOf(0f, 1f, 2f, 3f),
                            magnetThreshold = 0.18f,
                            valueText = LOG_MODE_LABELS[logModeIndex],
                        )
                        HorizontalDivider(
                            color = MiuixTheme.colorScheme.dividerLine,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SliderPreference(
                            title = "等待超时",
                            summary = "替换界面后等 SystemUI 重启的秒数",
                            value = keyTimeout,
                            onValueChange = { keyTimeout = it },
                            onValueChangeFinished = {
                                onSetConf("KEY_TIMEOUT", keyTimeout.roundToInt().toString())
                            },
                            valueRange = 10f..60f,
                            steps = 9,
                            valueText = "${keyTimeout.roundToInt()} 秒",
                        )
                        HorizontalDivider(
                            color = MiuixTheme.colorScheme.dividerLine,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SliderPreference(
                            title = "崩溃阈值",
                            summary = "累计多少条 fatal 日志后自动救砖",
                            value = fatalThreshold,
                            onValueChange = { fatalThreshold = it },
                            onValueChangeFinished = {
                                onSetConf("FATAL_THRESHOLD", fatalThreshold.roundToInt().toString())
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            valueText = "${fatalThreshold.roundToInt()} 次",
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

                // 版本信息统一放到「关于」tab，这里只留排查问题时才看的路径
                item { SmallTitle("模块") }
                item {
                    SectionCard {
                        InfoRow("模块路径", "/data/adb/modules/systemui_mount")
                    }
                }
            }
}
