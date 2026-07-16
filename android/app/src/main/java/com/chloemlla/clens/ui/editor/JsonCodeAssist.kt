package com.chloemlla.clens.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Code-mode helpers: symbol insert, auto-close, smart indent, diagnostics.
 */
object JsonCodeAssist {
    private val PAIR_CLOSE = mapOf(
        '{' to '}',
        '[' to ']',
        '"' to '"',
    )

    data class Diagnostic(
        val message: String,
        val line: Int? = null,
        val column: Int? = null,
    ) {
        fun display(): String {
            val location = when {
                line != null && column != null -> "L$line:C$column "
                line != null -> "L$line "
                else -> ""
            }
            return location + message
        }
    }

    data class EditResult(
        val text: String,
        val cursor: Int,
    )

    fun diagnostics(json: String): List<Diagnostic> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            return listOf(Diagnostic("JSON 不能为空", line = 1, column = 1))
        }
        return try {
            JSONTokener(trimmed).nextValue()
            emptyList<Diagnostic>()
        } catch (error: Exception) {
            val message = error.message ?: "JSON 语法错误"
            val position = extractPosition(message)
            val lineCol = position?.let { offsetToLineColumn(json, it) }
            listOf(
                Diagnostic(
                    message = humanizeJsonError(message),
                    line = lineCol?.first,
                    column = lineCol?.second,
                ),
            )
        }
    }

    fun diagnosticMessages(json: String): List<String> {
        return diagnostics(json).map { it.display() }
    }

    fun assistTyping(previous: TextFieldValue, next: TextFieldValue): TextFieldValue {
        val result = assistTyping(
            oldText = previous.text,
            oldCursor = previous.selection.start,
            newText = next.text,
            newCursor = next.selection.start,
            oldCollapsed = previous.selection.collapsed,
            newCollapsed = next.selection.collapsed,
        ) ?: return next
        return TextFieldValue(text = result.text, selection = TextRange(result.cursor))
    }

    /**
     * Pure-string typing assist used by unit tests and Compose wrappers.
     * Returns null when no special handling should apply.
     */
    fun assistTyping(
        oldText: String,
        oldCursor: Int,
        newText: String,
        newCursor: Int,
        oldCollapsed: Boolean = true,
        newCollapsed: Boolean = true,
    ): EditResult? {
        if (newText == oldText) return null
        if (!oldCollapsed || !newCollapsed) return null
        val insertAt = oldCursor
        if (insertAt < 0 || insertAt > oldText.length) return null
        if (newText.length < oldText.length) return null

        val insertedCount = newText.length - oldText.length
        if (insertedCount <= 0) return null
        if (insertAt + insertedCount > newText.length) return null
        if (!newText.startsWith(oldText.substring(0, insertAt))) return null
        if (!newText.endsWith(oldText.substring(insertAt))) return null

        val inserted = newText.substring(insertAt, insertAt + insertedCount)

        if (inserted == "\n") {
            return applySmartIndent(oldText, insertAt)
        }

        if (insertedCount == 1) {
            val ch = inserted[0]
            val closer = PAIR_CLOSE[ch] ?: return null
            if (ch == '"' && insertAt < oldText.length && oldText[insertAt] == '"') {
                return EditResult(text = oldText, cursor = insertAt + 1)
            }
            if (insertAt < oldText.length && oldText[insertAt] == closer) {
                return null
            }
            val builder = StringBuilder(oldText.length + 2)
            builder.append(oldText, 0, insertAt)
            builder.append(ch)
            builder.append(closer)
            builder.append(oldText, insertAt, oldText.length)
            return EditResult(text = builder.toString(), cursor = insertAt + 1)
        }

        // keep cursor from new value when no special transform
        return null
    }

    fun insertSymbol(value: TextFieldValue, symbol: String): TextFieldValue {
        val result = insertSymbol(
            text = value.text,
            selectionStart = value.selection.min,
            selectionEnd = value.selection.max,
            symbol = symbol,
        )
        return TextFieldValue(text = result.text, selection = TextRange(result.cursor))
    }

    fun insertSymbol(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        symbol: String,
    ): EditResult {
        if (symbol.isEmpty()) {
            return EditResult(text = text, cursor = selectionEnd.coerceIn(0, text.length))
        }
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)
        val pairCloser = when (symbol) {
            "{" -> "}"
            "[" -> "]"
            "\"" -> "\""
            else -> null
        }
        return if (pairCloser != null && start == end) {
            val next = text.substring(0, start) + symbol + pairCloser + text.substring(end)
            EditResult(text = next, cursor = start + symbol.length)
        } else if (pairCloser != null && start != end) {
            // wrap selected text with pair
            val selected = text.substring(start, end)
            val next = text.substring(0, start) + symbol + selected + pairCloser + text.substring(end)
            EditResult(text = next, cursor = end + 2)
        } else {
            val next = text.substring(0, start) + symbol + text.substring(end)
            EditResult(text = next, cursor = start + symbol.length)
        }
    }

    fun formatJsonIfValid(json: String): String? {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            when (val value = JSONTokener(trimmed).nextValue()) {
                is JSONObject -> value.toString(2)
                is JSONArray -> value.toString(2)
                else -> null
            }
        }.getOrNull()
    }

    private fun applySmartIndent(oldText: String, newlineAt: Int): EditResult {
        val lineStart = oldText.lastIndexOf('\n', newlineAt - 1).let { if (it < 0) 0 else it + 1 }
        val currentLine = oldText.substring(lineStart, newlineAt)
        val baseIndent = currentLine.takeWhile { it == ' ' || it == '\t' }
        val trimmedRight = currentLine.trimEnd()
        val extra = if (trimmedRight.endsWith("{") || trimmedRight.endsWith("[")) "  " else ""
        val indent = baseIndent + extra
        val next = oldText.substring(0, newlineAt) + "\n" + indent + oldText.substring(newlineAt)
        val cursor = newlineAt + 1 + indent.length
        return EditResult(text = next, cursor = cursor)
    }

    private fun extractPosition(message: String): Int? {
        val character = Regex("character (\\d+)", RegexOption.IGNORE_CASE).find(message)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (character != null) return character
        return Regex("index (\\d+)", RegexOption.IGNORE_CASE).find(message)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun offsetToLineColumn(text: String, offset: Int): Pair<Int, Int> {
        val safe = offset.coerceIn(0, text.length)
        var line = 1
        var column = 1
        for (i in 0 until safe) {
            if (text[i] == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
        }
        return line to column
    }

    private fun humanizeJsonError(message: String): String {
        val lower = message.lowercase()
        return when {
            "end of input" in lower || "unterminated" in lower -> "括号/引号可能未闭合"
            "expected" in lower && ("," in message || "comma" in lower) -> "可能缺少逗号或多余逗号"
            "expected" in lower && ("]" in message || "}" in message) -> "括号不匹配或结构不完整"
            else -> message
        }
    }
}
