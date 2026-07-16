package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.CollectionSummary
import com.chloemlla.clens.core.mongo.DatabaseSummary
import com.chloemlla.clens.core.mongo.IndexSummary
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import com.chloemlla.clens.core.mongo.ServerOverview
import com.chloemlla.clens.core.mongo.GridFsFileSummary
import com.chloemlla.clens.core.mongo.MongoUserSummary
import com.chloemlla.clens.core.mongo.MongoRoleSummary
import com.chloemlla.clens.core.mongo.QueryHistoryEntry
import com.chloemlla.clens.core.mongo.QueryFavoriteEntry
import com.chloemlla.clens.core.mongo.VisualFilterClause
import com.chloemlla.clens.core.mongo.AuditLogEntry
import com.chloemlla.clens.core.mongo.CurrentOpSummary
import com.chloemlla.clens.core.mongo.CollectionValidatorInfo
import com.chloemlla.clens.core.mongo.OpsCounterSampleState
import com.chloemlla.clens.core.storage.ThemeMode
import com.chloemlla.clens.core.storage.OfflineSnapshotMeta
import com.chloemlla.clens.core.storage.StagingItem
import com.chloemlla.clens.core.export.DocumentExportFormat
import com.chloemlla.clens.ui.editor.DocumentEditorState

enum class ResultViewMode {
    Json,
    Table,
    Cards,
}

enum class QueryInputMode {
    Visual,
    Json,
    Sql,
}

enum class DestructiveAction {
    DropDatabase,
    DropCollection,
    DeleteMany,
    DropIndex,
    CompactCollection,
    DropUser,
    DropRole,
    DropGridFsFile,
    ImportDropCollection,
    KillOp,
}

enum class DestructiveConfirmMode {
    /** P0: user must type the exact target name. */
    TypeName,
    /** P1: user must long-press confirm for 3 seconds. */
    LongPress,
}

data class PendingDestructiveAction(
    val action: DestructiveAction,
    val target: String = "",
    val message: String,
    val confirmToken: String = target,
    val confirmMode: DestructiveConfirmMode = DestructiveConfirmMode.TypeName,
)

enum class ClensTab(val label: String) {
    Connections("连接"),
    Browse("浏览"),
    Query("查询"),
    Admin("管理"),
    Advanced("高级"),
    Settings("设置"),
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
    val readOnly: Boolean = false,
    val sshEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUsername: String = "",
    val sshPassword: String = "",
    val sshPrivateKeyPem: String = "",
    val sshPrivateKeyPassphrase: String = "",
    val sshRemoteHost: String = "",
    val sshRemotePort: String = "",
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
    val resultViewMode: ResultViewMode = ResultViewMode.Cards,
    val documents: List<String> = emptyList(),
    val documentCountHint: Long? = null,
    val documentSkip: Int = 0,
    val documentLimit: Int = 50,
    val browseFilterJson: String = "{}",
    val browseSortJson: String = "{\"_id\": 1}",
    val browseProjectionJson: String = "{}",
    val selectedDocumentJson: String = "",
    val editorJson: String = "{\n  \n}",
    val documentEditor: DocumentEditorState = DocumentEditorState(),
    val queryModeAggregate: Boolean = false,
    val queryFilterJson: String = "{}",
    val querySortJson: String = "{}",
    val queryProjectionJson: String = "{}",
    val queryPipelineJson: String = "[\n  { \"\$match\": {} }\n]",
    val queryInputMode: QueryInputMode = QueryInputMode.Visual,
    val queryVisualClauses: List<VisualFilterClause> = listOf(VisualFilterClause()),
    val querySqlInput: String = "SELECT * FROM users WHERE age > 18",
    val querySqlPreview: String = "",
    val querySqlLimit: Int? = null,
    val querySqlSkip: Int? = null,
    val querySqlGuideExpanded: Boolean = true,
    val queryFavoriteNameInput: String = "",
    val queryFavorites: List<QueryFavoriteEntry> = emptyList(),
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
    // Advanced tab
    val gridFsBucket: String = "fs",
    val gridFsFiles: List<GridFsFileSummary> = emptyList(),
    val gridFsUploadName: String = "upload.txt",
    val gridFsUploadContent: String = "",
    val gridFsDownloadContent: String = "",
    val gridFsError: String? = null,
    val changeStreamRunning: Boolean = false,
    val changeStreamFilterHint: String = "",
    val changeStreamEvents: List<String> = emptyList(),
    val changeStreamError: String? = null,
    val authDatabaseInput: String = "admin",
    val detailedUsers: List<MongoUserSummary> = emptyList(),
    val detailedUsersError: String? = null,
    val createUserName: String = "",
    val createUserPassword: String = "",
    val createUserRolesJson: String = "[{\"role\":\"readWrite\",\"db\":\"admin\"}]",
    val roles: List<MongoRoleSummary> = emptyList(),
    val rolesError: String? = null,
    val createRoleName: String = "",
    val createRolePrivilegesJson: String = "[]",
    val createRoleRolesJson: String = "[]",
    val importJson: String = "[]",
    val importDropBefore: Boolean = false,
    val exportLimit: String = "200",
    val exportJson: String = "",
    val exportFormat: DocumentExportFormat = DocumentExportFormat.JSON,
    val exportFilePath: String = "",
    val importSourceName: String = "",
    val importMappingPreview: List<String> = emptyList(),
    val offlineSnapshots: List<OfflineSnapshotMeta> = emptyList(),
    val activeSnapshotId: String? = null,
    val offlineSnapshotNameInput: String = "",
    val stagingItems: List<StagingItem> = emptyList(),
    val queryHistory: List<QueryHistoryEntry> = emptyList(),
    val auditLog: List<AuditLogEntry> = emptyList(),
    val currentOps: List<CurrentOpSummary> = emptyList(),
    val currentOpsListError: String? = null,
    val opsCounterState: OpsCounterSampleState? = null,
    val opsCounterSampling: Boolean = false,
    val opsCounterError: String? = null,
    val collectionValidator: CollectionValidatorInfo? = null,
    val collectionValidatorError: String? = null,
    val validatorJsonInput: String = "{}",
    val validationLevelInput: String = "strict",
    val validationActionInput: String = "error",
    val connectedReadOnly: Boolean = false,
    val verticalCatalogLists: Boolean = false,
    val databaseSearchQuery: String = "",
    val collectionSearchQuery: String = "",
    val biometricEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val connectionHealthy: Boolean = true,
    val reconnecting: Boolean = false,
    val disconnectNotice: String? = null,
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
    val writesBlocked: Boolean
        get() = connectedReadOnly || isSelectedView
}
