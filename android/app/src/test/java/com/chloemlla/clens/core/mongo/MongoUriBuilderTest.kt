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
}
