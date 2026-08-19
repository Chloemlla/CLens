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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

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
        runGridFsAction("刷新 GridFS") {
            loadGridFs(database)
        }
    }

    /**
     * Runs a GridFS action and mirrors its failure into [ClensUiState.gridFsError] so the
     * GridFS error card is reachable, then rethrows for the global banner/loading handling.
     */
    private fun runGridFsAction(label: String, block: suspend () -> Unit) {
        ctx.actions.run(label) {
            try {
                block()
            } catch (error: Throwable) {
                // Driver messages can echo the connection string; scrub before display.
                val message = SecretSanitizer.sanitize(
                    error.message?.takeIf { text -> text.isNotBlank() } ?: (label + "失败"),
                )
                state.update { it.copy(gridFsError = message) }
                throw error
            }
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
        val database = state.value.selectedDatabase
        if (database.isBlank()) {
            state.update { it.copy(error = "请先选择数据库。") }
            return
        }
        runGridFsAction("上传 GridFS") {
            ctx.ensureWritable("GridFS 上传")
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
        runGridFsAction("下载 GridFS") {
            val content = repository.downloadGridFsText(database, fileId, state.value.gridFsBucket)
            state.update { it.copy(gridFsDownloadContent = content, gridFsError = null, status = "已下载文件内容") }
        }
    }

    fun requestDeleteGridFs(fileId: String) {
        if (state.value.connectedReadOnly) {
            state.update { it.copy(error = "当前连接为只读模式，已阻止：GridFS 删除") }
            return
        }
        if (fileId.isBlank()) return
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
        val database = state.value.selectedDatabase
        val fileId = state.value.pendingDestructive?.target.orEmpty()
        if (database.isBlank() || fileId.isBlank()) return
        runGridFsAction("删除 GridFS 文件") {
            ctx.ensureWritable("GridFS 删除")
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
                // Driver messages can echo the connection string; scrub before display.
                detailedUsersError = users.exceptionOrNull()?.message?.let(SecretSanitizer::sanitize),
                roles = roles.getOrDefault(emptyList()),
                rolesError = roles.exceptionOrNull()?.message?.let(SecretSanitizer::sanitize),
                status = "用户/角色已刷新",
            )
        }
    }

    fun createUser() {
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        ctx.actions.run("创建用户") {
            ctx.ensureWritable("创建用户")
            repository.createUser(
                authDatabase = authDb,
                user = state.value.createUserName,
                password = state.value.createUserPassword,
                rolesJson = state.value.createUserRolesJson,
            )
            state.update { current ->
                current.copy(
                    createUserPassword = "",
                    status = "用户已创建：" + current.createUserName,
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
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        val user = state.value.pendingDestructive?.target.orEmpty()
        if (user.isBlank()) return
        ctx.actions.run("删除用户") {
            ctx.ensureWritable("删除用户")
            repository.dropUser(authDb, user)
            state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "", status = "用户已删除：" + user) }
            ctx.recordAudit("dropUser", user)
            loadUsersAndRoles(authDb)
        }
    }

    fun createRole() {
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        ctx.actions.run("创建角色") {
            ctx.ensureWritable("创建角色")
            repository.createRole(
                authDatabase = authDb,
                role = state.value.createRoleName,
                privilegesJson = state.value.createRolePrivilegesJson,
                rolesJson = state.value.createRoleRolesJson,
            )
            state.update { current -> current.copy(status = "角色已创建：" + current.createRoleName) }
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
        val authDb = state.value.authDatabaseInput.ifBlank { "admin" }
        val role = state.value.pendingDestructive?.target.orEmpty()
        if (role.isBlank()) return
        ctx.actions.run("删除角色") {
            ctx.ensureWritable("删除角色")
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
            ctx.ensureWritable("导入文档")
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
                // Codec parsing plus queue enqueue are CPU + file IO; keep them off the main thread.
                val queued = withContext(Dispatchers.IO) {
                    val docs = runCatching { DocumentImportCodecs.parseJsonArrayToDocStrings(payload) }
                        .getOrDefault(emptyList())
                    val chunks = DocumentImportCodecs.chunk(docs, StagingQueueRules.IMPORT_CHUNK_SIZE)
                    if (chunks.isEmpty()) return@withContext null
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
                    chunks.size to ctx.stagingStore.list()
                } ?: throw error
                val (chunkCount, items) = queued
                state.update {
                    it.copy(
                        pendingDestructive = null,
                        destructiveConfirmInput = "",
                        stagingItems = items,
                        status = "导入失败，已分片入队（" + chunkCount + " 片），可稍后同步",
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
        val limit = resolvedExportLimit()
        ctx.actions.run("导出集合") {
            val json = repository.exportDocuments(database, collection, "{}", limit)
            state.update { it.copy(exportJson = json, status = "导出完成（limit=" + limit + "）") }
        }
    }

    /**
     * Effective export limit: non-numeric input falls back to [DEFAULT_EXPORT_LIMIT] and
     * out-of-range values clamp to [MIN_EXPORT_LIMIT]..[MAX_EXPORT_LIMIT], matching the
     * clamp the repository applies. The panel validates against the same bounds.
     */
    private fun resolvedExportLimit(): Int {
        val raw = state.value.exportLimit.trim().toIntOrNull() ?: DEFAULT_EXPORT_LIMIT
        return raw.coerceIn(MIN_EXPORT_LIMIT, MAX_EXPORT_LIMIT)
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
        val limit = resolvedExportLimit()
        val format = state.value.exportFormat
        ctx.actions.run("导出集合文件") {
            val json = repository.exportDocuments(database, collection, "{}", limit)
            // exportDocuments returns pretty JSON array string; re-encode to requested format when
            // needed. Both codec passes are CPU bound over possibly large payloads, so keep them
            // off the main thread.
            val encoded = withContext(Dispatchers.Default) {
                val docs = DocumentImportCodecs.parseJsonArrayToDocStrings(json)
                docs.size to DocumentExportCodecs.encode(docs, format)
            }
            val (docCount, content) = encoded
            state.update {
                it.copy(
                    exportJson = content,
                    status = "导出完成（" + format.name + "，" + docCount + " 条，limit=" + limit + "）",
                )
            }
        }
    }

    fun prepareImportFromText(fileName: String, text: String) {
        ctx.actions.run("解析导入文件") {
            // Whole-file parsing is CPU bound and can span megabytes; never on the main thread.
            val parsed = withContext(Dispatchers.Default) {
                if (fileName.lowercase().endsWith(".csv")) {
                    // Parse the table once and reuse it for both headers and documents.
                    val table = DocumentImportCodecs.parseCsv(text)
                    val docs = DocumentImportCodecs.applyCsvMapping(table, FieldMapping.identity(table.headers))
                    ParsedImport(
                        docCount = docs.size,
                        preview = table.headers,
                        payload = DocumentImportCodecs.toJsonArrayPayload(docs),
                    )
                } else {
                    val docs = DocumentImportCodecs.parseJsonArrayToDocStrings(text)
                    ParsedImport(
                        docCount = docs.size,
                        preview = DocumentImportCodecs.previewJsonFields(text),
                        payload = DocumentImportCodecs.toJsonArrayPayload(docs),
                    )
                }
            }
            state.update {
                it.copy(
                    importSourceName = fileName,
                    importMappingPreview = parsed.preview,
                    importJson = parsed.payload,
                    status = "已载入 " + fileName + "，" + parsed.docCount + " 条待导入",
                )
            }
        }
    }

    private data class ParsedImport(
        val docCount: Int,
        val preview: List<String>,
        val payload: String,
    )

    fun confirmMappedImport() {
        requestImport()
    }

    fun refreshStagingQueue() {
        // Queue listing reads the index plus every payload file; go through the action runner
        // so the file IO stays off the main thread.
        ctx.actions.run("刷新待提交队列", silent = true) {
            loadStagingItems("待提交队列已刷新")
        }
    }

    private suspend fun loadStagingItems(status: String) {
        val items = withContext(Dispatchers.IO) { ctx.stagingStore.list() }
        state.update { it.copy(stagingItems = items, status = status) }
    }

    fun discardStagingItem(id: String) {
        ctx.actions.run("丢弃队列项", silent = true) {
            withContext(Dispatchers.IO) { ctx.stagingStore.delete(id) }
            loadStagingItems("已丢弃队列项")
        }
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
            // Every stagingStore call touches index.json plus a payload file.
            val items = withContext(Dispatchers.IO) {
                if (onlyId != null) {
                    listOfNotNull(ctx.stagingStore.get(onlyId))
                } else {
                    ctx.stagingStore.peekReady()
                }
            }
            var success = 0
            var failed = 0
            items.forEach { item ->
                try {
                    val full = withContext(Dispatchers.IO) {
                        ctx.stagingStore.markInFlight(item.id)
                        ctx.stagingStore.get(item.id)
                    } ?: item
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
                    withContext(Dispatchers.IO) { ctx.stagingStore.markSuccess(full.id) }
                    success++
                } catch (error: Throwable) {
                    val message = SecretSanitizer.sanitize(
                        error.message?.takeIf { it.isNotBlank() } ?: "同步失败"
                    )
                    withContext(Dispatchers.IO) { ctx.stagingStore.markFailed(item.id, message) }
                    failed++
                }
            }
            val refreshed = withContext(Dispatchers.IO) { ctx.stagingStore.list() }
            state.update {
                it.copy(
                    stagingItems = refreshed,
                    status = "队列同步完成：成功 " + success + "，失败 " + failed,
                )
            }
        }
    }

    companion object {
        /** Export limit bounds; the panel validates against the same range. */
        const val MIN_EXPORT_LIMIT = 1
        const val MAX_EXPORT_LIMIT = 1_000
        const val DEFAULT_EXPORT_LIMIT = 200
    }
}
