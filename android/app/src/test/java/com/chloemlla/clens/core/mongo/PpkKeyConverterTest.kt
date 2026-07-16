package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PpkKeyConverterTest {
    @Test
    fun detectsPpkHeader() {
        assertTrue(PpkKeyConverter.looksLikePpk("PuTTY-User-Key-File-2: ssh-rsa\nEncryption: none\n"))
        assertFalse(PpkKeyConverter.looksLikePpk("-----BEGIN OPENSSH PRIVATE KEY-----"))
    }

    @Test
    fun rejectsUnsupportedAlgorithmCleanly() {
        val sample = """
            PuTTY-User-Key-File-2: ssh-ed25519
            Encryption: none
            Comment: test
            Public-Lines: 1
            AAAA
            Private-Lines: 1
            AAAA
            Private-MAC: 00
        """.trimIndent()
        try {
            PpkKeyConverter.toOpenSshPem(sample)
            fail("expected validation failure")
        } catch (error: MongoAdminException.Validation) {
            assertTrue(error.message!!.contains("ssh-rsa") || error.message!!.contains("PPK"))
        }
    }
}
