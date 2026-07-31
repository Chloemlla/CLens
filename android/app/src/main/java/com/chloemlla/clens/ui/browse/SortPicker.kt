package com.chloemlla.clens.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.ui.RecentSortEntry
import com.chloemlla.clens.ui.SortDirection

/**
 * A small chip showing the current sort, or a placeholder when no sort is active.
 * Tapping it opens the sort picker bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortChip(
    sortField: String?,
    sortDirection: SortDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (sortField.isNullOrBlank()) {
        "默认顺序"
    } else {
        "Sort: $sortField ${sortDirection.symbol}"
    }
    FilterChip(
        selected = sortField != null,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Sort,
                contentDescription = "排序",
                modifier = Modifier.height(16.dp),
            )
        },
        modifier = modifier,
    )
}

/**
 * Bottom sheet picker for sort field and direction selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SortPickerSheet(
    sortField: String?,
    sortDirection: SortDirection,
    fieldTypes: Map<String, String>,
    recentSorts: List<RecentSortEntry>,
    onApply: (field: String, direction: SortDirection) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedField by remember(sortField) { mutableStateOf(sortField ?: "") }
    var selectedDirection by remember(sortDirection) { mutableStateOf(sortDirection) }

    val sortableFields = remember(fieldTypes) {
        fieldTypes.filter { (field, type) ->
            field.isNotBlank() && QueryFieldInferencerHelper.isSortableType(type)
        }
    }
    val unsortableFields = remember(fieldTypes) {
        fieldTypes.filter { (field, type) ->
            field.isNotBlank() && !QueryFieldInferencerHelper.isSortableType(type)
        }
    }

    // System fields always available
    val systemFields = listOf("_id", "createdAt", "updatedAt")
    val availableSystemFields = remember(fieldTypes) {
        systemFields.filter { field ->
            // Include if not in field types (unknown), or if it's sortable
            !fieldTypes.containsKey(field) || QueryFieldInferencerHelper.isSortableType(fieldTypes[field] ?: "")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "排序",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Direction toggle
            Text(
                text = "方向",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedDirection == SortDirection.Ascending,
                    onClick = { selectedDirection = SortDirection.Ascending },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                ) {
                    Text("升序 ↑")
                }
                SegmentedButton(
                    selected = selectedDirection == SortDirection.Descending,
                    onClick = { selectedDirection = SortDirection.Descending },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                ) {
                    Text("降序 ↓")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Field name input
            OutlinedTextField(
                value = selectedField,
                onValueChange = { selectedField = it },
                label = { Text("字段名") },
                placeholder = { Text("_id, createdAt, status ...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // Recent sorts
            if (recentSorts.isNotEmpty()) {
                Text(
                    text = "最近使用",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    recentSorts.forEach { entry ->
                        FilterChip(
                            selected = selectedField == entry.field && selectedDirection == entry.direction,
                            onClick = {
                                selectedField = entry.field
                                selectedDirection = entry.direction
                            },
                            label = {
                                Text(entry.field + " " + entry.direction.symbol)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            // System fields section
            if (availableSystemFields.isNotEmpty()) {
                Text(
                    text = "系统字段",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availableSystemFields.forEach { field ->
                        FilterChip(
                            selected = selectedField == field,
                            onClick = { selectedField = field },
                            label = { Text(field) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Sortable fields section
            if (sortableFields.isNotEmpty()) {
                Text(
                    text = "可用字段（可排序）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height((sortableFields.size.coerceAtMost(10) * 48).dp),
                ) {
                    items(sortableFields.toList()) { (field, type) ->
                        ListItem(
                            headlineContent = { Text(field) },
                            supportingContent = { Text(type, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedField == field,
                                    onClick = { selectedField = field },
                                )
                            },
                            modifier = Modifier.clickable { selectedField = field },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Unsortable fields section
            if (unsortableFields.isNotEmpty()) {
                Text(
                    text = "不可排序字段（数组 / 对象）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    unsortableFields.forEach { (field, type) ->
                        FilterChip(
                            selected = false,
                            onClick = { /* disabled */ },
                            enabled = false,
                            label = {
                                Text(
                                    "$field ($type)",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (sortField != null) {
                    TextButton(onClick = onClear) {
                        Text("清除排序")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        if (selectedField.isNotBlank()) {
                            onApply(selectedField, selectedDirection)
                        }
                        onDismiss()
                    },
                    enabled = selectedField.isNotBlank(),
                ) {
                    Text("应用")
                }
            }
        }
    }
}

/**
 * Thin wrapper that bridges the Kotlin-only type inference to Compose.
 * Actual logic lives in QueryFieldInferencer.
 */
private object QueryFieldInferencerHelper {
    fun isSortableType(typeLabel: String): Boolean {
        return typeLabel != "array" && typeLabel != "object" && typeLabel != "unknown"
    }
}
