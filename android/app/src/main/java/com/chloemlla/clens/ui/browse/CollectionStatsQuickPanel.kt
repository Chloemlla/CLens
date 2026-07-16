package com.chloemlla.clens.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.core.mongo.CollectionSummary
import java.util.Locale

/**
 * Dense quick stats for the selected collection: Data Size / Storage Size / Indexes.
 */
@Composable
internal fun CollectionStatsQuickPanel(
    stats: CollectionSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "集合速览",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCell(
                    label = "数据大小",
                    value = formatBytes(stats.size),
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "存储大小",
                    value = formatBytes(stats.storageSize),
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "索引",
                    value = formatIndexes(stats),
                    modifier = Modifier.weight(1f),
                )
            }
            val secondary = buildList {
                stats.count?.let { add("文档 " + it) }
                stats.avgObjSize?.let { add("均对象 " + formatBytes(it.toLong())) }
                if (stats.type.isNotBlank()) add(stats.type)
            }
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatIndexes(stats: CollectionSummary): String {
    val count = stats.nindexes?.toString() ?: "-"
    val size = stats.totalIndexSize?.let { formatBytes(it) }
    return if (size != null) "$count ($size)" else count
}

private fun formatBytes(value: Long?): String {
    if (value == null) return "-"
    if (value < 1024) return value.toString() + " B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var unitIndex = -1
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }
    return if (unitIndex < 0) {
        value.toString() + " B"
    } else {
        String.format(Locale.US, "%.1f %s", size, units[unitIndex])
    }
}
