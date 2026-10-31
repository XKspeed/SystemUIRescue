package com.xkspeed.systemuimount.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xkspeed.systemuimount.core.CrashLog
import com.xkspeed.systemuimount.ui.component.glass.ScrollPage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 崩溃日志页
 *
 * 崩溃现场由 [CrashLog] 落在应用私有目录 这里把它显示出来
 * 用户可以直接复制内容反馈 —— 不用 root、不用连 adb
 */
@Composable
fun CrashLogPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val text = CrashLog.lastCrashText().ifBlank { "（没有崩溃记录）" }
        lines = text.lines()
    }

    ScrollPage(
        title = "崩溃日志",
        onBack = onBack,
        bottomPadding = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding() + 24.dp,
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    "文件位置：" + (CrashLog.externalCrashFile()?.absolutePath
                        ?: CrashLog.lastCrashFile()?.absolutePath ?: "未知"),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val text = CrashLog.lastCrashText()
                            if (text.isBlank()) return@Button
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                            cm?.setPrimaryClip(
                                android.content.ClipData.newPlainText("crash", text)
                            )
                            android.widget.Toast.makeText(context, "已复制堆栈", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("复制全部") }
                }
            }
        }
        itemsIndexed(lines) { _, line ->
            Text(
                line.ifEmpty { " " },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = if (line.contains("Exception") || line.contains("Error")) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
