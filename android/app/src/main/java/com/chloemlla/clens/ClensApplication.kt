package com.chloemlla.clens

import android.app.Application
import android.content.Context
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.CrashReport
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.LumenCrashConfig

class ClensApplication : Application() {
    val startupCrashReport: CrashReport?
        get() = LumenCrash.startupCrashReport

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installLumenCrashSdk()
        CrashBreadcrumbs.record("Application.attachBaseContext")
    }

    override fun onCreate() {
        super.onCreate()
        installLumenCrashSdk()
        CrashBreadcrumbs.record("Application.onCreate")
    }

    private fun installLumenCrashSdk() {
        if (LumenCrash.isInstalled()) return
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
    }

    fun recordStartupCrash(throwable: Throwable): CrashReport {
        return LumenCrash.record(throwable)
    }

    fun recordCrash(throwable: Throwable): CrashReport {
        return LumenCrash.record(throwable)
    }

    fun clearStartupCrashReport() {
        LumenCrash.clearPendingReport()
    }
}
