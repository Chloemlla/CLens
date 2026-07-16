package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UriImportParserTest {
    @Test
    fun extractUri_fromFreeText() {
        val text = "请连接: mongodb://user:pass@host:27017/db?authSource=admin 谢谢"
        assertEquals(
            "mongodb://user:pass@host:27017/db?authSource=admin",
            UriImportParser.extractUri(text),
        )
    }

    @Test
    fun extractUri_supportsSrvAndStripsTrailingPunctuation() {
        val text = "URI=mongodb+srv://u:p@cluster.example/app,"
        assertEquals(
            "mongodb+srv://u:p@cluster.example/app",
            UriImportParser.extractUri(text),
        )
    }

    @Test
    fun extractUri_returnsNullWhenMissing() {
        assertNull(UriImportParser.extractUri("no mongo here"))
        assertNull(UriImportParser.extractUri(null))
        assertNull(UriImportParser.extractUri("   "))
    }

    @Test
    fun parseImportPayload_fromJsonWithName() {
        val raw = """{"name":"prod","uri":"mongodb://root:secret@10.0.0.2:27017/admin"}"""
        val payload = UriImportParser.parseImportPayload(raw)
        assertEquals("prod", payload?.name)
        assertEquals("mongodb://root:secret@10.0.0.2:27017/admin", payload?.uri)
    }

    @Test
    fun parseImportPayload_acceptsConnectionStringKey() {
        val raw = """{"connectionName":"atlas","connectionString":"mongodb+srv://a:b@c.d/e"}"""
        val payload = UriImportParser.parseImportPayload(raw)
        assertEquals("atlas", payload?.name)
        assertEquals("mongodb+srv://a:b@c.d/e", payload?.uri)
    }

    @Test
    fun parseImportPayload_fallsBackToFreeTextUri() {
        val payload = UriImportParser.parseImportPayload(
            "clipboard note mongodb://localhost:27017",
        )
        assertNull(payload?.name)
        assertEquals("mongodb://localhost:27017", payload?.uri)
    }

    @Test
    fun looksLikeMongoUri() {
        assertTrue(UriImportParser.looksLikeMongoUri("mongodb://h"))
        assertTrue(UriImportParser.looksLikeMongoUri("MongoDB+SRV://h"))
        assertFalse(UriImportParser.looksLikeMongoUri("http://example.com"))
        assertFalse(UriImportParser.looksLikeMongoUri(null))
    }

    @Test
    fun maskUri_stillHidesSecretsAfterImport() {
        val uri = UriImportParser.extractUri("mongodb://alice:topsecret@localhost:27017")!!
        val masked = MongoUriBuilder.maskUri(uri)
        assertEquals("mongodb://alice:***@localhost:27017", masked)
        assertFalse(masked.contains("topsecret"))
    }
}
