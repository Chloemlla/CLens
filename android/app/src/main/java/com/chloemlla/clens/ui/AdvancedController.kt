package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.MongoAdminException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update

class AdvancedController(
    private val ctx: ClensSessionContext,
) {
    private val state get() = ctx.state
    private val repository get() = ctx.repository
    private var changeStreamJob: Job? = null

    fun updateText(field: ClensViewModel.Field, value: String) {
        state.update { current ->
            when (field) {
                ClensViewModel.Field.GridFsBucket -> current.copy(gridFsBucket = value)
                ClensViewModel.Field.GridFsUploadName -> current.copy(gridFsUploadName = value)
                ClensViewModel.Field.GridFsUploadContent -> current.copy(gridFsUploadContent = value)
                ClensViewModel.Field.AuthDatabaseInput -> current.copy(authDatabaseInput = value)
                ClensViewModel.Field.CreateUserName -> current.copy(createUserName = value)
                ClensViewModel.Field.CreateUserPassword -> current.copy(createUserPassword = value)
                ClensViewModel.Field.CreateUserRolesJson -> current.copy(createUserRolesJson = value)
                ClensViewModel.Field.CreateRoleName -> current.copy(createRoleName = value)
                ClensViewModel.Field.CreateRolePrivilegesJson -> current.copy(createRolePrivilegesJson = value)
                ClensViewModel.Field.CreateRoleRolesJson -> current.copy(createRoleRolesJson = value)
                ClensViewModel.Field.ImportJson -> current.copy(importJson = value)
                ClensViewModel.Field.ExportLimit -> current.copy(exportLimit = value)
                else -> current
            }
        }
    }

    fun setImportDropBefore(checked: Boolean) {
        state.update { it.copy(importDropBefore = checked) }
    }

    fun refreshGridFs() {
        val database = state.value.selectedDatabase
        if (database.isBlank()) {
            state.update { it.copy(error = "请先在「浏览」选择数据库。") }
            return
        }
        ctx.actions.run("刷新 GridFS") {
            loadGridFs(database)
        }
    }

    private suspend fun loadGridFs(database: String) {
        val files = repository.listGridFsFiles(database, state.value.gridFsBucket)
        state.update {
            it.copy(
                gridFsFiles = files,
                gridFsError = null,
                status = "GridFS 文件 " + files.size + " 个",
            )
        }
    }

    fun uploadGridFs() {
        ctx.ensureWritable("GridFS 上传")
        val database = state.value.selectedDatabase
        if (database.isBlank()) {
            state.update { it.copy(error = "请先选择数据库。") }
            return
        }
        ctx.actions.run("上传 GridFS") {
            val id = repository.uploadGridFsText(
                database = database,
                filename = state.value.gridFsUploadName,
                content = state.value.gridFsUploadContent,
                bucketName = state.value.gridFsBucket,
            )
            state.update { it.copy(status = "已上传 GridFS 文件 id=" + id, gridFsUploadContent = "") }
            ctx.recordAudit("gridfs.upload", id)
            loadGridFs(database)
        }
    }

    fun downloadGridFs(fileId: String) {
        val database = state.value.selectedDatabase
        if (database.isBlank()) return
        ctx.actions.run("下载 GridFS") {
            val content = repository.downloadGridFsText(database, fileId, state.value.gridFsBucket)
            state.update { it.copy(gridFsDownloadContent = content, status = "已下载文件内容") }
        }
    }

    fun requestDeleteGridFs(fileId: String) {
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropGridFsFile,
                    target = fileId,
                    message = "将删除 GridFS 文件 id=`" + fileId + "`。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun deleteGridFsConfirmed() {
        ctx.ensureWritable("GridFS 删除")
        val database = state.value.selectedDatabase
        val fileId = state.value.pendingDestructive?.target.orEmpty()
        if (database.isBlank() || fileId.isBlank()) return
        ctx.actions.run("删除 GridFS 文件") {
            repository.deleteGridFsFile(database, fileId, state.value.gridFsBucket)
            state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "", status = "GridFS 文件已删除") }
            ctx.recordAudit("gridfs.delete", fileId)
            loadGridFs(database)
        }
    }

    fun startChangeStream(scope: CoroutineScope) {
        val database = state.value.selectedDatabase
        val collection = state.value.selectedCollection
        if (database.isBlank() || collection.isBlank()) {
            state.update { it.copy(error = "请先选择数据库和集合。") }
            return
        }
        if (changeStreamJob?.isActive == true) return
        state.update {
            it.copy(
                changeStreamRunning = true,
                changeStreamError = null,
                changeStreamEvents = emptyList(),
                status = "Change Stream 已启动",
            )
        }
        changeStreamJob = repository.openChangeStream(
            scope = scope,
            database = database,
            collectionName = collection,
            onEvent = { event ->
                state.update { current ->
                    val next = (listOf(event) + current.changeStreamEvents).take(50)
                    current.copy(changeStreamEvents = next)
                }
            },
            onError = { message ->
                state.update {
                    it.copy(
                        changeStreamRunning = false,
                        changeStreamError = message,
                        error = message,
                    )
                }
            },
            onClosed = {
                state.update { it.copy(changeStreamRunning = false) }
            },
        )
    }

    fun stopChangeStream() {
        changeStreamJob?.cancel()
        changeStreamJob = null
        state.update { it.copy(changeStreamRunning = false, status = "Change Stream 已停止") }
    }

    fun refreshUsersAndRoles() {
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        ctx.actions.run("刷新用户角色") {
            loadUsersAndRoles(authDb)
        }
    }

    private suspend fun loadUsersAndRoles(authDb: String) {
        val users = runCatching { repository.listUsersDetailed(authDb) }
        val roles = runCatching { repository.listRoles(authDb) }
        state.update {
            it.copy(
                detailedUsers = users.getOrDefault(emptyList()),
                detailedUsersError = users.exceptionOrNull()?.message,
                roles = roles.getOrDefault(emptyList()),
                rolesError = roles.exceptionOrNull()?.message,
                status = "用户/角色已刷新",
            )
        }
    }

    fun createUser() {
        ctx.ensureWritable("创建用户")
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        ctx.actions.run("创建用户") {
            repository.createUser(
                authDatabase = authDb,
                user = state.value.createUserName,
                password = state.value.createUserPassword,
                rolesJson = state.value.createUserRolesJson,
            )
            state.update {
                it.copy(
                    createUserPassword = "",
                    status = "用户已创建：" + state.value.createUserName,
                
                )
            }
            loadUsersAndRoles(authDb)
        }
    }

    fun requestDropUser(user: String) {
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropUser,
                    target = user,
                    message = "将删除用户 `" + user + "`。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun dropUserConfirmed() {
        ctx.ensureWritable("删除用户")
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        val user = state.value.pendingDestructive?.target.orEmpty()
        ctx.actions.run("删除用户") {
            repository.dropUser(authDb, user)
            state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "", status = "用户已删除：" + user) }
            ctx.recordAudit("dropUser", user)
            loadUsersAndRoles(authDb)
        }
    }

    fun createRole() {
        ctx.ensureWritable("创建角色")
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        ctx.actions.run("创建角色") {
            repository.createRole(
                authDatabase = authDb,
                role = state.value.createRoleName,
                privilegesJson = state.value.createRolePrivilegesJson,
                rolesJson = state.value.createRoleRolesJson,
            )
            state.update { it.copy(status = "角色已创建：" + state.value.createRoleName) }
            ctx.recordAudit("createRole", state.value.createRoleName)
            loadUsersAndRoles(authDb)
        }
    }

    fun requestDropRole(role: String) {
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropRole,
                    target = role,
                    message = "将删除角色 `" + role + "`。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun dropRoleConfirmed() {
        ctx.ensureWritable("删除角色")
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        val role = state.value.pendingDestructive?.target.orEmpty()
        ctx.actions.run("删除角色") {
            repository.dropRole(authDb, role)
            state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "", status = "角色已删除：" + role) }
            ctx.recordAudit("dropRole", role)
            loadUsersAndRoles(authDb)
        }
    }

    fun requestImport() {
        if (state.value.importDropBefore) {
            state.update {
                it.copy(
                    pendingDestructive = PendingDestructiveAction(
                        action = DestructiveAction.ImportDropCollection,
                        target = it.selectedCollection,
                        message = "导入前将删除并重建集合 `" + it.selectedDatabase + "." + it.selectedCollection + "`。",
                    ),
                    destructiveConfirmInput = "",
                )
            }
        } else {
            importConfirmed()
        }
    }

    fun importConfirmed() {
        ctx.ensureWritable("导入文档")
        val database = state.value.selectedDatabase
        val collection = state.value.selectedCollection
        if (database.isBlank() || collection.isBlank()) {
            state.update { it.copy(error = "请先选择数据库和集合。") }
            return
        }
        ctx.actions.run("导入文档") {
            val count = repository.importDocuments(
                database = database,
                collectionName = collection,
                jsonArrayOrDocs = state.value.importJson,
                dropBeforeImport = state.value.importDropBefore,
            )
            state.update {
                it.copy(
                    pendingDestructive = null,
                    destructiveConfirmInput = "",
                    status = "导入完成，插入 " + count + " 条",
                )
            }
            ctx.recordAudit("importDocuments", database + "." + collection, "count=" + count)
        }
    }

    fun exportCollection() {
        val database = state.value.selectedDatabase
        val collection = state.value.selectedCollection
        if (database.isBlank() || collection.isBlank()) {
            state.update { it.copy(error = "请先选择数据库和集合。") }
            return
        }
        val limit = state.value.exportLimit.toIntOrNull() ?: 200
        ctx.actions.run("导出集合") {
            val json = repository.exportDocuments(database, collection, "{}", limit)
            state.update { it.copy(exportJson = json, status = "导出完成") }
        }
    }

    fun onCleared() {
        stopChangeStream()
    }

    fun refreshAuditLog() {
        ctx.refreshLocalLists()
        state.update { it.copy(status = "审计日志已刷新") }
    }

    fun clearAuditLog() {
        ctx.localStore.clearAuditLog()
        ctx.refreshLocalLists()
        state.update { it.copy(status = "审计日志已清空") }
    }
}
