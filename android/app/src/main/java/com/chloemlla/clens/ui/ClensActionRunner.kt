package com.chloemlla.clens.ui

import com.chloemlla.clens.core.util.SecretSanitizer
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

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
            if (!actionMutex.tryLock()) {
                state.update {
                    it.copy(error = "已有操作进行中，请等待完成后再试。")
                }
                return@launch
            }
            try {
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
            } finally {
                actionMutex.unlock()
            }
        }
    }
}

