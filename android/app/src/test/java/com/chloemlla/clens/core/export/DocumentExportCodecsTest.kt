package com.chloemlla.clens.core.export

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentExportCodecsTest {
    @Test
    fun toPrettyJsonArray_prettyPrintsObjectsAndKeepsInvalidAsString() {
        val output = DocumentExportCodecs.toPrettyJsonArray(
            listOf(
                """{"_id":1,"name":"a"}""",
                "not-json",
            ),
        )

        val array = JSONArray(output)
        assertEquals(2, array.length())
        assertEquals(1, array.getJSONObject(0).getInt("_id"))
        assertEquals("a", array.getJSONObject(0).getString("name"))
        assertEquals("not-json", array.getString(1))
        assertTrue(output.contains("\n"))
    }

    @Test
    fun toPrettyJsonArray_emptyList_returnsEmptyArray() {
        assertEquals("[]", DocumentExportCodecs.toPrettyJsonArray(emptyList()).trim())
    }

    @Test
    fun toCsv_flattensTopLevelAndStringifiesNestedValues() {
        val csv = DocumentExportCodecs.toCsv(
            listOf(
                """{"_id":"1","name":"alice","nested":{"city":"SF"},"tags":["a","b"]}""",
                """{"_id":"2","name":"bob","score":3}""",
            ),
        )

        val lines = csv.lines()
        assertEquals(3, lines.size)
        assertEquals("_id,name,nested,score,tags", lines[0])

        val row1 = parseCsvLine(lines[1])
        assertEquals(listOf("1", "alice", """{"city":"SF"}""", "", """["a","b"]"""), row1)

        val row2 = parseCsvLine(lines[2])
        assertEquals(listOf("2", "bob", "", "3", ""), row2)
    }

    @Test
    fun toCsv_escapesQuotesCommasAndNewlines() {
        val csv = DocumentExportCodecs.toCsv(
            listOf(
                JSONObject()
                    .put("note", "say \"hi\", please")
                    .put("bio", "line1\nline2")
                    .toString(),
            ),
        )

        val lines = csv.lines()
        assertEquals("bio,note", lines[0])
        assertTrue(csv.contains("\"say \"\"hi\"\", please\""))
        assertTrue(csv.contains("\"line1\nline2\"") || csv.contains("\"line1\r\nline2\""))
    }

    @Test
    fun toCsv_emptyList_returnsEmptyString() {
        assertEquals("", DocumentExportCodecs.toCsv(emptyList()))
    }

    @Test
    fun toCsv_invalidJson_isExportedUnderRawColumn() {
        val csv = DocumentExportCodecs.toCsv(
            listOf(
                """{"_id":1,"ok":true}""",
                "{bad-json",
            ),
        )

        val lines = csv.lines()
        assertEquals("_id,ok,_raw", lines[0])
        assertEquals("1,true,", lines[1])
        assertEquals(",,{bad-json", lines[2])
    }

    @Test
    fun toExtendedJsonLines_writesOneCompactDocumentPerLine() {
        val lines = DocumentExportCodecs.toExtendedJsonLines(
            listOf(
                """{"_id":1,"nested":{"a":1}}""",
                """{"_id":2}""",
            ),
        ).lines()

        assertEquals(2, lines.size)
        assertEquals(1, JSONObject(lines[0]).getInt("_id"))
        assertEquals(1, JSONObject(lines[0]).getJSONObject("nested").getInt("a"))
        assertEquals(2, JSONObject(lines[1]).getInt("_id"))
        assertFalse(lines[0].contains("\n"))
    }

    @Test
    fun toExtendedJsonLines_invalidJson_staysOnOneLine() {
        val output = DocumentExportCodecs.toExtendedJsonLines(
            listOf("broken\njson", """{"ok":true}"""),
        )
        val lines = output.lines()
        assertEquals(2, lines.size)
        assertEquals("broken json", lines[0])
        assertEquals(true, JSONObject(lines[1]).getBoolean("ok"))
    }

    @Test
    fun encode_dispatchesByFormat() {
        val docs = listOf("""{"_id":1}""")
        assertEquals(
            DocumentExportCodecs.toPrettyJsonArray(docs),
            DocumentExportCodecs.encode(docs, DocumentExportFormat.JSON),
        )
        assertEquals(
            DocumentExportCodecs.toCsv(docs),
            DocumentExportCodecs.encode(docs, DocumentExportFormat.CSV),
        )
        assertEquals(
            DocumentExportCodecs.toExtendedJsonLines(docs),
            DocumentExportCodecs.encode(docs, DocumentExportFormat.EXTENDED_JSON_LINES),
        )
        assertEquals("json", DocumentExportFormat.JSON.extension)
        assertEquals("csv", DocumentExportFormat.CSV.extension)
        assertEquals("jsonl", DocumentExportFormat.EXTENDED_JSON_LINES.extension)
    }

    @Test
    fun escapeCsv_onlyQuotesWhenNeeded() {
        assertEquals("plain", DocumentExportCodecs.escapeCsv("plain"))
        assertEquals("\"a,b\"", DocumentExportCodecs.escapeCsv("a,b"))
        assertEquals("\"a\"\"b\"", DocumentExportCodecs.escapeCsv("a\"b"))
        assertEquals("\"a\nb\"", DocumentExportCodecs.escapeCsv("a\nb"))
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        values += current.toString()
        return values
    }
}
