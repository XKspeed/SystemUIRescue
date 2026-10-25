package com.xkspeed.systemuimount.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xkspeed.systemuimount.core.DirEntry
import com.xkspeed.systemuimount.core.Module
import com.xkspeed.systemuimount.ui.components.ListRow
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 选文件给谁用 */
enum class PickerTarget { RESCUE, TARGET }

@Composable
fun PickerPage(
    target: PickerTarget,
    onCancel: () -> Unit,
    /** 选中后回调完整路径，由调用方决定是 prepare 还是记成待测文件 */
    onPicked: (String) -> Unit,
) {
    var dir by remember {
        mutableStateOf("/storage/emulated/0")
    }
    var items by remember { mutableStateOf<List<DirEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    fun goUp() {
        val parent = dir.substringBeforeLast('/', "")
        if (dir != "/") dir = if (parent.isBlank()) "/" else parent
    }

    suspend fun load(path: String) {
        loading = true
        items = Module.ls(path)
        loading = false
    }

    LaunchedEffect(dir) { load(dir) }

    BackHandler(enabled = true) {
        if (dir != "/") goUp() else onCancel()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = when (target) {
                    PickerTarget.RESCUE -> "选择救砖界面"
                    PickerTarget.TARGET -> "选择待测界面"
                },
                subtitle = dir,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    Text(
                        "‹",
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable(enabled = dir != "/") { goUp() }
                            .padding(horizontal = 12.dp),
                    )
                },
                actions = {
                    Text(
                        "取消",
                        style = MiuixTheme.textStyles.main,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onCancel() }
                            .padding(horizontal = 14.dp),
                    )
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            if (loading) {
                Column(
                    Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在读取目录…",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 4.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    if (dir != "/") {
                        item {
                            ListRow(
                                title = "↑  上级目录",
                                subtitle = dir.substringBeforeLast('/', "/").ifBlank { "/" },
                                onClick = { goUp() },
                            )
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        }
                    }

                    items(items, key = { it.path }) { entry ->
                        if (entry.isDir) {
                            ListRow(
                                title = "📁  ${entry.name}",
                                trailing = "›",
                                onClick = { dir = entry.path },
                            )
                        } else {
                            ListRow(
                                title = "📦  ${entry.name}",
                                subtitle = Module.fmtSize(entry.size),
                                trailing = "选择",
                                onClick = { onPicked(entry.path) },
                            )
                        }
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    }

                    if (items.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "此目录没有 APK 或子目录",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.subtitle,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
