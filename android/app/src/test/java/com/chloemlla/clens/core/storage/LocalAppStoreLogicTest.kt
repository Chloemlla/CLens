package com.chloemlla.clens.core.storage

import com.chloemlla.clens.core.mongo.QueryHistoryEntry
import com.chloemlla.clens.core.mongo.QueryFavoriteEntry
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

    @Test
    fun queryFavoriteTitleIncludesTarget() {
        val favorite = QueryFavoriteEntry(
            id = "f1",
            name = "活跃用户",
            database = "app",
            collection = "users",
            filterJson = """{"status":"active"}""",
        )
        assertEquals("活跃用户 · app.users", favorite.title)
        assertEquals("{}", QueryFavoriteEntry(id = "f2", name = "仅名称").filterJson)
        assertEquals("仅名称", QueryFavoriteEntry(id = "f3", name = "仅名称").title)
    }
}
