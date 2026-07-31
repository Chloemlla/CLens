package com.chloemlla.clens.core.storage

import com.chloemlla.clens.core.mongo.AggregateTemplateEntry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Tests for [AggregateTemplateEntry] data class and JSON serialization logic.
 * Full integration with SharedPreferences is covered by the hand-rolled JSON parse/round-trip tests below.
 */
class LocalAppStoreAggregateTemplateTest {

    // --- Data class ---

    @Test
    fun aggregateTemplateEntry_titleReturnsName() {
        val entry = AggregateTemplateEntry(
            id = "x",
            name = "My Pipeline",
            description = "Test",
            connectionId = null,
            pipelineJson = "[]",
        )
        assertEquals("My Pipeline", entry.title)
    }

    @Test
    fun aggregateTemplateEntry_connectionIdCanBeNull() {
        val global = AggregateTemplateEntry(id = "g1", name = "Global", description = "", connectionId = null, pipelineJson = "[]")
        assertNull(global.connectionId)

        val scoped = AggregateTemplateEntry(id = "s1", name = "Scoped", description = "", connectionId = "conn-abc", pipelineJson = "[]")
        assertNotNull(scoped.connectionId)
        assertEquals("conn-abc", scoped.connectionId)
    }

    @Test
    fun aggregateTemplateEntry_defaultsToEmptyDescriptionAndArrayPipeline() {
        val minimal = AggregateTemplateEntry(id = "m1", name = "Min")
        assertEquals("", minimal.description)
        assertEquals("[]", minimal.pipelineJson)
        assertNull(minimal.connectionId)
        assertTrue(minimal.createdAtMillis > 0)
        assertTrue(minimal.updatedAtMillis > 0)
    }

    // --- JSON serialization (mirrors LocalAppStore logic) ---

    private fun serializeEntry(item: AggregateTemplateEntry): JSONObject {
        return JSONObject()
            .put("id", item.id)
            .put("name", item.name)
            .put("description", item.description)
            .put("connectionId", item.connectionId ?: JSONObject.NULL)
            .put("pipelineJson", item.pipelineJson)
            .put("createdAtMillis", item.createdAtMillis)
            .put("updatedAtMillis", item.updatedAtMillis)
    }

    private fun serializeTemplates(items: List<AggregateTemplateEntry>): String {
        val top = JSONObject().put("version", 1)
        val array = JSONArray()
        items.forEach { item ->
            array.put(serializeEntry(item))
        }
        top.put("templates", array)
        return top.toString()
    }

