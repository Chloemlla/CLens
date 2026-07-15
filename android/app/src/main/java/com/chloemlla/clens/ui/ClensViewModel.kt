package com.chloemlla.clens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.clens.core.mongo.MongoAdminRepository
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import com.chloemlla.clens.core.mongo.MongoSessionManager
import com.chloemlla.clens.core.storage.MongoConnectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ClensViewModel(
    connectionStore: MongoConnectionStore,
    sessionManager: MongoSessionManager,
    repository: MongoAdminRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClensUiState())
    val state: StateFlow<ClensUiState> = _state.asStateFlow()

    private val actions = ClensActionRunner(viewModelScope, _state)
    private val ctx = ClensSessionContext(
        state = _state,
        connectionStore = connectionStore,
        sessionManager = sessionManager,
        repository = repository,
        actions = actions,
    )
    private val connections = ConnectionController(ctx)
    private val browse = BrowseController(ctx)
    private val query = QueryController(ctx)
    private val admin = AdminController(ctx)

    init {
        ctx.refreshProfiles(status = "连接配置已加载")
    }

    fun selectTab(tab: ClensTab) {
        _state.update { it.copy(selectedTab = tab) }
        when (tab) {
            ClensTab.Browse -> if (_state.value.isConnected && _state.value.databases.isEmpty()) browse.refreshDatabases()
            ClensTab.Admin -> if (_state.value.isConnected) admin.refreshServerOverview()
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
    fun disconnect() = connections.disconnect()

    fun updateSelectedDatabase(value: String) = browse.updateSelectedDatabase(value)
    fun updateSelectedCollection(value: String) = browse.updateSelectedCollection(value)
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
                browse.dropDatabaseConfirmed()
            }
            DestructiveAction.DropCollection -> browse.dropCollectionConfirmed()
            DestructiveAction.DeleteMany -> browse.deleteManyConfirmed()
            DestructiveAction.DropIndex -> admin.dropIndexConfirmed()
            DestructiveAction.CompactCollection -> browse.compactCollectionConfirmed()
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
