package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.CollectionSummary
import com.chloemlla.clens.core.mongo.DatabaseSummary
import com.chloemlla.clens.core.mongo.IndexSummary
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import com.chloemlla.clens.core.mongo.ServerOverview

enum class ResultViewMode {
    Json,
    Table,
}

enum class DestructiveAction {
    DropDatabase,
    DropCollection,
    DeleteMany,
    DropIndex,
    CompactCollection,
}

data class PendingDestructiveAction(
    val action: DestructiveAction,
    val target: String = "",
    val message: String,
)

enum class ClensTab(val label: String) {
    Connections("连接"),
    Browse("浏览"),
    Query("查询"),
    Admin("管理"),
}

data class ConnectionFormState(
    val id: String? = null,
    val name: String = "",
    val useUri: Boolean = true,
    val uri: String = "",
    val host: String = "127.0.0.1",
    val port: String = "27017",
    val username: String = "",
    val password: String = "",
    val authDatabase: String = "admin",
    val defaultDatabase: String = "",
    val replicaSet: String = "",
    val tls: Boolean = false,
    val directConnection: Boolean = true,
)

data class ClensUiState(
    val selectedTab: ClensTab = ClensTab.Connections,
    val profiles: List<MongoConnectionProfile> = emptyList(),
    val activeProfileId: String? = null,
    val connectedProfileId: String? = null,
    val connectionForm: ConnectionFormState = ConnectionFormState(),
    val editingConnection: Boolean = false,
    val databases: List<DatabaseSummary> = emptyList(),
    val collections: List<CollectionSummary> = emptyList(),
    val selectedDatabase: String = "",
    val selectedCollection: String = "",
    val newDatabaseName: String = "",
    val newCollectionName: String = "",
    val renameCollectionName: String = "",
    val databaseStatsJson: String = "",
    val databaseStatsError: String? = null,
    val selectedCollectionStats: CollectionSummary? = null,
    val collectionStatsError: String? = null,
    val maintenanceResultJson: String = "",
    val resultViewMode: ResultViewMode = ResultViewMode.Json,
    val documents: List<String> = emptyList(),
    val documentCountHint: Long? = null,
    val documentSkip: Int = 0,
    val documentLimit: Int = 50,
    val browseFilterJson: String = "{}",
    val browseSortJson: String = "{\"_id\": 1}",
    val browseProjectionJson: String = "{}",
    val selectedDocumentJson: String = "",
    val editorJson: String = "{\n  \n}",
    val queryModeAggregate: Boolean = false,
    val queryFilterJson: String = "{}",
    val querySortJson: String = "{}",
    val queryProjectionJson: String = "{}",
    val queryPipelineJson: String = "[\n  { \"\$match\": {} }\n]",
    val queryResults: List<String> = emptyList(),
    val explainJson: String = "",
    val queryDurationMillis: Long? = null,
    val indexes: List<IndexSummary> = emptyList(),
    val indexKeysJson: String = "{ \"field\": 1 }",
    val indexName: String = "",
    val indexUnique: Boolean = false,
    val indexSparse: Boolean = false,
    val indexExpireAfterSeconds: String = "",
    val serverOverview: ServerOverview? = null,
    val users: List<String> = emptyList(),
    val usersError: String? = null,
    val currentOpsJson: String = "",
    val currentOpsError: String? = null,
    val pendingDestructive: PendingDestructiveAction? = null,
    val destructiveConfirmInput: String = "",
    val cleartextWarning: String? = null,
    val loading: Boolean = false,
    val status: String = "",
    val error: String? = null,
) {
    val isConnected: Boolean = connectedProfileId != null
    val activeProfile: MongoConnectionProfile? = profiles.firstOrNull { it.id == activeProfileId }
    val connectedProfile: MongoConnectionProfile? = profiles.firstOrNull { it.id == connectedProfileId }
    val selectedCollectionType: String
        get() = collections.firstOrNull { it.name == selectedCollection }?.type ?: "collection"
    val isSelectedView: Boolean
        get() = selectedCollectionType.equals("view", ignoreCase = true)
}
