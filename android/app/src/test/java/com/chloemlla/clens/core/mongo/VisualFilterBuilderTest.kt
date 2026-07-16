package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class VisualFilterBuilderTest {
    @Test
    fun buildsEqualityAndComparisonOperators() {
        val json = VisualFilterBuilder.toFilterJson(
            listOf(
                VisualFilterClause(field = "status", op = VisualFilterOp.Eq, value = "active"),
                VisualFilterClause(field = "age", op = VisualFilterOp.Gte, value = "18"),
                VisualFilterClause(field = "score", op = VisualFilterOp.Lt, value = "100"),
            ),
        )
        val obj = JSONObject(json)
        assertEquals("active", obj.getString("status"))
        assertEquals(18L, obj.getJSONObject("age").getLong("\$gte"))
        assertEquals(100L, obj.getJSONObject("score").getLong("\$lt"))
    }

    @Test
    fun buildsInRegexAndExists() {
        val json = VisualFilterBuilder.toFilterJson(
            listOf(
                VisualFilterClause(field = "tag", op = VisualFilterOp.In, value = "a, b"),
                VisualFilterClause(field = "name", op = VisualFilterOp.Regex, value = "^cli"),
                VisualFilterClause(field = "deletedAt", op = VisualFilterOp.Exists, value = "false"),
            ),
        )
        val obj = JSONObject(json)
        val tags = obj.getJSONObject("tag").getJSONArray("\$in")
        assertEquals(2, tags.length())
        assertEquals("a", tags.getString(0))
        assertEquals("b", tags.getString(1))
        assertEquals("^cli", obj.getJSONObject("name").getString("\$regex"))
        assertEquals(false, obj.getJSONObject("deletedAt").getBoolean("\$exists"))
    }

    @Test
    fun roundTripsSimpleFilterJson() {
        val original = listOf(
            VisualFilterClause(field = "level", op = VisualFilterOp.Gt, value = "3"),
            VisualFilterClause(field = "role", op = VisualFilterOp.Ne, value = "guest"),
        )
        val encoded = VisualFilterBuilder.toFilterJson(original)
        val decoded = VisualFilterBuilder.fromFilterJson(encoded)
        assertEquals(2, decoded.size)
        // JSONObject key order is not stable across platforms/versions.
        val byField = decoded.associateBy { it.field }
        assertEquals(setOf("level", "role"), byField.keys)
        assertEquals(VisualFilterOp.Gt, byField.getValue("level").op)
        assertEquals("3", byField.getValue("level").value)
        assertEquals(VisualFilterOp.Ne, byField.getValue("role").op)
        assertEquals("guest", byField.getValue("role").value)
    }

    @Test
    fun ignoresBlankFields() {
        val json = VisualFilterBuilder.toFilterJson(
            listOf(
                VisualFilterClause(field = "  ", op = VisualFilterOp.Eq, value = "x"),
                VisualFilterClause(field = "ok", op = VisualFilterOp.Eq, value = "1"),
            ),
        )
        val obj = JSONObject(json)
        assertEquals(1, obj.length())
        assertTrue(obj.has("ok"))
    }
}
