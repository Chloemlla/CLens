package com.chloemlla.clens.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hard ceiling for Raw mode. Above this the editor is read-only and renders unstyled text.
 * Measured in characters as a cheap stand-in for UTF-8 bytes (JSON is mostly ASCII) so that
 * a keystroke never copies the whole document via [String.toByteArray].
 */
internal const val RAW_JSON_HARD_LIMIT_BYTES = 5 * 1024 * 1024

/** Above this size syntax highlighting and bracket matching are dropped to keep typing responsive. */
internal const val RAW_JSON_RICH_TEXT_LIMIT_CHARS = 200 * 1024

/** Above this line count the gutter is hidden instead of laying out one number per line. */
internal const val RAW_JSON_MAX_GUTTER_LINES = 2000

/**
 * Syntax highlighting colors for Raw JSON mode.
 */
private object SyntaxColors {
    val keyLight = Color(0xFF1D4ED8)
    val stringLight = Color(0xFF16A34A)
    val numberLight = Color(0xFFEA580C)
    val boolNullLight = Color(0xFF7C3AED)
    val bracketLight = Color(0xFF64748B)
    val lineNumLight = Color(0xFF94A3B8)
    val lineNumBgLight = Color(0xFFF1F5F9)

    val keyDark = Color(0xFF93C5FD)
    val stringDark = Color(0xFF4ADE80)
    val numberDark = Color(0xFFFB923C)
    val boolNullDark = Color(0xFFC084FC)
    val bracketDark = Color(0xFF94A3B8)
    val lineNumDark = Color(0xFF64748B)
    val lineNumBgDark = Color(0xFF1E293B)

    fun key(dark: Boolean) = if (dark) keyDark else keyLight
    fun string(dark: Boolean) = if (dark) stringDark else stringLight
    fun number(dark: Boolean) = if (dark) numberDark else numberLight
    fun boolNull(dark: Boolean) = if (dark) boolNullDark else boolNullLight
    fun bracket(dark: Boolean) = if (dark) bracketDark else bracketLight
    fun lineNum(dark: Boolean) = if (dark) lineNumDark else lineNumLight
    fun lineNumBg(dark: Boolean) = if (dark) lineNumBgDark else lineNumBgLight
    fun surface(dark: Boolean) = if (dark) Color(0xFF111827) else Color(0xFFFBFCFD)
}

/**
 * Syntax-highlighted JSON editor with line numbers.
 */
