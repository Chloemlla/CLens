package com.chloemlla.clens.core.importdata

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DocumentImportCodecsTest {
    @Test
    fun previewJsonFields_unionsTopLevelKeysInFirstSeenOrder() {
        val json = """
            [
              {"name":"a","age":1},
              {"age":2,"city":"SH","name":"b"},
              {"active":true}
            ]
        """.trimIndent()

        assertEquals(
            listOf("name", "age", "city", "active"),
            DocumentImportCodecs.previewJsonFields(json),
        )
    }

    @Test
    fun parseJsonArrayToDocStrings_keepsObjectsOnly() {
        val json = """[{"id":1}, 2, "x", {"id":2,"ok":true}]"""
        val docs = DocumentImportCodecs.parseJsonArrayToDocStrings(json)
        assertEquals(2, docs.size)
        assertEquals(1, JSONObject(docs[0]).getInt("id"))
        assertEquals(2, JSONObject(docs[1]).getInt("id"))
        assertTrue(JSONObject(docs[1]).getBoolean("ok"))
    }

    @Test
    fun applyJsonMapping_renamesAndSkipsFields() {
        val docs = listOf("""{"name":"Ada","secret":"x","age":30}""")
        val mapping = FieldMapping(
            linkedMapOf(
                "name" to "fullName",
                "secret" to "",
                "age" to "age",
            ),
        )

        val mapped = DocumentImportCodecs.applyJsonMapping(docs, mapping)
        assertEquals(1, mapped.size)
        val obj = JSONObject(mapped.single())
        assertEquals("Ada", obj.getString("fullName"))
        assertEquals(30, obj.getInt("age"))
        assertFalse(obj.has("name"))
        assertFalse(obj.has("secret"))
    }

    @Test
    fun parseCsv_supportsQuotesCommasAndEscapedQuotes() {
        // Avoid raw-string triple-quote conflicts from CSV escaped quotes.
        val csv = listOf(
            "name,note,score",
            "Ada,\"hello, world\",10",
            "\"Bob \"\"B\"\"\",plain,20",
        ).joinToString("\n")

        val table = DocumentImportCodecs.parseCsv(csv)
        assertEquals(listOf("name", "note", "score"), table.headers)
        assertEquals(2, table.rowCount)
        assertEquals(listOf("Ada", "hello, world", "10"), table.rows[0])
        assertEquals(listOf("Bob \"B\"", "plain", "20"), table.rows[1])
    }

    @Test
    fun applyCsvMapping_renamesSkipsAndParsesNestedJsonScalars() {
        val table = DocumentImportCodecs.parseCsv(
            listOf(
                "id,name,meta,flags,active,ratio,skipMe",
                "1,Ada,\"{\"\"city\"\":\"\"SH\"\"}\",\"[1,2]\",true,1.5,secret",
                "2,Bob,plain,x,false,2,hide",
            ).joinToString("\n"),
        )
        val mapping = FieldMapping(
            linkedMapOf(
                "id" to "_id",
                "name" to "name",
                "meta" to "meta",
                "flags" to "flags",
                "active" to "active",
                "ratio" to "ratio",
                "skipMe" to "",
            ),
        )

        val docs = DocumentImportCodecs.applyCsvMapping(table, mapping)
        assertEquals(2, docs.size)

        val first = JSONObject(docs[0])
        assertEquals(1L, first.getLong("_id"))
        assertEquals("Ada", first.getString("name"))
        assertEquals("SH", first.getJSONObject("meta").getString("city"))
        assertEquals(2, first.getJSONArray("flags").length())
        assertTrue(first.getBoolean("active"))
        assertEquals(1.5, first.getDouble("ratio"), 0.0)
        assertFalse(first.has("skipMe"))

        val second = JSONObject(docs[1])
        assertEquals("plain", second.getString("meta"))
        assertEquals("x", second.getString("flags"))
        assertFalse(second.getBoolean("active"))
        assertFalse(second.has("skipMe"))
    }

    @Test
    fun fieldMapping_fromHelpers_identityAndSkip() {
        val identity = FieldMapping.identity(listOf("a", "b", "a", " "))
        assertEquals(listOf("a", "b"), identity.sourceFields)
        assertEquals(listOf("a", "b"), identity.targetFields)

        val mapped = FieldMapping.from(
            sourceFields = listOf("a", "b", "c"),
            targetFields = listOf("A", "B", "C"),
            skipFields = listOf("b"),
        )
        assertEquals("", mapped.sourceToTarget["b"])
        assertEquals(listOf("b"), mapped.skipFields)
        assertEquals(listOf("A", "C"), mapped.targetFields)
    }

    @Test
    fun chunk_splitsWithDefaultAndCustomSize() {
        val docs = (1..105).map { """{"n":$it}""" }

        val defaultChunks = DocumentImportCodecs.chunk(docs)
        assertEquals(3, defaultChunks.size)
        assertEquals(50, defaultChunks[0].size)
        assertEquals(50, defaultChunks[1].size)
        assertEquals(5, defaultChunks[2].size)

        val custom = DocumentImportCodecs.chunk(docs.take(5), size = 2)
        assertEquals(listOf(2, 2, 1), custom.map { it.size })

        assertTrue(DocumentImportCodecs.chunk(emptyList()).isEmpty())
    }

    @Test
    fun toJsonArrayPayload_roundTripsMappedDocs() {
        val docs = listOf("""{"a":1}""", """{"b":"x"}""")
        val payload = DocumentImportCodecs.toJsonArrayPayload(docs)
        val array = JSONArray(payload)
        assertEquals(2, array.length())
        assertEquals(1, array.getJSONObject(0).getInt("a"))
        assertEquals("x", array.getJSONObject(1).getString("b"))
    }

    @Test
    fun parseCsv_rejectsMissingHeader() {
        try {
            DocumentImportCodecs.parseCsv("   ")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
