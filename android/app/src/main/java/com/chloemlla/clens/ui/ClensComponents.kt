package com.chloemlla.clens.ui

import org.json.JSONArray
import org.json.JSONObject
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.chloemlla.clens.core.export.OutgoingShareSpec
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.ui.browse.DocumentCardStream

@Composable
internal fun ClensAppHeader(state: ClensUiState) {
    val profile = state.connectedProfile ?: state.activeProfile
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MongoDB 管理中枢",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitle = if (state.isConnected) {
                        (profile?.name ?: "会话") + " · 已连接"
                    } else {
                        "连接、浏览、查询与运维集中管理"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 520.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Cable,
                            label = "连接",
                            value = profile?.name ?: "未选择",
                            active = profile != null,
                        )
                        StatusPill(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Storage,
                            label = "数据库",
                            value = state.selectedDatabase.ifBlank { "-" },
                            active = state.selectedDatabase.isNotBlank(),
                        )
                        StatusPill(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.TravelExplore,
                            label = "集合",
                            value = state.selectedCollection.ifBlank { "-" },
                            active = state.selectedCollection.isNotBlank(),
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Cable,
                            label = "连接",
                            value = profile?.name ?: "未选择",
                            active = profile != null,
                        )
                        StatusPill(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Storage,
                            label = "数据库",
                            value = state.selectedDatabase.ifBlank { "-" },
                            active = state.selectedDatabase.isNotBlank(),
                        )
                        StatusPill(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.TravelExplore,
                            label = "集合",
                            value = state.selectedCollection.ifBlank { "-" },
                            active = state.selectedCollection.isNotBlank(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PanelColumn(
    state: ClensUiState,
    onDismissFeedback: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 12.dp, vertical = 12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 960.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusBanner(state = state, onDismiss = onDismissFeedback)
            content()
            // Keep room so bottom fields can scroll above the IME.
            Spacer(Modifier.height(96.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActionRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
internal fun ScrollableActionRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
internal fun StatusBanner(state: ClensUiState, onDismiss: (() -> Unit)? = null) {
    if (!state.loading && state.status.isBlank() && state.error == null) return
    val isError = state.error != null
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                isError -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                else -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
            }
            Text(
                text = state.error ?: state.status.ifBlank { "处理中..." },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (!state.loading && onDismiss != null && (state.error != null || state.status.isNotBlank())) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭提示")
                }
            }
        }
    }
}

@Composable
internal fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
internal fun StatusPill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, subtitle: String? = null, icon: ImageVector? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun InfoCard(title: String, lines: List<String>, icon: ImageVector = Icons.Outlined.Info) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun JsonField(
    label: String,
    value: String,
    enabled: Boolean,
    minLines: Int = 4,
    maxLines: Int = DEFAULT_JSON_FIELD_MAX_LINES,
    onValueChange: (String) -> Unit,
) {
    // Huge Mongo payloads (serverStatus / explain / export) can measure far beyond
    // Compose Constraints limits when only minLines is set. Cap both line count and
    // measured height so OutlinedTextField stays representable on phones.
    val boundedMinLines = minLines.coerceIn(1, maxLines)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DEFAULT_JSON_FIELD_MAX_HEIGHT),
        label = { Text(label) },
        enabled = enabled,
        minLines = boundedMinLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}

private const val DEFAULT_JSON_FIELD_MAX_LINES = 20
private val DEFAULT_JSON_FIELD_MAX_HEIGHT = 360.dp

@Composable
internal fun FlagRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label)
    }
}

@Composable
internal fun ChipSelector(label: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (values.isEmpty()) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(value) })
                }
            }
        }
    }
}