@Composable
fun RawJsonEditor(
    jsonText: String,
    enabled: Boolean,
    onJsonChange: (String) -> Unit,
    onValidateRequest: () -> Unit,
    validationResult: JsonValidationResult?,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = !MaterialTheme.colorScheme.surface.luminance().let { it > 0.5f }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(jsonText, TextRange(jsonText.length))) }
    var cursorPosition by remember { mutableIntStateOf(0) }

    LaunchedEffect(jsonText) {
        // Only rebuild when the external text really differs from what the user typed, and
        // keep the caret where it was (clamped) instead of slamming it to the end on every
        // parent-side normalization.
        if (textFieldValue.text != jsonText) {
            val caret = textFieldValue.selection.start.coerceIn(0, jsonText.length)
            textFieldValue = TextFieldValue(jsonText, TextRange(caret))
        }
    }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    val colors = remember(isDarkTheme) {
        RawJsonColors(
            key = SyntaxColors.key(isDarkTheme),
            string = SyntaxColors.string(isDarkTheme),
            number = SyntaxColors.number(isDarkTheme),
            boolNull = SyntaxColors.boolNull(isDarkTheme),
            bracket = SyntaxColors.bracket(isDarkTheme),
            lineNum = SyntaxColors.lineNum(isDarkTheme),
            lineNumBg = SyntaxColors.lineNumBg(isDarkTheme),
            surface = SyntaxColors.surface(isDarkTheme),
            cursorColor = if (isDarkTheme) Color(0xFFE5E7EB) else Color(0xFF1D4ED8),
        )
    }

    val text = textFieldValue.text
    // Character count instead of toByteArray().size: the old version copied the whole
    // document on every keystroke just to render a size label.
    val approxBytes = text.length
    val overHardLimit = approxBytes > RAW_JSON_HARD_LIMIT_BYTES
    // Past the hard limit the editor is genuinely read-only; it used to keep accepting edits
    // while telling the user Raw mode was disabled.
    val editingEnabled = enabled && !overHardLimit
    // Large text drops highlighting and bracket matching so a keystroke does not re-scan
    // the entire document.
    val richText = !overHardLimit && approxBytes <= RAW_JSON_RICH_TEXT_LIMIT_CHARS

    val lineCount = remember(text) {
        if (approxBytes > RAW_JSON_RICH_TEXT_LIMIT_CHARS) 0 else text.count { it == '\n' } + 1
    }
    val showGutter = lineCount in 1..RAW_JSON_MAX_GUTTER_LINES
    val gutterDigits = lineCount.toString().length
    // One newline-joined string rather than a Text per line: a 10k-line document used to
    // compose 10k Text nodes.
    val gutterText = remember(lineCount) {
        if (lineCount !in 1..RAW_JSON_MAX_GUTTER_LINES) {
            ""
        } else {
            val digits = lineCount.toString().length
            buildString(lineCount * (digits + 1)) {
                for (i in 1..lineCount) {
                    if (i > 1) append('\n')
                    append(i.toString().padStart(digits))
                }
            }
        }
    }

    val notice: Pair<String, Boolean>? = when {
        overHardLimit ->
            "文档过大（约 ${approxBytes / 1024 / 1024}MB > 5MB），Raw 模式已切换为只读，并关闭语法高亮" to true
        !richText ->
            "文本较大（约 ${approxBytes / 1024}KB），已关闭语法高亮、括号匹配与行号以保持输入流畅" to false
        !showGutter ->
            "行数超过 $RAW_JSON_MAX_GUTTER_LINES，已隐藏行号以保持滚动流畅" to false
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Toolbar
        RawJsonToolbar(
            textFieldValue = text,
            enabled = editingEnabled,
            validationResult = validationResult,
            onValidateRequest = onValidateRequest,
            onJsonChange = onJsonChange,
        )

        // Size / degradation notice
        notice?.let { (msg, isError) ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        // Editor with line numbers
        Surface(
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 400.dp),
        ) {
            // Exactly one scrollable owns the vertical axis. The gutter and the body used to
            // share a single ScrollState, so maxValue depended on whichever measured last and
            // long documents scrolled out of sync / got clipped.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(verticalScrollState),
            ) {
                // Line numbers gutter (single Text, scrolls with the body)
                if (showGutter) {
                    Text(
                        text = gutterText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                        color = colors.lineNum,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width((gutterDigits * 10 + 16).dp)
                            .background(colors.lineNumBg)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }

                // Editor content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                        .background(colors.surface)
                        .padding(8.dp),
                ) {
                    // Highlight work is skipped entirely for large documents: these passes
                    // are O(n) per keystroke. They are also only meaningful when applied
                    // through the visual transformation below.
                    val highlighted: AnnotatedString? = if (richText) {
                        val annotated = remember(text, isDarkTheme) { highlightJson(text, colors) }
                        val bracketMatchPair = remember(text, cursorPosition) {
                            findMatchingBracket(text, cursorPosition)
                        }
                        remember(annotated, bracketMatchPair, isDarkTheme) {
                            if (bracketMatchPair != null) {
                                val (openIdx, closeIdx) = bracketMatchPair
                                buildAnnotatedString {
                                    append(annotated)
                                    val highlightColor = if (isDarkTheme) {
                                        Color(0xFFFBBF24).copy(alpha = 0.4f)
                                    } else {
                                        Color(0xFFFBBF24).copy(alpha = 0.3f)
                                    }
                                    if (openIdx in 0 until length) {
                                        addStyle(SpanStyle(background = highlightColor), openIdx, (openIdx + 1).coerceAtMost(length))
                                    }
                                    if (closeIdx in 0 until length) {
                                        addStyle(SpanStyle(background = highlightColor), closeIdx, (closeIdx + 1).coerceAtMost(length))
                                    }
                                }
                            } else {
                                annotated
                            }
                        }
                    } else {
                        null
                    }

                    // The highlighted string was previously computed and then dropped, so
                    // Raw mode rendered as plain text. Feed it through a transformation
                    // that preserves offsets, which is what makes the colors visible.
                    val visualTransformation = if (highlighted != null && highlighted.length == text.length) {
                        VisualTransformation { TransformedText(highlighted, OffsetMapping.Identity) }
                    } else {
                        VisualTransformation.None
                    }

                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { incoming ->
                            val assisted = JsonCodeAssist.assistTyping(textFieldValue, incoming)
                            cursorPosition = assisted.selection.start
                            textFieldValue = assisted
                            onJsonChange(assisted.text)
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colors.cursorColor,
                        ),
                        cursorBrush = SolidColor(colors.cursorColor),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editingEnabled,
                        visualTransformation = visualTransformation,
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "输入或粘贴 JSON...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = colors.lineNum,
                                        ),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class RawJsonColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val boolNull: Color,
    val bracket: Color,
    val lineNum: Color,
    val lineNumBg: Color,
    val surface: Color,
    val cursorColor: Color,
)

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

/**
 * Tokenize and apply syntax highlighting to JSON text.
 */
private fun highlightJson(text: String, colors: RawJsonColors): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '{' || ch == '}' || ch == '[' || ch == ']' -> {
                    withStyle(SpanStyle(color = colors.bracket)) { append(ch) }
                    i++
                }
                ch == ':' || ch == ',' -> {
                    withStyle(SpanStyle(color = colors.bracket)) { append(ch) }
                    i++
                }
                ch == '"' -> {
                    val (endIdx, isKey) = findStringExtent(text, i)
                    val str = text.substring(i, endIdx)
                    val color = if (isKey) colors.key else colors.string
                    withStyle(SpanStyle(color = color)) { append(str) }
                    i = endIdx
                }
                ch == '-' || ch.isDigit() -> {
                    val endIdx = findNumberExtent(text, i)
                    withStyle(SpanStyle(color = colors.number)) { append(text.substring(i, endIdx)) }
                    i = endIdx
                }
                ch == 't' || ch == 'f' || ch == 'n' -> {
                    val endIdx = findWordExtent(text, i)
                    val word = text.substring(i, endIdx)
                    withStyle(SpanStyle(color = colors.boolNull)) { append(word) }
                    i = endIdx
                }
                ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' -> {
                    append(ch)
                    i++
                }
                else -> {
                    append(ch)
                    i++
                }
            }
        }
    }
}

