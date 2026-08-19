package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.ConnectionHealthScore
import com.chloemlla.clens.core.mongo.ConnectionTestResult
import com.chloemlla.clens.core.mongo.MongoAdminException
import com.chloemlla.clens.core.mongo.MongoAdminRepository
import com.chloemlla.clens.core.mongo.MongoConnectionProfile
import com.chloemlla.clens.core.mongo.MongoUriBuilder
import com.chloemlla.clens.core.mongo.LocalNetworkAccess
import com.chloemlla.clens.core.mongo.LocalNetworkPermission
import com.chloemlla.clens.core.mongo.MongoSessionManager
import com.chloemlla.clens.core.mongo.SshTunnelSession
import com.chloemlla.clens.core.mongo.SessionHealthCallbacks
import com.chloemlla.clens.core.mongo.SessionHealthMonitor
import com.chloemlla.clens.core.storage.MongoConnectionStore
import com.chloemlla.clens.core.storage.LocalAppStore
import com.chloemlla.clens.core.storage.DocumentDraftStore
import com.chloemlla.clens.core.storage.OpsCounterArchiveStore
import com.chloemlla.clens.core.storage.OfflineSnapshotStore
import com.chloemlla.clens.core.storage.StagingQueueStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

class ClensSessionContext(
    val state: MutableStateFlow<ClensUiState>,
    val appContext: android.content.Context,
    val connectionStore: MongoConnectionStore,
    val localStore: LocalAppStore,
    val draftStore: DocumentDraftStore,
    val opsArchiveStore: OpsCounterArchiveStore,
    val snapshotStore: OfflineSnapshotStore,
    val stagingStore: StagingQueueStore,
    val sessionManager: MongoSessionManager,
    val repository: MongoAdminRepository,
    val actions: ClensActionRunner,
    val sessionHealth: SessionHealthController,
) {
    fun refreshLocalLists(resetSqlGuideState: Boolean = false) {
        state.update { current ->
            current.copy(
                // Queries carry the connection that created them. Do not offer an entry
                // from profile A while connected to profile B: identical db/collection
                // names can point at different data.
                queryHistory = localStore.listQueryHistory()
                    .filter { it.connectionId == current.connectedProfileId },
                queryFavorites = localStore.listQueryFavorites()
                    .filter { it.connectionId == current.connectedProfileId },
                aggregateTemplates = localStore.listAggregateTemplates(),
                auditLog = localStore.listAuditLog(),
                verticalCatalogLists = localStore.isVerticalCatalogListsEnabled(),
                // Only initialize this persisted preference during app bootstrap. Later
                // list refreshes (e.g. saving a query) must not undo a user's in-session
                // decision to re-open the guide.
                querySqlGuideExpanded = if (resetSqlGuideState) {
                    !localStore.isSqlGuideSeen()
                } else {
                    current.querySqlGuideExpanded
                },
                offlineSnapshots = snapshotStore.list(connectionId = current.connectedProfileId),
                stagingItems = stagingStore.list(),
            )
        }
    }

    /**
     * Reloads latency state for all profiles from LocalAppStore.
     */
    fun refreshLatencyState() {
        val latencyMap = mutableMapOf<String, Long>()
        val staleMap = mutableMapOf<String, Boolean>()
        for (profile in state.value.profiles) {
            val m = localStore.getLatencyMs(profile.id)
            if (m != null) {
                latencyMap[profile.id] = m.latencyMs
                staleMap[profile.id] = localStore.isLatencyStale(profile.id)
            }
        }
        state.update {
            it.copy(
                connectionLatencyMs = latencyMap,
                connectionLatencyStale = staleMap,
            )
        }
    }

    /**
     * Stores a new latency measurement and marks it fresh (not stale).
     */
    fun storeLatencyMs(connectionId: String, latencyMs: Long) {
        localStore.setLatencyMs(connectionId, latencyMs)
        state.update {
            it.copy(
                connectionLatencyMs = it.connectionLatencyMs + (connectionId to latencyMs),
                connectionLatencyStale = it.connectionLatencyStale + (connectionId to false),
            )
        }
    }

    /**
     * Sets the measuring-in-progress flag for [connectionId].
     */
    fun setMeasuringLatency(connectionId: String, measuring: Boolean) {
        state.update {
            it.copy(measuringLatency = it.measuringLatency + (connectionId to measuring))
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

    fun ensureLocalNetworkAllowed(profile: MongoConnectionProfile) {
        if (!LocalNetworkAccess.requiresLocalNetworkPermission(profile)) return
        if (LocalNetworkPermission.isGranted(appContext)) return
        throw MongoAdminException.Validation(LocalNetworkPermission.deniedMessage())
    }

    fun requestLocalNetworkIfNeeded(
        profile: MongoConnectionProfile,
        mode: LocalNetworkPermissionMode,
    ): Boolean {
        if (!LocalNetworkAccess.requiresLocalNetworkPermission(profile)) return false
        if (LocalNetworkPermission.isGranted(appContext)) return false
        state.update {
            it.copy(pendingLocalNetworkPermission = PendingLocalNetworkPermission(profile, mode))
        }
        return true
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
        val sshPort = if (form.sshEnabled) {
            form.sshPort.toIntOrNull()
                ?: throw MongoAdminException.Validation("SSH 端口必须是数字。")
        } else {
            form.sshPort.toIntOrNull() ?: 22
        }
        val sshRemotePort = form.sshRemotePort.trim()
            .takeIf { it.isNotBlank() }
            ?.toIntOrNull()
            ?: 0
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
            tlsCaPem = form.tlsCaPem,
            tlsClientCertPem = form.tlsClientCertPem,
            tlsClientKeyPem = form.tlsClientKeyPem,
            tlsClientKeyPassphrase = form.tlsClientKeyPassphrase,
            directConnection = form.directConnection,
            readOnly = form.readOnly,
            sshEnabled = form.sshEnabled,
            sshHost = form.sshHost.trim(),
            sshPort = sshPort,
            sshUsername = form.sshUsername.trim(),
            sshPassword = form.sshPassword,
            sshPrivateKeyPem = form.sshPrivateKeyPem,
            sshPrivateKeyPassphrase = form.sshPrivateKeyPassphrase,
            sshRemoteHost = form.sshRemoteHost.trim(),
            sshRemotePort = sshRemotePort,
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
                    tlsCaPem = profile.tlsCaPem,
                    tlsClientCertPem = profile.tlsClientCertPem,
                    tlsClientKeyPem = profile.tlsClientKeyPem,
                    tlsClientKeyPassphrase = profile.tlsClientKeyPassphrase,
                    directConnection = profile.directConnection,
                    readOnly = profile.readOnly,
                    sshEnabled = profile.sshEnabled,
                    sshHost = profile.sshHost,
                    sshPort = profile.sshPort.toString(),
                    sshUsername = profile.sshUsername,
                    sshPassword = profile.sshPassword,
                    sshPrivateKeyPem = profile.sshPrivateKeyPem,
                    sshPrivateKeyPassphrase = profile.sshPrivateKeyPassphrase,
                    sshRemoteHost = profile.sshRemoteHost,
                    sshRemotePort = if (profile.sshRemotePort > 0) profile.sshRemotePort.toString() else "",
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
            val sshPort = if (form.sshEnabled) {
                form.sshPort.toIntOrNull()
                    ?: throw MongoAdminException.Validation("SSH 端口必须是数字。")
            } else {
                form.sshPort.toIntOrNull() ?: 22
            }
            val sshRemotePort = form.sshRemotePort.trim()
                .takeIf { it.isNotBlank() }
                ?.toIntOrNull()
                ?: 0
            if (form.sshEnabled && form.sshRemotePort.isNotBlank() && sshRemotePort !in 1..65535) {
                throw MongoAdminException.Validation("SSH 远程端口必须在 1-65535。")
            }
            val profile = MongoConnectionProfile(
                id = form.id ?: UUID.randomUUID().toString(),
                name = form.name,
                uri = if (form.useUri) form.uri.trim() else "",
                host = if (form.useUri) {
                    MongoUriBuilder.parseUriToFormFields(form.uri)?.host?.takeIf { it.isNotBlank() } ?: ""
                } else {
                    form.host.trim()
                },
                port = port,
                username = form.username.trim(),
                password = form.password,
                authDatabase = form.authDatabase.trim().ifBlank { "admin" },
                defaultDatabase = form.defaultDatabase.trim(),
                replicaSet = form.replicaSet.trim(),
                tls = form.tls,
                tlsCaPem = form.tlsCaPem,
                tlsClientCertPem = form.tlsClientCertPem,
                tlsClientKeyPem = form.tlsClientKeyPem,
                tlsClientKeyPassphrase = form.tlsClientKeyPassphrase,
                directConnection = form.directConnection,
                readOnly = form.readOnly,
                sshEnabled = form.sshEnabled,
                sshHost = form.sshHost.trim(),
                sshPort = sshPort,
                sshUsername = form.sshUsername.trim(),
                sshPassword = form.sshPassword,
                sshPrivateKeyPem = form.sshPrivateKeyPem,
                sshPrivateKeyPassphrase = form.sshPrivateKeyPassphrase,
                sshRemoteHost = form.sshRemoteHost.trim(),
                sshRemotePort = sshRemotePort,
            )
            if (profile.sshEnabled) {
                SshTunnelSession.validate(profile)
            }
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
        val target = profile ?: ctx.formToProfile()
        if (ctx.requestLocalNetworkIfNeeded(target, LocalNetworkPermissionMode.Test)) return
        ctx.actions.run("测试连接") {
            ctx.ensureLocalNetworkAllowed(target)
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
        // Resolve first; persist form-only profiles only after permission allows the attempt.
        val target = profile ?: ctx.formToProfile()
        if (ctx.requestLocalNetworkIfNeeded(target, LocalNetworkPermissionMode.Connect)) return
        ctx.actions.run("建立连接") {
            ctx.ensureLocalNetworkAllowed(target)
            if (profile == null) {
                connectionStore.upsert(target)
            }
            val result = sessionManager.connect(target)
            connectionStore.setActiveProfileId(target.id)
            ctx.refreshProfiles()
            ctx.sessionHealth.bindHealthDataOnConnect(target.id, result)
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
                    healthScore = ctx.sessionHealth.getCurrentHealthScore(),
                )
            }
            // Query history/favorites are profile scoped. Refresh them immediately so
            // a previous connection's entries cannot be restored during this session.
            ctx.refreshLocalLists()
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

    /**
     * On-demand latency measurement for a connected profile.
     * Shows spinner during measurement; stores result on success.
     */
    fun measureConnectionLatency(profileId: String) {
        if (state.value.connectedProfileId != profileId) return
        ctx.setMeasuringLatency(profileId, true)
        ctx.actions.run("测量延迟") {
            try {
                val result = sessionManager.healthPing()
                if (result.ok) {
                    ctx.storeLatencyMs(profileId, result.latencyMillis)
                    state.update { it.copy(status = "延迟 ${result.latencyMillis}ms", error = null) }
                } else {
                    state.update { it.copy(error = "延迟测量失败") }
                }
            } finally {
                ctx.setMeasuringLatency(profileId, false)
            }
        }
    }
}

class SessionHealthController(
    private val sessionManager: MongoSessionManager,
) {
    lateinit var ctx: ClensSessionContext

    private val state get() = ctx.state
    private val monitor = SessionHealthMonitor(sessionManager)

    private val callbacks = object : SessionHealthCallbacks {
        override fun onHealthOk() {
            updateHealthState(healthy = true, notice = null)
        }

        override fun onHealthFailed(message: String) {
            monitor.recordOpResult(success = false)
            persistHealthData()
            updateHealthState(healthy = false, notice = "连接似乎已中断：$message")
        }

        override fun onReconnectStarted(attempt: Int, maxAttempts: Int) {
            updateHealthState(
                healthy = false,
                reconnecting = true,
                notice = "正在自动重连（$attempt/$maxAttempts）…",
            )
        }

        override fun onReconnectSucceeded(message: String) {
            monitor.recordOpResult(success = true)
            persistHealthData()
            updateHealthState(
                healthy = true,
                reconnecting = false,
                notice = null,
                status = message.ifBlank { "已重新连接" },
            )
        }

        override fun onReconnectFailed(message: String) {
            monitor.recordOpResult(success = false)
            persistHealthData()
            updateHealthState(
                healthy = false,
                reconnecting = true,
                notice = "重连未成功：$message",
            )
        }

        override fun onReconnectExhausted(message: String) {
            updateHealthState(
                healthy = false,
                reconnecting = false,
                notice = message,
            )
        }
    }

    private fun updateHealthState(
        healthy: Boolean? = null,
        reconnecting: Boolean? = null,
        notice: String? = null,
        status: String? = null,
    ) {
        val score = monitor.computeHealthScore()
        state.update {
            it.copy(
                connectionHealthy = healthy ?: it.connectionHealthy,
                reconnecting = reconnecting ?: it.reconnecting,
                disconnectNotice = notice ?: it.disconnectNotice,
                healthScore = score,
                status = status ?: it.status,
                error = null,
                connectedProfileId = sessionManager.activeProfile?.id ?: it.connectedProfileId,
                connectedReadOnly = sessionManager.activeProfile?.readOnly ?: it.connectedReadOnly,
            )
        }
    }

    fun bindHealthData(connectionId: String) {
        val saved = ctx.localStore.getConnectionHealthData(connectionId)
        monitor.bindConnection(connectionId, saved)
        state.update { it.copy(healthScore = monitor.computeHealthScore()) }
    }

    fun recordLatencyAndScore(latencyMs: Long) {
        monitor.recordLatencySample(latencyMs)
        monitor.recordOpResult(success = true)
        persistHealthData()
        state.update { it.copy(healthScore = monitor.computeHealthScore()) }
    }

    fun recordOpResult(success: Boolean) {
        monitor.recordOpResult(success)
        persistHealthData()
        state.update { it.copy(healthScore = monitor.computeHealthScore()) }
    }

    fun getCurrentHealthScore(): ConnectionHealthScore? = monitor.computeHealthScore()

    fun bindHealthDataOnConnect(connectionId: String, result: ConnectionTestResult) {
        val saved = ctx.localStore.getConnectionHealthData(connectionId)
        monitor.bindConnection(connectionId, saved)
        if (result.ok) {
            monitor.recordLatencySample(result.latencyMillis)
            monitor.recordOpResult(success = true)
            ctx.storeLatencyMs(connectionId, result.latencyMillis)
        } else {
            monitor.recordOpResult(success = false)
        }
        persistHealthData()
        state.update { it.copy(healthScore = monitor.computeHealthScore()) }
    }

    fun clearHealthHistory() {
        monitor.resetHealthData()
        val connId = state.value.connectedProfileId ?: return
        ctx.localStore.clearConnectionHealthData(connId)
        state.update { it.copy(healthScore = monitor.computeHealthScore()) }
    }

    fun showHealthDetailSheet() {
        state.update { it.copy(showHealthDetailSheet = true) }
    }

    fun hideHealthDetailSheet() {
        state.update { it.copy(showHealthDetailSheet = false) }
    }

    fun refreshHealthMeasurement() {
        val connId = state.value.connectedProfileId ?: return
        ctx.setMeasuringLatency(connId, true)
        ctx.actions.run("测量延迟") {
            try {
                val result = sessionManager.healthPing()
                if (result.ok) {
                    recordLatencyAndScore(result.latencyMillis)
                    ctx.storeLatencyMs(connId, result.latencyMillis)
                } else {
                    recordOpResult(success = false)
                }
            } finally {
                ctx.setMeasuringLatency(connId, false)
            }
        }
    }

    private fun persistHealthData() {
        val data = monitor.getHealthData()
        if (data.connectionId.isBlank()) return
        ctx.localStore.saveConnectionHealthData(data)
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
        if (ctx.requestLocalNetworkIfNeeded(profile, LocalNetworkPermissionMode.Reconnect)) return
        ctx.actions.run("手动重连", silent = true) {
            ctx.ensureLocalNetworkAllowed(profile)
            state.update {
                it.copy(
                    reconnecting = true,
                    connectionHealthy = false,
                    disconnectNotice = "正在重新连接…",
                )
            }
            // Ensure profileRef is populated even if client already dropped.
            val result = sessionManager.connect(profile)
            bindHealthDataOnConnect(profile.id, result)
            state.update {
                it.copy(
                    connectedProfileId = profile.id,
                    connectionHealthy = true,
                    reconnecting = false,
                    disconnectNotice = null,
                    status = result.message.ifBlank { "已重新连接" },
                    error = null,
                    connectedReadOnly = profile.readOnly,
                    healthScore = monitor.computeHealthScore(),
                )
            }
        }
    }
}
