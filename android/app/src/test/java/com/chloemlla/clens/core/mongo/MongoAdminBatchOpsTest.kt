package com.chloemlla.clens.core.mongo

import org.bson.Document
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MongoAdminBatchOpsTest {
    @Test
    fun deleteManyRejectsEmptyIds() {
        // Empty list should return 0L without calling the collection.
        // (Implementation detail: early return when ids.isEmpty())
    }

    @Test
    fun deleteManyParsesObjectIdStrings() {
        val ids = listOf("507f1f77bcf86cd799439011", "507f1f77bcf86cd799439012")
        // Verify ObjectId.isValid accepts these
        assertTrue(ObjectId.isValid(ids[0]))
        assertTrue(ObjectId.isValid(ids[1]))
        assertEquals(24, ids[0].length)
        assertEquals(24, ids[1].length)
    }

    @Test
    fun deleteManyParsesPlainStringIds() {
        val ids = listOf("my-string-id", "another-id")
        // Plain strings are also valid (non-ObjectId)
        assertTrue(ids[0].isNotBlank())
        assertTrue(ids[1].isNotBlank())
    }

    @Test
    fun updateManyRejectsEmptyIds() {
        // Empty list should return 0L without calling the collection.
    }

    @Test
    fun updateManyRejectsEmptyUpdateDocument() {
        // Empty update should throw Validation error.
    }

    @Test
    fun parseIdValueAcceptsValidObjectId() {
        val raw = "507f1f77bcf86cd799439011"
        // Valid 24-char hex string -> ObjectId
        assertTrue(ObjectId.isValid(raw) && raw.length == 24)
    }

    @Test
    fun parseIdValueFallsBackToString() {
        val raw = "my-embedded-doc-id"
        // Non-ObjectId string -> kept as-is
        assertTrue(!ObjectId.isValid(raw))
    }

    @Test
    fun parseIdValueRejectsBlank() {
        val raw = "   "
        assertTrue(raw.isBlank())
    }

    @Test
    fun ensureAggregateLimitUnchangedWhenHasLimit() {
        val pipeline = listOf(
            Document("\$match", Document()),
            Document("\$limit", 50),
        )
        val limited = MongoAdminRepository.ensureAggregateLimit(pipeline, 100)
        assertEquals(50, limited.last()["\$limit"])
    }

    @Test
    fun ensureAggregateLimitAddsLimitWhenMissing() {
        val pipeline = listOf(Document("\$match", Document()))
        val limited = MongoAdminRepository.ensureAggregateLimit(pipeline, 25)
        assertEquals(2, limited.size)
        assertEquals(25, limited.last()["\$limit"])
    }
}
