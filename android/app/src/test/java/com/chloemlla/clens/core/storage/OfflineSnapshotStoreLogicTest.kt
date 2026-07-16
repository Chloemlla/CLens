package com.chloemlla.clens.core.storage

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSnapshotStoreLogicTest {
    @Test
    fun defaultNameFormatsDbCollectionAndTimestamp() {
        // 2020-01-02 03:04 UTC+0 millis depends on locale TZ; pin Locale only and check prefix/shape.
        val name = OfflineSnapshotStore.defaultName(
            database = "app",
            collection = "users",
            createdAtMillis = 1_577_934_000_000L, // 2020-01-02 03:00:00 UTC
            locale = Locale.US,
        )
        assertTrue(name.startsWith("app.users "))
        assertEquals(2, name.split(' ', limit = 2).size)
        // yyyy-MM-dd HH:mm
        val stamp = name.removePrefix("app.users ")
        assertTrue(stamp.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")))
    }

    @Test
    fun clampLimitDefaultsAndCaps() {
        assertEquals(OfflineSnapshotStore.DEFAULT_LIMIT, OfflineSnapshotStore.clampLimit(0))
        assertEquals(OfflineSnapshotStore.DEFAULT_LIMIT, OfflineSnapshotStore.clampLimit(-3))
        assertEquals(100, OfflineSnapshotStore.clampLimit(100))
        assertEquals(250, OfflineSnapshotStore.clampLimit(250))
        assertEquals(OfflineSnapshotStore.HARD_CAP, OfflineSnapshotStore.clampLimit(501))
        assertEquals(OfflineSnapshotStore.HARD_CAP, OfflineSnapshotStore.clampLimit(10_000))
    }

    @Test
    fun validateAndCapDocumentsRejectsOverHardCap() {
        val ok = List(OfflineSnapshotStore.HARD_CAP) { """{"_id":$it}""" }
        assertEquals(OfflineSnapshotStore.HARD_CAP, OfflineSnapshotStore.validateAndCapDocuments(ok).size)

        val over = ok + """{"_id":"extra"}"""
        val error = assertThrows(IllegalArgumentException::class.java) {
            OfflineSnapshotStore.validateAndCapDocuments(over)
        }
        assertTrue(error.message!!.contains(OfflineSnapshotStore.HARD_CAP.toString()))
        assertTrue(error.message!!.contains(over.size.toString()))
    }

    @Test
    fun documentPathsUseJsonlUnderOfflineSnapshots() {
        assertEquals("abc.jsonl", OfflineSnapshotStore.documentFileName("abc"))
        assertEquals(
            "offline_snapshots/abc.jsonl",
            OfflineSnapshotStore.documentRelativePath("abc"),
        )
    }
}