/**
 * Find the extent of a JSON string starting at [start] (which points to '"').
 * Returns (endIndex, isKey) where isKey is true if this looks like an object key.
 */
private fun findStringExtent(text: String, start: Int): Pair<Int, Boolean> {
    var i = start + 1
    while (i < text.length) {
        if (text[i] == '"') {
            var j = i + 1
            while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
            val isKey = j < text.length && text[j] == ':'
            return i + 1 to isKey
        }
        if (text[i] == '\\' && i + 1 < text.length) {
            i += 2
        } else {
            i++
        }
    }
    return text.length to false
}

/**
 * Find the extent of a JSON number starting at [start].
 */
private fun findNumberExtent(text: String, start: Int): Int {
    var i = start
    while (i < text.length) {
        val ch = text[i]
        if (ch.isDigit() || ch == '.' || ch == 'e' || ch == 'E' || ch == '+' || ch == '-') {
            i++
        } else {
            break
        }
    }
    return i
}

/**
 * Find the extent of a JSON keyword (true/false/null) starting at [start].
 */
private fun findWordExtent(text: String, start: Int): Int {
    val keywords = listOf("true", "false", "null")
    for (kw in keywords) {
        if (text.regionMatches(start, kw, 0, kw.length)) {
            val endIdx = start + kw.length
            if (endIdx >= text.length || !text[endIdx].isLetterOrDigit()) {
                return endIdx
            }
        }
    }
    return start + 1
}

