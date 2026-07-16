package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    @Test
    fun privateIpv4RequiresPermission() {
        assertTrue(LocalNetworkAccess.hostRequiresLocalNetwork("192.168.1.10"))
        assertTrue(LocalNetworkAccess.hostRequiresLocalNetwork("10.0.0.2"))
        assertTrue(LocalNetworkAccess.hostRequiresLocalNetwork("172.16.4.8"))
        assertTrue(LocalNetworkAccess.hostRequiresLocalNetwork("172.31.255.1"))
    }

    @Test
    fun loopbackDoesNotRequireLocalNetworkPermission() {
        assertFalse(LocalNetworkAccess.hostRequiresLocalNetwork("127.0.0.1"))
        assertFalse(LocalNetworkAccess.hostRequiresLocalNetwork("localhost"))
        assertFalse(LocalNetworkAccess.hostRequiresLocalNetwork("::1"))
    }

    @Test
    fun publicHostsSkipPermission() {
        assertFalse(LocalNetworkAccess.hostRequiresLocalNetwork("cluster0.abc.mongodb.net"))
        assertFalse(LocalNetworkAccess.hostRequiresLocalNetwork("8.8.8.8"))
        assertFalse(LocalNetworkAccess.hostRequiresLocalNetwork("example.com"))
    }

    @Test
    fun localSuffixAndSingleLabelRequirePermission() {
        assertTrue(LocalNetworkAccess.hostRequiresLocalNetwork("nas.local"))
        assertTrue(LocalNetworkAccess.hostRequiresLocalNetwork("mongo.lan"))
        assertTrue(LocalNetworkAccess.hostRequiresLocalNetwork("dbserver"))
    }

    @Test
    fun uriHostExtraction() {
        assertEquals(
            "10.0.0.8",
            LocalNetworkAccess.hostFromMongoUri("mongodb://alice:secret@10.0.0.8:27017/app?tls=true"),
        )
        assertEquals(
            "cluster0.abc.mongodb.net",
            LocalNetworkAccess.hostFromMongoUri("mongodb+srv://u:p@cluster0.abc.mongodb.net/app"),
        )
    }

    @Test
    fun profileUsesSshHostWhenTunnelEnabled() {
        val profile = MongoConnectionProfile(
            name = "bastion",
            host = "10.0.0.5",
            sshEnabled = true,
            sshHost = "bastion.example.com",
        )
        assertFalse(LocalNetworkAccess.requiresLocalNetworkPermission(profile))

        val lanBastion = profile.copy(sshHost = "192.168.0.2")
        assertTrue(LocalNetworkAccess.requiresLocalNetworkPermission(lanBastion))
    }

    @Test
    fun profileUsesUriHostWhenPresent() {
        val profile = MongoConnectionProfile(
            name = "uri",
            uri = "mongodb://root:x@192.168.9.9:27017",
            host = "example.com",
        )
        assertTrue(LocalNetworkAccess.requiresLocalNetworkPermission(profile))
    }
}
