package com.chloemlla.clens

import android.app.Application
import android.content.Context
import com.chloemlla.clens.core.crash.CrashBreadcrumbs
import com.chloemlla.clens.core.crash.CrashReport
import com.chloemlla.clens.core.crash.CrashReportStore
import com.tencent.mmkv.MMKV

class ClensApplication : Application() {
    val crashReports: CrashReportStore by lazy { CrashReportStore(this) }
    private var crashExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    var startupCrashReport: CrashReport? = null
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashBreadcrumbs.record("Application.attachBaseContext")
        installCrashReporter()
    }

    override fun onCreate() {
        super.onCreate()
        CrashBreadcrumbs.record("Application.onCreate")
        installCrashReporter()
        initializeMmkvOrRecordCrash()
    }

    private fun installCrashReporter() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (defaultExceptionHandler === crashExceptionHandler) return

        lateinit var handler: Thread.UncaughtExceptionHandler
        handler = Thread.UncaughtExceptionHandler { thread, throwable ->
            val report = runCatching { CrashReport.fromThrowable(throwable) }
                .getOrElse { CrashReport.fromThrowableFallback(throwable, it) }
            runCatching { crashReports.save(report) }
            if (defaultExceptionHandler != null && defaultExceptionHandler !== handler) {
                defaultExceptionHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
        crashExceptionHandler = handler
        Thread.setDefaultUncaughtExceptionHandler(handler)
        CrashBreadcrumbs.record("Crash reporter installed")
    }

    private fun initializeMmkvOrRecordCrash() {
        runCatching { MMKV.initialize(this) }
            .onSuccess { CrashBreadcrumbs.record("MMKV initialized") }
            .onFailure(::recordCrash)
    }

    fun recordStartupCrash(throwable: Throwable): CrashReport {
        return recordCrash(throwable)
    }

    fun recordCrash(throwable: Throwable): CrashReport {
        CrashBreadcrumbs.record("Crash captured: ${throwable::class.java.name}")
        val report = runCatching { CrashReport.fromThrowable(throwable) }
            .getOrElse { CrashReport.fromThrowableFallback(throwable, it) }
        startupCrashReport = report
        runCatching { CrashReportStore(this).save(report) }
        return report
    }

    fun clearStartupCrashReport() {
        startupCrashReport = null
        crashReports.clear()
    }
}