package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.MongoAdminException
import kotlinx.coroutines.flow.update

class BrowseController(
    private val ctx: ClensSessionContext,
) {
    private val state get() = ctx.state
    private val repository get() = ctx.repository

    fun updateSelectedDatabase(value: String) {
        state.update {
            it.copy(
                selectedDatabase = value,
                selectedCollection = "",
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
                ClensViewModel.Field.EditorJson -> current.copy(editorJson = value)
                ClensViewModel.Field.SelectedDocument -> current.copy(selectedDocumentJson = value)
                else -> current
            }
        }
    }

    fun updateDocumentLimit(value: String) {
        val parsed = value.toIntOrNull() ?: return
        state.update { it.copy(documentLimit = parsed.coerceIn(1, 500)) }
    }

    fun refreshDatabases(silent: Boolean = false) {
        ctx.actions.run("刷新数据库", silent = silent) {
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
                refreshCollections(silent = true)
            }
        }
    }

    fun createDatabase() {
        ctx.actions.run("创建数据库") {
            val name = state.value.newDatabaseName
            repository.createDatabase(name)
            state.update { it.copy(newDatabaseName = "", selectedDatabase = name.trim()) }
            refreshDatabases(silent = true)
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
            refreshDatabases(silent = true)
            state.update { it.copy(status = "数据库已删除：" + name) }
        }
    }

    fun refreshCollections(silent: Boolean = false) {
        ctx.actions.run("刷新集合", silent = silent) {
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
    }

    fun createCollection() {
        ctx.actions.run("创建集合") {
            val database = state.value.selectedDatabase
            val collection = state.value.newCollectionName
            repository.createCollection(database, collection)
            state.update { it.copy(newCollectionName = "", selectedCollection = collection.trim()) }
            refreshCollections(silent = true)
            state.update { it.copy(status = "集合已创建：" + collection) }
        }
    }

    fun renameCollection() {
        ctx.actions.run("重命名集合") {
            val database = state.value.selectedDatabase
            val from = state.value.selectedCollection
            val to = state.value.renameCollectionName
            repository.renameCollection(database, from, to)
            state.update { it.copy(renameCollectionName = "", selectedCollection = to.trim()) }
            refreshCollections(silent = true)
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
            refreshCollections(silent = true)
            state.update { it.copy(status = "集合已删除：" + collection) }
        }
    }

    fun loadDocuments(resetSkip: Boolean = false) {
        ctx.actions.run("加载文档") {
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
        state.update { it.copy(selectedDocumentJson = json, editorJson = json) }
    }

    fun insertDocuments() {
        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持写入文档。") }
            return
        }
        ctx.actions.run("插入文档") {
            val current = state.value
            val count = repository.insertDocuments(
                current.selectedDatabase,
                current.selectedCollection,
                current.editorJson,
            )
            loadDocuments(resetSkip = true)
            state.update { it.copy(status = "已插入 " + count + " 条文档") }
        }
    }

    fun replaceSelectedDocument() {
        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持替换文档。") }
            return
        }
        ctx.actions.run("替换文档") {
            val current = state.value
            val filter = ctx.extractIdFilter(current.selectedDocumentJson)
                ?: throw MongoAdminException.Validation("当前文档缺少 _id，无法替换。")
            val modified = repository.replaceDocument(
                current.selectedDatabase,
                current.selectedCollection,
                filter,
                current.editorJson,
            )
            loadDocuments()
            state.update { it.copy(status = "替换完成，modified=" + modified) }
        }
    }

    fun updateDocuments(multi: Boolean) {
        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持更新文档。") }
            return
        }
        ctx.actions.run(if (multi) "批量更新" else "更新一条") {
            val current = state.value
            val modified = repository.updateDocuments(
                current.selectedDatabase,
                current.selectedCollection,
                current.browseFilterJson,
                current.editorJson,
                multi = multi,
            )
            loadDocuments()
            state.update { it.copy(status = "更新完成，modified=" + modified) }
        }
    }

    fun deleteDocuments(multi: Boolean) {
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
            loadDocuments(resetSkip = true)
            state.update { it.copy(status = "删除完成，deleted=" + deleted) }
        }
    }

    fun requestDeleteMany() {
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
            loadDocuments(resetSkip = true)
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

}
