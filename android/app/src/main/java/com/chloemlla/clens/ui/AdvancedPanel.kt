package com.chloemlla.clens.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
internal fun AdvancedPanel(state: ClensUiState, viewModel: ClensViewModel) {
    val context = LocalContext.current
    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "高级能力",
            subtitle = "GridFS / Change Stream / 用户角色 / 导入导出。",
            icon = Icons.Outlined.Build,
        )
        if (!state.isConnected) {
            InfoCard(title = "尚未连接", lines = listOf("先连接 MongoDB，并在「浏览」选择数据库/集合。"))
            return@PanelColumn
        }

        InfoCard(
            title = "当前目标",
            lines = listOf(
                "database: " + state.selectedDatabase.ifBlank { "-" },
                "collection: " + state.selectedCollection.ifBlank { "-" },
            ),
        )

        // GridFS
        SectionTitle(text = "GridFS", subtitle = "默认 bucket=fs，上传文本并按 ObjectId 管理。")
        OutlinedTextField(
            value = state.gridFsBucket,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.GridFsBucket, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Bucket 名") },
            enabled = !state.loading,
        )
        ActionRow {
            OutlinedButton(onClick = viewModel::refreshGridFs, enabled = !state.loading && state.selectedDatabase.isNotBlank()) { Text("刷新文件") }
        }
        OutlinedTextField(
            value = state.gridFsUploadName,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.GridFsUploadName, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("上传文件名") },
            enabled = !state.loading,
        )
        JsonField("上传文本内容", state.gridFsUploadContent, !state.loading, minLines = 4) {
            viewModel.updateText(ClensViewModel.Field.GridFsUploadContent, it)
        }
        Button(onClick = viewModel::uploadGridFs, enabled = !state.loading && state.selectedDatabase.isNotBlank()) { Text("上传到 GridFS") }
        state.gridFsError?.let { InfoCard(title = "GridFS 错误", lines = listOf(it)) }
        state.gridFsFiles.forEach { file ->
            InfoCard(
                title = file.filename.ifBlank { file.id },
                lines = listOf(
                    "id: " + file.id,
                    "length: " + file.length,
                    "uploadDate: " + file.uploadDate,
                    "contentType: " + file.contentType.ifBlank { "-" },
                ),
            )
            ActionRow {
                OutlinedButton(onClick = { viewModel.downloadGridFs(file.id) }, enabled = !state.loading) { Text("下载文本") }
                OutlinedButton(onClick = { viewModel.requestDeleteGridFs(file.id) }, enabled = !state.loading) { Text("删除") }
            }
        }
        if (state.gridFsDownloadContent.isNotBlank()) {
            JsonField("下载内容", state.gridFsDownloadContent, enabled = false, minLines = 5) {}
            OutlinedButton(onClick = {
                val ok = copyTextToClipboard(context, "clens-gridfs", state.gridFsDownloadContent)
                Toast.makeText(context, if (ok) "已复制下载内容" else "复制失败", Toast.LENGTH_SHORT).show()
            }) { Text("复制下载内容") }
        }

        // Change stream
        SectionTitle(text = "Change Stream", subtitle = "需要副本集/分片；事件最多保留 50 条。")
        ActionRow {
            Button(
                onClick = viewModel::startChangeStream,
                enabled = !state.loading && !state.changeStreamRunning && state.selectedCollection.isNotBlank(),
            ) { Text(if (state.changeStreamRunning) "监听中" else "开始监听") }
            OutlinedButton(onClick = viewModel::stopChangeStream, enabled = state.changeStreamRunning) { Text("停止") }
        }
        state.changeStreamError?.let { InfoCard(title = "Change Stream 错误", lines = listOf(it)) }
        state.changeStreamEvents.forEachIndexed { index, event ->
            DocumentSnippet(title = "事件 #" + (index + 1), json = event, selected = false, onClick = {})
        }

        // Users/Roles
        SectionTitle(text = "用户 / 角色", subtitle = "权限不足时会显示错误，而不是静默为空。")
        OutlinedTextField(
            value = state.authDatabaseInput,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.AuthDatabaseInput, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("认证库") },
            enabled = !state.loading,
        )
        OutlinedButton(onClick = viewModel::refreshUsersAndRoles, enabled = !state.loading) { Text("刷新用户/角色") }
        OutlinedTextField(
            value = state.createUserName,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.CreateUserName, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("新用户名") },
            enabled = !state.loading,
        )
        OutlinedTextField(
            value = state.createUserPassword,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.CreateUserPassword, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("新用户密码") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.loading,
        )
        JsonField("用户 roles JSON", state.createUserRolesJson, !state.loading, minLines = 3) {
            viewModel.updateText(ClensViewModel.Field.CreateUserRolesJson, it)
        }
        Button(onClick = viewModel::createUser, enabled = !state.loading) { Text("创建用户") }
        when {
            state.detailedUsersError != null -> InfoCard(title = "用户列表不可用", lines = listOf(state.detailedUsersError ?: ""))
            state.detailedUsers.isNotEmpty() -> state.detailedUsers.forEach { user ->
                InfoCard(title = user.user + "@" + user.db, lines = listOf(user.rolesJson))
                OutlinedButton(onClick = { viewModel.requestDropUser(user.user) }, enabled = !state.loading) { Text("删除用户") }
            }
            else -> InfoCard(title = "用户", lines = listOf("暂无用户数据。"))
        }

        OutlinedTextField(
            value = state.createRoleName,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.CreateRoleName, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("新角色名") },
            enabled = !state.loading,
        )
        JsonField("privileges JSON", state.createRolePrivilegesJson, !state.loading, minLines = 3) {
            viewModel.updateText(ClensViewModel.Field.CreateRolePrivilegesJson, it)
        }
        JsonField("roles JSON", state.createRoleRolesJson, !state.loading, minLines = 3) {
            viewModel.updateText(ClensViewModel.Field.CreateRoleRolesJson, it)
        }
        Button(onClick = viewModel::createRole, enabled = !state.loading) { Text("创建角色") }
        when {
            state.rolesError != null -> InfoCard(title = "角色列表不可用", lines = listOf(state.rolesError ?: ""))
            state.roles.isNotEmpty() -> state.roles.forEach { role ->
                InfoCard(title = role.role + "@" + role.db, lines = listOf(role.rolesJson, role.privilegesJson))
                OutlinedButton(onClick = { viewModel.requestDropRole(role.role) }, enabled = !state.loading) { Text("删除角色") }
            }
            else -> InfoCard(title = "角色", lines = listOf("暂无自定义角色数据。"))
        }

        // Import/export
        SectionTitle(text = "导入 / 导出", subtitle = "导入 JSON 数组；导出受 limit 限制。")
        FlagRow("导入前删除集合", state.importDropBefore, !state.loading, viewModel::setImportDropBefore)
        JsonField("导入 JSON 数组", state.importJson, !state.loading, minLines = 6) {
            viewModel.updateText(ClensViewModel.Field.ImportJson, it)
        }
        Button(
            onClick = viewModel::requestImport,
            enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.isSelectedView,
        ) { Text("导入到当前集合") }
        OutlinedTextField(
            value = state.exportLimit,
            onValueChange = { viewModel.updateText(ClensViewModel.Field.ExportLimit, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("导出 limit") },
            enabled = !state.loading,
        )
        ActionRow {
            OutlinedButton(
                onClick = viewModel::exportCollection,
                enabled = !state.loading && state.selectedCollection.isNotBlank(),
            ) { Text("导出当前集合") }
            OutlinedButton(
                onClick = {
                    if (state.exportJson.isBlank()) {
                        Toast.makeText(context, "没有可分享的导出内容", Toast.LENGTH_SHORT).show()
                    } else {
                        shareText(context, "CLens export", state.exportJson)
                    }
                },
                enabled = state.exportJson.isNotBlank(),
            ) { Text("分享导出 JSON") }
        }
        if (state.exportJson.isNotBlank()) {
            JsonField("导出结果", state.exportJson, enabled = false, minLines = 8) {}
        }

        SectionTitle(text = "本地审计日志", subtitle = "仅记录本机危险操作，最多 100 条。")
        ActionRow {
            OutlinedButton(onClick = viewModel::refreshAuditLog, enabled = !state.loading) { Text("刷新审计") }
            OutlinedButton(onClick = viewModel::clearAuditLog, enabled = !state.loading && state.auditLog.isNotEmpty()) { Text("清空审计") }
        }
        if (state.auditLog.isEmpty()) {
            InfoCard(title = "暂无审计事件", lines = listOf("删除/compact/导入覆盖/用户角色变更会写入这里。"))
        } else {
            state.auditLog.take(20).forEach { item ->
                InfoCard(
                    title = item.action,
                    lines = listOf(
                        "target: " + item.target,
                        "detail: " + item.detail.ifBlank { "-" },
                        "time: " + item.createdAtMillis.toString(),
                    ),
                )
            }
        }
    }
}
