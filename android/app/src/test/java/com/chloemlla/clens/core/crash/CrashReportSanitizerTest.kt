package com.chloemlla.clens.core.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportSanitizerTest {
    @Test
    fun redactsMongoUriPassword() {
        val raw = "failed mongodb://admin:SuperSecret@192.168.1.9:27017/app"
        val sanitized = CrashReportSanitizer.sanitize(raw)
        assertTrue(sanitized.contains("[redacted]"))
        assertFalse(sanitized.contains("SuperSecret"))
    }

    @Test
    fun redactsUserHomePaths() {
        val sanitized = CrashReportSanitizer.sanitize("""C:\Users\akira\project\file.kt""")
        assertTrue(sanitized.contains("[user-home]"))
        assertFalse(sanitized.contains("akira"))
    }
}
