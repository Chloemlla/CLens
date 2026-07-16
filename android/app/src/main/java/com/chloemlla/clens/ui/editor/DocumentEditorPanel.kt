package com.chloemlla.clens.ui.editor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.ui.copyTextToClipboard
import java.util.Calendar

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
    onDeleteNode: (String) -> Unit,
    onCloneNode: (String) -> Unit,
    onConvertNodeType: (pathKey: String, type: DocValueType) -> Unit,
    onEnsureObjectId: () -> Unit,
    onRestoreDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    onStartBlankDocument: () -> Unit,
) {
    val context = LocalContext.current
    var menuPath by remember { mutableStateOf<String?>(null) }
    var fullScreenPath by remember { mutableStateOf<String?>(null) }

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

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            TextButton(onClick = onStartBlankDocument, enabled = editable) {
                Text("新建空白")
            }
            TextButton(onClick = onEnsureObjectId, enabled = editable) {
                Text("生成 _id")
            }
        }

        when (editor.mode) {
            DocumentEditorMode.Tree -> {
                DocumentTreeView(
                    root = editor.root,
                    enabled = enabled,
                    editable = editable,
                    menuPath = menuPath,
                    onMenuPathChange = { menuPath = it },
                    onToggleCollapsed = onToggleCollapsed,
                    onEditNode = onEditNode,
                    onCopyKey = { key ->
                        val ok = copyTextToClipboard(context, "clens-field-key", key)
                        Toast.makeText(context, if (ok) "已复制 Key" else "复制失败", Toast.LENGTH_SHORT).show()
                    },
                    onCopyValue = { value ->
                        val ok = copyTextToClipboard(context, "clens-field-value", value)
                        Toast.makeText(context, if (ok) "已复制 Value" else "复制失败", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteNode = onDeleteNode,
                    onCloneNode = onCloneNode,
                    onConvertNodeType = onConvertNodeType,
                    onOpenFullScreen = { fullScreenPath = it },
                )
            }
            DocumentEditorMode.Code -> {
                DocumentCodeEditor(
                    codeText = editor.codeText,
                    diagnostics = editor.codeDiagnostics,
                    enabled = editable,
                    canApply = editor.canApplyCode,
                    onCodeChange = onCodeChange,
                    onApplyCode = onApplyCode,
                )
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
                onOpenFullScreen = {
                    onDismissLeafEdit()
                    fullScreenPath = editingPath
                },
            )
        }
    }

    fullScreenPath?.let { pathKey ->
        val node = DocNodeCodec.findNode(editor.root, pathKey)
        if (node != null) {
            FullScreenStringEditorDialog(
                title = node.displayLabel,
                initialValue = node.scalar.orEmpty(),
                enabled = editable,
                onDismiss = { fullScreenPath = null },
                onConfirm = { text ->
                    onCommitLeafEdit(pathKey, DocValueType.String, text)
                    fullScreenPath = null
                },
            )
        } else {
            fullScreenPath = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentTreeView(
    root: DocNode,
    enabled: Boolean,
    editable: Boolean,
    menuPath: String?,
    onMenuPathChange: (String?) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onEditNode: (String) -> Unit,
    onCopyKey: (String) -> Unit,
    onCopyValue: (String) -> Unit,
    onDeleteNode: (String) -> Unit,
    onCloneNode: (String) -> Unit,
    onConvertNodeType: (pathKey: String, type: DocValueType) -> Unit,
    onOpenFullScreen: (String) -> Unit,
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
                    menuExpanded = menuPath == row.node.pathKey,
                    onMenuExpandedChange = { open ->
                        onMenuPathChange(if (open) row.node.pathKey else null)
                    },
                    onToggleCollapsed = onToggleCollapsed,
                    onEditNode = onEditNode,
                    onCopyKey = onCopyKey,
                    onCopyValue = onCopyValue,
                    onDeleteNode = onDeleteNode,
                    onCloneNode = onCloneNode,
                    onConvertNodeType = onConvertNodeType,
                    onOpenFullScreen = onOpenFullScreen,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentTreeRow(
    row: DocTreeRow,
    enabled: Boolean,
    editable: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onEditNode: (String) -> Unit,
    onCopyKey: (String) -> Unit,
    onCopyValue: (String) -> Unit,
    onDeleteNode: (String) -> Unit,
    onCloneNode: (String) -> Unit,
    onConvertNodeType: (pathKey: String, type: DocValueType) -> Unit,
    onOpenFullScreen: (String) -> Unit,
) {
    val node = row.node
    val canMenu = node.pathKey.isNotBlank()
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = enabled,
                    onClick = {
                        when {
                            row.isExpandable -> onToggleCollapsed(node.pathKey)
                            !node.isContainer && editable -> onEditNode(node.pathKey)
                        }
                    },
                    onLongClick = {
                        if (canMenu) onMenuExpandedChange(true)
                    },
                )
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
                Text(
                    text = DocNodeCodec.displayValue(node),
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
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text("复制 Key") },
                onClick = {
                    onMenuExpandedChange(false)
                    onCopyKey(node.displayLabel)
                },
            )
            DropdownMenuItem(
                text = { Text("复制 Value") },
                onClick = {
                    onMenuExpandedChange(false)
                    onCopyValue(DocNodeCodec.displayValue(node))
                },
            )
            if (editable && !node.isContainer) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    onClick = {
                        onMenuExpandedChange(false)
                        onEditNode(node.pathKey)
                    },
                )
                if (node.type == DocValueType.String && (node.scalar?.length ?: 0) >= 40) {
                    DropdownMenuItem(
                        text = { Text("全屏编辑") },
                        onClick = {
                            onMenuExpandedChange(false)
                            onOpenFullScreen(node.pathKey)
                        },
                    )
                }
            }
            if (editable && canMenu) {
                DropdownMenuItem(
                    text = { Text("克隆字段") },
                    onClick = {
                        onMenuExpandedChange(false)
                        onCloneNode(node.pathKey)
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除字段") },
                    onClick = {
                        onMenuExpandedChange(false)
                        onDeleteNode(node.pathKey)
                    },
                )
            }
            if (editable && !node.isContainer) {
                convertTypeOptions().forEach { option ->
                    DropdownMenuItem(
                        text = { Text("转为 " + option.name) },
                        onClick = {
                            onMenuExpandedChange(false)
                            onConvertNodeType(node.pathKey, option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LeafEditDialog(
    node: DocNode,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (DocValueType, String?) -> Unit,
    onOpenFullScreen: () -> Unit,
) {
    val context = LocalContext.current
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    convertTypeOptions().forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = {
                                type = option
                                when (option) {
                                    DocValueType.ObjectId -> {
                                        if (!DocNodeCodec.isValidObjectId(text)) {
                                            text = DocNodeCodec.generateObjectIdHex()
                                        }
                                    }
                                    DocValueType.Date -> {
                                        if (DocNodeCodec.parseDateMillis(text) == null) {
                                            text = DocNodeCodec.nowIsoDate()
                                        }
                                    }
                                    DocValueType.GeoPoint -> {
                                        if (DocNodeCodec.parseGeoPoint(text) == null) {
                                            text = DocNodeCodec.encodeGeoPoint(0.0, 0.0)
                                        }
                                    }
                                    DocValueType.Binary -> {
                                        if (DocNodeCodec.parseBinary(text) == null) {
                                            text = DocNodeCodec.encodeBinary("", "00")
                                        }
                                    }
                                    DocValueType.Boolean -> {
                                        boolValue = text.equals("true", ignoreCase = true)
                                    }
                                    DocValueType.Null -> text = ""
                                    else -> Unit
                                }
                            },
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
                    DocValueType.ObjectId -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            label = { Text("ObjectId (24 hex)") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            singleLine = true,
                            isError = text.isNotBlank() && !DocNodeCodec.isValidObjectId(text),
                            supportingText = {
                                if (text.isNotBlank() && !DocNodeCodec.isValidObjectId(text)) {
                                    Text("需 24 位十六进制")
                                }
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { text = DocNodeCodec.generateObjectIdHex() },
                                enabled = enabled,
                            ) { Text("生成") }
                            OutlinedButton(
                                onClick = {
                                    val ok = copyTextToClipboard(context, "clens-objectid", text)
                                    Toast.makeText(context, if (ok) "已复制 ObjectId" else "复制失败", Toast.LENGTH_SHORT).show()
                                },
                                enabled = text.isNotBlank(),
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制")
                            }
                        }
                    }
                    DocValueType.Date -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            label = { Text("ISO-8601 Date") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            singleLine = true,
                            isError = text.isNotBlank() && DocNodeCodec.parseDateMillis(text) == null,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    DocNodeCodec.parseDateMillis(text)?.let { calendar.timeInMillis = it }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            calendar.set(Calendar.YEAR, year)
                                            calendar.set(Calendar.MONTH, month)
                                            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    calendar.set(Calendar.MINUTE, minute)
                                                    calendar.set(Calendar.SECOND, 0)
                                                    calendar.set(Calendar.MILLISECOND, 0)
                                                    text = DocNodeCodec.formatIsoDate(calendar.timeInMillis)
                                                },
                                                calendar.get(Calendar.HOUR_OF_DAY),
                                                calendar.get(Calendar.MINUTE),
                                                true,
                                            ).show()
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH),
                                    ).show()
                                },
                                enabled = enabled,
                            ) { Text("选择日期时间") }
                            OutlinedButton(
                                onClick = { text = DocNodeCodec.nowIsoDate() },
                                enabled = enabled,
                            ) { Text("设为当前") }
                        }
                    }
                    DocValueType.GeoPoint -> {
                        val point = DocNodeCodec.parseGeoPoint(text)
                        var latText by remember(node.pathKey, text) {
                            mutableStateOf(point?.first?.toString() ?: "0.0")
                        }
                        var lngText by remember(node.pathKey, text) {
                            mutableStateOf(point?.second?.toString() ?: "0.0")
                        }
                        OutlinedTextField(
                            value = latText,
                            onValueChange = {
                                latText = it
                                val lat = it.toDoubleOrNull()
                                val lng = lngText.toDoubleOrNull()
                                if (lat != null && lng != null) {
                                    text = DocNodeCodec.encodeGeoPoint(lat, lng)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            label = { Text("纬度 lat (-90..90)") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = lngText,
                            onValueChange = {
                                lngText = it
                                val lat = latText.toDoubleOrNull()
                                val lng = it.toDoubleOrNull()
                                if (lat != null && lng != null) {
                                    text = DocNodeCodec.encodeGeoPoint(lat, lng)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            label = { Text("经度 lng (-180..180)") },
                            singleLine = true,
                        )
                        Text(
                            text = "保存为 GeoJSON Point（coordinates=[lng,lat]）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DocValueType.Binary -> {
                        val binary = DocNodeCodec.parseBinary(text)
                        var base64Text by remember(node.pathKey, text) {
                            mutableStateOf(binary?.base64.orEmpty())
                        }
                        var subTypeText by remember(node.pathKey, text) {
                            mutableStateOf(binary?.subType ?: "00")
                        }
                        OutlinedTextField(
                            value = base64Text,
                            onValueChange = {
                                base64Text = it
                                text = DocNodeCodec.encodeBinary(it, subTypeText)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            label = { Text("Base64") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            minLines = 3,
                        )
                        OutlinedTextField(
                            value = subTypeText,
                            onValueChange = {
                                subTypeText = it
                                text = DocNodeCodec.encodeBinary(base64Text, it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            label = { Text("subType (hex, 默认 00)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                        Text(
                            text = "保存为 Extended JSON {\$binary:{base64,subType}}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            trailingIcon = if (type == DocValueType.String) {
                                {
                                    IconButton(onClick = onOpenFullScreen, enabled = enabled) {
                                        Icon(Icons.Outlined.OpenInFull, contentDescription = "全屏编辑")
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled && when (type) {
                    DocValueType.ObjectId -> DocNodeCodec.isValidObjectId(text)
                    DocValueType.Date -> DocNodeCodec.parseDateMillis(text) != null
                    DocValueType.GeoPoint -> DocNodeCodec.parseGeoPoint(text) != null
                    DocValueType.Binary -> DocNodeCodec.parseBinary(text) != null
                    else -> true
                },
                onClick = {
                    val scalar = when (type) {
                        DocValueType.Boolean -> if (boolValue) "true" else "false"
                        DocValueType.Null -> null
                        DocValueType.Date -> {
                            val millis = DocNodeCodec.parseDateMillis(text)
                            if (millis != null) DocNodeCodec.formatIsoDate(millis) else text
                        }
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

@Composable
private fun FullScreenStringEditorDialog(
    title: String,
    initialValue: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全屏编辑 · $title") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp),
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 12,
            )
        },
        confirmButton = {
            TextButton(enabled = enabled, onClick = { onConfirm(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}


@Composable
private fun DocumentCodeEditor(
    codeText: String,
    diagnostics: List<String>,
    enabled: Boolean,
    canApply: Boolean,
    onCodeChange: (String) -> Unit,
    onApplyCode: () -> Unit,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(codeText, TextRange(codeText.length))) }
    LaunchedEffect(codeText) {
        if (fieldValue.text != codeText && !isLikelyLocalTyping(fieldValue.text, codeText)) {
            fieldValue = TextFieldValue(codeText, TextRange(codeText.length.coerceAtLeast(0)))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorSymbolBar(
            enabled = enabled,
            onInsert = { symbol ->
                val next = JsonCodeAssist.insertSymbol(fieldValue, symbol)
                fieldValue = next
                onCodeChange(next.text)
            },
            onFormat = {
                val formatted = JsonCodeAssist.formatJsonIfValid(fieldValue.text)
                if (formatted != null) {
                    fieldValue = TextFieldValue(formatted, TextRange(formatted.length))
                    onCodeChange(formatted)
                }
            },
        )

        OutlinedTextField(
            value = fieldValue,
            onValueChange = { incoming ->
                val assisted = JsonCodeAssist.assistTyping(fieldValue, incoming)
                fieldValue = assisted
                onCodeChange(assisted.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 360.dp),
            enabled = enabled,
            label = { Text("JSON / 插入文档") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 8,
            isError = diagnostics.isNotEmpty(),
        )

        if (diagnostics.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "JSON 诊断",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    diagnostics.take(4).forEach { item ->
                        Text(
                            text = "• $item",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        } else {
            Text(
                text = "JSON 合法",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onApplyCode,
                enabled = enabled && canApply,
            ) {
                Text("应用到树")
            }
        }
    }
}

@Composable
private fun EditorSymbolBar(
    enabled: Boolean,
    onInsert: (String) -> Unit,
    onFormat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("{", "}", "[", "]", "\"", ":", ",", "\n").forEach { symbol ->
            val label = if (symbol == "\n") "↵" else symbol
            FilterChip(
                selected = false,
                onClick = { onInsert(symbol) },
                enabled = enabled,
                label = {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
        FilterChip(
            selected = false,
            onClick = onFormat,
            enabled = enabled,
            label = { Text("格式化") },
        )
    }
}

/**
 * Heuristic: if external codeText is a pretty-format or bulk replace, resync.
 * For small typing diffs we keep local TextFieldValue ownership.
 */
private fun isLikelyLocalTyping(local: String, external: String): Boolean {
    if (local == external) return true
    val delta = kotlin.math.abs(local.length - external.length)
    return delta <= 2
}

private fun convertTypeOptions(): List<DocValueType> {
    return listOf(
        DocValueType.String,
        DocValueType.Int32,
        DocValueType.Int64,
        DocValueType.Double,
        DocValueType.Boolean,
        DocValueType.Null,
        DocValueType.ObjectId,
        DocValueType.Date,
        DocValueType.GeoPoint,
        DocValueType.Binary,
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
        DocValueType.Date,
        DocValueType.GeoPoint,
        DocValueType.Binary,
        -> type
        else -> DocValueType.String
    }
}
