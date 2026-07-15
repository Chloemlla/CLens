package com.chloemlla.clens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun AdminPanel(state: ClensUiState, viewModel: ClensViewModel) {
    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "管理与运维",
            subtitle = "索引、服务器状态、用户列表与当前操作快照。",
            icon = Icons.Outlined.AdminPanelSettings,
        )
        if (!state.isConnected) {
            InfoCard(title = "尚未连接", lines = listOf("连接后可查看 serverStatus / indexes / usersInfo。"))
            return@PanelColumn
        }

        SectionTitle(
            text = "索引",
            subtitle = "当前集合：" + state.selectedDatabase + "." + state.selectedCollection,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::refreshIndexes,
                enabled = !state.loading && state.selectedCollection.isNotBlank(),
            ) { Text("刷新索引") }
        }
        JsonField("Keys JSON", state.indexKeysJson, !state.loading) {
            viewModel.updateText(ClensViewModel.Field.IndexKeys, it)
        }
        OutlinedTextField(
            value = state.indexName,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.IndexName, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("索引名（可选）") },
            enabled = !state.loading,
        )
        OutlinedTextField(
            value = state.indexExpireAfterSeconds,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.IndexExpire, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("TTL expireAfterSeconds（可选）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !state.loading,
        )
        FlagRow("unique", state.indexUnique, !state.loading) { viewModel.setIndexFlags(unique = it) }
        FlagRow("sparse", state.indexSparse, !state.loading) { viewModel.setIndexFlags(sparse = it) }
        Button(
            onClick = viewModel::createIndex,
            enabled = !state.loading && state.selectedCollection.isNotBlank(),
        ) { Text("创建索引") }

        state.indexes.forEach { index ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(index.name, fontWeight = FontWeight.SemiBold)
                    Text(index.keysJson, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    val options = buildString {
                        if (index.unique) append("unique ")
                        if (index.sparse) append("sparse ")
                        index.expireAfterSeconds?.let { append("ttl=").append(it).append(" ") }
                    }.ifBlank { "options: default" }
                    Text(options, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { viewModel.requestDropIndex(index.name) },
                        enabled = !state.loading && index.name != "_id_",
                    ) { Text("删除索引") }
                }
            }
        }

        SectionTitle(
            text = "服务器",
            subtitle = "serverStatus / usersInfo / currentOp 权限不足时会降级提示。",
        )
        OutlinedButton(onClick = viewModel::refreshServerOverview, enabled = !state.loading) {
            Icon(Icons.Outlined.Refresh, contentDescription = "刷新", Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("刷新服务器信息")
        }
        state.serverOverview?.let { overview ->
            InfoCard(
                title = "概览",
                lines = listOf(
                    "version: " + (overview.version ?: "-"),
                    "host: " + (overview.host ?: "-"),
                    "process: " + (overview.process ?: "-"),
                    "uptime: " + (overview.uptimeSeconds?.toString() ?: "-") + " s",
                    "connections: " + (overview.connectionsCurrent?.toString() ?: "-") + " / " + (overview.connectionsAvailable?.toString() ?: "-"),
                    "storageEngine: " + (overview.storageEngine ?: "-"),
                ) + overview.notes,
            )
            JsonField("serverStatus JSON", overview.rawStatusJson, enabled = false, minLines = 8) {}
        }
        when {
            state.usersError != null -> InfoCard(title = "用户列表不可用", lines = listOf(state.usersError ?: ""))
            state.users.isNotEmpty() -> InfoCard(title = "用户 (" + state.users.size + ")", lines = state.users)
            else -> InfoCard(title = "用户", lines = listOf("当前认证库没有可显示的用户，或结果为空。"))
        }
        when {
            state.currentOpsError != null -> InfoCard(title = "currentOp 不可用", lines = listOf(state.currentOpsError ?: ""))
            state.currentOpsJson.isNotBlank() -> JsonField("currentOp", state.currentOpsJson, enabled = false, minLines = 8) {}
            else -> InfoCard(title = "currentOp", lines = listOf("暂无当前操作数据。"))
        }
    }
}
