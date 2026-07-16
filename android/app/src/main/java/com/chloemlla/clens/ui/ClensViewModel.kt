package com.chloemlla.clens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.clens.core.mongo.MongoAdminRepository
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import com.chloemlla.clens.core.mongo.MongoSessionManager
import com.chloemlla.clens.core.storage.MongoConnectionStore
import com.chloemlla.clens.core.storage.LocalAppStore
import com.chloemlla.clens.core.storage.DocumentDraftStore
import com.chloemlla.clens.core.storage.OfflineSnapshotStore
import com.chloemlla.clens.core.storage.StagingQueueStore
import com.chloemlla.clens.core.export.DocumentExportFormat
import com.chloemlla.clens.core.storage.SecurityPrefsStore
import com.chloemlla.clens.core.storage.ThemeMode
import com.chloemlla.clens.ui.editor.DocValueType
import com.chloemlla.clens.ui.editor.DocumentEditorMode
import com.chloemlla.clens.core.mongo.VisualFilterClause
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ClensViewModel(
    connectionStore: MongoConnectionStore,
    private val localStore: LocalAppStore,
    private val draftStore: DocumentDraftStore,
    private val snapshotStore: OfflineSnapshotStore,
    private val stagingStore: StagingQueueStore,
    private val securityPrefs: SecurityPrefsStore,
    sessionManager: MongoSessionManager,
    repository: MongoAdminRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClensUiState())
    val state: StateFlow<ClensUiState> = _state.asStateFlow()

    private val actions = ClensActionRunner(viewModelScope, _state)
    private val ctx = ClensSessionContext(
        state = _state,
        connectionStore = connectionStore,
        localStore = localStore,
        draftStore = draftStore,
        snapshotStore = snapshotStore,
        stagingStore = stagingStore,
        sessionManager = sessionManager,
        repository = repository,
        actions = actions,
    )
    private val connections = ConnectionController(ctx)
    private val browse = BrowseController(ctx)
    private val query = QueryController(ctx)
    private val admin = AdminController(ctx)
    private val advanced = AdvancedController(ctx)
    private val sessionHealth = SessionHealthController(ctx)

    init {
        ctx.refreshProfiles(status = "连接配置已加载")
        ctx.refreshLocalLists()
        _state.update {
            it.copy(
                biometricEnabled = securityPrefs.isBiometricEnabled(),
                themeMode = securityPrefs.getThemeMode(),
            )
        }
    }

    fun selectTab(tab: ClensTab) {
        _state.update { it.copy(selectedTab = tab) }
        when (tab) {
            ClensTab.Browse -> if (_state.value.isConnected && _state.value.databases.isEmpty()) browse.refreshDatabases()
            ClensTab.Query -> {
                if (_state.value.isConnected &&
                    _state.value.selectedCollection.isNotBlank() &&
                    _state.value.indexes.isEmpty() &&
                    !_state.value.isSelectedView
                ) {
                    admin.refreshIndexes()
                }
            }
            ClensTab.Admin -> if (_state.value.isConnected) {
                admin.refreshServerOverview()
                admin.refreshCurrentOps()
            }
            ClensTab.Advanced -> if (_state.value.isConnected) {
                // no-op auto load; user triggers refresh actions
            }
            else -> Unit
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(status = "", error = null) }
    }

    fun updateConnectionForm(transform: (ConnectionFormState) -> ConnectionFormState) = connections.updateConnectionForm(transform)
    fun startCreateConnection() = connections.startCreateConnection()
    fun startEditConnection(profile: MongoConnectionProfile) = connections.startEditConnection(profile)
    fun cancelEditConnection() = connections.cancelEditConnection()
    fun saveConnection() = connections.saveConnection()
    fun deleteConnection(profileId: String) = connections.deleteConnection(profileId)
    fun setActiveProfile(profileId: String) = connections.setActiveProfile(profileId)
    fun testConnection(profile: MongoConnectionProfile? = null) = connections.testConnection(profile)
    fun connect(profile: MongoConnectionProfile? = null) = connections.connect(profile) {
        browse.refreshDatabases(silent = true)
    }
    fun disconnect() {
        advanced.stopChangeStream()
        admin.stopOpsCounterSampling(keepHistory = false)
        connections.disconnect()
    }

    fun updateSelectedDatabase(value: String) = browse.updateSelectedDatabase(value)
    fun updateSelectedCollection(value: String) = browse.updateSelectedCollection(value)
    fun clearSelectedDocument() = browse.clearSelectedDocument()
    fun updateDocumentLimit(value: String) = browse.updateDocumentLimit(value)
    fun refreshDatabases(silent: Boolean = false) = browse.refreshDatabases(silent)
    fun createDatabase() = browse.createDatabase()
    fun requestDropDatabase() = browse.requestDropDatabase()
    fun dropDatabaseConfirmed() = browse.dropDatabaseConfirmed()
    fun refreshCollections(silent: Boolean = false) = browse.refreshCollections(silent)
    fun createCollection() = browse.createCollection()
    fun renameCollection() = browse.renameCollection()
    fun requestDropCollection() = browse.requestDropCollection()
    fun dropCollectionConfirmed() = browse.dropCollectionConfirmed()
    fun loadDocuments(resetSkip: Boolean = false) = browse.loadDocuments(resetSkip)
    fun nextDocumentPage() = browse.nextDocumentPage()
    fun previousDocumentPage() = browse.previousDocumentPage()
    fun selectDocument(json: String) = browse.selectDocument(json)
    fun insertDocuments() = browse.insertDocuments()
    fun replaceSelectedDocument() = browse.replaceSelectedDocument()
    fun updateDocuments(multi: Boolean) = browse.updateDocuments(multi)
    fun deleteDocuments(multi: Boolean) = browse.deleteDocuments(multi)
    fun requestDeleteMany() = browse.requestDeleteMany()
    fun deleteManyConfirmed() = browse.deleteManyConfirmed()
    fun refreshDatabaseStats() = browse.refreshDatabaseStats()
    fun refreshCollectionStats() = browse.refreshCollectionStats()
    fun requestCompactCollection() = browse.requestCompactCollection()
    fun compactCollectionConfirmed() = browse.compactCollectionConfirmed()
    fun validateSelectedCollection() = browse.validateSelectedCollection()
    fun setResultViewMode(mode: ResultViewMode) {
        browse.setResultViewMode(mode)
        query.setResultViewMode(mode)
    }

    fun setQueryModeAggregate(enabled: Boolean) = query.setQueryModeAggregate(enabled)
    fun runQuery(withExplain: Boolean = false) = query.runQuery(withExplain)
    fun explainAggregate() = query.explainAggregate()
    fun setQueryVisualMode(enabled: Boolean) = query.setQueryVisualMode(enabled)
    fun setQueryInputMode(mode: QueryInputMode) = query.setQueryInputMode(mode)
    fun updateSqlInput(value: String) = query.updateSqlInput(value)
    fun translateSql() {
        query.translateSql(showStatus = true)
    }
    fun runSqlQuery() = query.runSqlQuery()
    fun updateVisualClause(index: Int, clause: VisualFilterClause) = query.updateVisualClause(index, clause)
    fun addVisualClause() = query.addVisualClause()
    fun removeVisualClause(index: Int) = query.removeVisualClause(index)
    fun updateFavoriteNameInput(value: String) = query.updateFavoriteNameInput(value)
    fun saveCurrentQueryFavorite() = query.saveCurrentQueryFavorite()
    fun restoreQueryFavorite(id: String) = query.restoreQueryFavorite(id)
    fun deleteQueryFavorite(id: String) = query.deleteQueryFavorite(id)
    fun suggestedQueryFields(): List<String> = query.suggestedQueryFields()

    fun setVerticalCatalogListsEnabled(enabled: Boolean) {
        localStore.setVerticalCatalogListsEnabled(enabled)
        _state.update {
            it.copy(
                verticalCatalogLists = enabled,
                status = if (enabled) "已切换为竖排列表" else "已切换为横向标签",
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securityPrefs.setBiometricEnabled(enabled)
        securityPrefs.setBiometricPromptSeen(true)
        _state.update {
            it.copy(
                biometricEnabled = enabled,
                status = if (enabled) "已启用生物识别锁定" else "已关闭生物识别锁定",
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        securityPrefs.setThemeMode(mode)
        _state.update {
            it.copy(
                themeMode = mode,
                status = when (mode) {
                    ThemeMode.System -> "主题：跟随系统"
                    ThemeMode.Light -> "主题：浅色"
                    ThemeMode.Dark -> "主题：深色"
                },
            )
        }
    }

    fun setIndexFlags(unique: Boolean? = null, sparse: Boolean? = null) = admin.setIndexFlags(unique, sparse)
    fun refreshIndexes() = admin.refreshIndexes()
    fun createIndex() = admin.createIndex()
    fun requestDropIndex(name: String) = admin.requestDropIndex(name)
    fun dropIndexConfirmed() = admin.dropIndexConfirmed()
    fun refreshServerOverview() = admin.refreshServerOverview()

    fun updateText(field: Field, value: String) {
        browse.updateText(field, value)
        query.updateText(field, value)
        admin.updateText(field, value)
        advanced.updateText(field, value)
    }


    fun refreshGridFs() = advanced.refreshGridFs()
    fun uploadGridFs() = advanced.uploadGridFs()
    fun downloadGridFs(fileId: String) = advanced.downloadGridFs(fileId)
    fun requestDeleteGridFs(fileId: String) = advanced.requestDeleteGridFs(fileId)
    fun deleteGridFsConfirmed() = advanced.deleteGridFsConfirmed()
    fun startChangeStream() = advanced.startChangeStream(viewModelScope)
    fun stopChangeStream() = advanced.stopChangeStream()
    fun refreshUsersAndRoles() = advanced.refreshUsersAndRoles()
    fun createUser() = advanced.createUser()
    fun requestDropUser(user: String) = advanced.requestDropUser(user)
    fun dropUserConfirmed() = advanced.dropUserConfirmed()
    fun createRole() = advanced.createRole()
    fun requestDropRole(role: String) = advanced.requestDropRole(role)
    fun dropRoleConfirmed() = advanced.dropRoleConfirmed()
    fun setImportDropBefore(checked: Boolean) = advanced.setImportDropBefore(checked)
    fun requestImport() = advanced.requestImport()
    fun importConfirmed() = advanced.importConfirmed()
    fun exportCollection() = advanced.exportCollection()
    fun setExportFormat(format: DocumentExportFormat) = advanced.setExportFormat(format)
    fun exportCollectionAsFile() = advanced.exportCollectionAsFile()
    fun prepareImportFromText(fileName: String, text: String) = advanced.prepareImportFromText(fileName, text)
    fun confirmMappedImport() = advanced.confirmMappedImport()
    fun refreshStagingQueue() = advanced.refreshStagingQueue()
    fun retryStagingItem(id: String) = advanced.retryStagingItem(id)
    fun discardStagingItem(id: String) = advanced.discardStagingItem(id)
    fun processStagingQueue() = advanced.processStagingQueue()
    fun saveOfflineSnapshot() = browse.saveOfflineSnapshot()
    fun refreshOfflineSnapshots() = browse.refreshOfflineSnapshots()
    fun openOfflineSnapshot(id: String) = browse.openOfflineSnapshot(id)
    fun deleteOfflineSnapshot(id: String) = browse.deleteOfflineSnapshot(id)
    fun clearActiveOfflineSnapshot() = browse.clearActiveOfflineSnapshot()
    fun exportCurrentPage(format: DocumentExportFormat) = browse.exportCurrentPage(format)
    fun restoreQueryHistory(id: String) = query.restoreQueryHistory(id)
    fun refreshQueryHistory() = query.refreshQueryHistory()
    fun refreshCurrentOps() = admin.refreshCurrentOps()
    fun requestKillOp(opId: String) = admin.requestKillOp(opId)
    fun killOpConfirmed() = admin.killOpConfirmed()
    fun setOpsCounterVisible(visible: Boolean) = admin.setOpsCounterVisible(visible, viewModelScope)
    fun loadCollectionValidator() = browse.loadCollectionValidator()
    fun applyCollectionValidator() = browse.applyCollectionValidator()
    fun setDocumentEditorMode(mode: DocumentEditorMode) = browse.setDocumentEditorMode(mode)
    fun applyCodeToTree() = browse.applyCodeToTree()
    fun toggleDocumentNode(pathKey: String) = browse.toggleDocumentNode(pathKey)
    fun beginEditDocumentNode(pathKey: String) = browse.beginEditDocumentNode(pathKey)
    fun dismissEditDocumentNode() = browse.dismissEditDocumentNode()
    fun commitDocumentLeafEdit(pathKey: String, type: DocValueType, scalar: String?) =
        browse.commitDocumentLeafEdit(pathKey, type, scalar)
    fun startBlankDocument() = browse.startBlankDocument()
    fun deleteDocumentNode(pathKey: String) = browse.deleteDocumentNode(pathKey)
    fun cloneDocumentNode(pathKey: String) = browse.cloneDocumentNode(pathKey)
    fun convertDocumentNodeType(pathKey: String, type: DocValueType) = browse.convertDocumentNodeType(pathKey, type)
    fun ensureDocumentObjectId() = browse.ensureDocumentObjectId()
    fun restoreDocumentDraft() = browse.restoreDraft()
    fun discardDocumentDraft() = browse.discardDraft()
    fun refreshAuditLog() = advanced.refreshAuditLog()
    fun clearAuditLog() = advanced.clearAuditLog()


    fun onAppForeground() {
        sessionHealth.onAppForeground(viewModelScope)
        val queued = runCatching { stagingStore.list() }.getOrDefault(emptyList())
        if (queued.isNotEmpty()) {
            _state.update { it.copy(stagingItems = queued) }
            advanced.processStagingQueue()
        }
    }
    fun dismissDisconnectNotice() = sessionHealth.dismissDisconnectNotice()
    fun reconnectManually() = sessionHealth.reconnectManually()

    override fun onCleared() {
        advanced.onCleared()
        admin.stopOpsCounterSampling(keepHistory = false)
        super.onCleared()
    }

    fun updateDestructiveConfirmInput(value: String) {
        _state.update { it.copy(destructiveConfirmInput = value) }
    }

    fun cancelDestructive() {
        _state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "") }
    }

    fun confirmDestructive() {
        val pending = _state.value.pendingDestructive ?: return
        val expected = pending.confirmToken.ifBlank { pending.target }
        if (
            pending.confirmMode == DestructiveConfirmMode.TypeName &&
            _state.value.destructiveConfirmInput != expected
        ) {
            _state.update { it.copy(error = "确认名称不匹配，已取消操作。") }
            return
        }
        when (pending.action) {
            DestructiveAction.DropDatabase -> browse.dropDatabaseConfirmed()
            DestructiveAction.DropCollection -> browse.dropCollectionConfirmed()
            DestructiveAction.DeleteMany -> browse.deleteManyConfirmed()
            DestructiveAction.DropIndex -> admin.dropIndexConfirmed()
            DestructiveAction.CompactCollection -> browse.compactCollectionConfirmed()
            DestructiveAction.DropUser -> advanced.dropUserConfirmed()
            DestructiveAction.DropRole -> advanced.dropRoleConfirmed()
            DestructiveAction.DropGridFsFile -> advanced.deleteGridFsConfirmed()
            DestructiveAction.ImportDropCollection -> advanced.importConfirmed()
            DestructiveAction.KillOp -> admin.killOpConfirmed()
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
        QuerySql,
        IndexKeys,
        IndexName,
        IndexExpire,
        GridFsBucket,
        GridFsUploadName,
        GridFsUploadContent,
        AuthDatabaseInput,
        CreateUserName,
        CreateUserPassword,
        CreateUserRolesJson,
        CreateRoleName,
        CreateRolePrivilegesJson,
        CreateRoleRolesJson,
        ImportJson,
        ExportLimit,
        ValidatorJsonInput,
        ValidationLevelInput,
        ValidationActionInput,
        DatabaseSearch,
        CollectionSearch,
        OfflineSnapshotName,
    }

    class Factory(
        private val connectionStore: MongoConnectionStore,
        private val localStore: LocalAppStore,
        private val draftStore: DocumentDraftStore,
        private val snapshotStore: OfflineSnapshotStore,
        private val stagingStore: StagingQueueStore,
        private val securityPrefs: SecurityPrefsStore,
        private val sessionManager: MongoSessionManager,
        private val repository: MongoAdminRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ClensViewModel(
                connectionStore,
                localStore,
                draftStore,
                snapshotStore,
                stagingStore,
                securityPrefs,
                sessionManager,
                repository,
            ) as T
        }
    }
}




