package com.chloemlla.clens.ui.security

import android.content.Context
import android.os.Process
import android.util.Log
import com.chloemlla.clens.BuildConfig
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.LumenCrash

/**
 * Central anti-tamper gate. Runs once at app startup and evaluates every
 * detection guard. In release builds a concrete detection (debugger, root,
 * emulator) terminates the process so the app cannot be analyzed on a hostile
 * environment. Debug builds only log, so development is unaffected.
 */
object HardeningGate {
    @Volatile
    private var ran = false

    fun bootstrap(context: Context) {
        if (ran) return
        ran = true

        val threats = mutableListOf<String>()
        runCatching { threats += AntiDebugGuard.assess(context) }
            .onFailure { Log.w(TAG, "AntiDebugGuard failed", it) }
        runCatching { threats += RootDetectionGuard.assess(context) }
            .onFailure { Log.w(TAG, "RootDetectionGuard failed", it) }
        runCatching { threats += EmulatorDetectionGuard.assess(context) }
            .onFailure { Log.w(TAG, "EmulatorDetectionGuard failed", it) }

        if (threats.isEmpty()) return

        val detail = threats.joinToString("; ")
        Log.w(TAG, "Hardening threats detected: $detail")
        if (LumenCrash.isInstalled()) {
            runCatching { CrashBreadcrumbs.record("hardening: $detail") }
        }

        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Debug build: logging hardening detections only.")
            return
        }

        Log.e(TAG, "Terminating process due to hardening detection: $detail")
        Process.killProcess(Process.myPid())
    }

    private const val TAG = "HardeningGate"
}