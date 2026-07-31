package com.chloemlla.clens.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDiffTest {

    // --- Empty / identical documents ---

    @Test
    fun identicalEmptyDocuments() {
        val diffs = computeDiff("{}", "{}")
        assertTrue(diffs.isEmpty())
    }

    @Test
    fun identicalFlatDocuments() {
        val json = """{"name":"Alice","age":30}"""
        val diffs = computeDiff(json, json)
        assertEquals(2, diffs.size)
        assertTrue(diffs.all { it.status == DiffStatus.UNCHANGED })
    }

    // --- Added fields ---

    @Test
    fun addedTopLevelField() {
        val original = """{"name":"Alice"}"""
        val modified = """{"name":"Alice","city":"Shanghai"}"""
        val diffs = computeDiff(original, modified)
        val added = diffs.find { it.path == "city" }
        assertEquals(DiffStatus.ADDED, added?.status)
        assertEquals(null, added?.originalValue)
        assertEquals("Shanghai", added?.modifiedValue)
    }

    @Test
    fun addedNestedField() {
        val original = """{"user":{"name":"Alice"}}"""
        val modified = """{"user":{"name":"Alice","email":"a@b.com"}}"""
        val diffs = computeDiff(original, modified)
        val added = diffs.find { it.path == "user.email" }
        assertEquals(DiffStatus.ADDED, added?.status)
        assertEquals("a@b.com", added?.modifiedValue)
    }

    @Test
    fun addedArrayElement() {
        val original = """{"items":[1,2]}"""
        val modified = """{"items":[1,2,3]}"""
        val diffs = computeDiff(original, modified)
        val added = diffs.find { it.path == "items[2]" }
        assertEquals(DiffStatus.ADDED, added?.status)
        assertEquals(3, added?.modifiedValue)
    }

    // --- Removed fields ---

    @Test
    fun removedTopLevelField() {
        val original = """{"name":"Alice","city":"Shanghai"}"""
        val modified = """{"name":"Alice"}"""
        val diffs = computeDiff(original, modified)
        val removed = diffs.find { it.path == "city" }
        assertEquals(DiffStatus.REMOVED, removed?.status)
        assertEquals("Shanghai", removed?.originalValue)
        assertEquals(null, removed?.modifiedValue)
    }

    @Test
    fun removedNestedField() {
        val original = """{"user":{"name":"Alice","email":"a@b.com"}}"""
        val modified = """{"user":{"name":"Alice"}}"""
        val diffs = computeDiff(original, modified)
        val removed = diffs.find { it.path == "user.email" }
        assertEquals(DiffStatus.REMOVED, removed?.status)
    }

    // --- Modified fields ---

    @Test
    fun modifiedStringValue() {
        val original = """{"name":"Alice"}"""
        val modified = """{"name":"Bob"}"""
        val diffs = computeDiff(original, modified)
        val mod = diffs.find { it.path == "name" }
        assertEquals(DiffStatus.MODIFIED, mod?.status)
        assertEquals("Alice", mod?.originalValue)
        assertEquals("Bob", mod?.modifiedValue)
    }

    @Test
    fun modifiedNumericValue() {
        val original = """{"age":30}"""
        val modified = """{"age":31}"""
        val diffs = computeDiff(original, modified)
        val mod = diffs.find { it.path == "age" }
        assertEquals(DiffStatus.MODIFIED, mod?.status)
        assertEquals(30, mod?.originalValue)
        assertEquals(31, mod?.modifiedValue)
    }

    @Test
    fun modifiedBooleanValue() {
        val original = """{"active":true}"""
        val modified = """{"active":false}"""
        val diffs = computeDiff(original, modified)
        val mod = diffs.find { it.path == "active" }
        assertEquals(DiffStatus.MODIFIED, mod?.status)
        assertEquals(true, mod?.originalValue)
        assertEquals(false, mod?.modifiedValue)
    }

    @Test
    fun modifiedNestedField() {
        val original = """{"user":{"name":"Alice"}}"""
        val modified = """{"user":{"name":"Bob"}}"""
        val diffs = computeDiff(original, modified)
        val mod = diffs.find { it.path == "user.name" }
        assertEquals(DiffStatus.MODIFIED, mod?.status)
        assertEquals("Alice", mod?.originalValue)
        assertEquals("Bob", mod?.modifiedValue)
    }

    @Test
    fun modifiedArrayElement() {
        val original = """{"items":[1,2,3]}"""
        val modified = """{"items":[1,99,3]}"""
        val diffs = computeDiff(original, modified)
        val mod = diffs.find { it.path == "items[1]" }
        assertEquals(DiffStatus.MODIFIED, mod?.status)
        assertEquals(2, mod?.originalValue)
        assertEquals(99, mod?.modifiedValue)
    }

    @Test
    fun modifiedArrayLength() {
        val original = """{"items":[1,2,3]}"""
        val modified = """{"items":[1,2,3,4,5]}"""
        val diffs = computeDiff(original, modified)
        val removedItems = diffs.filter { it.path.startsWith("items[") && it.status == DiffStatus.REMOVED }
        val addedItems = diffs.filter { it.path.startsWith("items[") && it.status == DiffStatus.ADDED }
        assertTrue(removedItems.isEmpty())
        assertEquals(2, addedItems.size)
    }

    // --- Unchanged fields ---

    @Test
    fun unchangedFieldsMarkedCorrectly() {
        val original = """{"name":"Alice","age":30,"city":"Shanghai"}"""
        val modified = """{"name":"Alice","age":31,"city":"Shanghai"}"""
        val diffs = computeDiff(original, modified)
        assertEquals(DiffStatus.UNCHANGED, diffs.find { it.path == "name" }?.status)
        assertEquals(DiffStatus.UNCHANGED, diffs.find { it.path == "city" }?.status)
        assertEquals(DiffStatus.MODIFIED, diffs.find { it.path == "age" }?.status)
    }

    // --- Null values ---

    @Test
    fun nullToValueIsAdded() {
        val original = """{"field":null}"""
        val modified = """{"field":42}"""
        val diffs = computeDiff(original, modified)
        val mod = diffs.find { it.path == "field" }
        assertEquals(DiffStatus.MODIFIED, mod?.status)
    }

    @Test
    fun valueToNullIsModified() {
        val original = """{"field":42}"""
        val modified = """{"field":null}"""
        val diffs = computeDiff(original, modified)
        val mod = diffs.find { it.path == "field" }
        assertEquals(DiffStatus.MODIFIED, mod?.status)
    }

    @Test
    fun stringNullRemainsDistinctFromJsonNull() {
        val diffs = computeDiff(
            """{"text":"null","actual":null}""",
            """{"text":"null","actual":null}""",
        )

        assertEquals("null", diffs.find { it.path == "text" }?.originalValue)
        assertEquals(null, diffs.find { it.path == "actual" }?.originalValue)
    }

    @Test
    fun nullFieldOnlyInOriginalIsRemoved() {
        val original = """{"field":null}"""
        val modified = """{}"""
        val diffs = computeDiff(original, modified)
        val rem = diffs.find { it.path == "field" }
        assertEquals(DiffStatus.REMOVED, rem?.status)
    }

    // --- ObjectId and BSON types ---

    @Test
    fun objectIdComparison() {
        val original = """{"_id":{"$oid":"507f1f77bcf86cd799439011"}}"""
        val modified = """{"_id":{"$oid":"507f1f77bcf86cd799439012"}}"""
        val diffs = computeDiff(original, modified)
        assertEquals(1, diffs.size)
        assertEquals(DiffStatus.MODIFIED, diffs[0].status)
    }

    @Test
    fun identicalObjectIdUnchanged() {
        val json = """{"_id":{"$oid":"507f1f77bcf86cd799439011"},"name":"test"}"""
        val diffs = computeDiff(json, json)
        assertTrue(diffs.all { it.status == DiffStatus.UNCHANGED })
    }

    // --- Edge cases ---

    @Test
    fun emptyDocumentToFullDocument() {
        val original = "{}"
        val modified = """{"name":"Alice","age":30}"""
        val diffs = computeDiff(original, modified)
        assertEquals(2, diffs.size)
        assertTrue(diffs.all { it.status == DiffStatus.ADDED })
    }

    @Test
    fun fullDocumentToEmptyDocument() {
        val original = """{"name":"Alice","age":30}"""
        val modified = "{}"
        val diffs = computeDiff(original, modified)
        assertEquals(2, diffs.size)
        assertTrue(diffs.all { it.status == DiffStatus.REMOVED })
    }

    @Test
    fun deeplyNestedFields() {
        val original = """{"a":{"b":{"c":{"d":1}}}}"""
        val modified = """{"a":{"b":{"c":{"d":2}}}}"""
        val diffs = computeDiff(original, modified)
        val mod = diffs.find { it.path == "a.b.c.d" }
        assertEquals(DiffStatus.MODIFIED, mod?.status)
        assertEquals(1, mod?.originalValue)
        assertEquals(2, mod?.modifiedValue)
    }

    @Test
    fun deeplyNestedAddedField() {
        val original = """{"a":{"b":{"c":1}}}"""
        val modified = """{"a":{"b":{"c":1,"d":2}}}"""
        val diffs = computeDiff(original, modified)
        val added = diffs.find { it.path == "a.b.d" }
        assertEquals(DiffStatus.ADDED, added?.status)
        assertEquals(2, added?.modifiedValue)
    }

    @Test
    fun arrayOfObjects() {
        val original = """{"users":[{"name":"Alice","age":30},{"name":"Bob","age":20}]}"""
        val modified = """{"users":[{"name":"Alice","age":31},{"name":"Bob","age":20}]}"""
        val diffs = computeDiff(original, modified)
        assertEquals(DiffStatus.MODIFIED, diffs.find { it.path == "users[0].age" }?.status)
        assertEquals(DiffStatus.UNCHANGED, diffs.find { it.path == "users[0].name" }?.status)
        assertEquals(DiffStatus.UNCHANGED, diffs.find { it.path == "users[1].name" }?.status)
    }

    @Test
    fun arrayOfObjectsDifferentLength() {
        val original = """{"users":[{"name":"Alice"}]}"""
        val modified = """{"users":[{"name":"Alice"},{"name":"Bob"}]}"""
        val diffs = computeDiff(original, modified)
        val added = diffs.find { it.path == "users[1].name" }
        assertEquals(DiffStatus.ADDED, added?.status)
    }

    @Test
    fun mixedOperations() {
        val original = """{"name":"Alice","age":30,"city":"Shanghai"}"""
        val modified = """{"name":"Alice","age":31,"country":"China"}"""
        val diffs = computeDiff(original, modified)
        assertEquals(DiffStatus.UNCHANGED, diffs.find { it.path == "name" }?.status)
        assertEquals(DiffStatus.MODIFIED, diffs.find { it.path == "age" }?.status)
        assertEquals(DiffStatus.REMOVED, diffs.find { it.path == "city" }?.status)
        assertEquals(DiffStatus.ADDED, diffs.find { it.path == "country" }?.status)
    }

    @Test
    fun orderingChangesFirstUnchangedLast() {
        val original = """{"name":"A","age":30,"city":"S"}"""
        val modified = """{"name":"B","age":31,"city":"S"}"""
        val diffs = computeDiff(original, modified)
        // Changes should come before UNCHANGED
        val unchangedIndex = diffs.indexOfFirst { it.status == DiffStatus.UNCHANGED }
        val modifiedIndex = diffs.indexOfFirst { it.status == DiffStatus.MODIFIED }
        assertTrue("UNCHANGED should come after MODIFIED", unchangedIndex > modifiedIndex)
    }

    @Test
    fun changedOnlyFilter() {
        val original = """{"name":"Alice","age":30}"""
        val modified = """{"name":"Bob","age":30}"""
        val diffs = computeDiff(original, modified)
        val changed = diffs.changedOnly()
        assertEquals(1, changed.size)
        assertEquals(DiffStatus.MODIFIED, changed[0].status)
    }

    @Test
    fun jsonExportFormat() {
        val original = """{"name":"Alice"}"""
        val modified = """{"name":"Bob","city":"Shanghai"}"""
        val diffs = computeDiff(original, modified)
        val json = diffs.toJsonExport()
        assertTrue(json.contains("diffs"))
        assertTrue(json.contains("totalChanges"))
        assertTrue(json.contains("addedCount"))
        assertTrue(json.contains("removedCount"))
        assertTrue(json.contains("modifiedCount"))
        assertTrue(json.contains("\"status\": \"MODIFIED\""))
        assertTrue(json.contains("\"status\": \"ADDED\""))
    }

    @Test
    fun whitespaceInJsonIsHandled() {
        val original = """
            {
              "name": "Alice"
            }
        """.trimIndent()
        val modified = """{"name":"Alice","city":"Shanghai"}"""
        val diffs = computeDiff(original, modified)
        val added = diffs.find { it.path == "city" }
        assertEquals(DiffStatus.ADDED, added?.status)
    }

    @Test
    fun numericPrecisionDoubleValues() {
        val original = """{"value":1.5}"""
        val modified = """{"value":1.5}"""
        val diffs = computeDiff(original, modified)
        assertEquals(DiffStatus.UNCHANGED, diffs[0].status)
    }

    @Test
    fun numericTypeChangeIntToDouble() {
        val original = """{"value":1}"""
        val modified = """{"value":1.0}"""
        val diffs = computeDiff(original, modified)
        // Both parse to same number value, so unchanged
        assertEquals(DiffStatus.UNCHANGED, diffs[0].status)
    }
}
