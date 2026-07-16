package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MongoUriBuilderTest {
    @Test
    fun buildsHostFormUri() {
        val profile = MongoConnectionProfile(
            name = "local",
            host = "10.0.0.8",
            port = 27017,
            username = "root",
            password = "s ecret",
            authDatabase = "admin",
            defaultDatabase = "app",
            tls = true,
            directConnection = true,
        )
        val uri = MongoUriBuilder.build(profile)
        assertTrue(uri.startsWith("mongodb://root:s%20ecret@10.0.0.8:27017/app?"))
        assertTrue(uri.contains("authSource=admin"))
        assertTrue(uri.contains("tls=true"))
        assertTrue(uri.contains("directConnection=true"))
    }

    @Test
    fun prefersExplicitUri() {
        val profile = MongoConnectionProfile(
            name = "atlas",
            uri = "mongodb+srv://user:pass@cluster.example/db",
            host = "ignored",
        )
        assertEquals("mongodb+srv://user:pass@cluster.example/db", MongoUriBuilder.build(profile))
    }

    @Test
    fun masksCredentials() {
        val masked = MongoUriBuilder.maskUri("mongodb://alice:topsecret@localhost:27017/admin")
        assertEquals("mongodb://alice:***@localhost:27017/admin", masked)
        assertFalse(masked.contains("topsecret"))
    }

    @Test
    fun validatesJsonObjectAndArray() {
        assertEquals("{\"a\":1}", MongoUriBuilder.validateJsonObject("{\"a\":1}", "filter"))
        assertEquals("[]", MongoUriBuilder.validateJsonArray("[]", "pipeline"))
    }

    @Test(expected = MongoAdminException.Validation::class)
    fun rejectsNonObjectJson() {
        MongoUriBuilder.validateJsonObject("[1,2]", "filter")
    }

    @Test(expected = MongoAdminException.Validation::class)
    fun rejectsInvalidDirectUriScheme() {
        MongoUriBuilder.build(
            MongoConnectionProfile(
                name = "bad",
                uri = "http://example.com",
            ),
        )
    }

    @Test(expected = MongoAdminException.Validation::class)
    fun rejectsMalformedMongoUri() {
        MongoUriBuilder.build(
            MongoConnectionProfile(
                name = "bad",
                uri = "mongodb://",
            ),
        )
    }

    @Test
    fun parseUriToFormFields_extractsHostCredentialsAndFlags() {
        val parsed = MongoUriBuilder.parseUriToFormFields(
            "mongodb://alice:secret@10.0.0.8:27017/app?authSource=admin&tls=true&directConnection=true&replicaSet=rs0",
        )
        requireNotNull(parsed)
        assertEquals("10.0.0.8", parsed.host)
        assertEquals(27017, parsed.port)
        assertEquals("alice", parsed.username)
        assertEquals("secret", parsed.password)
        assertEquals("admin", parsed.authDatabase)
        assertEquals("app", parsed.defaultDatabase)
        assertEquals("rs0", parsed.replicaSet)
        assertTrue(parsed.tls)
        assertTrue(parsed.directConnection)
    }

    @Test
    fun parseUriToFormFields_returnsNullForNonMongoScheme() {
        assertEquals(null, MongoUriBuilder.parseUriToFormFields("http://example.com"))
    }
}
