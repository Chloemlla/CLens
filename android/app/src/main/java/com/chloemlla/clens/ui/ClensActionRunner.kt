package com.chloemlla.clens.ui

import com.chloemlla.clens.core.util.SecretSanitizer
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClensActionRunner(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ClensUiState>,
) {
    private val actionMutex = Mutex()

    fun run(
        label: String,
        silent: Boolean = false,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            // Serialize all UI actions on one mutex.
            // Nested follow-up actions (for example connect -> refreshDatabases) launch a new
            // coroutine and wait here instead of failing with tryLock. Because the nested
            // launcher does not hold the lock itself, this queues rather than deadlocks.
            actionMutex.withLock {
                state.update {
                    it.copy(
                        loading = true,
                        error = null,
                        status = if (silent) it.status else (label + "..."),
                    )
                }
                CrashBreadcrumbs.record("Action start: $label")
                try {
                    block()
                    CrashBreadcrumbs.record("Action ok: $label")
                } catch (error: Throwable) {
                    val message = SecretSanitizer.sanitize(
                        error.message?.takeIf { it.isNotBlank() } ?: (label + " 失败"),
                    )
                    CrashBreadcrumbs.record("Action fail: $label")
                    state.update {
                        it.copy(
                            error = message,
                            status = "",
                        )
                    }
                } finally {
                    state.update { it.copy(loading = false) }
                }
            }
        }
    }
}

