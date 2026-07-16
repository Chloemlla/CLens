package com.chloemlla.clens.core.mongo

/**
 * Pure in-session opcounters sampler.
 * Diffs consecutive [OpCounterSnapshot] values into QPS and keeps a fixed ring buffer for charts.
 */
class OpsCounterSampler(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val points = ArrayDeque<OpsCounterPoint>(capacity.coerceAtLeast(2))
    private var lastSnapshot: OpCounterSnapshot? = null
    private var peakInsertQps = 0.0
    private var peakQueryQps = 0.0
    private var peakUpdateQps = 0.0
    private var peakDeleteQps = 0.0

    val size: Int
        get() = points.size

    fun reset() {
        points.clear()
        lastSnapshot = null
        peakInsertQps = 0.0
        peakQueryQps = 0.0
        peakUpdateQps = 0.0
        peakDeleteQps = 0.0
    }

    /**
     * Ingest a new serverStatus snapshot.
     * The first sample only seeds the baseline and returns null.
     */
    fun onSnapshot(snapshot: OpCounterSnapshot): OpsCounterSampleState? {
        val previous = lastSnapshot
        lastSnapshot = snapshot
        if (previous == null) {
            return null
        }
        val elapsed = snapshot.timestampMillis - previous.timestampMillis
        if (elapsed <= 0L) {
            return null
        }
        val rates = computeRates(previous, snapshot, elapsed)
        val point = OpsCounterPoint(
            timestampMillis = snapshot.timestampMillis,
            insertQps = rates.insertQps,
            queryQps = rates.queryQps,
            updateQps = rates.updateQps,
            deleteQps = rates.deleteQps,
            connectionsCurrent = snapshot.connectionsCurrent,
            connectionsActive = snapshot.connectionsActive,
            connectionsAvailable = snapshot.connectionsAvailable,
        )
        append(point)
        peakInsertQps = maxOf(peakInsertQps, point.insertQps)
        peakQueryQps = maxOf(peakQueryQps, point.queryQps)
        peakUpdateQps = maxOf(peakUpdateQps, point.updateQps)
        peakDeleteQps = maxOf(peakDeleteQps, point.deleteQps)
        return snapshotState(current = point)
    }

    fun currentState(): OpsCounterSampleState? {
        val current = points.lastOrNull() ?: return null
        return snapshotState(current)
    }

    private fun snapshotState(current: OpsCounterPoint): OpsCounterSampleState {
        return OpsCounterSampleState(
            points = points.toList(),
            current = current,
            peak = OpsCounterPeak(
                insertQps = peakInsertQps,
                queryQps = peakQueryQps,
                updateQps = peakUpdateQps,
                deleteQps = peakDeleteQps,
            ),
            connectionsCurrent = current.connectionsCurrent,
            connectionsActive = current.connectionsActive,
            connectionsAvailable = current.connectionsAvailable,
        )
    }

    private fun append(point: OpsCounterPoint) {
        if (points.size >= capacity) {
            points.removeFirst()
        }
        points.addLast(point)
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 72
        const val DEFAULT_INTERVAL_MS: Long = 5_000L

        fun computeRates(
            previous: OpCounterSnapshot,
            current: OpCounterSnapshot,
            elapsedMillis: Long,
        ): OpCounterRates {
            require(elapsedMillis > 0L) { "elapsedMillis must be > 0" }
            val seconds = elapsedMillis / 1000.0
            return OpCounterRates(
                insertQps = rate(previous.insert, current.insert, seconds),
                queryQps = rate(previous.query, current.query, seconds),
                updateQps = rate(previous.update, current.update, seconds),
                deleteQps = rate(previous.delete, current.delete, seconds),
                elapsedMillis = elapsedMillis,
            )
        }

        private fun rate(previous: Long, current: Long, seconds: Double): Double {
            val delta = (current - previous).coerceAtLeast(0L)
            if (seconds <= 0.0) return 0.0
            return delta / seconds
        }
    }
}