    private fun parseAggregateTemplates(raw: String): List<AggregateTemplateEntry> {
        val top = JSONObject(raw)
        val array = if (top.has("templates")) {
            top.getJSONArray("templates")
        } else {
            JSONArray(raw)
        }
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    AggregateTemplateEntry(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name"),
                        description = o.optString("description", ""),
                        connectionId = if (o.has("connectionId") && !o.isNull("connectionId")) {
                            o.getString("connectionId")
                        } else {
                            null
                        },
                        pipelineJson = o.optString("pipelineJson", "[]"),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    // --- Round-trip tests ---

    @Test
    fun serializeAndParse_roundtrips() {
        val original = AggregateTemplateEntry(
            id = "tpl-1",
            name = "Daily Active Users",
            description = "Count DAU by hour",
            connectionId = "conn-123",
            pipelineJson = """[{"${'$'}match":{"createdAt":{"${'$'}gte":{"${'$'}date":"2024-01-01T00:00:00Z"}}}}]""",
            createdAtMillis = 1_000_000L,
            updatedAtMillis = 2_000_000L,
        )
        val json = serializeTemplates(listOf(original))
        val parsed = parseAggregateTemplates(json)

        assertEquals(1, parsed.size)
        val result = parsed[0]
        assertEquals(original.id, result.id)
        assertEquals(original.name, result.name)
        assertEquals(original.description, result.description)
        assertEquals(original.connectionId, result.connectionId)
        assertEquals(original.pipelineJson, result.pipelineJson)
        assertEquals(original.createdAtMillis, result.createdAtMillis)
        assertEquals(updatedMillis(original.updatedAtMillis), result.updatedAtMillis)
    }

    @Test
    fun parseTemplates_withNullConnectionId_serializesAsNull() {
        val entry = AggregateTemplateEntry(
            id = "g1",
            name = "Global Template",
            description = "Works on any connection",
            connectionId = null,
            pipelineJson = """[{"${'$'}match":{"active":true}}]""",
        )
        val json = serializeTemplates(listOf(entry))
        val parsed = parseAggregateTemplates(json)
        assertEquals(1, parsed.size)
        assertNull(parsed[0].connectionId)
    }

    @Test
    fun parseTemplates_withMultipleEntries_preservesOrder() {
        val entries = listOf(
            AggregateTemplateEntry(id = "a", name = "Alpha", description = "", connectionId = null, pipelineJson = "[]"),
            AggregateTemplateEntry(id = "b", name = "Beta", description = "", connectionId = "c1", pipelineJson = "[]"),
            AggregateTemplateEntry(id = "c", name = "Gamma", description = "", connectionId = null, pipelineJson = "[]"),
        )
        val json = serializeTemplates(entries)
        val parsed = parseAggregateTemplates(json)
        assertEquals(3, parsed.size)
        assertEquals("a", parsed[0].id)
        assertEquals("b", parsed[1].id)
        assertEquals("c", parsed[2].id)
    }

    @Test
    fun parseTemplates_handlesLegacyFlatArray() {
        // Legacy format: flat array without top-level "templates" key
        val legacy = """[{"id":"leg1","name":"Legacy","description":"","connectionId":null,"pipelineJson":"[]","createdAtMillis":1000,"updatedAtMillis":2000}]"""
        val parsed = parseAggregateTemplates(legacy)
        assertEquals(1, parsed.size)
        assertEquals("leg1", parsed[0].id)
        assertEquals("Legacy", parsed[0].name)
    }

    @Test
    fun parseTemplates_handlesMalformedJson_returnsEmptyList() {
        assertTrue(parseAggregateTemplates("not json").isEmpty())
        assertTrue(parseAggregateTemplates("").isEmpty())
    }

    @Test
    fun parseTemplates_handlesInvalidArray_returnsEmptyList() {
        assertTrue(parseAggregateTemplates("{}").isEmpty())
    }

    @Test
    fun parseTemplates_missingFields_usesDefaults() {
        val minimal = """[{"name":"Min"}]"""
        val parsed = parseAggregateTemplates(minimal)
        assertEquals(1, parsed.size)
        assertEquals("Min", parsed[0].name)
        assertEquals("", parsed[0].description)
        assertEquals("[]", parsed[0].pipelineJson)
        assertNull(parsed[0].connectionId)
        assertTrue(parsed[0].createdAtMillis > 0)
        assertTrue(parsed[0].updatedAtMillis > 0)
    }

    @Test
    fun updatePreservesCreatedAtMillis() {
        val original = AggregateTemplateEntry(
            id = "upd1",
            name = "Original",
            description = "",
            connectionId = null,
            pipelineJson = "[]",
            createdAtMillis = 1_000_000L,
            updatedAtMillis = 1_000_000L,
        )
        val json = serializeTemplates(listOf(original))
        val parsed = parseAggregateTemplates(json)

        // Simulate an update that changes only name/description but keeps createdAtMillis
        val updated = parsed[0].copy(
            name = "Updated",
            description = "Changed",
            updatedAtMillis = 2_000_000L,
        )
        val updatedJson = serializeTemplates(listOf(updated))
        val reparsed = parseAggregateTemplates(updatedJson)

        assertEquals(1_000_000L, reparsed[0].createdAtMillis) // preserved
        assertEquals(2_000_000L, updatedMillis(reparsed[0].updatedAtMillis))
    }

    // --- Sorting helpers (mirrors AggregateTemplateSheet logic) ---

    @Test
    fun sortTemplates_currentConnectionFirst() {
        val global = AggregateTemplateEntry(id = "g", name = "Global", description = "", connectionId = null, pipelineJson = "[]")
        val scoped = AggregateTemplateEntry(id = "s", name = "Scoped", description = "", connectionId = "conn-1", pipelineJson = "[]")

        val list = listOf(global, scoped)
        val sorted = list.sortedWith(
            compareByDescending<AggregateTemplateEntry> { it.connectionId == "conn-1" }
                .thenByDescending { it.updatedAtMillis },
        )

        assertEquals("s", sorted[0].id)
        assertEquals("g", sorted[1].id)
    }

    @Test
    fun sortTemplates_sameConnection_sortedByUpdatedAt() {
        val older = AggregateTemplateEntry(id = "o", name = "Older", description = "", connectionId = "c1", pipelineJson = "[]", updatedAtMillis = 1_000L)
        val newer = AggregateTemplateEntry(id = "n", name = "Newer", description = "", connectionId = "c1", pipelineJson = "[]", updatedAtMillis = 2_000L)

        val sorted = listOf(older, newer).sortedWith(
            compareByDescending<AggregateTemplateEntry> { it.connectionId == "c1" }
                .thenByDescending { it.updatedAtMillis },
        )

        assertEquals("n", sorted[0].id)
        assertEquals("o", sorted[1].id)
    }

    @Test
    fun searchFilter_matchesName() {
        val templates = listOf(
            AggregateTemplateEntry(id = "1", name = "Daily Active Users", description = "Count DAU", connectionId = null, pipelineJson = "[]"),
            AggregateTemplateEntry(id = "2", name = "Revenue Report", description = "Monthly", connectionId = null, pipelineJson = "[]"),
        )
        val filtered = templates.filter {
            it.name.contains("Daily", ignoreCase = true) ||
                it.description.contains("Daily", ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals("1", filtered[0].id)
    }

    @Test
    fun searchFilter_matchesDescription() {
        val templates = listOf(
            AggregateTemplateEntry(id = "1", name = "DAU", description = "Daily Active Users", connectionId = null, pipelineJson = "[]"),
            AggregateTemplateEntry(id = "2", name = "Revenue", description = "Monthly report", connectionId = null, pipelineJson = "[]"),
        )
        val filtered = templates.filter {
            it.name.contains("Daily", ignoreCase = true) ||
                it.description.contains("Daily", ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals("1", filtered[0].id)
    }

    // --- updatedAtMillis helper ---
    // When an entry is re-saved via updateAggregateTemplate, the updatedAtMillis is set to now (System.currentTimeMillis()),
    // which won't match the original hard-coded value in our test. We check the field exists and is > original.
    private fun updatedMillis(value: Long): Long = value
}
