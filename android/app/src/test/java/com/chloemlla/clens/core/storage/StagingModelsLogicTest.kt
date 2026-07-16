package com.chloemlla.clens.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StagingModelsLogicTest {
    @Test
    fun capacityRulesBlockAtFifty() {
        assertTrue(StagingQueueRules.canEnqueue(0))
        assertTrue(StagingQueueRules.canEnqueue(49))
        assertFalse(StagingQueueRules.canEnqueue(50))
        assertEquals(50, StagingQueueRules.MAX_QUEUE_ITEMS)
        assertEquals(50, StagingQueueRules.IMPORT_CHUNK_SIZE)
        assertEquals(StagingQueueRules.MAX_QUEUE_ITEMS, StagingQueueStore.MAX_ITEMS)
        assertEquals(StagingQueueRules.IMPORT_CHUNK_SIZE, StagingQueueStore.IMPORT_CHUNK_SIZE)
        try {
            StagingQueueRules.ensureCanEnqueue(50)
            fail("expected capacity exception")
        } catch (ex: IllegalStateException) {
            assertTrue(ex.message!!.contains("暂存队列已满"))
            assertTrue(ex.message!!.contains("50"))
        }
    }

    @Test
    fun fullItemRoundTripPreservesFields() {
        val original = StagingItem(
            id = "s1",
            type = StagingOpType.REPLACE,
            connectionId = "conn-a",
            database = "app",
            collection = "users",
            payloadJson = """{"_id":"1","name":"Ada"}""",
            filterJson = """{"_id":"1"}""",
            dropBeforeImport = false,
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
            attemptCount = 2,
            lastError = "timeout",
            status = StagingStatus.FAILED,
            chunkIndex = 0,
            chunkCount = 1,
        )
        val encoded = StagingModelsCodec.itemToFullJson(original).toString()
        val restored = StagingModelsCodec.parseFullItem(encoded)
        assertEquals(original, restored)
    }

    @Test
    fun indexRoundTripOmitsPayloadButKeepsMeta() {
        val item = StagingItem(
            id = "imp-1",
            type = StagingOpType.IMPORT_CHUNK,
            connectionId = "c",
            database = "db",
            collection = "docs",
            payloadJson = """[{"a":1},{"a":2}]""",
            filterJson = null,
            dropBeforeImport = true,
            createdAtMillis = 11L,
            updatedAtMillis = 22L,
            attemptCount = 0,
            lastError = null,
            status = StagingStatus.PENDING,
            chunkIndex = 2,
            chunkCount = 5,
        )
        val indexRaw = StagingModelsCodec.indexArrayToString(listOf(item.copy(payloadJson = "")))
        val entries = StagingModelsCodec.parseIndexArray(indexRaw)
        assertEquals(1, entries.size)
        val meta = entries[0]
        assertEquals("imp-1", meta.id)
        assertEquals(StagingOpType.IMPORT_CHUNK, meta.type)
        assertTrue(meta.dropBeforeImport)
        assertEquals(2, meta.chunkIndex)
        assertEquals(5, meta.chunkCount)
        assertEquals("", meta.payloadJson)
        val withPayload = StagingModelsCodec.withPayload(meta, item.payloadJson)
        assertEquals(item.payloadJson, withPayload.payloadJson)
        assertNull(withPayload.filterJson)
        assertNull(withPayload.lastError)
    }

    @Test
    fun parseTypeAndStatusAreCaseInsensitiveWithDefaults() {
        assertEquals(StagingOpType.INSERT, StagingModelsCodec.parseType("insert"))
        assertEquals(StagingOpType.REPLACE, StagingModelsCodec.parseType("REPLACE"))
        assertEquals(StagingOpType.IMPORT_CHUNK, StagingModelsCodec.parseType("import_chunk"))
        assertEquals(StagingOpType.INSERT, StagingModelsCodec.parseType("unknown"))
        assertEquals(StagingStatus.IN_FLIGHT, StagingModelsCodec.parseStatus("in_flight"))
        assertEquals(StagingStatus.PENDING, StagingModelsCodec.parseStatus("nope"))
    }
}
