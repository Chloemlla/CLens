package com.chloemlla.clens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.chloemlla.clens.core.mongo.MongoAdminRepository
import com.chloemlla.clens.core.mongo.MongoSessionManager
import com.chloemlla.clens.core.storage.LocalAppStore
import com.chloemlla.clens.core.storage.MongoConnectionStore
import com.chloemlla.clens.ui.ClensApp
import com.chloemlla.clens.ui.ClensTheme
import com.chloemlla.clens.ui.ClensViewModel
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.ui.LumenCrashReportScreen

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ClensViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashBreadcrumbs.record("MainActivity.onCreate")

        val app = application as ClensApplication
        var initialStartupReport = LumenCrash.loadPendingReport()
        val initialViewModel = if (initialStartupReport == null) {
            createViewModel(app)?.also { viewModel = it }
        } else {
            null
        }
        if (initialStartupReport == null && initialViewModel == null) {
            initialStartupReport = LumenCrash.loadPendingReport()
        }

        setContent {
            var startupReport by remember { mutableStateOf(initialStartupReport) }
            ClensTheme {
                val report = startupReport
                if (report != null) {
                    LumenCrashReportScreen(
                        report = report,
                        onContinue = {
                            app.clearStartupCrashReport()
                            startupReport = null
                            if (initialViewModel == null) recreate()
                        },
                    )
                } else {
                    initialViewModel?.let { readyViewModel ->
                        ClensApp(viewModel = readyViewModel)
                    }
                }
            }
        }
    }

    private fun createViewModel(app: ClensApplication): ClensViewModel? {
        return try {
            val store = MongoConnectionStore(applicationContext)
            val localStore = LocalAppStore(applicationContext)
            val sessionManager = MongoSessionManager()
            val repository = MongoAdminRepository(sessionManager)
            ViewModelProvider(
                this,
                ClensViewModel.Factory(store, localStore, sessionManager, repository),
            )[ClensViewModel::class.java]
        } catch (throwable: Throwable) {
            app.recordStartupCrash(throwable)
            null
        }
    }
}
