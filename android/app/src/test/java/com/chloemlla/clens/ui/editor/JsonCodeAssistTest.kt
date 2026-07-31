package com.chloemlla.clens.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        // After typing `{`, assist already produces `{}` with cursor between braces.
        val withQuote = JsonCodeAssist.assistTyping(
            oldText = "{}",
            oldCursor = 1,
            newText = "{\"}",
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

    @Test
    fun compactJsonRemovesWhitespace() {
        val compacted = JsonCodeAssist.compactJson("""{"a": 1, "b": "hello"}""")
        assertEquals("""{"a":1,"b":"hello"}""", compacted)

        // Nested
        val nested = JsonCodeAssist.compactJson("""
            {
              "x": {
                "y": 1
              }
            }
        """.trimIndent())
        assertEquals("""{"x":{"y":1}}""", nested)

        // Array
        val array = JsonCodeAssist.compactJson("[\n  1,\n  2\n]")
        assertEquals("[1,2]", array)

        // Invalid JSON returns as-is
        val invalid = JsonCodeAssist.compactJson("{ broken")
        assertEquals("{ broken", invalid)
    }

    @Test
    fun validateJsonReturnsValidForCorrectJson() {
        val result = JsonCodeAssist.validateJson("""{"a":1}""")
        assertTrue(result is JsonValidationResult.Valid)
        val valid = result as JsonValidationResult.Valid
        assertTrue(valid.formattedJson.contains("\n"))
        assertTrue(valid.formattedJson.contains("  \"a\""))
    }

    @Test
    fun validateJsonReturnsInvalidWithLineForBrokenJson() {
        val json = """{"a":1,"b":2"""
        val result = JsonCodeAssist.validateJson(json)
        assertTrue(result is JsonValidationResult.Invalid)
        val invalid = result as JsonValidationResult.Invalid
        assertTrue(invalid.message.isNotBlank())
        // Should have line number since it's missing closing brace
        assertTrue(invalid.line != null)
    }

    @Test
    fun validateJsonReturnsInvalidForEmptyJson() {
        val result = JsonCodeAssist.validateJson("")
        assertTrue(result is JsonValidationResult.Invalid)
        val invalid = result as JsonValidationResult.Invalid
        assertEquals(1, invalid.line)
        assertEquals(1, invalid.column)
    }

    @Test
    fun findMatchingBracketFindsClosingBracket() {
        val text = """{"a":1}"""
        // Position at '{'
        val pair = JsonCodeAssist.findMatchingBracketPos(text, 0)
        assertEquals(Pair(0, 7), pair)

        // Position at '}'
        val pair2 = JsonCodeAssist.findMatchingBracketPos(text, 7)
        assertEquals(Pair(7, 0), pair2)
    }

    @Test
    fun findMatchingBracketFindsNestedMatching() {
        val text = """{"a":{"b":1}}"""
        // Position at outer '{'
        val pair = JsonCodeAssist.findMatchingBracketPos(text, 0)
        assertEquals(Pair(0, 13), pair)

        // Position at inner '{' (after "a":)
        val pair2 = JsonCodeAssist.findMatchingBracketPos(text, 5)
        assertEquals(Pair(5, 11), pair2)
    }

    @Test
    fun findMatchingBracketHandlesArrayBrackets() {
        val text = """[1, [2, 3], 4]"""
        // Position at outer '['
        val pair = JsonCodeAssist.findMatchingBracketPos(text, 0)
        assertEquals(Pair(0, 14), pair)

        // Position at inner '['
        val pair2 = JsonCodeAssist.findMatchingBracketPos(text, 4)
        assertEquals(Pair(4, 11), pair2)
    }

    @Test
    fun findMatchingBracketReturnsNullForNonBracket() {
        val text = """{"a":1}"""
        assertNull(JsonCodeAssist.findMatchingBracketPos(text, 1))  // "a"
        assertNull(JsonCodeAssist.findMatchingBracketPos(text, 3))  // ':'
        assertNull(JsonCodeAssist.findMatchingBracketPos(text, 100))  // out of bounds
    }

    @Test
    fun validateJsonHandlesArrays() {
        val result = JsonCodeAssist.validateJson("""[1, 2, 3]""")
        assertTrue(result is JsonValidationResult.Valid)
    }

    @Test
    fun compactJsonHandlesArray() {
        val compacted = JsonCodeAssist.compactJson("[\n  1,\n  2\n]")
        assertEquals("[1,2]", compacted)
    }

    @Test
    fun modeSwitchRoundTripTreeToRaw() {
        // Simulate: Tree -> Raw (serialize) -> Raw (validate) -> Tree (parse)
        val originalJson = """{"name":"test","count":42}"""
        val root = DocNodeCodec.parse(originalJson)

        // Tree -> Raw
        val rawJson = DocNodeCodec.serialize(root)
        assertEquals(originalJson, rawJson)

        // Raw -> validate
        val validation = JsonCodeAssist.validateJson(rawJson)
        assertTrue(validation is JsonValidationResult.Valid)

        // Raw -> Tree
        val reparsed = DocNodeCodec.tryParse(rawJson)
        assertTrue(reparsed.isSuccess)
        val reparsedName = DocNodeCodec.findNode(reparsed.getOrThrow(), "name")
        assertEquals("test", reparsedName?.scalar)
    }
}
