package com.chloemlla.clens.core.mongo

import org.bson.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MongoAggregateLimitTest {
    @Test
    fun appendsLimitWhenMissing() {
        val pipeline = listOf(Document("\$match", Document()))
        val limited = MongoAdminRepository.ensureAggregateLimit(pipeline, 25)
        assertEquals(2, limited.size)
        assertEquals(25, limited.last()["\$limit"])
    }

    @Test
    fun keepsExistingLimitStage() {
        val pipeline = listOf(
            Document("\$match", Document()),
            Document("\$limit", 10),
        )
        val limited = MongoAdminRepository.ensureAggregateLimit(pipeline, 25)
        assertEquals(2, limited.size)
        assertEquals(10, limited.last()["\$limit"])
        assertTrue(limited.count { it.containsKey("\$limit") } == 1)
    }
}
