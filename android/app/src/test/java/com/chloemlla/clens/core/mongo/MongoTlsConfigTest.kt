package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MongoTlsConfigTest {
    @Test
    fun noMaterialReturnsNullContext() {
        val profile = MongoConnectionProfile(name = "n", tls = true)
        assertFalse(MongoTlsConfig.hasCustomMaterial(profile))
        assertNull(MongoTlsConfig.sslContextOrNull(profile))
    }

    @Test
    fun detectsCustomMaterialFlags() {
        val ca = MongoConnectionProfile(name = "n", tlsCaPem = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----")
        assertTrue(MongoTlsConfig.hasCustomMaterial(ca))
    }
}
