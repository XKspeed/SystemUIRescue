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
import com.xkspeed.systemuimount.ui.components.BackIcon
import com.xkspeed.systemuimount.ui.components.SectionCard
import com.xkspeed.systemuimount.ui.util.BlurredBar
import com.xkspeed.systemuimount.ui.util.pageContentPadding
import com.xkspeed.systemuimount.ui.util.pageScrollModifiers
import com.xkspeed.systemuimount.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 界面偏好（和主题分开存，重启都能恢复）。
 */
object UiConfigState {
    var enableBlur by mutableStateOf(true)
    var useFloatingNavigationBar by mutableStateOf(false)
    var blurRadius by mutableStateOf(15f)

    fun load(context: Context) {
        val p = context.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        enableBlur = p.getBoolean("enable_blur", true)
        useFloatingNavigationBar = p.getBoolean("use_floating_navbar", false)
        blurRadius = p.getFloat("blur_radius", 15f)
    }

    fun save(context: Context) {
        context.getSharedPreferences("ui_config", Context.MODE_PRIVATE).edit().apply {
            putBoolean("enable_blur", enableBlur)
            putBoolean("use_floating_navbar", useFloatingNavigationBar)
            putFloat("blur_radius", blurRadius)
        }.apply()
    }
}

private val THEME_MODES = listOf("跟随系统", "浅色", "深色", "莫奈跟随系统", "莫奈浅色", "莫奈深色")
private val THEME_MODE_SUMMARIES = listOf(
    "跟随系统的深浅色",
    "始终浅色",
    "始终深色",
    "按壁纸取色，跟随系统深浅",
    "按壁纸取色，浅色",
    "按壁纸取色，深色",
)

@Composable
fun UiSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
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

    val seedItems = remember { listOf("跟随壁纸") + KeyColors.map { it.first } }
    val monet = ThemeState.mode >= ThemeState.MODE_MONET_SYSTEM

    Scaffold(
        topBar = {
            val barColor = when {
                blurActive -> Color.Transparent
                collapsed -> MiuixTheme.colorScheme.surface
                else -> Color.Transparent
            }
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    title = "界面",
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    navigationIcon = { BackIcon(onClick = onBack) },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val scrollPadding = pageContentPadding(
                innerPadding,
                innerPadding,
                false,
                extraStart = WindowInsets.displayCutout.asPaddingValues()
                    .calculateLeftPadding(LayoutDirection.Ltr),
                extraEnd = WindowInsets.displayCutout.asPaddingValues()
                    .calculateRightPadding(LayoutDirection.Ltr),
            )

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
                    bottom = scrollPadding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item { SmallTitle("主题") }
                item {
                    SectionCard {
                        OverlayDropdownPreference(
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
                            OverlayDropdownPreference(
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
                        }
                    }
                }

                item { SmallTitle("底栏") }
                item {
                    SectionCard {
                        SwitchPreference(
                            title = "底栏模糊",
                            summary = "滚动时底栏透出下方内容的模糊",
                            checked = UiConfigState.enableBlur,
                            onCheckedChange = {
                                UiConfigState.enableBlur = it
                                UiConfigState.save(context)
                            },
                        )
                        if (UiConfigState.enableBlur) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "模糊半径",
                                        style = MiuixTheme.textStyles.main,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "%.0f px".format(UiConfigState.blurRadius),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        style = MiuixTheme.textStyles.subtitle,
                                    )
                                }
                                Slider(
                                    value = UiConfigState.blurRadius,
                                    onValueChange = {
                                        UiConfigState.blurRadius = it
                                        UiConfigState.save(context)
                                    },
                                    valueRange = 0f..30f,
                                )
                            }
                        }
                        SwitchPreference(
                            title = "悬浮底栏",
                            summary = "底栏脱离屏幕边缘，浮在内容之上",
                            checked = UiConfigState.useFloatingNavigationBar,
                            onCheckedChange = {
                                UiConfigState.useFloatingNavigationBar = it
                                UiConfigState.save(context)
                            },
                        )
                    }
                }
            }
        }
    }
}
