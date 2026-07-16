package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SshTunnelSupportTest {
    @Test
    fun rewriteToLocalForward_preservesCredentialsAndForcesDirect() {
        val input = "mongodb://user:pass@db.internal:27017/app?authSource=admin&replicaSet=rs0"
        val out = MongoUriBuilder.rewriteToLocalForward(input, 45678)
        assertTrue(out.startsWith("mongodb://user:pass@127.0.0.1:45678"))
        assertTrue(out.contains("authSource=admin"))
        assertTrue(out.contains("directConnection=true"))
    }

    @Test
    fun rewriteSrv_toStandardLocalhost() {
        val input = "mongodb+srv://user:pass@cluster.example/app?authSource=admin"
        val out = MongoUriBuilder.rewriteToLocalForward(input, 12345)
        assertTrue(out.startsWith("mongodb://user:pass@127.0.0.1:12345"))
        assertFalse(out.lowercase().startsWith("mongodb+srv://"))
        assertTrue(out.contains("directConnection=true"))
    }

    @Test
    fun looksLikePpk_detectsPuttyKey() {
        assertTrue(SshTunnelSession.looksLikePpk("PuTTY-User-Key-File-2: ssh-rsa\n"))
        assertFalse(SshTunnelSession.looksLikePpk("-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----"))
    }

    @Test
    fun validate_requiresHostUserAndSecret() {
        val base = MongoConnectionProfile(
            name = "bastion",
            host = "mongo",
            sshEnabled = true,
            sshHost = "",
            sshUsername = "ubuntu",
            sshPassword = "x",
        )
        try {
            SshTunnelSession.validate(base)
            throw AssertionError("expected validation failure")
        } catch (error: MongoAdminException.Validation) {
            assertTrue(error.message!!.contains("SSH 主机"))
        }
    }

    @Test
    fun displayTarget_includesSshBadge() {
        val profile = MongoConnectionProfile(
            name = "p",
            host = "10.0.0.8",
            port = 27017,
            sshEnabled = true,
            sshHost = "bastion.example",
            sshPort = 22,
        )
        assertEquals("10.0.0.8:27017 via SSH bastion.example:22", profile.displayTarget)
    }
}
