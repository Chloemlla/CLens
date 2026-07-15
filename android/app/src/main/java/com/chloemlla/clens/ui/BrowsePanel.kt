package com.chloemlla.clens.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun BrowsePanel(state: ClensUiState, viewModel: ClensViewModel) {
    val context = LocalContext.current
    val writeEnabled = !state.loading && state.selectedCollection.isNotBlank() && !state.writesBlocked

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
        )

        when {
            state.databaseStatsError != null -> InfoCard(title = "数据库统计不可用", lines = listOf(state.databaseStatsError ?: ""))
            state.databaseStatsJson.isNotBlank() -> JsonField("dbStats", state.databaseStatsJson, enabled = false, minLines = 6) {}
        }

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
                val stats = state.selectedCollectionStats
                InfoCard(
                    title = "集合统计",
                    lines = listOf(
                        "type: " + (stats?.type ?: "-"),
                        "count: " + (stats?.count?.toString() ?: "-"),
                        "size: " + (stats?.size?.toString() ?: "-"),
                        "storageSize: " + (stats?.storageSize?.toString() ?: "-"),
                        "totalIndexSize: " + (stats?.totalIndexSize?.toString() ?: "-"),
                        "avgObjSize: " + (stats?.avgObjSize?.toString() ?: "-"),
                        "nindexes: " + (stats?.nindexes?.toString() ?: "-"),
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

        JsonField("Filter", state.browseFilterJson, !state.loading) {
            viewModel.updateText(ClensViewModel.Field.BrowseFilter, it)
        }
        JsonField("Sort", state.browseSortJson, !state.loading) {
            viewModel.updateText(ClensViewModel.Field.BrowseSort, it)
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

        ResultViewModeToggle(
            mode = state.resultViewMode,
            enabled = !state.loading,
            onChange = viewModel::setResultViewMode,
        )
        DocumentResultList(
            documents = state.documents,
            mode = state.resultViewMode,
            selectedJson = state.selectedDocumentJson,
            titlePrefix = "文档",
            startIndex = state.documentSkip + 1,
            onSelect = viewModel::selectDocument,
        )

        ActionRow {
            OutlinedButton(
                onClick = {
                    val ok = copyTextToClipboard(context, "clens-document", state.selectedDocumentJson)
                    Toast.makeText(context, if (ok && state.selectedDocumentJson.isNotBlank()) "已复制文档 JSON" else "没有可复制的文档", Toast.LENGTH_SHORT).show()
                },
                enabled = state.selectedDocumentJson.isNotBlank(),
            ) { Text("复制选中 JSON") }
            OutlinedButton(
                onClick = {
                    if (state.documents.isEmpty()) {
                        Toast.makeText(context, "当前页没有文档可导出", Toast.LENGTH_SHORT).show()
                    } else {
                        shareText(context, "CLens page export", documentsToJsonArray(state.documents))
                    }
                },
                enabled = state.documents.isNotEmpty(),
            ) { Text("导出当前页") }
        }

        JsonField("文档编辑器 / 插入 JSON", state.editorJson, writeEnabled, minLines = 8) {
            viewModel.updateText(ClensViewModel.Field.EditorJson, it)
        }
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
