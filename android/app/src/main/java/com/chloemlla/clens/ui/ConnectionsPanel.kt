package com.chloemlla.clens.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.core.mongo.MongoConnectionProfile

@Composable
internal fun ConnectionsPanel(state: ClensUiState, viewModel: ClensViewModel) {
    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "连接配置",
            subtitle = "保存加密连接档案，测试并激活 MongoDB 会话。",
            icon = Icons.Outlined.Cable,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::startCreateConnection, enabled = !state.loading) {
                Icon(Icons.Outlined.Add, contentDescription = "新建", modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("新建连接")
            }
            if (state.isConnected) {
                OutlinedButton(onClick = viewModel::disconnect, enabled = !state.loading) {
                    Icon(Icons.Outlined.LinkOff, contentDescription = "断开", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("断开")
                }
            }
        }
        if (state.profiles.isEmpty()) {
            InfoCard(
                title = "还没有连接",
                lines = listOf(
                    "支持 mongodb:// / mongodb+srv:// URI，或主机端口表单。",
                    "凭据仅在安全存储可用时写入 EncryptedSharedPreferences；安全存储失败会直接拒绝保存。",
                    "局域网实例可关闭 TLS；生产环境建议开启 TLS。",
                ),
            )
        }
        state.profiles.forEach { profile ->
            ConnectionCard(
                profile = profile,
                active = profile.id == state.activeProfileId,
                connected = profile.id == state.connectedProfileId,
                loading = state.loading,
                onActivate = { viewModel.setActiveProfile(profile.id) },
                onEdit = { viewModel.startEditConnection(profile) },
                onTest = { viewModel.testConnection(profile) },
                onConnect = { viewModel.connect(profile) },
                onDelete = { viewModel.deleteConnection(profile.id) },
            )
        }
        if (state.editingConnection) {
            ConnectionEditor(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun ConnectionCard(
    profile: MongoConnectionProfile,
    active: Boolean,
    connected: Boolean,
    loading: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = profile.displayTarget,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(
                    icon = if (connected) Icons.Outlined.Link else Icons.Outlined.Cable,
                    label = when {
                        connected && profile.readOnly -> "只读连接"
                        connected -> "已连接"
                        active -> "默认"
                        else -> "待命"
                    },
                    value = profile.host.ifBlank { "URI" } + if (profile.readOnly) " · RO" else "",
                    active = connected || active,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                OutlinedButton(onClick = onActivate, enabled = !loading) { Text("设默认") }
                OutlinedButton(onClick = onEdit, enabled = !loading) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑", Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("编辑")
                }
                OutlinedButton(onClick = onTest, enabled = !loading) { Text("测试") }
                Button(onClick = onConnect, enabled = !loading) { Text("连接") }
                OutlinedButton(onClick = onDelete, enabled = !loading) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun ConnectionEditor(state: ClensUiState, viewModel: ClensViewModel) {
    val form = state.connectionForm
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                text = if (form.id == null) "新建连接" else "编辑连接",
                subtitle = "URI 与表单二选一，URI 优先。",
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> viewModel.updateConnectionForm { it.copy(name = value) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
                enabled = !state.loading,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("使用 URI")
                Switch(
                    checked = form.useUri,
                    onCheckedChange = { checked -> viewModel.updateConnectionForm { it.copy(useUri = checked) } },
                    enabled = !state.loading,
                )
            }
            if (form.useUri) {
                OutlinedTextField(
                    value = form.uri,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(uri = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("MongoDB URI") },
                    enabled = !state.loading,
                    minLines = 2,
                )
            } else {
                OutlinedTextField(
                    value = form.host,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(host = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("主机") },
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = form.port,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(port = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("端口") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(username = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("用户名") },
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(password = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = form.authDatabase,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(authDatabase = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("认证库 authSource") },
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = form.defaultDatabase,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(defaultDatabase = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("默认数据库") },
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = form.replicaSet,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(replicaSet = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Replica Set") },
                    enabled = !state.loading,
                )
                FlagRow("TLS", form.tls, !state.loading) { checked ->
                    viewModel.updateConnectionForm { it.copy(tls = checked) }
                }
                FlagRow("Direct Connection", form.directConnection, !state.loading) { checked ->
                    viewModel.updateConnectionForm { it.copy(directConnection = checked) }
                }
                FlagRow("只读模式", form.readOnly, !state.loading) { checked ->
                    viewModel.updateConnectionForm { it.copy(readOnly = checked) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveConnection, enabled = !state.loading) { Text("保存") }
                OutlinedButton(onClick = { viewModel.testConnection() }, enabled = !state.loading) { Text("测试当前表单") }
                OutlinedButton(onClick = viewModel::cancelEditConnection, enabled = !state.loading) { Text("取消") }
            }
        }
    }
}
