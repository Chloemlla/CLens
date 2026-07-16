package com.chloemlla.clens.core.sync

import kotlin.math.min
import kotlin.math.pow

/**
 * Pure exponential backoff helpers for staging auto-retry after network recovery.
 * No ConnectivityManager / UI wiring here.
 */
object StagingRetryPolicy {
    const val BASE_DELAY_MS: Long = 1_000L
    const val MAX_DELAY_MS: Long = 60_000L
    const val MAX_ATTEMPTS: Int = 8
    const val DEFAULT_INITIAL_DELAY_MS: Long = BASE_DELAY_MS
    const val DEFAULT_MAX_DELAY_MS: Long = MAX_DELAY_MS
    const val DEFAULT_MULTIPLIER: Double = 2.0
    const val DEFAULT_MAX_ATTEMPTS: Int = MAX_ATTEMPTS

    /**
     * Delay before the next retry.
     * [attempt] is 0-based (0 = first retry after initial failure).
     * Defaults: 1s, 2s, 4s, 8s, 16s, 32s, 60s, 60s, ...
     */
    fun delayMillis(attempt: Int): Long = delayMs(attempt)

    fun delayMs(
        attemptIndex: Int,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        multiplier: Double = DEFAULT_MULTIPLIER,
    ): Long {
        val attempt = attemptIndex.coerceAtLeast(0)
        val base = initialDelayMs.coerceAtLeast(1L)
        val cap = maxDelayMs.coerceAtLeast(base)
        val factor = multiplier.coerceAtLeast(1.0)
        val scaled = base.toDouble() * factor.pow(attempt.toDouble())
        if (scaled.isNaN() || scaled.isInfinite()) return cap
        return min(scaled.toLong().coerceAtLeast(base), cap)
    }

    fun delaySequence(
        count: Int,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        multiplier: Double = DEFAULT_MULTIPLIER,
    ): List<Long> {
        val n = count.coerceAtLeast(0)
        return (0 until n).map { delayMs(it, initialDelayMs, maxDelayMs, multiplier) }
    }

    fun shouldRetry(attemptCount: Int, maxAttempts: Int = DEFAULT_MAX_ATTEMPTS): Boolean {
        return attemptCount < maxAttempts.coerceAtLeast(1)
    }
}
