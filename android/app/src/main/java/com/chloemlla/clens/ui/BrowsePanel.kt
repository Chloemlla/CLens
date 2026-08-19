package com.chloemlla.clens.ui

import android.widget.Toast
import com.chloemlla.clens.core.export.DocumentExportFormat
import java.io.File
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chloemlla.clens.ui.editor.DocumentEditorPanel
import com.chloemlla.clens.ui.browse.BrowseBreadcrumb
import com.chloemlla.clens.ui.browse.CollectionStatsQuickPanel
import com.chloemlla.clens.ui.browse.DocumentCardStream
import com.chloemlla.clens.ui.browse.extractDocumentIdLabel
import com.chloemlla.clens.ui.browse.SortChip
import com.chloemlla.clens.ui.browse.SortPickerSheet

@Composable
internal fun BrowsePanel(state: ClensUiState, viewModel: ClensViewModel) {
    val context = LocalContext.current
    val writeEnabled = !state.loading && state.selectedCollection.isNotBlank() && !state.writesBlocked
    var showBulkUpdateSheet by remember { mutableStateOf(false) }
    var showSortPicker by remember { mutableStateOf(false) }
    val fieldTypes = remember(state.documents) {
        com.chloemlla.clens.core.mongo.QueryFieldInferencer.inferFieldTypes(state.documents)
    }
    val recentSorts = remember(state.connectedProfileId, state.selectedDatabase, state.selectedCollection) {
        viewModel.suggestedRecentSorts()
    }

    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "数据浏览",
            subtitle = "管理数据库、集合，并分页查看 / 编辑文档。",
            icon = Icons.Outlined.TravelExplore,
        )
        if (!state.isConnected) {
            InfoCard(title = "尚未连接", lines = listOf("先到「连接」页建立会话。"))
            return@PanelColumn
        }

        ExpandableSection(
            title = "浏览标签",
            subtitle = "多上下文切换；每个标签保留 filter/page/编辑器状态。",
            icon = Icons.Outlined.Tab,
            key = "browse_tabs",
        ) {
            ActionRow {
                OutlinedButton(
                    onClick = viewModel::openBrowseTabFromCurrent,
                    enabled = !state.loading && state.selectedDatabase.isNotBlank(),
                ) { Text("从当前打开标签") }
            }
            if (state.browseTabs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.browseTabs.forEach { tab ->
                        FilterChip(
                            selected = tab.id == state.activeBrowseTabId,
                            onClick = { viewModel.switchBrowseTab(tab.id) },
                            enabled = !state.loading,
                            label = { Text(tab.title) },
                        )
                        IconButton(
                            onClick = { viewModel.closeBrowseTab(tab.id) },
                            enabled = !state.loading,
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭标签")
                        }
                    }
                }
            }
        }

        ExpandableSection(
            title = "数据库管理",
            subtitle = "创建、选择、查看统计。",
            icon = Icons.Outlined.Storage,
            key = "db_mgmt",
        ) {
            BrowseBreadcrumb(
                database = state.selectedDatabase,
                collection = state.selectedCollection,
                documentLabel = extractDocumentIdLabel(state.selectedDocumentJson),
                enabled = !state.loading,
                onClearToRoot = { viewModel.updateSelectedDatabase("") },
                onClearToDatabase = {
                    if (state.selectedCollection.isNotBlank() || state.selectedDocumentJson.isNotBlank()) {
                        viewModel.updateSelectedCollection("")
                    }
                },
                onClearToCollection = {
                    if (state.selectedDocumentJson.isNotBlank()) {
                        viewModel.clearSelectedDocument()
                    }
                },
            )

            ActionRow {
                OutlinedButton(onClick = { viewModel.refreshDatabases() }, enabled = !state.loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新", Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("刷新库")
                }
                OutlinedButton(
                    onClick = { viewModel.refreshCollections() },
                    enabled = !state.loading && state.selectedDatabase.isNotBlank(),
                ) { Text("刷新集合") }
                OutlinedButton(
                    onClick = viewModel::refreshDatabaseStats,
                    enabled = !state.loading && state.selectedDatabase.isNotBlank(),
                ) { Text("库统计") }
                OutlinedButton(
                    onClick = viewModel::refreshCollectionStats,
                    enabled = !state.loading && state.selectedCollection.isNotBlank(),
                ) { Text("集合统计") }
            }

            OutlinedTextField(
                value = state.newDatabaseName,
                onValueChange = { viewModel.updateText(ClensViewModel.Field.NewDatabase, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("新建数据库名") },
                enabled = !state.loading,
            )
            ActionRow {
                Button(onClick = viewModel::createDatabase, enabled = !state.loading) { Text("创建数据库") }
                OutlinedButton(
                    onClick = viewModel::requestDropDatabase,
                    enabled = !state.loading && state.selectedDatabase.isNotBlank(),
                ) { Text("删除当前库") }
            }

            SearchableCatalogSelector(
                label = "数据库",
                options = state.databases.map { db ->
                    CatalogOption(
                        id = db.name,
                        title = db.name,
                        subtitle = buildList {
                            db.collections?.let { add(it.toString() + " collections") }
                            db.sizeOnDisk?.let { add("size " + it) }
                            if (db.empty) add("empty")
                        }.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    )
                },
                selectedId = state.selectedDatabase,
                searchQuery = state.databaseSearchQuery,
                vertical = state.verticalCatalogLists,
                enabled = !state.loading,
                emptyText = "还没有数据库",
                searchPlaceholder = "搜索数据库",
                onSearchQueryChange = { viewModel.updateText(ClensViewModel.Field.DatabaseSearch, it) },
                onSelect = viewModel::updateSelectedDatabase,
                loading = state.loading,
            )

            when {
                state.databaseStatsError != null -> InfoCard(title = "数据库统计不可用", lines = listOf(state.databaseStatsError ?: ""))
                state.databaseStatsJson.isNotBlank() -> JsonField("dbStats", state.databaseStatsJson, enabled = false, minLines = 6) {}
            }
        }

        ExpandableSection(
            title = "集合管理",
            subtitle = "创建、重命名、删除、统计、Validator。",
            icon = Icons.Outlined.TravelExplore,
            key = "coll_mgmt",
        ) {
            OutlinedTextField(
                value = state.newCollectionName,
                onValueChange = { viewModel.updateText(ClensViewModel.Field.NewCollection, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("新建集合名") },
                enabled = !state.loading && state.selectedDatabase.isNotBlank(),
            )
            ActionRow {
                Button(
                    onClick = viewModel::createCollection,
                    enabled = !state.loading && state.selectedDatabase.isNotBlank(),
                ) { Text("创建集合") }
                OutlinedButton(
                    onClick = viewModel::requestDropCollection,
                    enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.isSelectedView,
                ) { Text("删除集合") }
            }
            OutlinedTextField(
                value = state.renameCollectionName,
                onValueChange = { viewModel.updateText(ClensViewModel.Field.RenameCollection, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("重命名为") },
                enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.isSelectedView,
            )
            OutlinedButton(
                onClick = viewModel::renameCollection,
                enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.isSelectedView,
            ) { Text("重命名集合") }

            SearchableCatalogSelector(
                label = "集合",
                options = state.collections.map { coll ->
                    val isView = coll.type.equals("view", ignoreCase = true)
                    CatalogOption(
                        id = coll.name,
                        title = if (isView) coll.name + " [view]" else coll.name,
                        subtitle = buildList {
                            add(coll.type)
                            coll.count?.let { add("count " + it) }
                            coll.size?.let { add("size " + it) }
                        }.joinToString(" · "),
                    )
                },
                selectedId = state.selectedCollection,
                searchQuery = state.collectionSearchQuery,
                vertical = state.verticalCatalogLists,
                enabled = !state.loading && state.selectedDatabase.isNotBlank(),
                emptyText = if (state.selectedDatabase.isBlank()) "先选择数据库" else "当前库没有集合",
                searchPlaceholder = "搜索集合",
                onSearchQueryChange = { viewModel.updateText(ClensViewModel.Field.CollectionSearch, it) },
                onSelect = viewModel::updateSelectedCollection,
                loading = state.loading,
            )

            if (state.connectedReadOnly) {
                InfoCard(title = "只读连接", lines = listOf("当前连接启用了只读模式，所有写入/破坏性操作都会被阻止。"))
            }
            if (state.isSelectedView) {
                InfoCard(title = "当前对象是视图", lines = listOf("视图支持查询，不支持写入、索引维护、compact。"))
            }

            when {
                state.collectionStatsError != null -> InfoCard(title = "集合统计不可用", lines = listOf(state.collectionStatsError ?: ""))
                state.selectedCollectionStats != null -> {
                    val stats = checkNotNull(state.selectedCollectionStats)
                    CollectionStatsQuickPanel(stats = stats)
                    InfoCard(
                        title = "集合统计",
                        lines = listOf(
                            "type: " + stats.type,
                            "count: " + (stats.count?.toString() ?: "-"),
                            "size: " + (stats.size?.toString() ?: "-"),
                            "storageSize: " + (stats.storageSize?.toString() ?: "-"),
                            "totalIndexSize: " + (stats.totalIndexSize?.toString() ?: "-"),
                            "avgObjSize: " + (stats.avgObjSize?.toString() ?: "-"),
                            "nindexes: " + (stats.nindexes?.toString() ?: "-"),
                        ),
                    )
                }
            }

            ActionRow {
                OutlinedButton(
                    onClick = viewModel::validateSelectedCollection,
                    enabled = !state.loading && state.selectedCollection.isNotBlank(),
                ) { Text("validate") }
                OutlinedButton(
                    onClick = viewModel::requestCompactCollection,
                    enabled = writeEnabled,
                ) { Text("compact") }
            }
            if (state.maintenanceResultJson.isNotBlank()) {
                JsonField("维护命令结果", state.maintenanceResultJson, enabled = false, minLines = 6) {}
            }

            SectionTitle(text = "集合 Validator", subtitle = "collMod best-effort。")
            ActionRow {
                OutlinedButton(
                    onClick = viewModel::loadCollectionValidator,
                    enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.isSelectedView,
                ) { Text("加载 validator") }
                Button(
                    onClick = viewModel::applyCollectionValidator,
                    enabled = writeEnabled,
                ) { Text("应用 validator") }
            }
            when {
                state.collectionValidatorError != null -> InfoCard(title = "Validator 不可用", lines = listOf(state.collectionValidatorError ?: ""))
                else -> {
                    JsonField("validator JSON", state.validatorJsonInput, writeEnabled, minLines = 4) {
                        viewModel.updateText(ClensViewModel.Field.ValidatorJsonInput, it)
                    }
                    OutlinedTextField(
                        value = state.validationLevelInput,
                        onValueChange = { viewModel.updateText(ClensViewModel.Field.ValidationLevelInput, it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("validationLevel") },
                        enabled = writeEnabled,
                    )
                    OutlinedTextField(
                        value = state.validationActionInput,
                        onValueChange = { viewModel.updateText(ClensViewModel.Field.ValidationActionInput, it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("validationAction") },
                        enabled = writeEnabled,
                    )
                }
            }
        }

        ExpandableSection(
            title = "查询条件",
            subtitle = "Filter / Sort / Projection / Limit。",
            icon = Icons.Outlined.ManageSearch,
            key = "query_conditions",
        ) {
            JsonField("Filter", state.browseFilterJson, !state.loading) {
                viewModel.updateText(ClensViewModel.Field.BrowseFilter, it)
            }
            ActionRow {
                SortChip(
                    sortField = state.browseSortField,
                    sortDirection = state.browseSortDirection,
                    onClick = { showSortPicker = true },
                )
            }
            JsonField("Sort", state.browseSortJson, !state.loading) {
                viewModel.updateText(ClensViewModel.Field.BrowseSort, it)
            }
            if (showSortPicker) {
                SortPickerSheet(
                    sortField = state.browseSortField,
                    sortDirection = state.browseSortDirection,
                    fieldTypes = fieldTypes,
                    recentSorts = recentSorts,
                    onApply = { field, direction -> viewModel.applyBrowseSort(field, direction) },
                    onClear = { viewModel.clearBrowseSort() },
                    onDismiss = { showSortPicker = false },
                )
            }
            JsonField("Projection", state.browseProjectionJson, !state.loading) {
                viewModel.updateText(ClensViewModel.Field.BrowseProjection, it)
            }
            OutlinedTextField(
                value = state.documentLimit.toString(),
                onValueChange = viewModel::updateDocumentLimit,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Limit (1-500)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.loading,
            )
            ActionRow {
                Button(
                    onClick = { viewModel.loadDocuments(resetSkip = true) },
                    enabled = !state.loading && state.selectedCollection.isNotBlank(),
                ) { Text("加载文档") }
                OutlinedButton(
                    onClick = viewModel::previousDocumentPage,
                    enabled = !state.loading && state.documentSkip > 0,
                ) { Text("上一页") }
                OutlinedButton(
                    onClick = viewModel::nextDocumentPage,
                    enabled = !state.loading && state.documents.isNotEmpty(),
                ) { Text("下一页") }
            }
            Text(
                text = "skip=" + state.documentSkip + " · limit=" + state.documentLimit +
                    (state.documentCountHint?.let { " · 约 " + it + " 条" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ExpandableSection(
            title = "文档列表",
            subtitle = "三种视图模式；长按进入多选；支持分页导航。",
            icon = Icons.Outlined.Description,
            key = "doc_list",
        ) {
            ResultViewModeToggle(
                mode = state.resultViewMode,
                enabled = !state.loading,
                onChange = viewModel::setResultViewMode,
            )
            if (state.resultViewMode == ResultViewMode.Cards) {
                DocumentCardStream(
                    documents = state.documents,
                    selectedJson = state.selectedDocumentJson,
                    titlePrefix = "文档",
                    startIndex = state.documentSkip + 1,
                    onClick = { _, json ->
                        if (state.isSelectMode) {
                            viewModel.toggleDocumentSelection(json)
                        } else {
                            viewModel.selectDocument(json)
                        }
                    },
                    onLongClick = { _, json ->
                        viewModel.enterSelectMode(json)
                    },
                )
            } else {
                DocumentResultList(
                    documents = state.documents,
                    mode = state.resultViewMode,
                    selectedJson = state.selectedDocumentJson,
                    titlePrefix = "文档",
                    startIndex = state.documentSkip + 1,
                    onSelect = viewModel::selectDocument,
                )
            }

            // Multi-select FAB action bar
            if (state.isSelectMode) {
                SelectModeActionBar(
                    selectedCount = state.selectedDocIds.size,
                    pageCount = state.documents.size,
                    onSelectAll = viewModel::selectAllOnPage,
                    onDelete = viewModel::requestDeleteSelected,
                    onExportJson = { viewModel.exportSelected(DocumentExportFormat.JSON) },
                    onExportCsv = { viewModel.exportSelected(DocumentExportFormat.CSV) },
                    onBulkUpdate = { showBulkUpdateSheet = true },
                    onClose = viewModel::exitSelectMode,
                )
            }

            // Bulk update bottom sheet
            if (showBulkUpdateSheet) {
                BulkUpdateSheet(
                    initialField = state.bulkUpdateField,
                    initialValue = state.bulkUpdateValue,
                    selectedCount = state.selectedDocIds.size,
                    onApply = { field, value ->
                        showBulkUpdateSheet = false
                        viewModel.applyBulkUpdate(field, value)
                    },
                    onDismiss = { showBulkUpdateSheet = false },
                )
            }
        }

        ExpandableSection(
            title = "导出与复制",
            subtitle = "复制单文档或导出当前页为 JSON/CSV/JSONL。",
            icon = Icons.Outlined.Download,
            key = "export_copy",
        ) {
            ActionRow {
                OutlinedButton(
                    onClick = {
                        val ok = copyTextToClipboard(context, "clens-document", state.selectedDocumentJson)
                        Toast.makeText(context, if (ok && state.selectedDocumentJson.isNotBlank()) "已复制文档 JSON" else "没有可复制的文档", Toast.LENGTH_SHORT).show()
                    },
                    enabled = state.selectedDocumentJson.isNotBlank(),
                ) { Text("复制选中 JSON") }
                OutlinedButton(
                    onClick = { viewModel.exportCurrentPage(DocumentExportFormat.JSON) },
                    enabled = !state.loading && state.documents.isNotEmpty(),
                ) { Text("导出 JSON") }
                OutlinedButton(
                    onClick = { viewModel.exportCurrentPage(DocumentExportFormat.CSV) },
                    enabled = !state.loading && state.documents.isNotEmpty(),
                ) { Text("导出 CSV") }
                OutlinedButton(
                    onClick = { viewModel.exportCurrentPage(DocumentExportFormat.EXTENDED_JSON_LINES) },
                    enabled = !state.loading && state.documents.isNotEmpty(),
                ) { Text("导出 JSONL") }
            }
            if (state.exportJson.isNotBlank()) {
                ActionRow {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val dir = File(context.cacheDir, "export").apply { mkdirs() }
                                val ext = state.exportFormat.extension
                                val file = File(dir, "clens-page." + ext)
                                file.writeText(state.exportJson, Charsets.UTF_8)
                                shareFile(context, "CLens page export", file, state.exportFormat.mimeType)
                            }.onFailure {
                                shareText(context, "CLens page export", state.exportJson)
                            }
                        },
                    ) { Text("分享导出文件") }
                }
            }
        }

        ExpandableSection(
            title = "离线快照",
            subtitle = "保存当前 filter 前 N 条；无网可只读打开。",
            icon = Icons.Outlined.SaveAlt,
            key = "offline_snapshots",
        ) {
            OutlinedTextField(
                value = state.offlineSnapshotNameInput,
                onValueChange = { viewModel.updateText(ClensViewModel.Field.OfflineSnapshotName, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("快照名称（可空自动生成）") },
                enabled = !state.loading,
            )
            ActionRow {
                Button(
                    onClick = viewModel::saveOfflineSnapshot,
                    enabled = !state.loading && state.selectedCollection.isNotBlank(),
                ) { Text("保存离线快照") }
                OutlinedButton(onClick = viewModel::refreshOfflineSnapshots, enabled = !state.loading) { Text("刷新快照") }
                if (state.activeSnapshotId != null) {
                    OutlinedButton(onClick = viewModel::clearActiveOfflineSnapshot) { Text("退出快照") }
                }
            }
            if (state.offlineSnapshots.isEmpty()) {
                InfoCard(title = "暂无快照", lines = listOf("加载文档后可保存命名快照，供地铁/弱网回看。"))
            } else {
                state.offlineSnapshots.take(20).forEach { snap ->
                    InfoCard(
                        title = snap.name,
                        lines = listOf(
                            snap.database + "." + snap.collection,
                            "filter: " + snap.filterJson,
                            "docs=" + snap.documentCount + " limit=" + snap.limit,
                        ),
                    )
                    ActionRow {
                        OutlinedButton(onClick = { viewModel.openOfflineSnapshot(snap.snapshotId) }, enabled = !state.loading) { Text("打开") }
                        OutlinedButton(onClick = { viewModel.deleteOfflineSnapshot(snap.snapshotId) }, enabled = !state.loading) { Text("删除") }
                    }
                }
            }
        }

        ExpandableSection(
            title = "文档草稿",
            subtitle = "Room 本地草稿；按当前连接/库/集合过滤。",
            icon = Icons.Outlined.EditNote,
            key = "doc_drafts",
        ) {
            ActionRow {
                OutlinedButton(
                    onClick = viewModel::refreshDocumentDrafts,
                    enabled = !state.loading && state.connectedProfileId != null,
                ) { Text("刷新草稿") }
            }
            if (state.documentDrafts.isEmpty()) {
                InfoCard(title = "暂无草稿", lines = listOf("编辑文档后会自动保存；可在此恢复或删除。"))
            } else {
                state.documentDrafts.take(15).forEach { draft ->
                    val docLabel = draft.documentId ?: "new"
                    InfoCard(
                        title = draft.database + "." + draft.collection + " · " + docLabel,
                        lines = listOf(
                            "mode=" + draft.mode + " · source=" + draft.source,
                            "updated=" + draft.updatedAtMillis,
                            draft.codeText.take(120).replace("\n", " "),
                        ),
                    )
                    ActionRow {
                        OutlinedButton(
                            onClick = { viewModel.restoreDocumentDraftById(draft.draftId) },
                            enabled = !state.loading,
                        ) { Text("恢复") }
                        OutlinedButton(
                            onClick = { viewModel.deleteDocumentDraftById(draft.draftId) },
                            enabled = !state.loading,
                        ) { Text("删除") }
                    }
                }
            }
        }

        ExpandableSection(
            title = "文档编辑器",
            subtitle = "树形 / 代码 双模式；支持草稿自动保存。",
            icon = Icons.Outlined.Code,
            key = "doc_editor",
            initiallyExpanded = state.selectedDocumentJson.isNotBlank() || state.editorJson != "{\n  \n}",
        ) {
            DocumentEditorPanel(
                editor = state.documentEditor,
                enabled = !state.loading && state.selectedCollection.isNotBlank(),
                editable = writeEnabled,
                onModeChange = viewModel::setDocumentEditorMode,
                onCodeChange = { viewModel.updateText(ClensViewModel.Field.EditorJson, it) },
                onApplyCode = viewModel::applyCodeToTree,
                onToggleCollapsed = viewModel::toggleDocumentNode,
                onEditNode = viewModel::beginEditDocumentNode,
                onCommitLeafEdit = viewModel::commitDocumentLeafEdit,
                onDismissLeafEdit = viewModel::dismissEditDocumentNode,
                onDeleteNode = viewModel::deleteDocumentNode,
                onCloneNode = viewModel::cloneDocumentNode,
                onConvertNodeType = viewModel::convertDocumentNodeType,
                onEnsureObjectId = viewModel::ensureDocumentObjectId,
                onRestoreDraft = viewModel::restoreDocumentDraft,
                onDiscardDraft = viewModel::discardDocumentDraft,
                onStartBlankDocument = viewModel::startBlankDocument,
                onSave = {
                    if (state.selectedDocumentJson.isNotBlank()) {
                        viewModel.replaceSelectedDocument()
                    } else {
                        viewModel.insertDocuments()
                    }
                },
            )
            ScrollableActionRow {
                Button(onClick = viewModel::insertDocuments, enabled = writeEnabled) { Text("插入") }
                OutlinedButton(onClick = viewModel::replaceSelectedDocument, enabled = writeEnabled) { Text("替换(_id)") }
                OutlinedButton(onClick = { viewModel.updateDocuments(false) }, enabled = writeEnabled) { Text("updateOne") }
                OutlinedButton(onClick = { viewModel.updateDocuments(true) }, enabled = writeEnabled) { Text("updateMany") }
                OutlinedButton(onClick = { viewModel.deleteDocuments(false) }, enabled = writeEnabled) { Text("deleteOne") }
                OutlinedButton(onClick = { viewModel.deleteDocuments(true) }, enabled = writeEnabled) { Text("deleteMany") }
            }
        }
    }
}

/**
 * Floating action bar shown when multi-select mode is active.
 * Displays selected count, select-all chip, and batch action buttons.
 */
@Composable
private fun SelectModeActionBar(
    selectedCount: Int,
    pageCount: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onBulkUpdate: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "退出选择")
                    }
                    Text(
                        text = "已选 $selectedCount 条",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                FilterChip(
                    selected = selectedCount == pageCount && pageCount > 0,
                    onClick = onSelectAll,
                    label = { Text("全选本页") },
                    enabled = pageCount > 0,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
                OutlinedButton(
                    onClick = onExportJson,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出 JSON")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onExportCsv,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出 CSV")
                }
                OutlinedButton(
                    onClick = onBulkUpdate,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("批量更新")
                }
            }
        }
    }
}

/**
 * Bottom-sheet dialog for bulk update: field name + new value, applies $set to all selected docs.
 */
@Composable
private fun BulkUpdateSheet(
    initialField: String,
    initialValue: String,
    selectedCount: Int,
    onApply: (fieldName: String, fieldValue: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldName by remember { mutableStateOf(initialField) }
    var fieldValue by remember { mutableStateOf(initialValue) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "批量更新 $selectedCount 条文档",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "将字段值设置为指定内容（使用 \$set 操作符）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("字段名") },
                    placeholder = { Text("例如：status") },
                )
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    label = { Text("新值（JSON 格式）") },
                    placeholder = { Text("例如：\"active\"" ) },
                    minLines = 2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(fieldName, fieldValue) },
                        enabled = fieldName.isNotBlank(),
                    ) {
                        Text("应用更新")
                    }
                }
            }
        }
    }
}
