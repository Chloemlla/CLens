package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.MongoAdminException
import com.chloemlla.clens.core.mongo.MongoAdminRepository
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import com.chloemlla.clens.core.mongo.MongoSessionManager
import com.chloemlla.clens.core.mongo.SessionHealthCallbacks
import com.chloemlla.clens.core.mongo.SessionHealthMonitor
import com.chloemlla.clens.core.storage.MongoConnectionStore
import com.chloemlla.clens.core.storage.LocalAppStore
import com.chloemlla.clens.core.storage.DocumentDraftStore
import com.chloemlla.clens.core.storage.OfflineSnapshotStore
import com.chloemlla.clens.core.storage.StagingQueueStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

class ClensSessionContext(
    val state: MutableStateFlow<ClensUiState>,
    val connectionStore: MongoConnectionStore,
    val localStore: LocalAppStore,
    val draftStore: DocumentDraftStore,
    val snapshotStore: OfflineSnapshotStore,
    val stagingStore: StagingQueueStore,
    val sessionManager: MongoSessionManager,
    val repository: MongoAdminRepository,
    val actions: ClensActionRunner,
) {
    fun refreshLocalLists() {
        state.update {
            it.copy(
                queryHistory = localStore.listQueryHistory(),
                queryFavorites = localStore.listQueryFavorites(),
                auditLog = localStore.listAuditLog(),
                verticalCatalogLists = localStore.isVerticalCatalogListsEnabled(),
                offlineSnapshots = snapshotStore.list(connectionId = state.value.connectedProfileId),
                stagingItems = stagingStore.list(),
            )
        }
    }

    fun recordAudit(action: String, target: String, detail: String = "") {
        localStore.addAudit(action, target, detail)
        refreshLocalLists()
    }

    fun ensureWritable(operation: String) {
        if (state.value.connectedReadOnly) {
            throw MongoAdminException.Validation("当前连接为只读模式，已阻止：" + operation)
        }
    }
    fun refreshProfiles(status: String? = null) {
        val profiles = connectionStore.listProfiles()
        val activeId = connectionStore.getActiveProfileId()
        state.update {
            it.copy(
                profiles = profiles,
                activeProfileId = activeId,
                status = status ?: it.status,
                error = null,
            )
        }
    }

    fun formToProfile(): MongoConnectionProfile {
        val form = state.value.connectionForm
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
            readOnly = form.readOnly,
        )
    }

    fun extractIdFilter(documentJson: String): String? {
        return runCatching {
            val obj = org.json.JSONObject(documentJson)
            if (!obj.has("_id")) return null
            org.json.JSONObject().put("_id", obj.get("_id")).toString()
        }.getOrNull()
    }
}