data class CatalogOption(
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

@Composable
internal fun SearchableCatalogSelector(
    label: String,
    options: List<CatalogOption>,
    selectedId: String,
    searchQuery: String,
    vertical: Boolean,
    enabled: Boolean,
    emptyText: String = "暂无数据",
    searchPlaceholder: String = "搜索",
    onSearchQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val query = searchQuery.trim()
    val filtered = if (query.isBlank()) {
        options
    } else {
        options.filter { option ->
            option.id.contains(query, ignoreCase = true) ||
                option.title.contains(query, ignoreCase = true) ||
                option.subtitle.orEmpty().contains(query, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            label = { Text(searchPlaceholder) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }, enabled = enabled) {
                        Icon(Icons.Outlined.Close, contentDescription = "清除搜索")
                    }
                }
            },
        )
        Text(
            text = "显示 " + filtered.size + " / " + options.size,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            options.isEmpty() -> {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            filtered.isEmpty() -> {
                Text("没有匹配项", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vertical -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        filtered.forEach { option ->
                            val selected = option.id == selectedId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) { onSelect(option.id) },
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!option.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = option.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filtered.forEach { option ->
                        FilterChip(
                            selected = option.id == selectedId,
                            onClick = { onSelect(option.id) },
                            enabled = enabled,
                            label = {
                                Text(
                                    text = option.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DocumentSnippet(title: String, json: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(json, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 12, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
internal fun ResultViewModeToggle(
    mode: ResultViewMode,
    enabled: Boolean,
    onChange: (ResultViewMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == ResultViewMode.Json,
            onClick = { onChange(ResultViewMode.Json) },
            enabled = enabled,
            label = { Text("JSON") },
        )
        FilterChip(
            selected = mode == ResultViewMode.Table,
            onClick = { onChange(ResultViewMode.Table) },
            enabled = enabled,
            label = { Text("表格") },
        )
        FilterChip(
            selected = mode == ResultViewMode.Cards,
            onClick = { onChange(ResultViewMode.Cards) },
            enabled = enabled,
            label = { Text("卡片") },
        )
    }
}

internal fun topLevelFields(documents: List<String>, maxDocs: Int = 20): List<String> {
    val keys = linkedSetOf<String>()
    documents.take(maxDocs).forEach { raw ->
        runCatching {
            val obj = JSONObject(raw)
            obj.keys().forEach { key -> keys += key }
        }
    }
    if (keys.isEmpty()) return emptyList()
    // org.json key iteration is not stable across Android/JVM. Prefer a deterministic
    // table header order: _id first, then remaining fields alphabetically.
    val ordered = mutableListOf<String>()
    if ("_id" in keys) {
        ordered += "_id"
    }
    ordered += keys.asSequence().filter { it != "_id" }.sorted().toList()
    return ordered
}

@Composable
internal fun DocumentResultList(
    documents: List<String>,
    mode: ResultViewMode,
    selectedJson: String = "",
    titlePrefix: String = "文档",
    startIndex: Int = 1,
    onSelect: (String) -> Unit = {},
) {
    if (documents.isEmpty()) {
        Text("暂无结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    when (mode) {
        ResultViewMode.Json -> {
            documents.forEachIndexed { index, doc ->
                DocumentSnippet(
                    title = titlePrefix + " #" + (startIndex + index),
                    json = doc,
                    selected = doc == selectedJson,
                    onClick = { onSelect(doc) },
                )
            }
        }
        ResultViewMode.Table -> {
            val fields = topLevelFields(documents)
            if (fields.isEmpty()) {
                Text("无法从表结果提取字段，已回退 JSON。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                documents.forEachIndexed { index, doc ->
                    DocumentSnippet(
                        title = titlePrefix + " #" + (startIndex + index),
                        json = doc,
                        selected = doc == selectedJson,
                        onClick = { onSelect(doc) },
                    )
                }
            } else {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fields.forEach { field ->
                                Text(
                                    text = field,
                                    modifier = Modifier.widthIn(min = 96.dp, max = 160.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        documents.forEach { raw ->
                            val obj = runCatching { JSONObject(raw) }.getOrNull()
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable { onSelect(raw) },
                            ) {
                                fields.forEach { field ->
                                    val value = obj?.opt(field)?.toString() ?: ""
                                    Text(
                                        text = value,
                                        modifier = Modifier.widthIn(min = 96.dp, max = 160.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (raw == selectedJson) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        ResultViewMode.Cards -> {
            DocumentCardStream(
                documents = documents,
                selectedJson = selectedJson,
                titlePrefix = titlePrefix,
                startIndex = startIndex,
                onClick = { _, json -> onSelect(json) },
            )
        }
    }
}

internal fun copyTextToClipboard(context: android.content.Context, label: String, text: String): Boolean {
    return runCatching {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        true
    }.getOrDefault(false)
}

internal fun shareText(context: android.content.Context, subject: String, text: String) {
    val spec = OutgoingShareSpec.text(subject = subject, body = text)
    val intent = Intent(spec.action).apply {
        type = spec.mimeType
        putExtra(Intent.EXTRA_SUBJECT, spec.subject)
        putExtra(Intent.EXTRA_TEXT, spec.text)
    }
    // External share via chooser — not an implicit launch of an unexported app component.
    context.startActivity(Intent.createChooser(intent, spec.subject))
}

internal fun shareFile(
    context: android.content.Context,
    subject: String,
    file: java.io.File,
    mimeType: String,
) {
    val spec = OutgoingShareSpec.file(subject = subject, mimeType = mimeType)
    val authority = context.packageName + ".fileprovider"
    val uri: Uri = FileProvider.getUriForFile(context, authority, file)
    val intent = Intent(spec.action).apply {
        type = spec.mimeType
        putExtra(Intent.EXTRA_SUBJECT, spec.subject)
        putExtra(Intent.EXTRA_STREAM, uri)
        if (spec.requiresReadUriGrant) {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (spec.requiresClipData) {
            clipData = android.content.ClipData.newUri(context.contentResolver, spec.subject, uri)
        }
    }
    context.startActivity(Intent.createChooser(intent, spec.subject))
}

internal fun documentsToJsonArray(documents: List<String>): String {
    val array = JSONArray()
    documents.forEach { raw ->
        runCatching { array.put(JSONObject(raw)) }.getOrElse { array.put(raw) }
    }
    return array.toString(2)
}
