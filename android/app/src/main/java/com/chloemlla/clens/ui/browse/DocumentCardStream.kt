package com.chloemlla.clens.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Lightweight document stream for mobile: `_id` plus up to four other top-level fields.
 */
@Composable
internal fun DocumentCardStream(
    documents: List<String>,
    selectedJson: String = "",
    titlePrefix: String = "文档",
    startIndex: Int = 1,
    maxExtraFields: Int = 4,
    onClick: (index: Int, json: String) -> Unit,
) {
    if (documents.isEmpty()) {
        Text("暂无文档", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        documents.forEachIndexed { index, json ->
            val preview = previewDocumentFields(json, maxExtraFields)
            val selected = json == selectedJson
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(index, json) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = titlePrefix + " #" + (startIndex + index),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = "_id: " + preview.id,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    preview.fields.forEach { field ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = field.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.35f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = field.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(0.65f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (preview.truncated) {
                        Text(
                            text = "…更多字段",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

internal data class DocumentFieldPreview(
    val id: String,
    val fields: List<Pair<String, String>>,
    val truncated: Boolean,
)

internal fun previewDocumentFields(json: String, maxExtraFields: Int = 4): DocumentFieldPreview {
    if (json.isBlank()) {
        return DocumentFieldPreview(id = "-", fields = emptyList(), truncated = false)
    }
    return runCatching {
        val obj = JSONObject(json)
        val id = formatJsonValue(obj.opt("_id"))
        val keys = mutableListOf<String>()
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key != "_id") keys += key
        }
        // Stable presentation: alphabetical non-_id keys.
        val ordered = keys.sorted()
        val shown = ordered.take(maxExtraFields).map { key ->
            key to formatJsonValue(obj.opt(key))
        }
        DocumentFieldPreview(
            id = id,
            fields = shown,
            truncated = ordered.size > maxExtraFields,
        )
    }.getOrElse {
        DocumentFieldPreview(id = "(无法解析)", fields = emptyList(), truncated = false)
    }
}

internal fun extractDocumentIdLabel(json: String): String {
    if (json.isBlank()) return ""
    return runCatching {
        val obj = JSONObject(json)
        if (!obj.has("_id")) return ""
        formatJsonValue(obj.opt("_id"))
    }.getOrDefault("").take(48)
}

private fun formatJsonValue(value: Any?): String {
    return when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            when {
                value.has("\$oid") -> value.optString("\$oid", value.toString())
                value.has("\$date") -> value.opt("\$date")?.toString() ?: value.toString()
                value.has("\$numberLong") -> value.optString("\$numberLong", value.toString())
                value.has("\$numberDecimal") -> value.optString("\$numberDecimal", value.toString())
                else -> value.toString()
            }
        }
        is Boolean, is Number -> value.toString()
        is String -> value
        else -> value.toString()
    }.let { raw ->
        if (raw.length <= 120) raw else raw.take(117) + "..."
    }
}
