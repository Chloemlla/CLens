package com.chloemlla.clens.ui.security

import android.content.Context
import android.os.Process
import android.util.Log
import com.chloemlla.clens.BuildConfig
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.LumenCrash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Central anti-tamper gate. Runs once at app startup and evaluates every
 * detection guard. In release builds a concrete detection (debugger, root,
 * emulator, Frida, Xposed, signature mismatch) terminates the process so the
 * app cannot be analyzed on a hostile environment. Debug builds only log, so
 * development is unaffected.
 *
 * Termination is belt-and-braces: SIGKILL, System.exit, and a hard
 * Runtime.halt() that bypasses shutdown hooks, plus a delayed re-kill from a
 * fresh coroutine stack in case the first calls were intercepted.
 *
 * A periodic watchdog re-checks the vectors whose presence can change after
 * startup (a debugger can attach, Frida/Xposed can inject at any time) and
 * enforces the same kill policy in release builds.
 */
object HardeningGate {
    @Volatile
    private var ran = false

    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun bootstrap(context: Context) {
        if (ran) return
        ran = true

        if (BuildConfig.DEBUG) {
            runCatching { SignatureIntegrityGuard.logCurrentDigest(context) }
        }

        val startup = mutableListOf<String>()
        runCatching { startup += RootDetectionGuard.assess(context) }
            .onFailure { Log.w(TAG, "RootDetectionGuard failed", it) }
        runCatching { startup += EmulatorDetectionGuard.assess(context) }
            .onFailure { Log.w(TAG, "EmulatorDetectionGuard failed", it) }
        runCatching { startup += AntiDebugGuard.assess(context) }
            .onFailure { Log.w(TAG, "AntiDebugGuard failed", it) }
        runCatching { startup += FridaXposedDetectionGuard.assess(context) }
            .onFailure { Log.w(TAG, "FridaXposedDetectionGuard failed", it) }
        runCatching { startup += SignatureIntegrityGuard.assess(context) }
            .onFailure { Log.w(TAG, "SignatureIntegrityGuard failed", it) }

        handleStartup(context, startup)
        startWatchdog(context)
    }

    private fun startWatchdog(context: Context) {
        watchdogScope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                val threats = mutableListOf<String>()
                runCatching { threats += AntiDebugGuard.assess(context) }
                    .onFailure { Log.w(TAG, "watchdog: AntiDebugGuard failed", it) }
                runCatching { threats += FridaXposedDetectionGuard.assess(context) }
                    .onFailure { Log.w(TAG, "watchdog: FridaXposedDetectionGuard failed", it) }
                runCatching { threats += SignatureIntegrityGuard.assess(context) }
                    .onFailure { Log.w(TAG, "watchdog: SignatureIntegrityGuard failed", it) }
                runCatching { threats += RootDetectionGuard.assess(context) }
                    .onFailure { Log.w(TAG, "watchdog: RootDetectionGuard failed", it) }
                if (threats.isNotEmpty()) {
                    enforceIfRelease("Hardening watchdog: ${threats.joinToString("; ")}")
                }
            }
        }
    }

    private fun handleStartup(context: Context, threats: List<String>) {
        if (threats.isEmpty()) return
        val detail = threats.joinToString("; ")
        Log.w(TAG, "Hardening threats detected: $detail")
        recordBreadcrumb("hardening: $detail")
        enforceIfRelease("Terminating process due to hardening detection: $detail")
    }

    private fun enforceIfRelease(message: String) {
        if (BuildConfig.DEBUG) return
        Log.e(TAG, message)
        hardTerminate()
    }

    private fun hardTerminate() {
        val pid = Process.myPid()
        // Belt and braces: if one call is intercepted, the next still lands.
        // Runtime.halt() bypasses Java shutdown hooks and is the hardest exit.
        runCatching { Process.killProcess(pid) }
        runCatching { System.exit(EXIT_CODE) }
        runCatching { Runtime.getRuntime().halt(EXIT_CODE) }
        // If everything above was swallowed, a re-kill from a fresh coroutine
        // stack (outside the hookable caller frame) still terminates.
        watchdogScope.launch {
            delay(RE_KILL_DELAY_MS)
            runCatching { Process.killProcess(pid) }
            runCatching { Runtime.getRuntime().halt(EXIT_CODE) }
        }
    }

    private fun recordBreadcrumb(detail: String) {
        if (!LumenCrash.isInstalled()) return
        runCatching { CrashBreadcrumbs.record(detail) }
    }

    private const val TAG = "HardeningGate"
    private const val WATCHDOG_INTERVAL_MS = 3000L
    private const val RE_KILL_DELAY_MS = 800L
    private const val EXIT_CODE = 2
}
