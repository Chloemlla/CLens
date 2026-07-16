package com.chloemlla.clens

import android.os.Bundle
import android.util.Log
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
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.chloemlla.clens.core.mongo.MongoAdminRepository
import com.chloemlla.clens.core.mongo.MongoSessionManager
import com.chloemlla.clens.core.storage.LocalAppStore
import com.chloemlla.clens.core.storage.MongoConnectionStore
import com.chloemlla.clens.core.storage.DocumentDraftStore
import com.chloemlla.clens.core.storage.OpsCounterArchiveStore
import com.chloemlla.clens.core.storage.OfflineSnapshotStore
import com.chloemlla.clens.core.storage.StagingQueueStore
import com.chloemlla.clens.core.storage.SecurityPrefsStore
import com.chloemlla.clens.ui.ClensApp
import com.chloemlla.clens.ui.ClensTheme
import com.chloemlla.clens.ui.ClensViewModel
import com.chloemlla.clens.ui.security.BiometricLockGate
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.ui.LumenCrashReportScreen

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: ClensViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 16 forces edge-to-edge and removes windowOptOutEdgeToEdgeEnforcement.
        // We still keep decor fitting enabled so windowSoftInputMode=adjustResize shrinks
        // the Compose root when the IME opens (product requirement for editors).
        // This is not the deprecated opt-out flag; predictive back is enabled in the manifest.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        recordBreadcrumbSafe("MainActivity.onCreate")

        val app = application as ClensApplication
        val securityPrefs = SecurityPrefsStore(applicationContext)
        var initialStartupReport = app.loadPendingCrashReport()
        val initialViewModel = if (initialStartupReport == null) {
            createViewModel(app, securityPrefs)?.also { viewModel = it }
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
            val themeMode = remember { mutableStateOf(securityPrefs.getThemeMode()) }
            ClensTheme(themeMode = themeMode.value) {
                BiometricLockGate(securityPrefs = securityPrefs) {
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
                            ClensApp(
                                viewModel = initialViewModel,
                                onThemeModeChanged = { mode -> themeMode.value = mode },
                            )
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
    }

    private fun createViewModel(
        app: ClensApplication,
        securityPrefs: SecurityPrefsStore,
    ): ClensViewModel? {
        return try {
            val store = MongoConnectionStore(applicationContext)
            val localStore = LocalAppStore(applicationContext)
            val draftStore = DocumentDraftStore(applicationContext)
            val opsArchiveStore = OpsCounterArchiveStore(applicationContext)
            val snapshotStore = OfflineSnapshotStore(applicationContext)
            val stagingStore = StagingQueueStore(applicationContext)
            val sessionManager = MongoSessionManager()
            val repository = MongoAdminRepository(sessionManager)
            ViewModelProvider(
                this,
                ClensViewModel.Factory(applicationContext, store, localStore, draftStore, opsArchiveStore, snapshotStore, stagingStore, securityPrefs, sessionManager, repository),
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

