package com.chloemlla.clens.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonCodeAssistTest {
    @Test
    fun diagnosticsIncludeLocationForBrokenJson() {
        val diagnostics = JsonCodeAssist.diagnostics("{ broken")
        assertTrue(diagnostics.isNotEmpty())
        val text = diagnostics.first().display()
        assertTrue(text.isNotBlank())
        assertTrue(JsonCodeAssist.diagnosticMessages("""{"ok":1}""").isEmpty())
    }

    @Test
    fun autoClosesBracesAndQuotes() {
        val withBrace = JsonCodeAssist.assistTyping(
            oldText = "",
            oldCursor = 0,
            newText = "{",
            newCursor = 1,
        )
        assertEquals("{}", withBrace?.text)
        assertEquals(1, withBrace?.cursor)

        val withQuote = JsonCodeAssist.assistTyping(
            oldText = "{",
            oldCursor = 1,
            newText = "{\"",
            newCursor = 2,
        )
        assertEquals("{\"\"}", withQuote?.text)
        assertEquals(2, withQuote?.cursor)
    }

    @Test
    fun smartIndentsAfterObjectOpen() {
        val assisted = JsonCodeAssist.assistTyping(
            oldText = "{",
            oldCursor = 1,
            newText = "{\n",
            newCursor = 2,
        )
        assertTrue(assisted != null)
        assertTrue(assisted!!.text.startsWith("{\n  "))
        assertEquals(4, assisted.cursor)
    }

    @Test
    fun insertSymbolWrapsSelectionWithQuotes() {
        val result = JsonCodeAssist.insertSymbol(
            text = "name",
            selectionStart = 0,
            selectionEnd = 4,
            symbol = "\"",
        )
        assertEquals("\"name\"", result.text)
        assertEquals(6, result.cursor)
    }

    @Test
    fun formatJsonIfValidPrettyPrints() {
        val formatted = JsonCodeAssist.formatJsonIfValid("""{"a":1}""")
        assertTrue(formatted != null)
        assertTrue(formatted!!.contains("\n"))
        assertTrue(formatted.contains("\"a\""))
        assertEquals(null, JsonCodeAssist.formatJsonIfValid("{"))
    }
}
