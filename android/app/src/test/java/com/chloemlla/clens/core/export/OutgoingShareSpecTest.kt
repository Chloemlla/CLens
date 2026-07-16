package com.chloemlla.clens.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingShareSpecTest {
    @Test
    fun textShareDoesNotNeedUriGrant() {
        val spec = OutgoingShareSpec.text(subject = "CLens export", body = "{\"a\":1}")
        assertEquals(OutgoingShareSpec.ACTION_SEND, spec.action)
        assertEquals("text/plain", spec.mimeType)
        assertFalse(spec.requiresReadUriGrant)
        assertFalse(spec.requiresClipData)
        assertTrue(spec.isChooserFriendlyExternalShare)
    }

    @Test
    fun fileShareRequiresReadGrantAndClipData() {
        val spec = OutgoingShareSpec.file(subject = "CLens page export", mimeType = "application/json")
        assertTrue(spec.requiresReadUriGrant)
        assertTrue(spec.requiresClipData)
        assertEquals("application/json", spec.mimeType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankSubjectRejected() {
        OutgoingShareSpec.text(subject = " ", body = "x")
    }
}
