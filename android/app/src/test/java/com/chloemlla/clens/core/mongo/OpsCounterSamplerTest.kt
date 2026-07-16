package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsCounterSamplerTest {

    @Test
    fun computeRates_dividesDeltasByElapsedSeconds() {
        val previous = OpCounterSnapshot(
            timestampMillis = 1_000L,
            insert = 10,
            query = 100,
            update = 5,
            delete = 1,
        )
        val current = OpCounterSnapshot(
            timestampMillis = 6_000L,
            insert = 20,
            query = 150,
            update = 15,
            delete = 11,
        )
        val rates = OpsCounterSampler.computeRates(previous, current, elapsedMillis = 5_000L)
        assertEquals(2.0, rates.insertQps, 0.0001)
        assertEquals(10.0, rates.queryQps, 0.0001)
        assertEquals(2.0, rates.updateQps, 0.0001)
        assertEquals(2.0, rates.deleteQps, 0.0001)
        assertEquals(5_000L, rates.elapsedMillis)
    }

    @Test
    fun computeRates_clampsNegativeDeltasToZero() {
        val previous = OpCounterSnapshot(insert = 50, query = 50, update = 50, delete = 50)
        val current = OpCounterSnapshot(insert = 10, query = 60, update = 40, delete = 50)
        val rates = OpsCounterSampler.computeRates(previous, current, elapsedMillis = 1_000L)
        assertEquals(0.0, rates.insertQps, 0.0001)
        assertEquals(10.0, rates.queryQps, 0.0001)
        assertEquals(0.0, rates.updateQps, 0.0001)
        assertEquals(0.0, rates.deleteQps, 0.0001)
    }

    @Test
    fun sampler_firstSnapshotIsBaselineOnly() {
        val sampler = OpsCounterSampler(capacity = 4)
        val result = sampler.onSnapshot(
            OpCounterSnapshot(
                timestampMillis = 1_000L,
                insert = 1,
                query = 2,
                update = 3,
                delete = 4,
            ),
        )
        assertNull(result)
        assertEquals(0, sampler.size)
    }

    @Test
    fun sampler_ringBufferEvictsOldestAndTracksPeaks() {
        val sampler = OpsCounterSampler(capacity = 3)
        val base = 0L
        // baseline
        sampler.onSnapshot(OpCounterSnapshot(timestampMillis = base, insert = 0, query = 0, update = 0, delete = 0))
        // t=1s -> insert 10/s
        sampler.onSnapshot(OpCounterSnapshot(timestampMillis = base + 1_000L, insert = 10, query = 0, update = 0, delete = 0))
        // t=2s -> insert 20/s (peak)
        sampler.onSnapshot(OpCounterSnapshot(timestampMillis = base + 2_000L, insert = 30, query = 5, update = 0, delete = 0))
        // t=3s -> insert 1/s
        val state = sampler.onSnapshot(
            OpCounterSnapshot(
                timestampMillis = base + 3_000L,
                insert = 31,
                query = 5,
                update = 0,
                delete = 0,
                connectionsCurrent = 7,
                connectionsActive = 2,
                connectionsAvailable = 100,
            ),
        )

        requireNotNull(state)
        assertEquals(3, state.points.size)
        assertEquals(1.0, state.current?.insertQps ?: -1.0, 0.0001)
        assertEquals(20.0, state.peak.insertQps, 0.0001)
        assertEquals(5.0, state.peak.queryQps, 0.0001)
        assertEquals(7, state.connectionsCurrent)
        assertEquals(2, state.connectionsActive)
        assertEquals(100, state.connectionsAvailable)

        // one more sample should keep capacity=3
        val next = sampler.onSnapshot(
            OpCounterSnapshot(timestampMillis = base + 4_000L, insert = 41, query = 5, update = 0, delete = 0),
        )
        requireNotNull(next)
        assertEquals(3, next.points.size)
        assertEquals(10.0, next.current?.insertQps ?: -1.0, 0.0001)
        assertTrue(next.points.first().insertQps > 0.0)
    }

    @Test
    fun sampler_resetClearsHistory() {
        val sampler = OpsCounterSampler()
        sampler.onSnapshot(OpCounterSnapshot(timestampMillis = 1, insert = 0))
        sampler.onSnapshot(OpCounterSnapshot(timestampMillis = 1001, insert = 5))
        assertEquals(1, sampler.size)
        sampler.reset()
        assertEquals(0, sampler.size)
        assertNull(sampler.currentState())
    }
}