class ConnectionController(
    private val ctx: ClensSessionContext,
) {
    private val state get() = ctx.state
    private val connectionStore get() = ctx.connectionStore
    private val sessionManager get() = ctx.sessionManager

    fun updateConnectionForm(transform: (ConnectionFormState) -> ConnectionFormState) {
        state.update {
            val form = transform(it.connectionForm)
            it.copy(
                connectionForm = form,
                editingConnection = true,
                cleartextWarning = CleartextRisk.forForm(form),
            )
        }
    }

    fun startCreateConnection() {
        state.update {
            it.copy(
                editingConnection = true,
                connectionForm = ConnectionFormState(),
                status = "填写连接信息",
                error = null,
            )
        }
    }

    fun startEditConnection(profile: MongoConnectionProfile) {
        state.update {
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
                    readOnly = profile.readOnly,
                ),
                status = "编辑连接：" + profile.name,
                error = null,
                cleartextWarning = CleartextRisk.forProfile(profile),
            )
        }
    }

    fun cancelEditConnection() {
        state.update { it.copy(editingConnection = false, connectionForm = ConnectionFormState(), cleartextWarning = null) }
    }

    fun saveConnection() {
        ctx.actions.run("保存连接") {
            val form = state.value.connectionForm
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
                readOnly = form.readOnly,
            )
            connectionStore.upsert(profile)
            ctx.refreshProfiles(status = "已保存连接 " + profile.name)
            state.update { it.copy(editingConnection = false, connectionForm = ConnectionFormState()) }
        }
    }

    fun deleteConnection(profileId: String) {
        ctx.actions.run("删除连接") {
            if (state.value.connectedProfileId == profileId) {
                sessionManager.disconnect()
            }
            connectionStore.delete(profileId)
            ctx.refreshProfiles(status = "连接已删除")
            state.update {
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
        ctx.refreshProfiles(status = "已设为默认连接")
    }

    fun testConnection(profile: MongoConnectionProfile? = null) {
        ctx.actions.run("测试连接") {
            val target = profile ?: ctx.formToProfile()
            val result = sessionManager.test(target)
            state.update {
                it.copy(
                    status = result.message,
                    error = null,
                    cleartextWarning = CleartextRisk.forProfile(target),
                )
            }
        }
    }

    fun connect(profile: MongoConnectionProfile? = null, onConnected: () -> Unit) {
        ctx.actions.run("建立连接") {
            val target = profile ?: ctx.formToProfile().also { connectionStore.upsert(it) }
            val result = sessionManager.connect(target)
            connectionStore.setActiveProfileId(target.id)
            ctx.refreshProfiles()
            state.update {
                it.copy(
                    connectedProfileId = target.id,
                    selectedDatabase = target.defaultDatabase,
                    status = result.message,
                    error = null,
                    selectedTab = ClensTab.Browse,
                    cleartextWarning = CleartextRisk.forProfile(target),
                    connectedReadOnly = target.readOnly,
                    connectionHealthy = true,
                    reconnecting = false,
                    disconnectNotice = null,
                )
            }
            onConnected()
        }
    }

    fun disconnect() {
        ctx.actions.run("断开连接") {
            sessionManager.disconnect()
            state.update {
                it.copy(
                    connectedProfileId = null,
                    databases = emptyList(),
                    collections = emptyList(),
                    documents = emptyList(),
                    indexes = emptyList(),
                    serverOverview = null,
                    users = emptyList(),
                    usersError = null,
                    currentOpsJson = "",
                    currentOpsError = null,
                    opsCounterState = null,
                    opsCounterSampling = false,
                    opsCounterError = null,
                    status = "已断开连接",
                    error = null,
                    cleartextWarning = null,
                    connectedReadOnly = false,
                    connectionHealthy = true,
                    reconnecting = false,
                    disconnectNotice = null,
                )
            }
        }
    }
}

class SessionHealthController(
    private val ctx: ClensSessionContext,
) {
    private val state get() = ctx.state
    private val sessionManager get() = ctx.sessionManager
    private val monitor = SessionHealthMonitor(sessionManager)

    private val callbacks = object : SessionHealthCallbacks {
        override fun onHealthOk() {
            state.update {
                it.copy(
                    connectionHealthy = true,
                    reconnecting = false,
                    disconnectNotice = null,
                )
            }
        }

        override fun onHealthFailed(message: String) {
            state.update {
                it.copy(
                    connectionHealthy = false,
                    disconnectNotice = "连接似乎已中断：$message",
                )
            }
        }

        override fun onReconnectStarted(attempt: Int, maxAttempts: Int) {
            state.update {
                it.copy(
                    reconnecting = true,
                    connectionHealthy = false,
                    disconnectNotice = "正在自动重连（$attempt/$maxAttempts）…",
                )
            }
        }

        override fun onReconnectSucceeded(message: String) {
            state.update {
                it.copy(
                    connectionHealthy = true,
                    reconnecting = false,
                    disconnectNotice = null,
                    status = message.ifBlank { "已重新连接" },
                    error = null,
                    connectedProfileId = sessionManager.activeProfile?.id ?: it.connectedProfileId,
                    connectedReadOnly = sessionManager.activeProfile?.readOnly ?: it.connectedReadOnly,
                )
            }
        }

        override fun onReconnectFailed(message: String) {
            state.update {
                it.copy(
                    connectionHealthy = false,
                    reconnecting = true,
                    disconnectNotice = "重连未成功：$message",
                )
            }
        }

        override fun onReconnectExhausted(message: String) {
            state.update {
                it.copy(
                    connectionHealthy = false,
                    reconnecting = false,
                    disconnectNotice = message,
                    // Keep connectedProfileId so the user can still tap reconnect against the last profile.
                )
            }
        }
    }

    /**
     * Foreground / resume entry: ping active session and reconnect gently on failure.
     * Runs outside the main action mutex so resume does not flash global loading.
     * Banner state carries the mild notice (no error toast spam).
     */
    fun onAppForeground(scope: CoroutineScope) {
        val uiConnected = state.value.isConnected
        val hasSession = sessionManager.isConnected || sessionManager.activeProfile != null
        if (!uiConnected && !hasSession) return
        if (state.value.reconnecting) return
        scope.launch {
            runCatching {
                monitor.ensureHealthyOrReconnect(callbacks)
            }.onFailure { error ->
                val message = error.message?.takeIf { it.isNotBlank() } ?: "会话检查失败"
                state.update {
                    it.copy(
                        connectionHealthy = false,
                        reconnecting = false,
                        disconnectNotice = "连接似乎已中断：$message",
                    )
                }
            }
        }
    }

    fun dismissDisconnectNotice() {
        state.update { it.copy(disconnectNotice = null) }
    }

    fun reconnectManually() {
        val profile = sessionManager.activeProfile
            ?: state.value.connectedProfile
            ?: state.value.activeProfile
        if (profile == null) {
            state.update {
                it.copy(
                    disconnectNotice = "没有可重连的连接配置",
                    reconnecting = false,
                    connectionHealthy = false,
                )
            }
            return
        }
        ctx.actions.run("手动重连", silent = true) {
            state.update {
                it.copy(
                    reconnecting = true,
                    connectionHealthy = false,
                    disconnectNotice = "正在重新连接…",
                )
            }
            // Ensure profileRef is populated even if client already dropped.
            val result = sessionManager.connect(profile)
            state.update {
                it.copy(
                    connectedProfileId = profile.id,
                    connectionHealthy = true,
                    reconnecting = false,
                    disconnectNotice = null,
                    status = result.message.ifBlank { "已重新连接" },
                    error = null,
                    connectedReadOnly = profile.readOnly,
                )
            }
        }
    }
}

