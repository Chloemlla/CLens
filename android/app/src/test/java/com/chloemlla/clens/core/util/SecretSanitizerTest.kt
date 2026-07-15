package com.chloemlla.clens.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretSanitizerTest {
    @Test
    fun redactsMongoUriPassword() {
        val raw = "failed mongodb://admin:SuperSecret@192.168.1.9:27017/app"
        val sanitized = SecretSanitizer.sanitize(raw)
        assertTrue(sanitized.contains("[redacted]"))
        assertFalse(sanitized.contains("SuperSecret"))
    }

    @Test
    fun redactsUserHomePaths() {
        val sanitized = SecretSanitizer.sanitize("""C:\Users\akira\project\file.kt""")
        assertTrue(sanitized.contains("[user-home]"))
        assertFalse(sanitized.contains("akira"))
    }
}
