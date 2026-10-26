package com.xkspeed.systemuimount.ui

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xkspeed.systemuimount.core.DirEntry
import com.xkspeed.systemuimount.core.Module
import com.xkspeed.systemuimount.core.Root
import com.xkspeed.systemuimount.ui.components.BackIcon
import com.xkspeed.systemuimount.ui.components.DialogActions
import com.xkspeed.systemuimount.ui.components.ListRow
import com.xkspeed.systemuimount.ui.components.MiuixDialog
import com.xkspeed.systemuimount.ui.component.glass.ScrollPage
import com.xkspeed.systemuimount.ui.util.pageScrollModifiers
import com.xkspeed.systemuimount.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 选文件给谁用 */
@kotlinx.serialization.Serializable
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
    /** 长按顶栏路径弹出的「跳转到目录」对话框 */
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }
    var jumpError by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val backdrop = rememberBlurBackdrop()

    fun goUp() {
        val parent = dir.substringBeforeLast('/', "")
        if (dir != "/") dir = if (parent.isBlank()) "/" else parent
    }

    suspend fun load(path: String) {
        loading = true
        items = Module.ls(path)
        loading = false
    }

    /** 校验目标路径是不是存在的目录（root 权限，能穿透 /data） */
    suspend fun checkDir(path: String): Boolean {
        val out = Root.exec("[ -d ${Root.q(path)} ] && echo OK || echo NO")
        return out.trim().endsWith("OK")
    }

    LaunchedEffect(dir) {
        load(dir)
        // 进新目录回到顶部：否则列表一短，视图停在半空，顶栏也还缩着
        runCatching { listState.scrollToItem(0) }
    }

    // 系统返回键 = 返回上一级目录；已经在根目录才退出。
    // 必须用 NavigationBackHandler（预测性返回），用 activity 的 BackHandler
    // 会把手势的进度回调吃掉，返回变成硬跳转。
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        // 根目录时让出去，交给 NavDisplay 弹出路由（这样才有转场动画）；
        // 只有"还能往上"的时候才由本页吃掉这次返回
        isBackEnabled = dir != "/",
        onBackCompleted = { goUp() },
    )

    ScrollPage(
        title = when (target) {
            PickerTarget.RESCUE -> "选择救砖界面"
            PickerTarget.TARGET -> "选择待测界面"
        },
        subtitle = dir,
        // 折叠后顶栏那行显示当前路径：点一下回上级，长按输路径跳转
        smallTitle = dir,
        onTitleClick = { goUp() },
        onTitleLongClick = {
            jumpInput = dir
            jumpError = null
            showJumpDialog = true
        },
        // 左上角 = 返回上一级目录；到根了就只能退出选择器
        onBack = { if (dir == "/") onCancel() else goUp() },
        backIcon = if (dir == "/") MiuixIcons.Close else MiuixIcons.Back,
        bottomPadding = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding() + 24.dp,
        listState = listState,
    ) {
        if (loading) {
            item(key = "loading") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在读取目录…",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }
            }
        } else {
            if (dir != "/") {
                item(key = "up") {
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
                item(key = "empty") {
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

    // 长按顶栏路径弹出的「跳转到目录」：居中对话框
    if (showJumpDialog) {
        MiuixDialog(
            title = "跳转到目录",
            onDismiss = { showJumpDialog = false },
        ) {
            TextField(
                value = jumpInput,
                onValueChange = { jumpInput = it; jumpError = null },
                label = "目录路径",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (jumpError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    jumpError!!,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            DialogActions(
                confirmText = "跳转",
                onConfirm = {
                    val raw = jumpInput.trim()
                    if (raw.isBlank()) {
                        jumpError = "路径不能为空"
                        return@DialogActions
                    }
                    val target = if (raw.startsWith("/")) raw else "/$raw"
                    scope.launch {
                        if (checkDir(target)) {
                            showJumpDialog = false
                            dir = target
                        } else {
                            jumpError = "目录不存在或无法访问"
                        }
                    }
                },
                cancelText = "取消",
                onCancel = { showJumpDialog = false },
            )
        }
    }
}