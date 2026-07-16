package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryFieldInferencerTest {
    @Test
    fun infersNestedFieldsFromDocumentsAndIndexKeys() {
        val fields = QueryFieldInferencer.inferFieldNames(
            sampleDocumentsJson = listOf(
                """{"_id":1,"user":{"name":"a","age":20},"tags":["x"]}""",
                """{"status":"ok","user":{"email":"a@b.c"}}""",
            ),
            indexKeysJson = listOf("""{"status":1,"createdAt":-1}"""),
        )
        assertTrue(fields.contains("_id"))
        assertTrue(fields.contains("user"))
        assertTrue(fields.contains("user.name"))
        assertTrue(fields.contains("user.age"))
        assertTrue(fields.contains("user.email"))
        assertTrue(fields.contains("tags"))
        assertTrue(fields.contains("status"))
        assertTrue(fields.contains("createdAt"))
        // Stable first-seen order: document fields first, then missing index keys.
        assertEquals("_id", fields.first())
    }

    @Test
    fun failsSoftOnInvalidJson() {
        val fields = QueryFieldInferencer.inferFieldNames(
            sampleDocumentsJson = listOf("not-json", "{"),
            indexKeysJson = listOf("[]", "oops"),
        )
        assertTrue(fields.isEmpty())
    }
}
