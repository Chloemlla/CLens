package com.chloemlla.clens.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagingRetryPolicyTest {
    @Test
    fun exponentialBackoffSequenceCapsAtMax() {
        val seq = StagingRetryPolicy.delaySequence(
            count = 8,
            initialDelayMs = 1_000L,
            maxDelayMs = 60_000L,
            multiplier = 2.0,
        )
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L),
            seq,
        )
        assertEquals(1_000L, StagingRetryPolicy.delayMillis(0))
        assertEquals(1_000L, StagingRetryPolicy.delayMs(0))
        assertEquals(60_000L, StagingRetryPolicy.delayMs(10))
        assertEquals(StagingRetryPolicy.BASE_DELAY_MS, StagingRetryPolicy.DEFAULT_INITIAL_DELAY_MS)
        assertEquals(StagingRetryPolicy.MAX_DELAY_MS, StagingRetryPolicy.DEFAULT_MAX_DELAY_MS)
    }

    @Test
    fun negativeAttemptClampedToZero() {
        assertEquals(500L, StagingRetryPolicy.delayMs(-3, initialDelayMs = 500L, maxDelayMs = 5_000L))
    }

    @Test
    fun shouldRetryRespectsMaxAttempts() {
        assertTrue(StagingRetryPolicy.shouldRetry(0, maxAttempts = 3))
        assertTrue(StagingRetryPolicy.shouldRetry(2, maxAttempts = 3))
        assertFalse(StagingRetryPolicy.shouldRetry(3, maxAttempts = 3))
        assertTrue(StagingRetryPolicy.shouldRetry(0, maxAttempts = 0)) // coerced to 1
        assertTrue(StagingRetryPolicy.shouldRetry(0))
        assertFalse(StagingRetryPolicy.shouldRetry(StagingRetryPolicy.MAX_ATTEMPTS))
    }
}
