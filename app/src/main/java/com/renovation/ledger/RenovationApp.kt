package com.renovation.ledger

import android.app.Application
import com.didichuxing.doraemonkit.DoKit
import dagger.hilt.android.HiltAndroidApp

/**
 * DoraemonKit / DoKit 初始化。
 * 开源仓库：https://github.com/didi/DoKit
 * 官方接入：debug 用 dokitx，release 用 dokitx-no-op。
 */
@HiltAndroidApp
class RenovationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DoKit.Builder(this)
            .disableUpload()
            .alwaysShowMainIcon(true)
            .build()
    }
}