/**
 * Find matching bracket position. Returns (cursorBracketIdx, matchingBracketIdx) or null.
 *
 * Brackets inside string literals are skipped: counting them shifted the depth and paired
 * the caret with the wrong bracket whenever a JSON value contained `{}` or `[]`.
 */
private fun findMatchingBracket(text: String, cursor: Int): Pair<Int, Int>? {
    if (cursor !in text.indices) return null
    val ch = text[cursor]
    val isOpen = ch == '{' || ch == '['
    val isClose = ch == '}' || ch == ']'
    if (!isOpen && !isClose) return null
    if (indexIsInsideString(text, cursor)) return null

    val targetOpen = when (ch) {
        '{', '}' -> '{'
        '[', ']' -> '['
        else -> return null
    }
    val targetClose = when (ch) {
        '{', '}' -> '}'
        '[', ']' -> ']'
        else -> return null
    }

    // Precompute which offsets sit inside a string once, then scan only real structure.
    val inString = stringMask(text)

    return if (isOpen) {
        var depth = 0
        var i = cursor
        while (i < text.length) {
            if (!inString[i]) {
                val c = text[i]
                if (c == targetOpen) depth++
                if (c == targetClose) {
                    depth--
                    if (depth == 0) return cursor to i
                }
            }
            i++
        }
        null
    } else {
        var depth = 0
        var i = cursor
        while (i >= 0) {
            if (!inString[i]) {
                val c = text[i]
                if (c == targetClose) depth++
                if (c == targetOpen) {
                    depth--
                    if (depth == 0) return cursor to i
                }
            }
            i--
        }
        null
    }
}

/**
 * Marks every offset that falls inside a JSON string literal, quotes included, honouring
 * backslash escapes so an escaped quote does not end the string.
 */
private fun stringMask(text: String): BooleanArray {
    val mask = BooleanArray(text.length)
    var inside = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (inside) {
            mask[i] = true
            when {
                c == '\\' && i + 1 < text.length -> {
                    mask[i + 1] = true
                    i++
                }
                c == '"' -> inside = false
            }
        } else if (c == '"') {
            inside = true
            mask[i] = true
        }
        i++
    }
    return mask
}

private fun indexIsInsideString(text: String, index: Int): Boolean {
    if (index !in text.indices) return false
    return stringMask(text)[index]
}

@Composable
private fun RawJsonToolbar(
    textFieldValue: String,
    enabled: Boolean,
    validationResult: JsonValidationResult?,
    onValidateRequest: () -> Unit,
    onJsonChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = false,
            onClick = {
                // Silently returning the original text made the button look broken on
                // invalid JSON; say why nothing changed.
                val formatted = JsonCodeAssist.formatJsonIfValid(textFieldValue)
                if (formatted == null) {
                    Toast.makeText(context, "JSON 不合法，无法格式化", Toast.LENGTH_SHORT).show()
                } else {
                    onJsonChange(formatted)
                }
            },
            enabled = enabled,
            label = { Text("格式化") },
        )
        FilterChip(
            selected = false,
            onClick = {
                val compacted = JsonCodeAssist.compactJson(textFieldValue)
                if (compacted == textFieldValue) {
                    Toast.makeText(context, "JSON 不合法或已是压缩形式", Toast.LENGTH_SHORT).show()
                } else {
                    onJsonChange(compacted)
                }
            },
            enabled = enabled,
            label = { Text("压缩") },
        )
        FilterChip(
            selected = false,
            onClick = onValidateRequest,
            enabled = enabled,
            label = { Text("验证") },
        )
        FilterChip(
            selected = false,
            onClick = {
                try {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("JSON", textFieldValue)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = enabled,
            label = { Text("复制") },
        )

        // Validation result indicator
        validationResult?.let { result ->
            when (result) {
                is JsonValidationResult.Valid -> {
                    Text(
                        text = "✓ JSON 合法",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF16A34A),
                    )
                }
                is JsonValidationResult.Invalid -> {
                    val lineInfo = if (result.line != null) " (行 ${result.line})" else ""
                    Text(
                        text = "✗ ${result.message}$lineInfo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
