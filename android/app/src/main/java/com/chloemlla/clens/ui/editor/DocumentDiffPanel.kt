package com.chloemlla.clens.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.core.util.DiffStatus
import com.chloemlla.clens.core.util.FieldDiff
import com.chloemlla.clens.core.util.computeDiff
import com.chloemlla.clens.core.util.toJsonExport
import com.chloemlla.clens.ui.copyTextToClipboard
import kotlinx.coroutines.launch

enum class DiffDisplayMode {
    SideBySide,
    Inline,
}

/**
 * Top-level composable for the document diff view.
 * Shown inside a dialog or panel triggered from editor, query history, or snapshot view.
 */
@Composable
fun DocumentDiffPanel(
    originalJson: String,
    modifiedJson: String,
    onDismiss: () -> Unit,
    onApplyOriginalToModified: ((FieldDiff) -> Unit)? = null,
    onApplyModifiedToOriginal: ((FieldDiff) -> Unit)? = null,
    applyDirection: DiffApplyDirection = DiffApplyDirection.ModifiedToOriginal,
) {
    val diffs = remember(originalJson, modifiedJson) {
        computeDiff(originalJson, modifiedJson)
    }

    var displayMode by remember { mutableStateOf(DiffDisplayMode.SideBySide) }
    var collapseUnchanged by remember { mutableStateOf(diffs.count { it.status == DiffStatus.UNCHANGED } > 10) }
    val context = LocalContext.current

    val changedCount = diffs.count { it.status != DiffStatus.UNCHANGED }
    val addedCount = diffs.count { it.status == DiffStatus.ADDED }
    val removedCount = diffs.count { it.status == DiffStatus.REMOVED }
    val modifiedCount = diffs.count { it.status == DiffStatus.MODIFIED }
    val unchangedCount = diffs.count { it.status == DiffStatus.UNCHANGED }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("文档对比", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "+$addedCount -$removedCount ~$modifiedCount =$unchangedCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            val ok = copyTextToClipboard(context, "clens-diff-json", diffs.toJsonExport())
                            android.widget.Toast.makeText(
                                context,
                                if (ok) "已复制为 JSON" else "复制失败",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制差异为 JSON")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 560.dp),
            ) {
                // Mode toggle + collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = displayMode == DiffDisplayMode.SideBySide,
                        onClick = { displayMode = DiffDisplayMode.SideBySide },
                        label = { Text("并排") },
                    )
                    FilterChip(
                        selected = displayMode == DiffDisplayMode.Inline,
                        onClick = { displayMode = DiffDisplayMode.Inline },
                        label = { Text("内联") },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (unchangedCount > 0) {
                        FilterChip(
                            selected = !collapseUnchanged,
                            onClick = { collapseUnchanged = !collapseUnchanged },
                            label = {
                                Text(
                                    if (collapseUnchanged) "显示相同 ($unchangedCount)" else "隐藏相同",
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Legend
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiffLegendChip("新增", addedColor(), addedCount)
                    DiffLegendChip("移除", removedColor(), removedCount)
                    DiffLegendChip("修改", modifiedColor(), modifiedCount)
                    DiffLegendChip("相同", unchangedColor(), unchangedCount)
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()

                // Diff content
                val displayDiffs = if (collapseUnchanged) {
                    diffs.filter { it.status != DiffStatus.UNCHANGED }
                } else {
                    diffs
                }

                when (displayMode) {
                    DiffDisplayMode.SideBySide -> SideBySideDiffView(
                        diffs = displayDiffs,
                        originalJson = originalJson,
                        modifiedJson = modifiedJson,
                        onApplyToOriginal = onApplyModifiedToOriginal,
                        onApplyToModified = onApplyOriginalToModified,
                        applyDirection = applyDirection,
                    )
                    DiffDisplayMode.Inline -> InlineDiffView(
                        diffs = displayDiffs,
                        onApplyToOriginal = onApplyModifiedToOriginal,
                        onApplyToModified = onApplyOriginalToModified,
                        applyDirection = applyDirection,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DiffLegendChip(label: String, bgColor: Color, count: Int) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor.copy(alpha = 0.15f),
    ) {
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = bgColor,
        )
    }
}

@Composable
private fun SideBySideDiffView(
    diffs: List<FieldDiff>,
    originalJson: String,
    modifiedJson: String,
    onApplyToOriginal: ((FieldDiff) -> Unit)?,
    onApplyToModified: ((FieldDiff) -> Unit)?,
    applyDirection: DiffApplyDirection,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Synchronized scroll: mirror the scroll position
    var otherScrollOffset by remember { mutableStateOf(0) }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Left panel (original)
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 200.dp, max = 440.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            ) {
                Text(
                    text = "原始",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                itemsIndexed(diffs, key = { _, diff -> "orig_${diff.path}" }) { _, diff ->
                    SideBySideFieldRow(
                        diff = diff,
                        side = Side.Original,
                        onApply = if (onApplyToModified != null && diff.status != DiffStatus.ADDED) {
                            { onApplyToModified(diff) }
                        } else null,
                        showApply = applyDirection == DiffApplyDirection.OriginalToModified,
                    )
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        // Right panel (modified)
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 200.dp, max = 440.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            ) {
                Text(
                    text = "修改后",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                itemsIndexed(diffs, key = { _, diff -> "mod_${diff.path}" }) { _, diff ->
                    SideBySideFieldRow(
                        diff = diff,
                        side = Side.Modified,
                        onApply = if (onApplyToOriginal != null && diff.status != DiffStatus.REMOVED) {
                            { onApplyToOriginal(diff) }
                        } else null,
                        showApply = applyDirection == DiffApplyDirection.ModifiedToOriginal,
                    )
                }
            }
        }
    }
}

private enum class Side { Original, Modified }

@Composable
private fun SideBySideFieldRow(
    diff: FieldDiff,
    side: Side,
    onApply: (() -> Unit)?,
    showApply: Boolean,
) {
    val value = when (side) {
        Side.Original -> diff.originalValue
        Side.Modified -> diff.modifiedValue
    }

    val bgColor = when (diff.status) {
        DiffStatus.ADDED -> if (side == Side.Modified) addedColor().copy(alpha = 0.12f) else Color.Transparent
        DiffStatus.REMOVED -> if (side == Side.Original) removedColor().copy(alpha = 0.12f) else Color.Transparent
        DiffStatus.MODIFIED -> modifiedColor().copy(alpha = 0.08f)
        DiffStatus.UNCHANGED -> Color.Transparent
    }

    val textDecoration = when {
        diff.status == DiffStatus.REMOVED && side == Side.Original -> TextDecoration.LineThrough
        else -> null
    }

    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = diff.path,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = formatValueForDisplay(value),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDecoration = textDecoration,
                ),
                color = when {
                    diff.status == DiffStatus.UNCHANGED -> mutedColor
                    diff.status == DiffStatus.REMOVED && side == Side.Original -> removedColor()
                    diff.status == DiffStatus.ADDED && side == Side.Modified -> addedColor()
                    diff.status == DiffStatus.MODIFIED -> modifiedColor()
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 3,
            )
        }
        if (showApply && onApply != null) {
            IconButton(
                onClick = onApply,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SwapHoriz,
                    contentDescription = "应用此字段",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun InlineDiffView(
    diffs: List<FieldDiff>,
    onApplyToOriginal: ((FieldDiff) -> Unit)?,
    onApplyToModified: ((FieldDiff) -> Unit)?,
    applyDirection: DiffApplyDirection,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 440.dp),
    ) {
        itemsIndexed(diffs, key = { _, diff -> "inline_${diff.path}" }) { _, diff ->
            InlineFieldRow(
                diff = diff,
                onApplyToOriginal = onApplyToOriginal,
                onApplyToModified = onApplyToModified,
                applyDirection = applyDirection,
            )
        }
    }
}

@Composable
private fun InlineFieldRow(
    diff: FieldDiff,
    onApplyToOriginal: ((FieldDiff) -> Unit)?,
    onApplyToModified: ((FieldDiff) -> Unit)?,
    applyDirection: DiffApplyDirection,
) {
    val bgColor = when (diff.status) {
        DiffStatus.ADDED -> addedColor().copy(alpha = 0.12f)
        DiffStatus.REMOVED -> removedColor().copy(alpha = 0.12f)
        DiffStatus.MODIFIED -> modifiedColor().copy(alpha = 0.08f)
        DiffStatus.UNCHANGED -> Color.Transparent
    }

    val textColor = when (diff.status) {
        DiffStatus.ADDED -> addedColor()
        DiffStatus.REMOVED -> removedColor()
        DiffStatus.MODIFIED -> modifiedColor()
        DiffStatus.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when (diff.status) {
        DiffStatus.ADDED -> Icons.Outlined.Add
        DiffStatus.REMOVED -> Icons.Outlined.Remove
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (statusIcon != null) {
            Icon(
                imageVector = statusIcon,
                contentDescription = diff.status.name,
                modifier = Modifier.size(14.dp),
                tint = textColor,
            )
            Spacer(Modifier.width(4.dp))
        }

        Text(
            text = diff.path,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )

        when (diff.status) {
            DiffStatus.ADDED -> {
                Text(
                    text = "+ " + formatValueForDisplay(diff.modifiedValue),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = textColor,
                    modifier = Modifier.weight(0.65f),
                )
            }
            DiffStatus.REMOVED -> {
                Text(
                    text = "- " + formatValueForDisplay(diff.originalValue),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDecoration = TextDecoration.LineThrough,
                    ),
                    color = textColor,
                    modifier = Modifier.weight(0.65f),
                )
            }
            DiffStatus.MODIFIED -> {
                Column(modifier = Modifier.weight(0.65f)) {
                    Text(
                        text = "- " + formatValueForDisplay(diff.originalValue),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = removedColor(),
                    )
                    Text(
                        text = "+ " + formatValueForDisplay(diff.modifiedValue),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = addedColor(),
                    )
                }
            }
            DiffStatus.UNCHANGED -> {
                Text(
                    text = "= " + formatValueForDisplay(diff.originalValue),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.weight(0.65f),
                )
            }
        }

        // Apply buttons
        when (applyDirection) {
            DiffApplyDirection.ModifiedToOriginal -> {
                if (onApplyToOriginal != null && diff.status != DiffStatus.REMOVED) {
                    IconButton(
                        onClick = { onApplyToOriginal(diff) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = "应用到原始",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            DiffApplyDirection.OriginalToModified -> {
                if (onApplyToModified != null && diff.status != DiffStatus.ADDED) {
                    IconButton(
                        onClick = { onApplyToModified(diff) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = "应用到修改后",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a value for display in the diff view.
 */
private fun formatValueForDisplay(value: Any?): String {
    if (value == null) return "null"
    val str = value.toString()
    return when {
        str.startsWith("{") && str.endsWith("}") -> str
        str.startsWith("[") && str.endsWith("]") -> str
        str.length > 60 -> str.take(57) + "..."
        else -> str
    }
}

// Color helpers - using Material Design semantic colors
@Composable
private fun addedColor(): Color = Color(0xFF2E7D32) // green-800

@Composable
private fun removedColor(): Color = Color(0xFFC62828) // red-800

@Composable
private fun modifiedColor(): Color = Color(0xFF8D6E63) // brown-600

@Composable
private fun unchangedColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

enum class DiffApplyDirection {
    ModifiedToOriginal, // Copy from modified doc to original doc
    OriginalToModified, // Copy from original doc to modified doc
}
