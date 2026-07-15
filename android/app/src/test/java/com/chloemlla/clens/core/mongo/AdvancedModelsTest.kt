package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedModelsTest {
    @Test
    fun gridFsSummaryHoldsIdentity() {
        val file = GridFsFileSummary(
            id = "64b",
            filename = "a.txt",
            length = 12,
            uploadDate = "date",
            contentType = "text/plain",
        )
        assertEquals("a.txt", file.filename)
        assertEquals(12, file.length)
    }
}
