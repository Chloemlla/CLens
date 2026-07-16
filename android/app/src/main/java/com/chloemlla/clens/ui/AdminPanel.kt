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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.ui.monitor.OpsCounterChartPanel

private const val SLOW_OP_SECS_THRESHOLD = 5L

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
            subtitle = "当前集合：" + state.selectedDatabase + "." + state.selectedCollection +
                if (state.isSelectedView) "（view）" else "",
        )
        if (state.isSelectedView) {
            InfoCard(title = "视图限制", lines = listOf("MongoDB view 不支持索引创建/删除。"))
        }
        ActionRow {
            OutlinedButton(
                onClick = viewModel::refreshIndexes,
                enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.isSelectedView,
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
            enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.isSelectedView,
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
                        enabled = !state.loading && !state.isSelectedView && index.name != "_id_",
                    ) { Text("删除索引") }
                }
            }
        }

        SectionTitle(
            text = "服务器",
            subtitle = "serverStatus / usersInfo / currentOp 权限不足时会降级提示。",
        )
        OpsCounterChartPanel(
            sampleState = state.opsCounterState,
            sampling = state.opsCounterSampling,
            error = state.opsCounterError,
            onVisibleChanged = viewModel::setOpsCounterVisible,
        )
        ActionRow {
            OutlinedButton(onClick = viewModel::refreshServerOverview, enabled = !state.loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("刷新服务器信息")
            }
            OutlinedButton(onClick = viewModel::refreshCurrentOps, enabled = !state.loading) { Text("刷新当前操作") }
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
            state.currentOpsListError != null -> InfoCard(title = "当前操作不可用", lines = listOf(state.currentOpsListError ?: ""))
            state.currentOpsError != null -> InfoCard(title = "currentOp 不可用", lines = listOf(state.currentOpsError ?: ""))
            state.currentOps.isNotEmpty() -> {
                state.currentOps.forEach { op ->
                    val secs = op.secsRunning
                    val slow = secs != null && secs >= SLOW_OP_SECS_THRESHOLD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (slow) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "opid " + op.opId,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (slow) {
                                    Text(
                                        text = "慢操作",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            Text("op: " + op.op.ifBlank { "-" }, fontFamily = FontFamily.Monospace)
                            Text("ns: " + op.ns.ifBlank { "-" }, fontFamily = FontFamily.Monospace)
                            Text(
                                text = "secsRunning: " + (secs?.toString() ?: "-"),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (slow) FontWeight.Bold else FontWeight.Normal,
                                color = if (slow) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text("client: " + op.client.ifBlank { "-" }, fontFamily = FontFamily.Monospace)
                            Button(
                                onClick = { viewModel.requestKillOp(op.opId) },
                                enabled = !state.loading && !state.connectedReadOnly,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) { Text("Kill") }
                        }
                    }
                }
            }
            state.currentOpsJson.isNotBlank() -> JsonField("currentOp", state.currentOpsJson, enabled = false, minLines = 8) {}
            else -> InfoCard(title = "currentOp", lines = listOf("暂无当前操作数据。点击刷新。"))
        }
    }
}
