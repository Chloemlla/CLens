package com.chloemlla.clens.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.ui.JsonField

@Composable
internal fun DocumentEditorPanel(
    editor: DocumentEditorState,
    enabled: Boolean,
    editable: Boolean = enabled,
    onModeChange: (DocumentEditorMode) -> Unit,
    onCodeChange: (String) -> Unit,
    onApplyCode: () -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onEditNode: (String) -> Unit,
    onCommitLeafEdit: (pathKey: String, type: DocValueType, scalar: String?) -> Unit,
    onDismissLeafEdit: () -> Unit,
    onRestoreDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    onStartBlankDocument: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DataObject,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "文档编辑器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (editor.dirty) {
                Text(
                    text = "未保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        editor.draftBanner?.let { banner ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(banner, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRestoreDraft, enabled = editable) { Text("恢复草稿") }
                        TextButton(onClick = onDiscardDraft, enabled = editable) { Text("丢弃") }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = editor.mode == DocumentEditorMode.Tree,
                onClick = { onModeChange(DocumentEditorMode.Tree) },
                enabled = enabled,
                label = { Text("Tree") },
            )
            FilterChip(
                selected = editor.mode == DocumentEditorMode.Code,
                onClick = { onModeChange(DocumentEditorMode.Code) },
                enabled = enabled,
                label = { Text("Code") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onStartBlankDocument, enabled = editable) {
                Text("新建空白")
            }
        }

        when (editor.mode) {
            DocumentEditorMode.Tree -> {
                DocumentTreeView(
                    root = editor.root,
                    enabled = enabled,
                    editable = editable,
                    onToggleCollapsed = onToggleCollapsed,
                    onEditNode = onEditNode,
                )
            }
            DocumentEditorMode.Code -> {
                JsonField(
                    label = "JSON / 插入文档",
                    value = editor.codeText,
                    enabled = editable,
                    minLines = 8,
                    onValueChange = onCodeChange,
                )
                if (editor.codeDiagnostics.isNotEmpty()) {
                    Text(
                        text = editor.codeDiagnostics.joinToString("\n"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    onClick = onApplyCode,
                    enabled = editable && editor.canApplyCode,
                ) {
                    Text("应用到树")
                }
            }
        }

        editor.parseError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    val editingPath = editor.editingPath
    if (editingPath != null) {
        val node = DocNodeCodec.findNode(editor.root, editingPath)
        if (node != null && !node.isContainer) {
            LeafEditDialog(
                node = node,
                enabled = editable,
                onDismiss = onDismissLeafEdit,
                onConfirm = { type, scalar -> onCommitLeafEdit(editingPath, type, scalar) },
            )
        }
    }
}

@Composable
private fun DocumentTreeView(
    root: DocNode,
    enabled: Boolean,
    editable: Boolean,
    onToggleCollapsed: (String) -> Unit,
    onEditNode: (String) -> Unit,
) {
    val rows = remember(root) { DocNodeCodec.flattenVisible(root) }
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 360.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(rows, key = { it.node.pathKey.ifBlank { "root" } }) { row ->
                DocumentTreeRow(
                    row = row,
                    enabled = enabled,
                    editable = editable,
                    onToggleCollapsed = onToggleCollapsed,
                    onEditNode = onEditNode,
                )
            }
        }
    }
}

@Composable
private fun DocumentTreeRow(
    row: DocTreeRow,
    enabled: Boolean,
    editable: Boolean,
    onToggleCollapsed: (String) -> Unit,
    onEditNode: (String) -> Unit,
) {
    val node = row.node
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                when {
                    row.isExpandable -> onToggleCollapsed(node.pathKey)
                    !node.isContainer && editable -> onEditNode(node.pathKey)
                }
            }
            .padding(start = (12 + row.depth * 14).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.isExpandable) {
            Icon(
                imageVector = if (node.collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = if (node.collapsed) "展开" else "折叠",
                modifier = Modifier.size(18.dp),
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val preview = when {
                node.isContainer -> {
                    val count = node.children?.size ?: 0
                    "${node.type.name.lowercase()} · $count 项"
                }
                node.type == DocValueType.Null -> "null"
                else -> node.scalar.orEmpty().ifBlank { "\"\"" }
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (node.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
            )
        }
        Text(
            text = node.type.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LeafEditDialog(
    node: DocNode,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (DocValueType, String?) -> Unit,
) {
    var type by remember(node.pathKey) { mutableStateOf(normalizeEditableType(node.type)) }
    var text by remember(node.pathKey) {
        mutableStateOf(if (node.type == DocValueType.Null) "" else node.scalar.orEmpty())
    }
    var boolValue by remember(node.pathKey) {
        mutableStateOf(node.scalar.equals("true", ignoreCase = true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 " + node.displayLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        DocValueType.String,
                        DocValueType.Int32,
                        DocValueType.Int64,
                        DocValueType.Double,
                        DocValueType.Boolean,
                        DocValueType.Null,
                        DocValueType.ObjectId,
                    ).forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            enabled = enabled,
                            label = { Text(option.name) },
                        )
                    }
                }
                when (type) {
                    DocValueType.Boolean -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("值")
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = boolValue,
                                onCheckedChange = { boolValue = it },
                                enabled = enabled,
                            )
                        }
                    }
                    DocValueType.Null -> {
                        Text("将写入 null")
                    }
                    else -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            label = { Text("值") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            singleLine = type != DocValueType.String,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled,
                onClick = {
                    val scalar = when (type) {
                        DocValueType.Boolean -> if (boolValue) "true" else "false"
                        DocValueType.Null -> null
                        else -> text
                    }
                    onConfirm(type, scalar)
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun normalizeEditableType(type: DocValueType): DocValueType {
    return when (type) {
        DocValueType.String,
        DocValueType.Int32,
        DocValueType.Int64,
        DocValueType.Double,
        DocValueType.Boolean,
        DocValueType.Null,
        DocValueType.ObjectId,
        -> type
        else -> DocValueType.String
    }
}
