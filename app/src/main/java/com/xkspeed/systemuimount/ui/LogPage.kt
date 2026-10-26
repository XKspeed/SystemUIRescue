package com.xkspeed.systemuimount.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xkspeed.systemuimount.core.Module
import com.xkspeed.systemuimount.ui.component.glass.ScrollPage
import com.xkspeed.systemuimount.ui.components.BackIcon
import com.xkspeed.systemuimount.ui.util.pageScrollModifiers
import com.xkspeed.systemuimount.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 日志页。
 *
 * 守护运行时每 1.5 秒拉一次最新日志并自动滚到底；
 * 没在运行时只读一次，右上角「刷新」可手动重读。
 *
 * 返回键必须自己接管，否则会一路传到 Activity 把应用退掉。
 */
@Composable
fun LogPage(running: Boolean, onBack: () -> Unit) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    // 返回交给 NavDisplay：它自己接了预测性返回，页面再注册 BackHandler 会把
    // 手势的进度回调吃掉，返回就变成硬跳转
    val backdrop = rememberBlurBackdrop()

    LaunchedEffect(running, tick) {
        loading = lines.isEmpty()
        while (true) {
            val text = Module.tailLog(500)
            lines = if (text.isBlank()) listOf("（暂无日志）") else text.lines()
            loading = false
            if (!running) break
            delay(1500)
        }
    }

    // 运行中自动跟随最新一行；不在运行时不动，免得打断用户回看
    LaunchedEffect(lines.size) {
        if (running && lines.size > 1) {
            runCatching { listState.scrollToItem(lines.size - 1) }
        }
    }

    ScrollPage(
        title = "日志",
        onBack = onBack,
        bottomPadding = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding() + 24.dp,
    ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        line.ifEmpty { " " },
                        modifier = Modifier.fillMaxWidth(),
                        color = when {
                            line.contains("FAIL") || line.contains("错误") ->
                                MiuixTheme.colorScheme.error
                            line.contains("OK") || line.contains("成功") ->
                                MiuixTheme.colorScheme.primary
                            else -> MiuixTheme.colorScheme.onSurface
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (loading) {
                    item {
                        Text(
                            "正在读取日志…",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                }
            }
}
