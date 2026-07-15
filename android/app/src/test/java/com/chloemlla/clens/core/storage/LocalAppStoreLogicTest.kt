package com.chloemlla.clens.core.storage

import com.chloemlla.clens.core.mongo.QueryHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAppStoreLogicTest {
    @Test
    fun queryHistoryTitleFormat() {
        val entry = QueryHistoryEntry(
            id = "1",
            modeAggregate = true,
            database = "db",
            collection = "c",
        )
        assertEquals("agg db.c", entry.title)
        val find = entry.copy(modeAggregate = false)
        assertEquals("find db.c", find.title)
        assertTrue(find.filterJson == "{}")
    }
}
