package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.MongoAdminException
import com.chloemlla.clens.core.export.DocumentExportCodecs
import com.chloemlla.clens.core.export.DocumentExportFormat
import com.chloemlla.clens.core.importdata.DocumentImportCodecs
import com.chloemlla.clens.core.importdata.FieldMapping
import com.chloemlla.clens.core.storage.StagingOpType
import com.chloemlla.clens.core.storage.StagingQueueRules
import com.chloemlla.clens.core.util.SecretSanitizer
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
                    message = "将删除 GridFS 文件 id=`" + fileId + "`。请长按 3 秒确认。",
                    confirmToken = fileId,
                    confirmMode = DestructiveConfirmMode.LongPress,
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
                    message = "将删除用户 `" + user + "`。请输入用户名确认。",
                    confirmToken = user,
                    confirmMode = DestructiveConfirmMode.TypeName,
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
                    message = "将删除角色 `" + role + "`。请输入角色名确认。",
                    confirmToken = role,
                    confirmMode = DestructiveConfirmMode.TypeName,
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
                        message = "导入前将删除并重建集合 `" + it.selectedDatabase + "." + it.selectedCollection + "`。请输入集合名确认。",
                        confirmToken = it.selectedCollection,
                        confirmMode = DestructiveConfirmMode.TypeName,
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
        val connectionId = state.value.connectedProfileId
        val dropBefore = state.value.importDropBefore
        val payload = state.value.importJson
        ctx.actions.run("导入文档") {
            try {
                val count = repository.importDocuments(
                    database = database,
                    collectionName = collection,
                    jsonArrayOrDocs = payload,
                    dropBeforeImport = dropBefore,
                )
                state.update {
                    it.copy(
                        pendingDestructive = null,
                        destructiveConfirmInput = "",
                        status = "导入完成，插入 " + count + " 条",
                    )
                }
                ctx.recordAudit("importDocuments", database + "." + collection, "count=" + count)
            } catch (error: Throwable) {
                if (connectionId.isNullOrBlank()) throw error
                val docs = runCatching { DocumentImportCodecs.parseJsonArrayToDocStrings(payload) }
                    .getOrDefault(emptyList())
                val chunks = DocumentImportCodecs.chunk(docs, StagingQueueRules.IMPORT_CHUNK_SIZE)
                if (chunks.isEmpty()) throw error
                chunks.forEachIndexed { index, chunk ->
                    ctx.stagingStore.enqueue(
                        type = StagingOpType.IMPORT_CHUNK,
                        connectionId = connectionId,
                        database = database,
                        collection = collection,
                        payloadJson = DocumentImportCodecs.toJsonArrayPayload(chunk),
                        dropBeforeImport = dropBefore && index == 0,
                        chunkIndex = index,
                        chunkCount = chunks.size,
                    )
                }
                state.update {
                    it.copy(
                        pendingDestructive = null,
                        destructiveConfirmInput = "",
                        stagingItems = ctx.stagingStore.list(),
                        status = "导入失败，已分片入队（" + chunks.size + " 片），可稍后同步",
                        error = SecretSanitizer.sanitize(error.message ?: "导入失败"),
                    )
                }
            }
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

    fun setExportFormat(format: DocumentExportFormat) {
        state.update { it.copy(exportFormat = format) }
    }

    fun exportCollectionAsFile() {
        val database = state.value.selectedDatabase
        val collection = state.value.selectedCollection
        if (database.isBlank() || collection.isBlank()) {
            state.update { it.copy(error = "请先选择数据库和集合。") }
            return
        }
        val limit = state.value.exportLimit.toIntOrNull() ?: 200
        val format = state.value.exportFormat
        ctx.actions.run("导出集合文件") {
            val json = repository.exportDocuments(database, collection, "{}", limit)
            // exportDocuments returns pretty JSON array string; re-encode to requested format when needed
            val docs = DocumentImportCodecs.parseJsonArrayToDocStrings(json)
            val content = DocumentExportCodecs.encode(docs, format)
            state.update {
                it.copy(
                    exportJson = content,
                    status = "导出完成（" + format.name + "，" + docs.size + " 条）",
                )
            }
        }
    }

    fun prepareImportFromText(fileName: String, text: String) {
        ctx.actions.run("解析导入文件") {
            val lower = fileName.lowercase()
            val docs = if (lower.endsWith(".csv")) {
                val table = DocumentImportCodecs.parseCsv(text)
                val mapping = FieldMapping.identity(table.headers)
                DocumentImportCodecs.applyCsvMapping(table, mapping)
            } else {
                DocumentImportCodecs.parseJsonArrayToDocStrings(text)
            }
            val preview = if (lower.endsWith(".csv")) {
                DocumentImportCodecs.parseCsv(text).headers
            } else {
                DocumentImportCodecs.previewJsonFields(text)
            }
            val payload = DocumentImportCodecs.toJsonArrayPayload(docs)
            state.update {
                it.copy(
                    importSourceName = fileName,
                    importMappingPreview = preview,
                    importJson = payload,
                    status = "已载入 " + fileName + "，" + docs.size + " 条待导入",
                )
            }
        }
    }

    fun confirmMappedImport() {
        requestImport()
    }

    fun refreshStagingQueue() {
        state.update {
            it.copy(
                stagingItems = ctx.stagingStore.list(),
                status = "待提交队列已刷新",
            )
        }
    }

    fun discardStagingItem(id: String) {
        ctx.stagingStore.delete(id)
        refreshStagingQueue()
        state.update { it.copy(status = "已丢弃队列项") }
    }

    fun retryStagingItem(id: String) {
        processStagingQueue(onlyId = id)
    }

    fun processStagingQueue(onlyId: String? = null) {
        if (state.value.connectedReadOnly) {
            state.update { it.copy(error = "只读连接不能同步待提交队列") }
            return
        }
        ctx.actions.run("同步待提交队列") {
            val items = if (onlyId != null) {
                listOfNotNull(ctx.stagingStore.get(onlyId))
            } else {
                ctx.stagingStore.peekReady()
            }
            var success = 0
            var failed = 0
            items.forEach { item ->
                try {
                    ctx.stagingStore.markInFlight(item.id)
                    val full = ctx.stagingStore.get(item.id) ?: item
                    when (full.type) {
                        StagingOpType.INSERT -> {
                            repository.insertDocuments(full.database, full.collection, full.payloadJson)
                        }
                        StagingOpType.REPLACE -> {
                            val filter = full.filterJson
                                ?: throw MongoAdminException.Validation("REPLACE 队列项缺少 filter")
                            repository.replaceDocument(full.database, full.collection, filter, full.payloadJson)
                        }
                        StagingOpType.IMPORT_CHUNK -> {
                            repository.importDocuments(
                                database = full.database,
                                collectionName = full.collection,
                                jsonArrayOrDocs = full.payloadJson,
                                dropBeforeImport = full.dropBeforeImport && full.chunkIndex == 0,
                            )
                        }
                    }
                    ctx.stagingStore.markSuccess(full.id)
                    success++
                } catch (error: Throwable) {
                    val message = SecretSanitizer.sanitize(
                        error.message?.takeIf { it.isNotBlank() } ?: "同步失败"
                    )
                    ctx.stagingStore.markFailed(item.id, message)
                    failed++
                }
            }
            state.update {
                it.copy(
                    stagingItems = ctx.stagingStore.list(),
                    status = "队列同步完成：成功 " + success + "，失败 " + failed,
                )
            }
        }
    }
