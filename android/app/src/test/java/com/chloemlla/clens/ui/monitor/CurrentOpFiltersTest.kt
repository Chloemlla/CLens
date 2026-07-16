package com.chloemlla.clens.ui.monitor

import com.chloemlla.clens.core.mongo.CurrentOpSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentOpFiltersTest {
    private fun op(
        op: String,
        secs: Long? = null,
        ns: String = "db.col",
        client: String = "1.2.3.4",
        raw: String = "{}",
        id: String = "1",
    ) = CurrentOpSummary(opId = id, op = op, ns = ns, secsRunning = secs, client = client, rawJson = raw)

    @Test
    fun slowFilter_usesThreshold() {
        assertTrue(CurrentOpFilters.matchesFilter(op("query", secs = 5), CurrentOpFilter.Slow))
        assertFalse(CurrentOpFilters.matchesFilter(op("query", secs = 4), CurrentOpFilter.Slow))
    }

    @Test
    fun writeFilter_matchesInsertAndCommandPayload() {
        assertTrue(CurrentOpFilters.matchesFilter(op("insert"), CurrentOpFilter.Write))
        assertTrue(
            CurrentOpFilters.matchesFilter(
                op("command", raw = "{\"insert\":\"col\"}"),
                CurrentOpFilter.Write,
            ),
        )
        assertFalse(CurrentOpFilters.matchesFilter(op("query"), CurrentOpFilter.Write))
    }

    @Test
    fun queryFilter_andTextSearch() {
        val item = op("getmore", ns = "orders.active", client = "app-1", id = "99")
        assertTrue(CurrentOpFilters.matchesFilter(item, CurrentOpFilter.Query))
        assertTrue(CurrentOpFilters.matchesQuery(item, "orders"))
        assertTrue(CurrentOpFilters.matchesQuery(item, "99"))
        assertFalse(CurrentOpFilters.matchesQuery(item, "missing"))
    }
}
