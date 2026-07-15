package com.chloemlla.clens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.clens.core.crash.CrashBreadcrumbs
import com.chloemlla.clens.core.crash.CrashReportSanitizer
import com.chloemlla.clens.core.mongo.MongoAdminException
import com.chloemlla.clens.core.mongo.MongoAdminRepository
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import com.chloemlla.clens.core.mongo.MongoSessionManager
import com.chloemlla.clens.core.storage.MongoConnectionStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClensViewModel(
    private val connectionStore: MongoConnectionStore,
    private val sessionManager: MongoSessionManager,
    private val repository: MongoAdminRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClensUiState())
    val state: StateFlow<ClensUiState> = _state.asStateFlow()
    private val actionMutex = Mutex()

    init {
        refreshProfiles(status = "连接配置已加载")
    }

    fun selectTab(tab: ClensTab) {
        _state.update { it.copy(selectedTab = tab) }
        when (tab) {
            ClensTab.Browse -> if (_state.value.isConnected && _state.value.databases.isEmpty()) refreshDatabases()
            ClensTab.Admin -> if (_state.value.isConnected) refreshServerOverview()
            else -> Unit
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(status = "", error = null) }
    }

    fun updateConnectionForm(transform: (ConnectionFormState) -> ConnectionFormState) {
        _state.update {
            val form = transform(it.connectionForm)
            it.copy(
                connectionForm = form,
                editingConnection = true,
                cleartextWarning = CleartextRisk.forForm(form),
            )
        }
    }

    fun startCreateConnection() {
        _state.update {
            it.copy(
                editingConnection = true,
                connectionForm = ConnectionFormState(),
                status = "填写连接信息",
                error = null,
            )
        }
    }

    fun startEditConnection(profile: MongoConnectionProfile) {
        _state.update {
            it.copy(
                editingConnection = true,
                connectionForm = ConnectionFormState(
                    id = profile.id,
                    name = profile.name,
                    useUri = profile.uri.isNotBlank(),
                    uri = profile.uri,
                    host = profile.host,
                    port = profile.port.toString(),
                    username = profile.username,
                    password = profile.password,
                    authDatabase = profile.authDatabase,
                    defaultDatabase = profile.defaultDatabase,
                    replicaSet = profile.replicaSet,
                    tls = profile.tls,
                    directConnection = profile.directConnection,
                ),
                status = "编辑连接：${profile.name}",
                error = null,
            )
        }
    }

    fun cancelEditConnection() {
        _state.update { it.copy(editingConnection = false, connectionForm = ConnectionFormState()) }
    }

    fun saveConnection() {
        runAction("保存连接") {
            val form = _state.value.connectionForm
            val port = form.port.toIntOrNull()
                ?: throw MongoAdminException.Validation("端口必须是数字。")
            val profile = MongoConnectionProfile(
                id = form.id ?: UUID.randomUUID().toString(),
                name = form.name,
                uri = if (form.useUri) form.uri.trim() else "",
                host = form.host.trim(),
                port = port,
                username = form.username.trim(),
                password = form.password,
                authDatabase = form.authDatabase.trim().ifBlank { "admin" },
                defaultDatabase = form.defaultDatabase.trim(),
                replicaSet = form.replicaSet.trim(),
                tls = form.tls,
                directConnection = form.directConnection,
            )
            connectionStore.upsert(profile)
            refreshProfiles(status = "已保存连接 ${profile.name}")
            _state.update { it.copy(editingConnection = false, connectionForm = ConnectionFormState()) }
        }
    }

    fun deleteConnection(profileId: String) {
        runAction("删除连接") {
            if (_state.value.connectedProfileId == profileId) {
                sessionManager.disconnect()
            }
            connectionStore.delete(profileId)
            refreshProfiles(status = "连接已删除")
            _state.update {
                it.copy(
                    connectedProfileId = if (it.connectedProfileId == profileId) null else it.connectedProfileId,
                    databases = emptyList(),
                    collections = emptyList(),
                    documents = emptyList(),
                    indexes = emptyList(),
                )
            }
        }
    }

    fun setActiveProfile(profileId: String) {
        connectionStore.setActiveProfileId(profileId)
        refreshProfiles(status = "已设为默认连接")
    }

    fun testConnection(profile: MongoConnectionProfile? = null) {
        runAction("测试连接") {
            val target = profile ?: formToProfile()
            val result = sessionManager.test(target)
            _state.update {
                it.copy(status = result.message, error = null)
            }
        }
    }

    fun connect(profile: MongoConnectionProfile? = null) {
        runAction("建立连接") {
            val target = profile ?: formToProfile().also { connectionStore.upsert(it) }
            val result = sessionManager.connect(target)
            connectionStore.setActiveProfileId(target.id)
            refreshProfiles()
            _state.update {
                it.copy(
                    connectedProfileId = target.id,
                    selectedDatabase = target.defaultDatabase,
                    status = result.message,
                    error = null,
                    selectedTab = ClensTab.Browse,
                )
            }
            refreshDatabases(silent = true)
        }
    }

    fun disconnect() {
        runAction("断开连接") {
            sessionManager.disconnect()
            _state.update {
                it.copy(
                    connectedProfileId = null,
                    databases = emptyList(),
                    collections = emptyList(),
                    documents = emptyList(),
                    indexes = emptyList(),
                    serverOverview = null,
                    users = emptyList(),
                    currentOpsJson = "",
                    status = "已断开连接",
                    error = null,
                )
            }
        }
    }

    fun updateSelectedDatabase(value: String) {
        _state.update {
            it.copy(
                selectedDatabase = value,
                selectedCollection = "",
                collections = emptyList(),
                documents = emptyList(),
                indexes = emptyList(),
            )
        }
        if (value.isNotBlank() && _state.value.isConnected) {
            refreshCollections()
        }
    }

    fun updateSelectedCollection(value: String) {
        _state.update {
            it.copy(
                selectedCollection = value,
                documents = emptyList(),
                indexes = emptyList(),
                documentSkip = 0,
            )
        }
    }

    fun updateText(field: Field, value: String) {
        _state.update { state ->
            when (field) {
                Field.NewDatabase -> state.copy(newDatabaseName = value)
                Field.NewCollection -> state.copy(newCollectionName = value)
                Field.RenameCollection -> state.copy(renameCollectionName = value)
                Field.BrowseFilter -> state.copy(browseFilterJson = value)
                Field.BrowseSort -> state.copy(browseSortJson = value)
                Field.BrowseProjection -> state.copy(browseProjectionJson = value)
                Field.EditorJson -> state.copy(editorJson = value)
                Field.SelectedDocument -> state.copy(selectedDocumentJson = value)
                Field.QueryFilter -> state.copy(queryFilterJson = value)
                Field.QuerySort -> state.copy(querySortJson = value)
                Field.QueryProjection -> state.copy(queryProjectionJson = value)
                Field.QueryPipeline -> state.copy(queryPipelineJson = value)
                Field.IndexKeys -> state.copy(indexKeysJson = value)
                Field.IndexName -> state.copy(indexName = value)
                Field.IndexExpire -> state.copy(indexExpireAfterSeconds = value)
            }
        }
    }

    fun updateDocumentLimit(value: String) {
        val parsed = value.toIntOrNull() ?: return
        _state.update { it.copy(documentLimit = parsed.coerceIn(1, 500)) }
    }

    fun setQueryModeAggregate(enabled: Boolean) {
        _state.update { it.copy(queryModeAggregate = enabled) }
    }

    fun setIndexFlags(unique: Boolean? = null, sparse: Boolean? = null) {
        _state.update {
            it.copy(
                indexUnique = unique ?: it.indexUnique,
                indexSparse = sparse ?: it.indexSparse,
            )
        }
    }

    fun refreshDatabases(silent: Boolean = false) {
        runAction("刷新数据库", silent = silent) {
            val databases = repository.listDatabases()
            val selected = _state.value.selectedDatabase
                .takeIf { current -> databases.any { it.name == current } }
                ?: databases.firstOrNull()?.name.orEmpty()
            _state.update {
                it.copy(
                    databases = databases,
                    selectedDatabase = selected,
                    status = if (silent) it.status else "已加载 ${databases.size} 个数据库",
                )
            }
            if (selected.isNotBlank()) {
                refreshCollections(silent = true)
            }
        }
    }

    fun createDatabase() {
        runAction("创建数据库") {
            val name = _state.value.newDatabaseName
            repository.createDatabase(name)
            _state.update { it.copy(newDatabaseName = "", selectedDatabase = name.trim()) }
            refreshDatabases(silent = true)
            _state.update { it.copy(status = "数据库已创建：$name") }
        }
    }

    fun requestDropDatabase() {
        val name = _state.value.selectedDatabase
        if (name.isBlank()) return
        _state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropDatabase,
                    target = name,
                    message = "将永久删除数据库 `$name` 及其全部集合。请输入数据库名以确认。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun dropDatabaseConfirmed() {
        runAction("删除数据库") {
            val name = _state.value.selectedDatabase
            repository.dropDatabase(name)
            _state.update {
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
            _state.update { it.copy(status = "数据库已删除：$name") }
        }
    }

    fun refreshCollections(silent: Boolean = false) {
        runAction("刷新集合", silent = silent) {
            val database = _state.value.selectedDatabase
            val collections = repository.listCollections(database)
            val selected = _state.value.selectedCollection
                .takeIf { current -> collections.any { it.name == current } }
                ?: collections.firstOrNull()?.name.orEmpty()
            _state.update {
                it.copy(
                    collections = collections,
                    selectedCollection = selected,
                    status = if (silent) it.status else "已加载 ${collections.size} 个集合",
                )
            }
        }
    }

    fun createCollection() {
        runAction("创建集合") {
            val database = _state.value.selectedDatabase
            val collection = _state.value.newCollectionName
            repository.createCollection(database, collection)
            _state.update { it.copy(newCollectionName = "", selectedCollection = collection.trim()) }
            refreshCollections(silent = true)
            _state.update { it.copy(status = "集合已创建：$collection") }
        }
    }

    fun renameCollection() {
        runAction("重命名集合") {
            val database = _state.value.selectedDatabase
            val from = _state.value.selectedCollection
            val to = _state.value.renameCollectionName
            repository.renameCollection(database, from, to)
            _state.update { it.copy(renameCollectionName = "", selectedCollection = to.trim()) }
            refreshCollections(silent = true)
            _state.update { it.copy(status = "集合已重命名为 $to") }
        }
    }

    fun requestDropCollection() {
        val collection = _state.value.selectedCollection
        if (collection.isBlank()) return
        _state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropCollection,
                    target = collection,
                    message = "将永久删除集合 `${_state.value.selectedDatabase}.$collection`。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun dropCollectionConfirmed() {
        runAction("删除集合") {
            val database = _state.value.selectedDatabase
            val collection = _state.value.selectedCollection
            repository.dropCollection(database, collection)
            _state.update {
                it.copy(
                    selectedCollection = "",
                    documents = emptyList(),
                    indexes = emptyList(),
                    pendingDestructive = null,
                    destructiveConfirmInput = "",
                )
            }
            refreshCollections(silent = true)
            _state.update { it.copy(status = "集合已删除：$collection") }
        }
    }

    fun loadDocuments(resetSkip: Boolean = false) {
        runAction("加载文档") {
            if (resetSkip) {
                _state.update { it.copy(documentSkip = 0) }
            }
            val state = _state.value
            val page = repository.findDocuments(
                database = state.selectedDatabase,
                collection = state.selectedCollection,
                filterJson = state.browseFilterJson,
                sortJson = state.browseSortJson,
                projectionJson = state.browseProjectionJson,
                limit = state.documentLimit,
                skip = state.documentSkip,
            )
            _state.update {
                it.copy(
                    documents = page.documents,
                    documentCountHint = page.countHint,
                    selectedDocumentJson = page.documents.firstOrNull().orEmpty(),
                    status = "已加载 ${page.documents.size} 条文档" +
                        (page.countHint?.let { count -> " / 约 $count" }.orEmpty()),
                )
            }
        }
    }

    fun nextDocumentPage() {
        _state.update { it.copy(documentSkip = it.documentSkip + it.documentLimit) }
        loadDocuments()
    }

    fun previousDocumentPage() {
        _state.update { it.copy(documentSkip = (it.documentSkip - it.documentLimit).coerceAtLeast(0)) }
        loadDocuments()
    }

    fun selectDocument(json: String) {
        _state.update { it.copy(selectedDocumentJson = json, editorJson = json) }
    }

    fun insertDocuments() {
        runAction("插入文档") {
            val state = _state.value
            val count = repository.insertDocuments(
                state.selectedDatabase,
                state.selectedCollection,
                state.editorJson,
            )
            loadDocuments(resetSkip = true)
            _state.update { it.copy(status = "已插入 $count 条文档") }
        }
    }

    fun replaceSelectedDocument() {
        runAction("替换文档") {
            val state = _state.value
            val filter = extractIdFilter(state.selectedDocumentJson)
                ?: throw MongoAdminException.Validation("当前文档缺少 _id，无法替换。")
            val modified = repository.replaceDocument(
                state.selectedDatabase,
                state.selectedCollection,
                filter,
                state.editorJson,
            )
            loadDocuments()
            _state.update { it.copy(status = "替换完成，modified=$modified") }
        }
    }

    fun updateDocuments(multi: Boolean) {
        runAction(if (multi) "批量更新" else "更新一条") {
            val state = _state.value
            val modified = repository.updateDocuments(
                state.selectedDatabase,
                state.selectedCollection,
                state.browseFilterJson,
                state.editorJson,
                multi = multi,
            )
            loadDocuments()
            _state.update { it.copy(status = "更新完成，modified=$modified") }
        }
    }

    fun deleteDocuments(multi: Boolean) {
        if (multi) {
            requestDeleteMany()
            return
        }
        runAction("删除一条") {
            val state = _state.value
            val filter = extractIdFilter(state.selectedDocumentJson)
                ?: throw MongoAdminException.Validation("请先选择带 _id 的文档。")
            val deleted = repository.deleteDocuments(
                state.selectedDatabase,
                state.selectedCollection,
                filter,
                multi = false,
            )
            loadDocuments(resetSkip = true)
            _state.update { it.copy(status = "删除完成，deleted=$deleted") }
        }
    }

    fun requestDeleteMany() {
        _state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DeleteMany,
                    target = it.selectedCollection,
                    message = "将按当前 Filter 执行 deleteMany：`${it.selectedDatabase}.${it.selectedCollection}`。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun deleteManyConfirmed() {
        runAction("批量删除") {
            val state = _state.value
            val deleted = repository.deleteDocuments(
                state.selectedDatabase,
                state.selectedCollection,
                state.browseFilterJson,
                multi = true,
            )
            _state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "") }
            loadDocuments(resetSkip = true)
            _state.update { it.copy(status = "删除完成，deleted=$deleted") }
        }
    }

    fun runQuery(withExplain: Boolean = false) {
        runAction(if (withExplain) "Explain" else "执行查询") {
            val state = _state.value
            if (state.queryModeAggregate) {
                val result = repository.aggregate(
                    state.selectedDatabase,
                    state.selectedCollection,
                    state.queryPipelineJson,
                )
                _state.update {
                    it.copy(
                        queryResults = result.documents,
                        queryDurationMillis = result.durationMillis,
                        explainJson = "",
                        status = "聚合返回 ${result.documents.size} 条 · ${result.durationMillis}ms",
                    )
                }
            } else {
                val page = repository.findDocuments(
                    database = state.selectedDatabase,
                    collection = state.selectedCollection,
                    filterJson = state.queryFilterJson,
                    sortJson = state.querySortJson,
                    projectionJson = state.queryProjectionJson,
                    limit = state.documentLimit,
                    skip = 0,
                )
                val explain = if (withExplain) {
                    repository.explainFind(
                        state.selectedDatabase,
                        state.selectedCollection,
                        state.queryFilterJson,
                        state.querySortJson,
                        state.queryProjectionJson,
                    )
                } else {
                    ""
                }
                _state.update {
                    it.copy(
                        queryResults = page.documents,
                        queryDurationMillis = null,
                        explainJson = explain,
                        status = "查询返回 ${page.documents.size} 条" +
                            if (withExplain) "（含 explain）" else "",
                    )
                }
            }
        }
    }

    fun refreshIndexes() {
        runAction("刷新索引") {
            val state = _state.value
            val indexes = repository.listIndexes(state.selectedDatabase, state.selectedCollection)
            _state.update {
                it.copy(
                    indexes = indexes,
                    status = "已加载 ${indexes.size} 个索引",
                )
            }
        }
    }

    fun createIndex() {
        runAction("创建索引") {
            val state = _state.value
            val expire = state.indexExpireAfterSeconds.trim()
                .takeIf { it.isNotBlank() }
                ?.toLongOrNull()
            val name = repository.createIndex(
                database = state.selectedDatabase,
                collection = state.selectedCollection,
                keysJson = state.indexKeysJson,
                name = state.indexName,
                unique = state.indexUnique,
                sparse = state.indexSparse,
                expireAfterSeconds = expire,
            )
            refreshIndexes()
            _state.update { it.copy(status = "索引已创建：$name") }
        }
    }

    fun requestDropIndex(name: String) {
        if (name.isBlank() || name == "_id_") return
        _state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropIndex,
                    target = name,
                    message = "将删除索引 `$name`。",
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun dropIndexConfirmed() {
        runAction("删除索引") {
            val state = _state.value
            val name = state.pendingDestructive?.target.orEmpty()
            repository.dropIndex(state.selectedDatabase, state.selectedCollection, name)
            _state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "") }
            refreshIndexes()
            _state.update { it.copy(status = "索引已删除：$name") }
        }
    }

    fun refreshServerOverview() {
        runAction("刷新服务器信息") {
            val overview = repository.serverOverview()
            val usersResult = runCatching {
                repository.listUsers(_state.value.activeProfile?.authDatabase ?: "admin")
            }
            val opsResult = runCatching { repository.currentOps() }
            _state.update {
                it.copy(
                    serverOverview = overview,
                    users = usersResult.getOrDefault(emptyList()),
                    usersError = usersResult.exceptionOrNull()?.message,
                    currentOpsJson = opsResult.getOrDefault(""),
                    currentOpsError = opsResult.exceptionOrNull()?.message,
                    status = "服务器信息已更新",
                )
            }
        }
    }

    private fun refreshProfiles(status: String? = null) {
        val profiles = connectionStore.listProfiles()
        val activeId = connectionStore.getActiveProfileId()
        _state.update {
            it.copy(
                profiles = profiles,
                activeProfileId = activeId,
                status = status ?: it.status,
                error = null,
            )
        }
    }

    private fun formToProfile(): MongoConnectionProfile {
        val form = _state.value.connectionForm
        val port = form.port.toIntOrNull()
            ?: throw MongoAdminException.Validation("端口必须是数字。")
        return MongoConnectionProfile(
            id = form.id ?: UUID.randomUUID().toString(),
            name = form.name.ifBlank { "临时连接" },
            uri = if (form.useUri) form.uri.trim() else "",
            host = form.host.trim(),
            port = port,
            username = form.username.trim(),
            password = form.password,
            authDatabase = form.authDatabase.trim().ifBlank { "admin" },
            defaultDatabase = form.defaultDatabase.trim(),
            replicaSet = form.replicaSet.trim(),
            tls = form.tls,
            directConnection = form.directConnection,
        )
    }


    fun updateDestructiveConfirmInput(value: String) {
        _state.update { it.copy(destructiveConfirmInput = value) }
    }

    fun cancelDestructive() {
        _state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "") }
    }

    fun confirmDestructive() {
        val pending = _state.value.pendingDestructive ?: return
        when (pending.action) {
            DestructiveAction.DropDatabase -> {
                if (_state.value.destructiveConfirmInput != pending.target) {
                    _state.update { it.copy(error = "数据库名不匹配，已取消删除。") }
                    return
                }
                dropDatabaseConfirmed()
            }
            DestructiveAction.DropCollection -> dropCollectionConfirmed()
            DestructiveAction.DeleteMany -> deleteManyConfirmed()
            DestructiveAction.DropIndex -> dropIndexConfirmed()
        }
    }


    private fun extractIdFilter(documentJson: String): String? {
        return runCatching {
            val obj = org.json.JSONObject(documentJson)
            if (!obj.has("_id")) return null
            org.json.JSONObject().put("_id", obj.get("_id")).toString()
        }.getOrNull()
    }

    private fun runAction(
        label: String,
        silent: Boolean = false,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            if (!actionMutex.tryLock()) {
                _state.update {
                    it.copy(error = "已有操作进行中，请等待完成后再试。")
                }
                return@launch
            }
            try {
                _state.update {
                    it.copy(
                        loading = true,
                        error = null,
                        status = if (silent) it.status else (label + "..."),
                    )
                }
                CrashBreadcrumbs.record("Action start: $label")
                try {
                    block()
                    CrashBreadcrumbs.record("Action ok: $label")
                } catch (error: Throwable) {
                    val message = CrashReportSanitizer.sanitize(
                        error.message?.takeIf { it.isNotBlank() } ?: "$label 失败",
                    )
                    CrashBreadcrumbs.record("Action fail: $label")
                    _state.update {
                        it.copy(
                            error = message,
                            status = "",
                        )
                    }
                } finally {
                    _state.update { it.copy(loading = false) }
                }
            } finally {
                actionMutex.unlock()
            }
        }
    }

    enum class Field {
        NewDatabase,
        NewCollection,
        RenameCollection,
        BrowseFilter,
        BrowseSort,
        BrowseProjection,
        EditorJson,
        SelectedDocument,
        QueryFilter,
        QuerySort,
        QueryProjection,
        QueryPipeline,
        IndexKeys,
        IndexName,
        IndexExpire,
    }

    class Factory(
        private val connectionStore: MongoConnectionStore,
        private val sessionManager: MongoSessionManager,
        private val repository: MongoAdminRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ClensViewModel(connectionStore, sessionManager, repository) as T
        }
    }
}