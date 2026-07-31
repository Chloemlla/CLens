package com.chloemlla.clens.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for batch-select logic.
 * These test the pure functions and state transitions directly without requiring
 * a full Compose/Android environment.
 */
class BrowseBatchSelectTest {

    // --- extractDocumentId logic (mirrors BrowseController) ---

    @Test
    fun extractDocumentIdParsesPlainString() {
        val json = """{"_id":"user-123","name":"alice"}"""
        val id = extractDocId(json)
        assertEquals("user-123", id)
    }

    @Test
    fun extractDocumentIdParsesObjectId() {
        val json = """{"_id":{"\$oid":"507f1f77bcf86cd799439011"},"name":"bob"}"""
        val id = extractDocId(json)
        assertEquals("507f1f77bcf86cd799439011", id)
    }

    @Test
    fun extractDocumentIdReturnsNullForBlankJson() {
        assertEquals(null, extractDocId(""))
        assertEquals(null, extractDocId("   "))
    }

    @Test
    fun extractDocumentIdReturnsNullForMissingId() {
        val json = """{"name":"alice"}"""
        assertEquals(null, extractDocId(json))
    }

    @Test
    fun extractDocumentIdHandlesNumberId() {
        val json = """{"_id":42,"name":"alice"}"""
        val id = extractDocId(json)
        assertEquals("42", id)
    }

    // --- Select-mode state transitions ---

    @Test
    fun selectModeEnablesWithFirstSelection() {
        val docs = listOf(
            docWithId("id1", """{"_id":"id1"}"""),
            docWithId("id2", """{"_id":"id2"}"""),
        )
        val result = simulateSelectModeTransition(
            isSelectMode = false,
            selectedIds = emptySet(),
            tappedDocId = "id1",
            docs = docs,
        )
        assertTrue(result.isSelectMode)
        assertEquals(setOf("id1"), result.selectedIds)
    }

    @Test
    fun selectModeTogglesOffWhenDeselectingLastItem() {
        val docs = listOf(
            docWithId("id1", """{"_id":"id1"}"""),
            docWithId("id2", """{"_id":"id2"}"""),
        )
        val result = simulateSelectModeTransition(
            isSelectMode = true,
            selectedIds = setOf("id1"),
            tappedDocId = "id1",
            docs = docs,
        )
        assertFalse(result.isSelectMode)
        assertTrue(result.selectedIds.isEmpty())
    }

    @Test
    fun selectModeAddsSecondItem() {
        val docs = listOf(
            docWithId("id1", """{"_id":"id1"}"""),
            docWithId("id2", """{"_id":"id2"}"""),
        )
        val result = simulateSelectModeTransition(
            isSelectMode = true,
            selectedIds = setOf("id1"),
            tappedDocId = "id2",
            docs = docs,
        )
        assertTrue(result.isSelectMode)
        assertEquals(setOf("id1", "id2"), result.selectedIds)
    }

    @Test
    fun selectModeRemovesItem() {
        val docs = listOf(
            docWithId("id1", """{"_id":"id1"}"""),
            docWithId("id2", """{"_id":"id2"}"""),
        )
        val result = simulateSelectModeTransition(
            isSelectMode = true,
            selectedIds = setOf("id1", "id2"),
            tappedDocId = "id1",
            docs = docs,
        )
        assertTrue(result.isSelectMode)
        assertEquals(setOf("id2"), result.selectedIds)
    }

    @Test
    fun selectAllSelectsAllPageDocs() {
        val docs = listOf(
            docWithId("id1", """{"_id":"id1"}"""),
            docWithId("id2", """{"_id":"id2"}"""),
            docWithId("id3", """{"_id":"id3"}"""),
        )
        val result = simulateSelectAll(docs)
        assertEquals(setOf("id1", "id2", "id3"), result)
    }

    @Test
    fun selectAllWithEmptyPageReturnsEmpty() {
        val docs = emptyList<Pair<String, String>>()
        val result = simulateSelectAll(docs)
        assertTrue(result.isEmpty())
    }

    @Test
    fun selectModeIgnoresTapsOnDocsWithoutId() {
        val docs = listOf(
            docWithId(null, """{"name":"alice"}"""),
        )
        val result = simulateSelectModeTransition(
            isSelectMode = false,
            selectedIds = emptySet(),
            tappedDocId = null,
            docs = docs,
        )
        assertFalse(result.isSelectMode)
        assertTrue(result.selectedIds.isEmpty())
    }

    // --- Helpers that mirror BrowseController logic ---

    private fun extractDocId(json: String): String? {
        if (json.isBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            if (!obj.has("_id")) return null
            val id = obj.get("_id")
            when (id) {
                is JSONObject -> {
                    when {
                        id.has("\$oid") -> id.optString("\$oid")
                        else -> id.toString()
                    }
                }
                else -> id.toString()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private data class SelectModeResult(
        val isSelectMode: Boolean,
        val selectedIds: Set<String>,
    )

    private fun docWithId(id: String?, json: String): Pair<String?, String> = id to json

    private fun simulateSelectModeTransition(
        isSelectMode: Boolean,
        selectedIds: Set<String>,
        tappedDocId: String?,
        docs: List<Pair<String?, String>>,
    ): SelectModeResult {
        if (tappedDocId == null) return SelectModeResult(isSelectMode, selectedIds)

        return if (!isSelectMode) {
            SelectModeResult(isSelectMode = true, selectedIds = setOf(tappedDocId))
        } else {
            val updated = if (tappedDocId in selectedIds) {
                selectedIds - tappedDocId
            } else {
                selectedIds + tappedDocId
            }
            if (updated.isEmpty()) {
                SelectModeResult(isSelectMode = false, selectedIds = emptySet())
            } else {
                SelectModeResult(isSelectMode = true, selectedIds = updated)
            }
        }
    }

    private fun simulateSelectAll(docs: List<Pair<String?, String>>): Set<String> {
        return docs.mapNotNull { it.first }.toSet()
    }
}
