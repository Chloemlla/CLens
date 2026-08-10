package com.chloemlla.clens.ui.security

import android.view.Window
import android.view.WindowManager

object ScreenCaptureJail {
    fun apply(window: Window) {
        runCatching {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }
}