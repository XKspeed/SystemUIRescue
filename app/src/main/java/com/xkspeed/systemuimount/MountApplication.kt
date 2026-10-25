package com.xkspeed.systemuimount

import android.app.Application
import com.xkspeed.systemuimount.core.Root

/**
 * libsu 的默认 builder 必须在任何 Shell.cmd 之前设置，
 * 放在 Application.onCreate 是最早且唯一的时机。
 */
class MountApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Root.init(this)
    }
}
