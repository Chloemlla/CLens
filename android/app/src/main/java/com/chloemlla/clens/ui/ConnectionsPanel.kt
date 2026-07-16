package com.chloemlla.clens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import com.chloemlla.clens.core.mongo.MongoUriBuilder
import com.chloemlla.clens.ui.connection.ConnectionImportSection

@Composable
internal fun ConnectionsPanel(state: ClensUiState, viewModel: ClensViewModel) {
    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "连接配置",
            subtitle = "保存加密连接档案，测试并激活 MongoDB 会话。",
            icon = Icons.Outlined.Cable,
        )
        ActionRow {
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 420.dp
                val titleBlock: @Composable (Modifier) -> Unit = { titleModifier ->
                    Column(modifier = titleModifier) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = profile.displayTarget,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val statusPill: @Composable (Modifier) -> Unit = { pillModifier ->
                    StatusPill(
                        modifier = pillModifier,
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
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        titleBlock(Modifier.fillMaxWidth())
                        statusPill(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        titleBlock(Modifier.weight(1f))
                        statusPill(Modifier)
                    }
                }
            }
            ScrollableActionRow {
                OutlinedButton(onClick = onActivate, enabled = !loading) { Text("设默认") }
                OutlinedButton(onClick = onEdit, enabled = !loading) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑", Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("编辑")
                }
                OutlinedButton(onClick = onTest, enabled = !loading) { Text("测试") }
                Button(onClick = onConnect, enabled = !loading) {
                    Icon(Icons.Outlined.Link, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (connected) "重连" else "连接")
                }
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
    var importMessage by remember { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                text = if (form.id == null) "新建连接" else "编辑连接",
                subtitle = "URI 与表单二选一，URI 优先。",
            )
            ConnectionImportSection(
                enabled = !state.loading,
                onUriImported = { uri, name ->
                    val parsed = MongoUriBuilder.parseUriToFormFields(uri)
                    viewModel.updateConnectionForm { current ->
                        current.copy(
                            useUri = true,
                            uri = uri,
                            name = when {
                                !name.isNullOrBlank() && current.name.isBlank() -> name
                                else -> current.name
                            },
                            host = parsed?.host?.takeIf { it.isNotBlank() } ?: current.host,
                            port = parsed?.port?.toString() ?: current.port,
                            username = parsed?.username?.takeIf { it.isNotBlank() } ?: current.username,
                            password = parsed?.password?.takeIf { it.isNotBlank() } ?: current.password,
                            authDatabase = parsed?.authDatabase?.takeIf { it.isNotBlank() }
                                ?: current.authDatabase,
                            defaultDatabase = parsed?.defaultDatabase?.takeIf { it.isNotBlank() }
                                ?: current.defaultDatabase,
                            replicaSet = parsed?.replicaSet?.takeIf { it.isNotBlank() }
                                ?: current.replicaSet,
                            tls = parsed?.tls ?: current.tls,
                            directConnection = parsed?.directConnection ?: current.directConnection,
                        )
                    }
                    importMessage = buildString {
                        append("已导入连接 URI")
                        if (!name.isNullOrBlank()) append(" · ").append(name)
                        if (parsed != null) append("，并同步了主机/认证字段")
                    }
                },
                onImportMessage = { message ->
                    importMessage = message
                },
            )
            importMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> viewModel.updateConnectionForm { it.copy(name = value) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
                enabled = !state.loading,
            )
            SwitchSettingRow(
                label = "使用 URI",
                checked = form.useUri,
                enabled = !state.loading,
                onCheckedChange = { checked -> viewModel.updateConnectionForm { it.copy(useUri = checked) } },
            )
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
                ResponsiveFieldRow(
                    first = {
                        OutlinedTextField(
                            value = form.host,
                            onValueChange = { value -> viewModel.updateConnectionForm { it.copy(host = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("主机") },
                            enabled = !state.loading,
                        )
                    },
                    second = {
                        OutlinedTextField(
                            value = form.port,
                            onValueChange = { value -> viewModel.updateConnectionForm { it.copy(port = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !state.loading,
                        )
                    },
                )
                ResponsiveFieldRow(
                    first = {
                        OutlinedTextField(
                            value = form.username,
                            onValueChange = { value -> viewModel.updateConnectionForm { it.copy(username = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("用户名") },
                            enabled = !state.loading,
                        )
                    },
                    second = {
                        OutlinedTextField(
                            value = form.password,
                            onValueChange = { value -> viewModel.updateConnectionForm { it.copy(password = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !state.loading,
                        )
                    },
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
                SwitchSettingRow(
                    label = "TLS",
                    checked = form.tls,
                    enabled = !state.loading,
                    onCheckedChange = { checked -> viewModel.updateConnectionForm { it.copy(tls = checked) } },
                )
                SwitchSettingRow(
                    label = "Direct Connection",
                    checked = form.directConnection,
                    enabled = !state.loading,
                    onCheckedChange = { checked -> viewModel.updateConnectionForm { it.copy(directConnection = checked) } },
                )
                SwitchSettingRow(
                    label = "只读模式",
                    checked = form.readOnly,
                    enabled = !state.loading,
                    onCheckedChange = { checked -> viewModel.updateConnectionForm { it.copy(readOnly = checked) } },
                )
            }
            
            SwitchSettingRow(
                label = "SSH 隧道",
                checked = form.sshEnabled,
                enabled = !state.loading,
                onCheckedChange = { checked -> viewModel.updateConnectionForm { it.copy(sshEnabled = checked) } },
            )
            if (form.sshEnabled) {
                Text(
                    text = "通过堡垒机本地端口转发访问 Mongo。私钥支持 OpenSSH PEM 与 PuTTY PPK2（ssh-rsa）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ResponsiveFieldRow(
                    first = {
                        OutlinedTextField(
                            value = form.sshHost,
                            onValueChange = { value -> viewModel.updateConnectionForm { it.copy(sshHost = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("SSH 主机") },
                            enabled = !state.loading,
                        )
                    },
                    second = {
                        OutlinedTextField(
                            value = form.sshPort,
                            onValueChange = { value -> viewModel.updateConnectionForm { it.copy(sshPort = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("SSH 端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !state.loading,
                        )
                    },
                )
                OutlinedTextField(
                    value = form.sshUsername,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(sshUsername = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("SSH 用户名") },
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = form.sshPassword,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(sshPassword = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("SSH 密码（可选）") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = form.sshPrivateKeyPem,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(sshPrivateKeyPem = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SSH 私钥 PEM / PPK（可选）") },
                    enabled = !state.loading,
                    minLines = 4,
                )
                OutlinedTextField(
                    value = form.sshPrivateKeyPassphrase,
                    onValueChange = { value -> viewModel.updateConnectionForm { it.copy(sshPrivateKeyPassphrase = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("私钥口令（可选）") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.loading,
                )
                ResponsiveFieldRow(
                    first = {
                        OutlinedTextField(
                            value = form.sshRemoteHost,
                            onValueChange = { value -> viewModel.updateConnectionForm { it.copy(sshRemoteHost = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("远程 Mongo 主机") },
                            placeholder = { Text("默认取连接主机 / 127.0.0.1") },
                            enabled = !state.loading,
                        )
                    },
                    second = {
                        OutlinedTextField(
                            value = form.sshRemotePort,
                            onValueChange = { value -> viewModel.updateConnectionForm { it.copy(sshRemotePort = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("远程 Mongo 端口") },
                            placeholder = { Text("默认 27017") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !state.loading,
                        )
                    },
                )
            }
            ActionRow {
                Button(onClick = viewModel::saveConnection, enabled = !state.loading) {
                    Text("保存连接")
                }
                OutlinedButton(onClick = { viewModel.testConnection() }, enabled = !state.loading) { Text("测试当前表单") }
                OutlinedButton(onClick = viewModel::cancelEditConnection, enabled = !state.loading) { Text("取消") }
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ResponsiveFieldRow(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 480.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                first()
                second()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) { first() }
                Column(modifier = Modifier.weight(1f)) { second() }
            }
        }
    }
}
