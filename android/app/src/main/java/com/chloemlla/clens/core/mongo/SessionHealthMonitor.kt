package com.chloemlla.clens.core.mongo

import kotlin.math.min
import kotlinx.coroutines.delay
import org.bson.BsonDocument
import org.bson.BsonInt32

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
    private var healthData = ConnectionHealthData(connectionId = "")

    fun bindConnection(connectionId: String, healthData: ConnectionHealthData) {
        this.healthData = healthData
    }

    suspend fun ping(): ConnectionTestResult = sessionManager.healthPing()

    /**
     * Measures round-trip time by executing a lightweight `ping` command.
     * Returns null on failure.
     */
    suspend fun measureLatency(): Long? {
        return try {
            val start = System.currentTimeMillis()
            val result = sessionManager.requireClient()
                .getDatabase("admin")
                .runCommand(BsonDocument("ping", BsonInt32(1)))
            val latency = System.currentTimeMillis() - start
            if (isCommandOk(result)) latency else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun isCommandOk(document: org.bson.Document): Boolean {
        return when (val value = document["ok"]) {
            is Number -> value.toDouble() == 1.0
            else -> false
        }
    }

    fun recordLatencySample(latencyMs: Long) {
        healthData = healthData.withLatencySample(latencyMs)
    }

    /**
     * Record the result of an operation (success or failure).
     */
    fun recordOpResult(success: Boolean) {
        healthData = healthData.withOpResult(success)
    }

    /**
     * Compute the current health score (0-100) from stored samples.
     */
    fun computeHealthScore(): ConnectionHealthScore {
        val latencyScore = computeLatencyScore()
        val errorRateScore = computeErrorRateScore()
        val uptimeScore = computeUptimeScore()
        val overall = (latencyScore * 0.4 + errorRateScore * 0.4 + uptimeScore * 0.2).toInt()
            .coerceIn(0, 100)
        val latencyAvg = healthData.latencySamples.takeIf { it.isNotEmpty() }?.average()?.toLong()
        return ConnectionHealthScore(
            overall = overall,
            latencyScore = latencyScore,
            errorRateScore = errorRateScore,
            uptimeScore = uptimeScore,
            latencyAvgMs = latencyAvg,
            successCount = healthData.errorSamples.count { !it },
            totalCount = healthData.errorSamples.size,
            uptimeMillis = System.currentTimeMillis() - healthData.connectedAtMillis,
        )
    }

    fun getHealthData(): ConnectionHealthData = healthData

    fun resetHealthData() {
        healthData = ConnectionHealthData(
            connectionId = healthData.connectionId,
            connectedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun computeLatencyScore(): Int {
        val samples = healthData.latencySamples.takeLast(10)
        if (samples.isEmpty()) return 100
        val avg = samples.average()
        return when {
            avg <= 0 -> 100
            avg < 50 -> 100 - (avg / 5).toInt()
            avg < 100 -> 90 - ((avg - 50) * 0.3).toInt()
            avg < 200 -> 75 - ((avg - 100) * 0.25).toInt()
            avg < 500 -> 50 - ((avg - 200) * 0.083).toInt()
            avg < 1000 -> 25 - ((avg - 500) * 0.05).toInt()
            else -> 0
        }.coerceIn(0, 100)
    }

    private fun computeErrorRateScore(): Int {
        val errors = healthData.errorSamples.takeLast(10)
        if (errors.isEmpty()) return 100
        val successRate = errors.count { !it }.toDouble() / errors.size
        return (successRate * 100).toInt().coerceIn(0, 100)
    }

    private fun computeUptimeScore(): Int {
        val uptimeMillis = System.currentTimeMillis() - healthData.connectedAtMillis
        val uptimeHours = uptimeMillis / (1000.0 * 60.0 * 60.0)
        return when {
            uptimeHours < 1.0 -> 100
            uptimeHours < 6.0 -> 80
            uptimeHours < 24.0 -> 60
            else -> 40
        }
    }

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
