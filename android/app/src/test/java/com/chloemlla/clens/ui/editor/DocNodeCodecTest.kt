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

    @Test
    fun convertsAndGeneratesObjectIdAndDate() {
        val root = DocNodeCodec.parse("""{"name":"a"}""")
        val withId = DocNodeCodec.ensureRootObjectId(root)
        val id = DocNodeCodec.findNode(withId, "_id")
        assertEquals(DocValueType.ObjectId, id?.type)
        assertTrue(DocNodeCodec.isValidObjectId(id?.scalar.orEmpty()))

        val asDate = DocNodeCodec.convertType(withId, "name", DocValueType.Date)
        val dateNode = DocNodeCodec.findNode(asDate, "name")
        assertEquals(DocValueType.Date, dateNode?.type)
        assertTrue(DocNodeCodec.parseDateMillis(dateNode?.scalar) != null)
    }

    @Test
    fun cloneAndDeleteNodes() {
        val root = DocNodeCodec.parse("""{"a":1,"b":{"x":2}}""", autoExpandDepth = 2)
        val cloned = DocNodeCodec.cloneNode(root, "a")
        assertTrue(DocNodeCodec.findNode(cloned, "a_copy") != null)
        val deleted = DocNodeCodec.deleteNode(cloned, "b")
        assertTrue(DocNodeCodec.findNode(deleted, "b") == null)
        assertTrue(DocNodeCodec.findNode(deleted, "a") != null)
    }


    @Test
    fun geoPointAndBinaryRoundTrip() {
        val geoJson = """{"loc":{"type":"Point","coordinates":[121.5,31.2]}}"""
        val geoRoot = DocNodeCodec.parse(geoJson)
        val loc = DocNodeCodec.findNode(geoRoot, "loc")
        assertEquals(DocValueType.GeoPoint, loc?.type)
        val point = DocNodeCodec.parseGeoPoint(loc?.scalar)
        assertEquals(31.2, point!!.first, 0.0001)
        assertEquals(121.5, point.second, 0.0001)
        val geoSerialized = DocNodeCodec.serialize(geoRoot)
        assertTrue(geoSerialized.contains("Point"))
        assertTrue(geoSerialized.contains("121.5"))

        val binJson = "{\"blob\":{\"${'$'}binary\":{\"base64\":\"AQID\",\"subType\":\"00\"}}}"
        val binRoot = DocNodeCodec.parse(binJson)
        val blob = DocNodeCodec.findNode(binRoot, "blob")
        assertEquals(DocValueType.Binary, blob?.type)
        val binary = DocNodeCodec.parseBinary(blob?.scalar)
        assertEquals("AQID", binary!!.base64)
        assertEquals("00", binary.subType)
        val binSerialized = DocNodeCodec.serialize(binRoot)
        assertTrue(binSerialized.contains("AQID"))
        assertTrue(binSerialized.contains("binary"))
    }

}
