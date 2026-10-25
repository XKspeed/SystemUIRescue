package com.xkspeed.systemuimount

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 主题状态。0=跟随系统 1=浅色 2=深色 3/4/5=莫奈（跟随/浅/深）。
 * 写法和 ApkEditorMiuix 的 ThemeState 一致：改完立即落盘，重启恢复。
 */
object ThemeState {
    var mode by mutableIntStateOf(0)
    var seedIndex by mutableIntStateOf(0)

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2
    const val MODE_MONET_SYSTEM = 3
    const val MODE_MONET_LIGHT = 4
    const val MODE_MONET_DARK = 5

    private const val PREFS = "theme_config"
    private const val KEY_MODE = "mode"
    private const val KEY_SEED = "seed_index"

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = p.getInt(KEY_MODE, MODE_SYSTEM)
        seedIndex = p.getInt(KEY_SEED, 0)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MODE, mode)
            .putInt(KEY_SEED, seedIndex)
            .apply()
    }
}
