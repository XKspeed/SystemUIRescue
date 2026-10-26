package com.xkspeed.systemuimount.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xkspeed.systemuimount.BuildConfig
import com.xkspeed.systemuimount.R
import com.xkspeed.systemuimount.core.ModuleState
import com.xkspeed.systemuimount.ui.component.glass.ScrollPage
import com.xkspeed.systemuimount.ui.components.InfoRow
import com.xkspeed.systemuimount.ui.components.SectionCard
import com.xkspeed.systemuimount.ui.util.LocalIsWideScreen
import com.xkspeed.systemuimount.ui.util.pageContentPadding
import com.xkspeed.systemuimount.ui.util.pageScrollModifiers
import com.xkspeed.systemuimount.ui.util.rememberBlurBackdrop
import com.xkspeed.systemuimount.ui.util.rememberDrawablePainter
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页（底栏第三个 tab）。
 *
 * 结构照示例 App：大图标 + 应用名 + 版本，往上滑时整块按不同节奏淡出缩小，
 * 顶栏同时从透明切成模糊。
 *
 * [onBack]：顶栏左上角返回键。底栏切页时"退出"只能滑回上一页（设置页），
 * 回不到主页；这个返回键直接回主页。
 */
@Composable
fun AboutPage(padding: PaddingValues, state: ModuleState, onBack: () -> Unit) {
    val isWideScreen = LocalIsWideScreen.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    // 前 600px 的滚动量当作淡出进度；再往上就完全收起
    val scrollProgress by remember {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset / 600f).coerceIn(0f, 1f)
            }
        }
    }
    val backdrop = rememberBlurBackdrop()

    // 三条内容错开时间淡出，看起来像"一层层收走"而不是整块消失
    val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
    val nameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
    val versionProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)

    val appVersion = "v${BuildConfig.VERSION_NAME}"
    val moduleVersion = if (state.version.isNotBlank()) "v${state.version}" else "未读到"
    val iconPainter = rememberDrawablePainter(R.mipmap.ic_launcher)

    ScrollPage(
        title = "关于",
        // 底栏 tab：底部要含底栏高度（外层 Scaffold 的 padding），再加呼吸
        bottomPadding = padding.calculateBottomPadding() + 16.dp,
        // 顶栏返回键：直接回主页（不是滑回设置页）
        onBack = onBack,
    ) {
                // ---- 图标 + 名字 + 版本 ----
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(100.dp)
                                .graphicsLayer {
                                    alpha = 1 - iconProgress
                                    scaleX = 1 - iconProgress * 0.05f
                                    scaleY = 1 - iconProgress * 0.05f
                                }
                                .squircleClip(cornerRadius = 26.dp),
                        ) {
                            if (iconPainter != null) {
                                Image(
                                    painter = iconPainter,
                                    contentDescription = null,
                                    modifier = Modifier.size(100.dp),
                                )
                            }
                        }
                        Text(
                            "SystemUI救援",
                            modifier = Modifier
                                .padding(top = 14.dp, bottom = 4.dp)
                                .graphicsLayer {
                                    alpha = 1 - nameProgress
                                    scaleX = 1 - nameProgress * 0.05f
                                    scaleY = 1 - nameProgress * 0.05f
                                },
                            color = MiuixTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                        )
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = 1 - versionProgress
                                    scaleX = 1 - versionProgress * 0.05f
                                    scaleY = 1 - versionProgress * 0.05f
                                },
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            text = appVersion,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item { SmallTitle("版本") }
                item {
                    SectionCard {
                        InfoRow("应用版本", appVersion)
                        InfoRow("模块版本", moduleVersion)
                        InfoRow("最低系统", "Android 15 (SDK 35)")
                    }
                }

                item { SmallTitle("链接") }
                item {
                    SectionCard {
                        ArrowPreference(
                            title = "GitHub 仓库",
                            summary = "github.com/XKspeed/SystemUIRescue",
                            onClick = {
                                runCatching {
                                    uriHandler.openUri("https://github.com/XKspeed/SystemUIRescue")
                                }
                            },
                        )
                    }
                }

                item { SmallTitle("说明") }
                item {
                    SectionCard {
                        Text(
                            "这个应用替代模块的 WebUI，用原生界面控制换界面与救砖流程。",
                            color = MiuixTheme.colorScheme.onSurface,
                            style = MiuixTheme.textStyles.subtitle,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Text(
                            "守护运行期间会挂一个前台服务，避免进程被系统冻结；" +
                                "停止守护后服务自动退出。所有操作都通过 su 调用模块里的脚本，" +
                                "不会改动系统分区。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
}
