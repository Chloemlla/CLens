package com.chloemlla.clens

import android.app.Application
import android.content.Context
import android.util.Log
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.CrashReport
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.LumenCrashConfig

class ClensApplication : Application() {
    @Volatile
    private var lumenCrashInstallError: String? = null

    val startupCrashReport: CrashReport?
        get() = if (LumenCrash.isInstalled()) LumenCrash.startupCrashReport else null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installLumenCrashSdk()
        recordBreadcrumbSafe("Application.attachBaseContext")
    }

    override fun onCreate() {
        super.onCreate()
        installLumenCrashSdk()
        recordBreadcrumbSafe("Application.onCreate")
    }

    private fun installLumenCrashSdk() {
        if (LumenCrash.isInstalled()) return
        runCatching {
            val appName = runCatching { getString(R.string.app_name) }.getOrDefault("CLens")
            LumenCrash.install(
                this,
                LumenCrashConfig(
                    appDisplayName = appName,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    commitHash = BuildConfig.SHORT_HASH,
                    fileProviderAuthority = "${packageName}.fileprovider",
                    shareSubject = runCatching { getString(R.string.crash_report_share_subject) }.getOrNull(),
                    reportTitle = runCatching { getString(R.string.crash_report_title) }.getOrNull(),
                    reportMessage = runCatching { getString(R.string.crash_report_message) }.getOrNull(),
                ),
            )
            lumenCrashInstallError = null
        }.onFailure { error ->
            lumenCrashInstallError = error::class.java.name + ": " + (error.message ?: "unknown")
            Log.e(TAG, "LumenCrash.install failed", error)
        }
    }

    fun recordStartupCrash(throwable: Throwable): CrashReport? {
        return recordCrash(throwable)
    }

    fun recordCrash(throwable: Throwable): CrashReport? {
        if (!LumenCrash.isInstalled()) {
            Log.e(TAG, "LumenCrash is not installed; cannot persist crash", throwable)
            return null
        }
        return runCatching { LumenCrash.record(throwable) }
            .onFailure { error -> Log.e(TAG, "LumenCrash.record failed", error) }
            .getOrNull()
    }

    fun clearStartupCrashReport() {
        if (!LumenCrash.isInstalled()) return
        runCatching { LumenCrash.clearPendingReport() }
            .onFailure { error -> Log.e(TAG, "LumenCrash.clearPendingReport failed", error) }
    }

    fun loadPendingCrashReport(): CrashReport? {
        if (!LumenCrash.isInstalled()) return null
        return runCatching { LumenCrash.loadPendingReport() }
            .onFailure { error -> Log.e(TAG, "LumenCrash.loadPendingReport failed", error) }
            .getOrNull()
    }

    fun crashSdkStatusMessage(): String? = lumenCrashInstallError

    private fun recordBreadcrumbSafe(event: String) {
        if (!LumenCrash.isInstalled()) return
        runCatching { CrashBreadcrumbs.record(event) }
    }

    private companion object {
        const val TAG = "ClensApplication"
    }
}
