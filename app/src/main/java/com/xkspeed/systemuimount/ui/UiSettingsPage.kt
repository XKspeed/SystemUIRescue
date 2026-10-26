package com.xkspeed.systemuimount.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xkspeed.systemuimount.KeyColors
import com.xkspeed.systemuimount.ThemeState
import com.xkspeed.systemuimount.core.CrashLog
import com.xkspeed.systemuimount.ui.component.glass.GlassEffectState
import com.xkspeed.systemuimount.ui.component.glass.ScrollPage
import com.xkspeed.systemuimount.ui.components.BackIcon
import com.xkspeed.systemuimount.ui.components.SectionCard
import com.xkspeed.systemuimount.ui.util.pageContentPadding
import com.xkspeed.systemuimount.ui.util.pageScrollModifiers
import com.xkspeed.systemuimount.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 界面偏好（和主题分开存，重启都能恢复）。
 */
object UiConfigState {
    var enableBlur by mutableStateOf(true)
    /** 二级页转场与预测性返回预览；关掉以后返回是瞬时的，不做缩放/圆角动画 */
    var predictiveBack by mutableStateOf(true)

    /**
     * 安全模式：连续崩溃过（多半是液态玻璃这块的绘制炸了），
     * 此时关掉所有玻璃效果与预测性返回，让 App 至少能打开、能进设置。
     */
    var safeMode by mutableStateOf(false)

    fun load(context: Context) {
        val p = context.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        enableBlur = p.getBoolean("enable_blur", true)
        predictiveBack = p.getBoolean("predictive_back", true)
        safeMode = CrashLog.safeMode()
        GlassEffectState.load(context)
    }

    fun save(context: Context) {
        context.getSharedPreferences("ui_config", Context.MODE_PRIVATE).edit().apply {
            putBoolean("enable_blur", enableBlur)
            putBoolean("predictive_back", predictiveBack)
        }.apply()
    }
}

private val NAV_STYLES = listOf("普通悬浮", "液态玻璃")

private val THEME_MODES = listOf("跟随系统", "浅色", "深色", "莫奈跟随系统", "莫奈浅色", "莫奈深色")
private val THEME_MODE_SUMMARIES = listOf(
    "跟随系统的深浅色",
    "始终浅色",
    "始终深色",
    "按壁纸取色，跟随系统深浅",
    "按壁纸取色，浅色",
    "按壁纸取色，深色",
)

/**
 * 调色风格 / 色彩规范：与 miuix 示例 App 的 ThemePaletteStyle / ThemeColorSpec 一一对应，
 * 顺序必须与枚举声明顺序相同（靠下标索引切换）。
 */
private val PALETTE_STYLES = listOf(
    "TonalSpot", "Neutral", "Vibrant", "Expressive", "Rainbow",
    "FruitSalad", "Monochrome", "Fidelity", "Content",
)
private val PALETTE_STYLE_SUMMARIES = listOf(
    "经典色调（默认）",
    "低饱和中性",
    "高饱和鲜艳",
    "夸张表达",
    "彩虹渐变",
    "水果拼盘",
    "单色",
    "忠于原色",
    "跟随内容取色",
)

private val COLOR_SPECS = listOf("Spec2021", "Spec2025")
private val COLOR_SPEC_SUMMARIES = listOf(
    "Material You 2021",
    "Material You 2025（部分风格支持）",
)

@Composable
fun UiSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    val backdrop = rememberBlurBackdrop()

    val seedItems = remember { listOf("跟随壁纸") + KeyColors.map { it.first } }
    val monet = ThemeState.mode >= ThemeState.MODE_MONET_SYSTEM

    ScrollPage(
        title = "界面",
        onBack = onBack,
        bottomPadding = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding() + 24.dp,
    ) {
                item { SmallTitle("主题") }
                item {
                    SectionCard {
                        WindowDropdownPreference(
                            title = "主题模式",
                            summary = THEME_MODE_SUMMARIES[ThemeState.mode.coerceIn(0, 5)],
                            items = THEME_MODES,
                            selectedIndex = ThemeState.mode.coerceIn(0, 5),
                            onSelectedIndexChange = {
                                ThemeState.mode = it
                                ThemeState.save(context)
                            },
                        )
                        if (monet) {
                            WindowDropdownPreference(
                                title = "取色种子",
                                summary = if (ThemeState.seedIndex == 0)
                                    "按当前壁纸取色"
                                else
                                    "固定 ${KeyColors[ThemeState.seedIndex - 1].first}色",
                                items = seedItems,
                                selectedIndex = ThemeState.seedIndex.coerceIn(0, seedItems.size - 1),
                                onSelectedIndexChange = {
                                    ThemeState.seedIndex = it
                                    ThemeState.save(context)
                                },
                            )
                            // 调色风格 / 色彩规范：与示例 App 一致，只在"固定取色"时才有意义
                            //（跟随壁纸取色走系统动态色，忽略这两个参数）
                            if (ThemeState.seedIndex > 0) {
                                WindowDropdownPreference(
                                    title = "调色风格",
                                    summary = PALETTE_STYLE_SUMMARIES[
                                        ThemeState.paletteStyle.coerceIn(0, PALETTE_STYLES.size - 1)
                                    ],
                                    items = PALETTE_STYLES,
                                    selectedIndex = ThemeState.paletteStyle.coerceIn(0, PALETTE_STYLES.size - 1),
                                    onSelectedIndexChange = {
                                        ThemeState.paletteStyle = it
                                        ThemeState.save(context)
                                    },
                                )
                                WindowDropdownPreference(
                                    title = "色彩规范",
                                    summary = COLOR_SPEC_SUMMARIES[
                                        ThemeState.colorSpec.coerceIn(0, COLOR_SPECS.size - 1)
                                    ],
                                    items = COLOR_SPECS,
                                    selectedIndex = ThemeState.colorSpec.coerceIn(0, COLOR_SPECS.size - 1),
                                    onSelectedIndexChange = {
                                        ThemeState.colorSpec = it
                                        ThemeState.save(context)
                                    },
                                )
                            }
                        }
                    }
                }

                item { SmallTitle("玻璃效果") }
                item {
                    SectionCard {
                        SliderPreference(
                            title = "模糊强度",
                            summary = "调节玻璃控件的模糊程度，拖动即时生效",
                            value = GlassEffectState.blurScale,
                            onValueChange = {
                                GlassEffectState.blurScale = it
                                GlassEffectState.save(context)
                            },
                            valueRange = 0f..2f,
                            valueText = "${(GlassEffectState.blurScale * 100f).roundToInt()}%",
                        )
                        BasicComponent(
                            title = "重置",
                            summary = "恢复默认模糊强度（100%）",
                            onClick = {
                                GlassEffectState.blurScale = 1f
                                GlassEffectState.save(context)
                            },
                        )
                    }
                }

                item { SmallTitle("交互") }
                item {
                    SectionCard {
                        SwitchPreference(
                            title = "预测性返回",
                            summary = "返回手势拖动时预览上一页；关闭后返回是瞬时的",
                            checked = UiConfigState.predictiveBack,
                            onCheckedChange = {
                                UiConfigState.predictiveBack = it
                                UiConfigState.save(context)
                            },
                        )
                    }
                }

                item { SmallTitle("底栏") }
                item {
                    SectionCard {
                        SwitchPreference(
                            title = "底栏模糊",
                            summary = "关闭后底栏退化为磨砂胶囊，不做折射",
                            checked = UiConfigState.enableBlur,
                            onCheckedChange = {
                                UiConfigState.enableBlur = it
                                UiConfigState.save(context)
                            },
                        )
                    }
                }
            }
}
