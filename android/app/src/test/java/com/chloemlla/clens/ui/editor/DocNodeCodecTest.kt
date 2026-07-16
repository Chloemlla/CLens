package com.chloemlla.clens.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocNodeCodecTest {
    @Test
    fun parseAndSerializeNestedObjectAndArray() {
        val json = """
            {
              "_id": {"${'$'}oid": "507f1f77bcf86cd799439011"},
              "name": "demo",
              "count": 3,
              "tags": ["a", "b"],
              "meta": {"ok": true, "score": 1.5, "note": null}
            }
        """.trimIndent()

        val root = DocNodeCodec.parse(json, autoExpandDepth = 2)
        assertEquals(DocValueType.Object, root.type)
        assertFalse(root.collapsed)

        val rows = DocNodeCodec.flattenVisible(root)
        assertTrue(rows.any { it.node.displayLabel == "name" })
        assertTrue(rows.any { it.node.displayLabel == "tags" })

        val roundTrip = DocNodeCodec.serialize(root)
        val reparsed = DocNodeCodec.parse(roundTrip, autoExpandDepth = 2)
        assertEquals(DocValueType.ObjectId, DocNodeCodec.findNode(reparsed, "_id")?.type)
        assertEquals("demo", DocNodeCodec.findNode(reparsed, "name")?.scalar)
        assertEquals(DocValueType.Array, DocNodeCodec.findNode(reparsed, "tags")?.type)
        assertEquals("true", DocNodeCodec.findNode(reparsed, "meta.ok")?.scalar)
    }

    @Test
    fun updatesLeafScalarAndValidatesObjectId() {
        val root = DocNodeCodec.parse("""{"name":"a","_id":{"${'$'}oid":"507f1f77bcf86cd799439011"}}""")
        val updated = DocNodeCodec.updateScalar(root, "name", DocValueType.String, "beta")
        assertEquals("beta", DocNodeCodec.findNode(updated, "name")?.scalar)

        val invalid = DocNodeCodec.updateScalar(updated, "_id", DocValueType.ObjectId, "zz")
        assertEquals("ObjectId 必须是 24 位十六进制", DocNodeCodec.findNode(invalid, "_id")?.error)

        assertTrue(DocNodeCodec.isValidObjectId("507f1f77bcf86cd799439011"))
        assertFalse(DocNodeCodec.isValidObjectId("not-an-object-id"))
        assertEquals(24, DocNodeCodec.generateObjectIdHex().length)
    }

    @Test
    fun diagnosticsCatchInvalidJson() {
        val diagnostics = DocNodeCodec.diagnostics("{ broken")
        assertTrue(diagnostics.isNotEmpty())
        assertTrue(DocNodeCodec.diagnostics("""{"ok":1}""").isEmpty())
    }

    @Test
    fun toggleCollapsedChangesVisibleRows() {
        val root = DocNodeCodec.parse("""{"child":{"a":1}}""", autoExpandDepth = 0)
        assertTrue(root.collapsed)
        val expanded = DocNodeCodec.toggleCollapsed(root, "")
        assertFalse(expanded.collapsed)
        val rows = DocNodeCodec.flattenVisible(expanded)
        assertTrue(rows.any { it.node.displayLabel == "child" })
    }
}
