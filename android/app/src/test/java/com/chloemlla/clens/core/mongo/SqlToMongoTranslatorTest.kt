package com.chloemlla.clens.core.mongo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SqlToMongoTranslatorTest {
    @Test
    fun translatesSimpleWhereComparison() {
        val result = SqlToMongoTranslator.translate("SELECT * FROM users WHERE age > 18")
        assertEquals("users", result.collection)
        val filter = JSONObject(result.filterJson)
        assertEquals(18L, filter.getJSONObject("age").getLong("\$gt"))
        assertEquals("{}", JSONObject(result.projectionJson).toString())
        assertTrue(result.shellPreview.contains("db.users.find("))
    }

    @Test
    fun translatesProjectionSortLimitOffsetAndAnd() {
        val sql = """
            SELECT name, age
            FROM users
            WHERE status = 'active' AND age >= 18
            ORDER BY age DESC, name ASC
            LIMIT 20
            OFFSET 5
        """.trimIndent()
        val result = SqlToMongoTranslator.translate(sql)
        val projection = JSONObject(result.projectionJson)
        assertEquals(1, projection.getInt("name"))
        assertEquals(1, projection.getInt("age"))
        val filter = JSONObject(result.filterJson)
        assertEquals("active", filter.getString("status"))
        assertEquals(18L, filter.getJSONObject("age").getLong("\$gte"))
        val sort = JSONObject(result.sortJson)
        assertEquals(-1, sort.getInt("age"))
        assertEquals(1, sort.getInt("name"))
        assertEquals(20, result.limit)
        assertEquals(5, result.skip)
    }

    @Test
    fun translatesInLikeAndNullChecks() {
        val sql = """
            SELECT *
            FROM logs
            WHERE tag IN ('a', 'b', 3)
              AND name LIKE 'cli%'
              AND deletedAt IS NULL
              AND owner IS NOT NULL
        """.trimIndent()
        val result = SqlToMongoTranslator.translate(sql)
        val filter = JSONObject(result.filterJson)
        val tags = filter.getJSONObject("tag").getJSONArray("\$in")
        assertEquals(3, tags.length())
        assertEquals("a", tags.getString(0))
        assertEquals("b", tags.getString(1))
        assertEquals(3L, tags.getLong(2))
        assertEquals("^cli.*$", filter.getJSONObject("name").getString("\$regex"))
        assertEquals(JSONObject.NULL, filter.get("deletedAt"))
        assertEquals(JSONObject.NULL, filter.getJSONObject("owner").get("\$ne"))
    }

    @Test
    fun allowsMissingFromForCurrentCollection() {
        val result = SqlToMongoTranslator.translate("SELECT _id, status WHERE status != 'deleted'")
        assertNull(result.collection)
        val filter = JSONObject(result.filterJson)
        assertEquals("deleted", filter.getJSONObject("status").getString("\$ne"))
        val projection = JSONObject(result.projectionJson)
        assertEquals(1, projection.getInt("_id"))
        assertEquals(1, projection.getInt("status"))
    }

    @Test
    fun rejectsJoinAndGroupByAndNot() {
        assertRejects("SELECT * FROM a JOIN b ON a.id = b.id")
        assertRejects("SELECT role, COUNT(*) FROM users GROUP BY role")
        assertRejects("SELECT * FROM users WHERE NOT age > 18")
        assertRejects("UPDATE users SET age = 1")
    }

    @Test
    fun translatesOrBetweenAliasesAndParens() {
        val sql = """
            SELECT name AS n, age
            FROM users
            WHERE (status = 'active' OR status = 'trial')
              AND age BETWEEN 18 AND 60
        """.trimIndent()
        val result = SqlToMongoTranslator.translate(sql)
        val projection = JSONObject(result.projectionJson)
        assertEquals(1, projection.getInt("name"))
        assertEquals(1, projection.getInt("age"))
        val filter = JSONObject(result.filterJson)
        val and = filter.getJSONArray("\$and")
        assertEquals(2, and.length())
        var sawOr = false
        var sawAge = false
        for (i in 0 until and.length()) {
            val obj = and.getJSONObject(i)
            if (obj.has("\$or")) {
                sawOr = true
                assertEquals(2, obj.getJSONArray("\$or").length())
            }
            if (obj.has("age")) {
                sawAge = true
                val age = obj.getJSONObject("age")
                assertEquals(18L, age.getLong("\$gte"))
                assertEquals(60L, age.getLong("\$lte"))
            }
        }
        assertTrue(sawOr)
        assertTrue(sawAge)
    }

    @Test
    fun acceptsDistinctWithoutChangingProjectionSemantics() {
        val result = SqlToMongoTranslator.translate("SELECT DISTINCT name FROM users WHERE age >= 1")
        assertEquals("users", result.collection)
        val projection = JSONObject(result.projectionJson)
        assertEquals(1, projection.getInt("name"))
    }

    private fun assertRejects(sql: String) {
        try {
            SqlToMongoTranslator.translate(sql)
            fail("Expected rejection for: $sql")
        } catch (error: SqlTranslateException) {
            assertTrue(error.message?.isNotBlank() == true)
        }
    }
}
