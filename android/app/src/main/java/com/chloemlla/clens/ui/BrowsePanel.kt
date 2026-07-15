package com.chloemlla.clens.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun BrowsePanel(state: ClensUiState, viewModel: ClensViewModel) {
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.refreshDatabases() }, enabled = !state.loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("刷新库")
            }
            OutlinedButton(
                onClick = { viewModel.refreshCollections() },
                enabled = !state.loading && state.selectedDatabase.isNotBlank(),
            ) { Text("刷新集合") }
        }

        OutlinedTextField(
            value = state.newDatabaseName,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.NewDatabase, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("新建数据库名") },
            enabled = !state.loading,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::createDatabase, enabled = !state.loading) { Text("创建数据库") }
            OutlinedButton(
                onClick = viewModel::requestDropDatabase,
                enabled = !state.loading && state.selectedDatabase.isNotBlank(),
            ) { Text("删除当前库") }
        }

        ChipSelector(
            label = "数据库",
            values = state.databases.map { it.name },
            selected = state.selectedDatabase,
            onSelect = viewModel::updateSelectedDatabase,
        )

        OutlinedTextField(
            value = state.newCollectionName,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.NewCollection, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("新建集合名") },
            enabled = !state.loading && state.selectedDatabase.isNotBlank(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::createCollection,
                enabled = !state.loading && state.selectedDatabase.isNotBlank(),
            ) { Text("创建集合") }
            OutlinedButton(
                onClick = viewModel::requestDropCollection,
                enabled = !state.loading && state.selectedCollection.isNotBlank(),
            ) { Text("删除集合") }
        }
        OutlinedTextField(
            value = state.renameCollectionName,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.RenameCollection, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("重命名为") },
            enabled = !state.loading && state.selectedCollection.isNotBlank(),
        )
        OutlinedButton(
            onClick = viewModel::renameCollection,
            enabled = !state.loading && state.selectedCollection.isNotBlank(),
        ) { Text("重命名集合") }

        ChipSelector(
            label = "集合",
            values = state.collections.map { it.name },
            selected = state.selectedCollection,
            onSelect = viewModel::updateSelectedCollection,
        )

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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            text = "skip=${state.documentSkip} · limit=${state.documentLimit}" +
                (state.documentCountHint?.let { " · 约 $it 条" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.documents.forEachIndexed { index, doc ->
            DocumentSnippet(
                title = "文档 #${state.documentSkip + index + 1}",
                json = doc,
                selected = doc == state.selectedDocumentJson,
                onClick = { viewModel.selectDocument(doc) },
            )
        }

        JsonField("文档编辑器 / 插入 JSON", state.editorJson, !state.loading, minLines = 8) {
            viewModel.updateText(ClensViewModel.Field.EditorJson, it)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Button(onClick = viewModel::insertDocuments, enabled = !state.loading) { Text("插入") }
            OutlinedButton(onClick = viewModel::replaceSelectedDocument, enabled = !state.loading) { Text("替换(_id)") }
            OutlinedButton(onClick = { viewModel.updateDocuments(false) }, enabled = !state.loading) { Text("updateOne") }
            OutlinedButton(onClick = { viewModel.updateDocuments(true) }, enabled = !state.loading) { Text("updateMany") }
            OutlinedButton(onClick = { viewModel.deleteDocuments(false) }, enabled = !state.loading) { Text("deleteOne") }
            OutlinedButton(onClick = { viewModel.deleteDocuments(true) }, enabled = !state.loading) { Text("deleteMany") }
        }
    }
}
