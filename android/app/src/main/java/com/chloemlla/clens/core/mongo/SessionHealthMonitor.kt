package com.chloemlla.clens.core.mongo

import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * Lightweight session probe + bounded reconnect helper.
 * UI remains outside this type; callers supply [SessionHealthCallbacks].
 */
interface SessionHealthCallbacks {
    fun onHealthOk()
    fun onHealthFailed(message: String)
    fun onReconnectStarted(attempt: Int, maxAttempts: Int)
    fun onReconnectSucceeded(message: String)
    fun onReconnectFailed(message: String)
    fun onReconnectExhausted(message: String)
}

class SessionHealthMonitor(
    private val sessionManager: MongoSessionManager,
) {
    suspend fun ping(): ConnectionTestResult = sessionManager.healthPing()

    /**
     * If the active session is still alive, report healthy.
     * Otherwise attempt a gentle reconnect with exponential backoff.
     *
     * @return true when the session is healthy (or was restored).
     */
    suspend fun ensureHealthyOrReconnect(
        callbacks: SessionHealthCallbacks,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    ): Boolean {
        if (!sessionManager.isConnected && sessionManager.activeProfile == null) {
            return false
        }

        val pingResult = runCatching { sessionManager.healthPing() }
        val healthy = pingResult.getOrNull()?.takeIf { it.ok }
        if (healthy != null) {
            callbacks.onHealthOk()
            return true
        }

        val failureMessage = pingResult.exceptionOrNull()?.message
            ?: pingResult.getOrNull()?.message
            ?: "会话无响应"
        callbacks.onHealthFailed(failureMessage)
        return reconnectWithBackoff(
            callbacks = callbacks,
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            maxDelayMs = maxDelayMs,
        )
    }

    suspend fun reconnectWithBackoff(
        callbacks: SessionHealthCallbacks,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    ): Boolean {
        if (sessionManager.activeProfile == null) {
            callbacks.onReconnectExhausted("没有可重连的活动连接配置")
            return false
        }

        var delayMs = initialDelayMs.coerceAtLeast(100L)
        val attempts = maxAttempts.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            callbacks.onReconnectStarted(attempt, attempts)
            val result = runCatching { sessionManager.reconnectActive() }
            val ok = result.getOrNull()?.takeIf { it.ok }
            if (ok != null) {
                callbacks.onReconnectSucceeded(ok.message)
                return true
            }
            val message = result.exceptionOrNull()?.message
                ?: result.getOrNull()?.message
                ?: "重连失败"
            callbacks.onReconnectFailed(message)
            if (attempt < attempts) {
                delay(delayMs)
                delayMs = min(delayMs * 2, maxDelayMs)
            }
        }
        callbacks.onReconnectExhausted("自动重连已达上限，请手动重连")
        return false
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS: Int = 3
        const val DEFAULT_INITIAL_DELAY_MS: Long = 800L
        const val DEFAULT_MAX_DELAY_MS: Long = 5_000L
    }
}
