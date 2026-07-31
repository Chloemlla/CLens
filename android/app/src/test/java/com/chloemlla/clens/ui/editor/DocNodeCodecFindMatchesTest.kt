package com.chloemlla.clens.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocNodeCodecFindMatchesTest {

    @Test
    fun findMatches_byFieldName() {
        val root = DocNodeCodec.parse("""{"username":"alice","password":"secret123","email":"a@b.com"}""")
        val matches = DocNodeCodec.findMatches(root, "name")
        val paths = matches.map { it.pathKey }
        assertTrue(paths.contains("username"))
        assertTrue(paths.contains("password"))
        assertTrue(paths.contains("email"))
    }

    @Test
    fun findMatches_byFieldValue() {
        val root = DocNodeCodec.parse("""{"name":"bob","count":42,"active":true}""")
        val matches = DocNodeCodec.findMatches(root, "bob")
        assertEquals(1, matches.size)
        assertEquals("name", matches[0].pathKey)
    }

    @Test
    fun findMatches_caseInsensitive() {
        val root = DocNodeCodec.parse("""{"Name":"Alice"}""")
        val matches = DocNodeCodec.findMatches(root, "ALICE")
        assertEquals(1, matches.size)
        assertEquals("Name", matches[0].pathKey)
    }

    @Test
    fun findMatches_emptyQueryReturnsEmpty() {
        val root = DocNodeCodec.parse("""{"a":1}""")
        assertTrue(DocNodeCodec.findMatches(root, "").isEmpty())
        assertTrue(DocNodeCodec.findMatches(root, "   ").isEmpty())
    }

    @Test
    fun findMatches_noMatches() {
        val root = DocNodeCodec.parse("""{"name":"alice"}""")
        val matches = DocNodeCodec.findMatches(root, "xyz")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun findMatches_nestedObject() {
        val root = DocNodeCodec.parse("""{"user":{"name":"alice","email":"a@b.com"},"admin":false}""")
        val matches = DocNodeCodec.findMatches(root, "name")
        val paths = matches.map { it.pathKey }
        assertTrue(paths.contains("user.name"))
    }

    @Test
    fun findMatches_arrayElements() {
        val root = DocNodeCodec.parse("""{"items":["apple","banana","apple"]}""")
        val matches = DocNodeCodec.findMatches(root, "apple")
        assertEquals(2, matches.size)
        val paths = matches.map { it.pathKey }
        assertTrue(paths.contains("items.0") || paths.contains("[0]"))
        assertTrue(paths.contains("items.2") || paths.contains("[2]"))
    }

    @Test
    fun findMatches_specialChars() {
        val root = DocNodeCodec.parse("""{"field.with.dots":"value","field_with_underscore":"other"}""")
        val matches = DocNodeCodec.findMatches(root, "field")
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun findMatches_matchesBothPathAndValue() {
        val root = DocNodeCodec.parse("""{"name":"notname"}""")
        val matches = DocNodeCodec.findMatches(root, "name")
        // Both the field name "name" and the value "notname" match
        assertTrue(matches.size >= 1)
    }

    @Test
    fun findMatches_withObjectId() {
        val root = DocNodeCodec.parse("""{"_id":{"$oid":"507f1f77bcf86cd799439011"}}""")
        val matches = DocNodeCodec.findMatches(root, "507f1f77")
        assertTrue(matches.isNotEmpty())
        assertEquals(DocValueType.ObjectId, matches[0].type)
    }

    @Test
    fun findMatches_withDate() {
        val root = DocNodeCodec.parse("""{"created":{"$date":"2024-01-15T10:30:00.000Z"}}""")
        val matches = DocNodeCodec.findMatches(root, "2024")
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun flatten_allNodesReturned() {
        val root = DocNodeCodec.parse("""{"a":1,"b":{"c":2}}""")
        val flat = root.flatten()
        assertTrue(flat.size >= 3) // root + a + b + c
    }

    @Test
    fun flatten_deepNesting() {
        val root = DocNodeCodec.parse("""{"l1":{"l2":{"l3":{"l4":"deep"}}}}""")
        val flat = root.flatten()
        val paths = flat.map { it.pathKey }
        assertTrue(paths.contains("l1"))
        assertTrue(paths.contains("l1.l2"))
        assertTrue(paths.contains("l1.l2.l3"))
        assertTrue(paths.contains("l1.l2.l3.l4"))
    }
}
