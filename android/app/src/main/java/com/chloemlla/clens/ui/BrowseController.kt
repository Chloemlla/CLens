package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.MongoAdminException
import com.chloemlla.clens.core.storage.DocumentDraft
import com.chloemlla.clens.ui.editor.DocNodeCodec
import com.chloemlla.clens.ui.editor.DocValueType
import com.chloemlla.clens.ui.editor.DocumentEditorMode
import com.chloemlla.clens.ui.editor.DocumentEditorSource
import com.chloemlla.clens.ui.editor.DocumentEditorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.update
import org.json.JSONObject

class BrowseController(
    private val ctx: ClensSessionContext,
) {
    private val state get() = ctx.state
    private val repository get() = ctx.repository
    private val draftStore get() = ctx.draftStore

    fun updateSelectedDatabase(value: String) {
        state.update {
            it.copy(
                selectedDatabase = value,
                selectedCollection = "",
                collectionSearchQuery = "",
                collections = emptyList(),
                documents = emptyList(),
                indexes = emptyList(),
                databaseStatsJson = "",
                databaseStatsError = null,
                selectedCollectionStats = null,
                collectionStatsError = null,
                maintenanceResultJson = "",
            )
        }
        if (value.isNotBlank() && state.value.isConnected) {
            refreshCollections()
            refreshDatabaseStats()
        }
    }

    fun updateSelectedCollection(value: String) {
        state.update {
            it.copy(
                selectedCollection = value,
                documents = emptyList(),
                indexes = emptyList(),
                documentSkip = 0,
                selectedCollectionStats = null,
                collectionStatsError = null,
                maintenanceResultJson = "",
            )
        }
        if (value.isNotBlank() && state.value.isConnected) {
            refreshCollectionStats()
        }
    }

    fun updateText(field: ClensViewModel.Field, value: String) {
        state.update { current ->
            when (field) {
                ClensViewModel.Field.NewDatabase -> current.copy(newDatabaseName = value)
                ClensViewModel.Field.NewCollection -> current.copy(newCollectionName = value)
                ClensViewModel.Field.RenameCollection -> current.copy(renameCollectionName = value)
                ClensViewModel.Field.BrowseFilter -> current.copy(browseFilterJson = value)
                ClensViewModel.Field.BrowseSort -> current.copy(browseSortJson = value)
                ClensViewModel.Field.BrowseProjection -> current.copy(browseProjectionJson = value)
                ClensViewModel.Field.EditorJson -> {
                    val diagnostics = DocNodeCodec.diagnostics(value)
                    current.copy(
                        editorJson = value,
                        documentEditor = current.documentEditor.copy(
                            mode = DocumentEditorMode.Code,
                            codeText = value,
                            codeDiagnostics = diagnostics,
                            dirty = true,
                            parseError = diagnostics.firstOrNull(),
                        ),
                    )
                }
                ClensViewModel.Field.SelectedDocument -> current.copy(selectedDocumentJson = value)
                ClensViewModel.Field.ValidatorJsonInput -> current.copy(validatorJsonInput = value)
                ClensViewModel.Field.ValidationLevelInput -> current.copy(validationLevelInput = value)
                ClensViewModel.Field.ValidationActionInput -> current.copy(validationActionInput = value)
                ClensViewModel.Field.DatabaseSearch -> current.copy(databaseSearchQuery = value)
                ClensViewModel.Field.CollectionSearch -> current.copy(collectionSearchQuery = value)
                else -> current
            }
        }
        if (field == ClensViewModel.Field.EditorJson) {
            persistDraft()
        }
    }

    fun updateDocumentLimit(value: String) {
        val parsed = value.toIntOrNull() ?: return
        state.update { it.copy(documentLimit = parsed.coerceIn(1, 500)) }
    }

    fun refreshDatabases(silent: Boolean = false) {
        ctx.actions.run("刷新数据库", silent = silent) {
            loadDatabases(silent = silent)
        }
    }

    fun createDatabase() {
        ctx.ensureWritable("创建数据库")

        ctx.actions.run("创建数据库") {
            val name = state.value.newDatabaseName
            repository.createDatabase(name)
            state.update { it.copy(newDatabaseName = "", selectedDatabase = name.trim()) }
            loadDatabases(silent = true)
            state.update { it.copy(status = "数据库已创建：" + name) }
        }
    }

    fun requestDropDatabase() {
        val name = state.value.selectedDatabase
        if (name.isBlank()) return
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropDatabase,
                    target = name,
                    message = "将永久删除数据库 `" + name + "` 及其全部集合。请输入数据库名以确认。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun dropDatabaseConfirmed() {
        ctx.ensureWritable("删除数据库")

        ctx.actions.run("删除数据库") {
            val name = state.value.selectedDatabase
            repository.dropDatabase(name)
            state.update {
                it.copy(
                    selectedDatabase = "",
                    selectedCollection = "",
                    collections = emptyList(),
                    documents = emptyList(),
                    indexes = emptyList(),
                    pendingDestructive = null,
                    destructiveConfirmInput = "",
                )
            }
            loadDatabases(silent = true)
            ctx.recordAudit("dropDatabase", name)
            state.update { it.copy(status = "数据库已删除：" + name) }
        }
    }

    fun refreshCollections(silent: Boolean = false) {
        ctx.actions.run("刷新集合", silent = silent) {
            loadCollections(silent = silent)
        }
    }

    fun createCollection() {
        ctx.ensureWritable("创建集合")

        ctx.actions.run("创建集合") {
            val database = state.value.selectedDatabase
            val collection = state.value.newCollectionName
            repository.createCollection(database, collection)
            state.update { it.copy(newCollectionName = "", selectedCollection = collection.trim()) }
            loadCollections(silent = true)
            state.update { it.copy(status = "集合已创建：" + collection) }
        }
    }

    fun renameCollection() {
        ctx.ensureWritable("重命名集合")

        ctx.actions.run("重命名集合") {
            val database = state.value.selectedDatabase
            val from = state.value.selectedCollection
            val to = state.value.renameCollectionName
            repository.renameCollection(database, from, to)
            state.update { it.copy(renameCollectionName = "", selectedCollection = to.trim()) }
            loadCollections(silent = true)
            state.update { it.copy(status = "集合已重命名为 " + to) }
        }
    }

    fun requestDropCollection() {
        val collection = state.value.selectedCollection
        if (collection.isBlank()) return
        val db = state.value.selectedDatabase
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropCollection,
                    target = collection,
                    message = "将永久删除集合 `" + db + "." + collection + "`。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun dropCollectionConfirmed() {
        ctx.ensureWritable("删除集合")

        ctx.actions.run("删除集合") {
            val database = state.value.selectedDatabase
            val collection = state.value.selectedCollection
            repository.dropCollection(database, collection)
            state.update {
                it.copy(
                    selectedCollection = "",
                    documents = emptyList(),
                    indexes = emptyList(),
                    pendingDestructive = null,
                    destructiveConfirmInput = "",
                )
            }
            loadCollections(silent = true)
            ctx.recordAudit("dropCollection", database + "." + collection)
            state.update { it.copy(status = "集合已删除：" + collection) }
        }
    }

    fun loadDocuments(resetSkip: Boolean = false) {
        ctx.actions.run("加载文档") {
            loadDocumentsInternal(resetSkip = resetSkip)
        }
    }

    fun nextDocumentPage() {
        state.update { it.copy(documentSkip = it.documentSkip + it.documentLimit) }
        loadDocuments()
    }

    fun previousDocumentPage() {
        state.update { it.copy(documentSkip = (it.documentSkip - it.documentLimit).coerceAtLeast(0)) }
        loadDocuments()
    }

    fun selectDocument(json: String) {
        val editor = buildEditorFromJson(
            json = json,
            source = DocumentEditorSource.SelectedDocument,
            preferredMode = state.value.documentEditor.mode,
        )
        state.update {
            it.copy(
                selectedDocumentJson = json,
                editorJson = editor.codeText,
                documentEditor = editor,
            )
        }
        offerDraftIfPresent(documentId = extractDocumentId(json), sourceJson = json)
    }

    fun insertDocuments() {
        ctx.ensureWritable("插入文档")

        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持写入文档。") }
            return
        }
        ctx.actions.run("插入文档") {
            val current = state.value
            val payload = resolveEditorPayload(current)
            val count = repository.insertDocuments(
                current.selectedDatabase,
                current.selectedCollection,
                payload,
            )
            clearCurrentDraft(current)
            state.update {
                it.copy(
                    editorJson = payload,
                    documentEditor = it.documentEditor.copy(
                        dirty = false,
                        draftBanner = null,
                        draftId = null,
                        codeText = payload,
                    ),
                    status = "已插入 " + count + " 条文档",
                )
            }
            loadDocumentsInternal(resetSkip = true)
        }
    }

    fun replaceSelectedDocument() {
        ctx.ensureWritable("替换文档")

        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持替换文档。") }
            return
        }
        ctx.actions.run("替换文档") {
            val current = state.value
            val filter = ctx.extractIdFilter(current.selectedDocumentJson)
                ?: throw MongoAdminException.Validation("当前文档缺少 _id，无法替换。")
            val payload = resolveEditorPayload(current)
            val modified = repository.replaceDocument(
                current.selectedDatabase,
                current.selectedCollection,
                filter,
                payload,
            )
            clearCurrentDraft(current)
            state.update {
                it.copy(
                    editorJson = payload,
                    selectedDocumentJson = payload,
                    documentEditor = it.documentEditor.copy(
                        dirty = false,
                        draftBanner = null,
                        draftId = null,
                        codeText = payload,
                    ),
                    status = "替换完成，modified=" + modified,
                )
            }
            loadDocumentsInternal()
        }
    }

    fun updateDocuments(multi: Boolean) {
        ctx.ensureWritable("更新文档")

        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持更新文档。") }
            return
        }
        ctx.actions.run(if (multi) "批量更新" else "更新一条") {
            val current = state.value
            val payload = resolveEditorPayload(current)
            val modified = repository.updateDocuments(
                current.selectedDatabase,
                current.selectedCollection,
                current.browseFilterJson,
                payload,
                multi = multi,
            )
            loadDocumentsInternal()
            state.update { it.copy(status = "更新完成，modified=" + modified) }
        }
    }

    fun deleteDocuments(multi: Boolean) {
        ctx.ensureWritable("删除文档")

        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持删除文档。") }
            return
        }
        if (multi) {
            requestDeleteMany()
            return
        }
        ctx.actions.run("删除一条") {
            val current = state.value
            val filter = ctx.extractIdFilter(current.selectedDocumentJson)
                ?: throw MongoAdminException.Validation("请先选择带 _id 的文档。")
            val deleted = repository.deleteDocuments(
                current.selectedDatabase,
                current.selectedCollection,
                filter,
                multi = false,
            )
            loadDocumentsInternal(resetSkip = true)
            ctx.recordAudit("deleteOne", current.selectedDatabase + "." + current.selectedCollection, "deleted=" + deleted)
            state.update { it.copy(status = "删除完成，deleted=" + deleted) }
        }
    }

    fun requestDeleteMany() {
        ctx.ensureWritable("批量删除")

        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持批量删除。") }
            return
        }
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DeleteMany,
                    target = it.selectedCollection,
                    message = "将按当前 Filter 执行 deleteMany：`" + it.selectedDatabase + "." + it.selectedCollection + "`。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun deleteManyConfirmed() {
        ctx.actions.run("批量删除") {
            val current = state.value
            val deleted = repository.deleteDocuments(
                current.selectedDatabase,
                current.selectedCollection,
                current.browseFilterJson,
                multi = true,
            )
            state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "") }
            loadDocumentsInternal(resetSkip = true)
            ctx.recordAudit("deleteMany", current.selectedDatabase + "." + current.selectedCollection, "deleted=" + deleted)
            state.update { it.copy(status = "删除完成，deleted=" + deleted) }
        }
    }

    fun refreshDatabaseStats() {
        val database = state.value.selectedDatabase
        if (database.isBlank()) return
        ctx.actions.run("刷新库统计") {
            val stats = runCatching { repository.databaseStats(database) }
            state.update {
                it.copy(
                    databaseStatsJson = stats.getOrDefault(""),
                    databaseStatsError = stats.exceptionOrNull()?.message,
                    status = if (stats.isSuccess) "数据库统计已更新" else it.status,
                )
            }
        }
    }

    fun refreshCollectionStats() {
        val database = state.value.selectedDatabase
        val collection = state.value.selectedCollection
        if (database.isBlank() || collection.isBlank()) return
        ctx.actions.run("刷新集合统计") {
            val stats = runCatching { repository.collectionStats(database, collection) }
            state.update {
                it.copy(
                    selectedCollectionStats = stats.getOrNull(),
                    collectionStatsError = stats.exceptionOrNull()?.message,
                    status = if (stats.isSuccess) "集合统计已更新" else it.status,
                )
            }
        }
    }

    fun requestCompactCollection() {
        val collection = state.value.selectedCollection
        if (collection.isBlank() || state.value.isSelectedView) return
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.CompactCollection,
                    target = collection,
                    message = "将对集合 `" + state.value.selectedDatabase + "." + collection + "` 执行 compact。此操作可能长时间锁表。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun compactCollectionConfirmed() {
        ctx.ensureWritable("compact")

        ctx.actions.run("压缩集合") {
            val database = state.value.selectedDatabase
            val collection = state.value.selectedCollection
            val result = repository.compactCollection(database, collection)
            state.update {
                it.copy(
                    maintenanceResultJson = result,
                    pendingDestructive = null,
                    destructiveConfirmInput = "",
                    status = "compact 完成",
                )
            }
            ctx.recordAudit("compact", database + "." + collection)
        }
    }

    fun validateSelectedCollection() {
        val database = state.value.selectedDatabase
        val collection = state.value.selectedCollection
        if (database.isBlank() || collection.isBlank()) return
        ctx.actions.run("校验集合") {
            val result = repository.validateCollection(database, collection)
            state.update {
                it.copy(
                    maintenanceResultJson = result,
                    status = "validate 完成",
                )
            }
        }
    }

    fun setResultViewMode(mode: ResultViewMode) {
        state.update { it.copy(resultViewMode = mode) }
    }

    fun loadCollectionValidator() {
        val database = state.value.selectedDatabase
        val collection = state.value.selectedCollection
        if (database.isBlank() || collection.isBlank()) return
        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持 validator。", collectionValidator = null) }
            return
        }
        ctx.actions.run("加载 validator") {
            loadValidator(database, collection)
        }
    }

    private suspend fun loadValidator(database: String, collection: String) {
        val info = runCatching { repository.getCollectionValidator(database, collection) }
        state.update {
            it.copy(
                collectionValidator = info.getOrNull(),
                collectionValidatorError = info.exceptionOrNull()?.message,
                validatorJsonInput = info.getOrNull()?.validatorJson ?: it.validatorJsonInput,
                validationLevelInput = info.getOrNull()?.validationLevel ?: it.validationLevelInput,
                validationActionInput = info.getOrNull()?.validationAction ?: it.validationActionInput,
                status = if (info.isSuccess) "validator 已加载" else it.status,
            )
        }
    }

    fun applyCollectionValidator() {
        ctx.ensureWritable("更新 validator")
        val database = state.value.selectedDatabase
        val collection = state.value.selectedCollection
        if (database.isBlank() || collection.isBlank() || state.value.isSelectedView) return
        ctx.actions.run("更新 validator") {
            val result = repository.setCollectionValidator(
                database = database,
                collectionName = collection,
                validatorJson = state.value.validatorJsonInput,
                validationLevel = state.value.validationLevelInput,
                validationAction = state.value.validationActionInput,
            )
            state.update { it.copy(maintenanceResultJson = result, status = "validator 已更新") }
            ctx.recordAudit("collMod.validator", database + "." + collection)
            loadValidator(database, collection)
        }
    }

    fun setDocumentEditorMode(mode: DocumentEditorMode) {
        state.update { current ->
            val editor = current.documentEditor
            if (mode == DocumentEditorMode.Code) {
                val code = runCatching { DocNodeCodec.serialize(editor.root) }.getOrDefault(editor.codeText)
                current.copy(
                    editorJson = code,
                    documentEditor = editor.copy(
                        mode = DocumentEditorMode.Code,
                        codeText = code,
                        codeDiagnostics = DocNodeCodec.diagnostics(code),
                        parseError = null,
                    ),
                )
            } else {
                current.copy(documentEditor = editor.copy(mode = DocumentEditorMode.Tree))
            }
        }
        persistDraft()
    }

    fun applyCodeToTree() {
        val current = state.value
        val code = current.documentEditor.codeText
        val diagnostics = DocNodeCodec.diagnostics(code)
        if (diagnostics.isNotEmpty()) {
            state.update {
                it.copy(
                    documentEditor = it.documentEditor.copy(
                        codeDiagnostics = diagnostics,
                        parseError = diagnostics.firstOrNull(),
                    ),
                    error = diagnostics.firstOrNull(),
                )
            }
            return
        }
        val parsed = DocNodeCodec.tryParse(code)
        parsed.onSuccess { root ->
            state.update {
                it.copy(
                    editorJson = code,
                    documentEditor = it.documentEditor.copy(
                        root = root,
                        codeText = code,
                        codeDiagnostics = emptyList(),
                        parseError = null,
                        dirty = true,
                        mode = DocumentEditorMode.Tree,
                    ),
                )
            }
            persistDraft()
        }.onFailure { error ->
            state.update {
                it.copy(
                    documentEditor = it.documentEditor.copy(
                        parseError = error.message ?: "无法解析 JSON",
                        codeDiagnostics = listOf(error.message ?: "无法解析 JSON"),
                    ),
                    error = error.message ?: "无法解析 JSON",
                )
            }
        }
    }

    fun toggleDocumentNode(pathKey: String) {
        state.update { current ->
            val root = DocNodeCodec.toggleCollapsed(current.documentEditor.root, pathKey)
            current.copy(documentEditor = current.documentEditor.copy(root = root))
        }
    }

    fun beginEditDocumentNode(pathKey: String) {
        state.update {
            it.copy(documentEditor = it.documentEditor.copy(editingPath = pathKey, selectedPath = pathKey))
        }
    }

    fun dismissEditDocumentNode() {
        state.update { it.copy(documentEditor = it.documentEditor.copy(editingPath = null)) }
    }

    fun commitDocumentLeafEdit(pathKey: String, type: DocValueType, scalar: String?) {
        state.update { current ->
            val root = DocNodeCodec.updateScalar(current.documentEditor.root, pathKey, type, scalar)
            val code = runCatching { DocNodeCodec.serialize(root) }.getOrDefault(current.documentEditor.codeText)
            current.copy(
                editorJson = code,
                documentEditor = current.documentEditor.copy(
                    root = root,
                    codeText = code,
                    dirty = true,
                    editingPath = null,
                    codeDiagnostics = emptyList(),
                    parseError = null,
                ),
            )
        }
        persistDraft()
    }

    fun startBlankDocument(keepMode: Boolean = false) {
        val mode = if (keepMode) state.value.documentEditor.mode else DocumentEditorMode.Tree
        val root = DocNodeCodec.emptyObject(collapsed = false)
        val code = DocNodeCodec.serialize(root)
        state.update {
            it.copy(
                selectedDocumentJson = "",
                editorJson = code,
                documentEditor = DocumentEditorState(
                    mode = mode,
                    root = root,
                    codeText = code,
                    source = DocumentEditorSource.InsertBlank,
                    dirty = false,
                    draftId = null,
                    draftBanner = null,
                ),
            )
        }
        offerDraftIfPresent(documentId = null, sourceJson = code)
    }

    fun restoreDraft() {
        val current = state.value
        val documentId = extractDocumentId(current.selectedDocumentJson)
        val draft = currentDraft(current, documentId) ?: return
        val editor = buildEditorFromJson(
            json = draft.codeText,
            source = if (documentId == null) DocumentEditorSource.InsertBlank else DocumentEditorSource.SelectedDocument,
            preferredMode = if (draft.mode == "code") DocumentEditorMode.Code else DocumentEditorMode.Tree,
            draftId = draft.draftId,
            dirty = true,
        )
        state.update {
            it.copy(
                editorJson = editor.codeText,
                documentEditor = editor.copy(draftBanner = null),
            )
        }
    }

    fun discardDraft() {
        val current = state.value
        val documentId = extractDocumentId(current.selectedDocumentJson)
        clearCurrentDraft(current, documentId)
        state.update {
            it.copy(documentEditor = it.documentEditor.copy(draftBanner = null, draftId = null, dirty = false))
        }
    }

    private suspend fun loadDatabases(silent: Boolean = false) {
        val databases = repository.listDatabases()
        val selected = state.value.selectedDatabase
            .takeIf { current -> databases.any { it.name == current } }
            ?: databases.firstOrNull()?.name.orEmpty()
        state.update {
            it.copy(
                databases = databases,
                selectedDatabase = selected,
                status = if (silent) it.status else ("已加载 " + databases.size + " 个数据库"),
            )
        }
        if (selected.isNotBlank()) {
            loadCollections(silent = true)
        }
    }

    private suspend fun loadCollections(silent: Boolean = false) {
        val database = state.value.selectedDatabase
        val collections = repository.listCollections(database)
        val selected = state.value.selectedCollection
            .takeIf { current -> collections.any { it.name == current } }
            ?: collections.firstOrNull()?.name.orEmpty()
        state.update {
            it.copy(
                collections = collections,
                selectedCollection = selected,
                status = if (silent) it.status else ("已加载 " + collections.size + " 个集合"),
            )
        }
    }

    private suspend fun loadDocumentsInternal(resetSkip: Boolean = false) {
        if (resetSkip) {
            state.update { it.copy(documentSkip = 0) }
        }
        val current = state.value
        val page = repository.findDocuments(
            database = current.selectedDatabase,
            collection = current.selectedCollection,
            filterJson = current.browseFilterJson,
            sortJson = current.browseSortJson,
            projectionJson = current.browseProjectionJson,
            limit = current.documentLimit,
            skip = current.documentSkip,
        )
        state.update {
            it.copy(
                documents = page.documents,
                documentCountHint = page.countHint,
                selectedDocumentJson = page.documents.firstOrNull().orEmpty(),
                status = "已加载 " + page.documents.size + " 条文档" +
                    (page.countHint?.let { count -> " / 约 " + count }.orEmpty()),
            )
        }
        page.documents.firstOrNull()?.let { selectDocument(it) }
            ?: startBlankDocument(keepMode = true)
    }

    private fun buildEditorFromJson(
        json: String,
        source: DocumentEditorSource,
        preferredMode: DocumentEditorMode,
        draftId: String? = null,
        dirty: Boolean = false,
    ): DocumentEditorState {
        val diagnostics = DocNodeCodec.diagnostics(json)
        val parsed = DocNodeCodec.tryParse(json)
        val root = parsed.getOrElse { DocNodeCodec.emptyObject(collapsed = false) }
        val code = if (diagnostics.isEmpty()) {
            runCatching { DocNodeCodec.serialize(root) }.getOrDefault(json)
        } else {
            json
        }
        return DocumentEditorState(
            mode = preferredMode,
            root = root,
            codeText = code,
            codeDiagnostics = diagnostics,
            dirty = dirty,
            draftId = draftId,
            source = source,
            parseError = diagnostics.firstOrNull() ?: parsed.exceptionOrNull()?.message,
        )
    }

    private fun resolveEditorPayload(current: ClensUiState): String {
        val editor = current.documentEditor
        return when (editor.mode) {
            DocumentEditorMode.Tree -> {
                runCatching { DocNodeCodec.serialize(editor.root) }
                    .getOrElse { throw MongoAdminException.Validation(it.message ?: "文档树序列化失败") }
            }
            DocumentEditorMode.Code -> {
                val diagnostics = DocNodeCodec.diagnostics(editor.codeText)
                if (diagnostics.isNotEmpty()) {
                    throw MongoAdminException.Validation(diagnostics.first())
                }
                editor.codeText
            }
        }
    }

    private fun persistDraft() {
        val current = state.value
        val connectionId = current.connectedProfileId ?: return
        if (current.selectedDatabase.isBlank() || current.selectedCollection.isBlank()) return
        if (!current.documentEditor.dirty) return
        val documentId = extractDocumentId(current.selectedDocumentJson)
        val draftId = current.documentEditor.draftId ?: UUID.randomUUID().toString()
        val draft = DocumentDraft(
            draftId = draftId,
            connectionId = connectionId,
            database = current.selectedDatabase,
            collection = current.selectedCollection,
            documentId = documentId,
            updatedAtMillis = System.currentTimeMillis(),
            mode = if (current.documentEditor.mode == DocumentEditorMode.Code) "code" else "tree",
            codeText = current.documentEditor.codeText.ifBlank { current.editorJson },
            source = when (current.documentEditor.source) {
                DocumentEditorSource.SelectedDocument -> "selected"
                DocumentEditorSource.ImportedJson -> "imported"
                DocumentEditorSource.InsertBlank -> "insert"
            },
        )
        draftStore.save(draft)
        state.update {
            it.copy(documentEditor = it.documentEditor.copy(draftId = draftId))
        }
    }

    private fun offerDraftIfPresent(documentId: String?, sourceJson: String) {
        val current = state.value
        val draft = currentDraft(current, documentId) ?: return
        if (draft.codeText.trim() == sourceJson.trim()) return
        state.update {
            it.copy(
                documentEditor = it.documentEditor.copy(
                    draftId = draft.draftId,
                    draftBanner = "发现本地草稿（" + formatDraftTime(draft.updatedAtMillis) + "）",
                ),
            )
        }
    }

    private fun currentDraft(current: ClensUiState, documentId: String?): DocumentDraft? {
        val connectionId = current.connectedProfileId ?: return null
        if (current.selectedDatabase.isBlank() || current.selectedCollection.isBlank()) return null
        return draftStore.load(
            connectionId = connectionId,
            database = current.selectedDatabase,
            collection = current.selectedCollection,
            documentId = documentId,
        )
    }

    private fun clearCurrentDraft(
        current: ClensUiState,
        documentId: String? = extractDocumentId(current.selectedDocumentJson),
    ) {
        val connectionId = current.connectedProfileId ?: return
        if (current.selectedDatabase.isBlank() || current.selectedCollection.isBlank()) return
        draftStore.clear(
            connectionId = connectionId,
            database = current.selectedDatabase,
            collection = current.selectedCollection,
            documentId = documentId,
        )
    }

    private fun extractDocumentId(documentJson: String): String? {
        if (documentJson.isBlank()) return null
        return runCatching {
            val obj = JSONObject(documentJson)
            if (!obj.has("_id")) return null
            val id = obj.get("_id")
            when (id) {
                is JSONObject -> {
                    when {
                        id.has("\$oid") -> id.optString("\$oid")
                        else -> id.toString()
                    }
                }
                else -> id.toString()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun formatDraftTime(millis: Long): String {
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(millis))
    }
}
