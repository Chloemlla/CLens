package com.chloemlla.clens

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        recordBreadcrumbSafe("MainActivity.onCreate")

        val app = application as ClensApplication
        var initialStartupReport = app.loadPendingCrashReport()
        val initialViewModel = if (initialStartupReport == null) {
            createViewModel(app)?.also { viewModel = it }
        } else {
            null
        }
        if (initialStartupReport == null && initialViewModel == null) {
            initialStartupReport = app.loadPendingCrashReport()
        }
        val startupError = if (initialStartupReport == null && initialViewModel == null) {
            app.crashSdkStatusMessage()
                ?: "CLens failed to start. No crash report was available."
        } else {
            null
        }

        setContent {
            var startupReport by remember { mutableStateOf(initialStartupReport) }
            var bootstrapError by remember { mutableStateOf(startupError) }
            ClensTheme {
                val report = startupReport
                when {
                    report != null -> {
                        LumenCrashReportScreen(
                            report = report,
                            onContinue = {
                                app.clearStartupCrashReport()
                                startupReport = null
                                if (initialViewModel == null) {
                                    recreate()
                                }
                            },
                        )
                    }
                    initialViewModel != null -> {
                        ClensApp(viewModel = initialViewModel)
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "CLens failed to start",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = bootstrapError ?: "Unknown startup failure.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    bootstrapError = null
                                    recreate()
                                },
                            ) {
                                Text("Retry")
                            }
                        }
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
            Log.e(TAG, "Failed to create ClensViewModel", throwable)
            app.recordStartupCrash(throwable)
            null
        }
    }

    private fun recordBreadcrumbSafe(event: String) {
        if (!LumenCrash.isInstalled()) return
        runCatching { CrashBreadcrumbs.record(event) }
            .onFailure { error -> Log.w(TAG, "breadcrumb failed: $event", error) }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
