package com.chloemlla.clens.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentResultHelpersTest {
    @Test
    fun extractsTopLevelFields() {
        val docs = listOf(
            """{"_id":1,"name":"a"}""",
            """{"_id":2,"age":18,"name":"b"}""",
        )
        val fields = topLevelFields(docs)
        assertEquals(listOf("_id", "name", "age"), fields)
    }

    @Test
    fun exportsJsonArray() {
        val docs = listOf("""{"a":1}""", """{"b":2}""")
        val json = documentsToJsonArray(docs)
        assertTrue(json.contains("\"a\""))
        assertTrue(json.contains("\"b\""))
        assertTrue(json.trim().startsWith("["))
    }
}
