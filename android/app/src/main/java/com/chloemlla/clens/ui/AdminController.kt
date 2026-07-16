package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.OpsCounterSampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

class AdminController(
    private val ctx: ClensSessionContext,
) {
    private val state get() = ctx.state
    private val repository get() = ctx.repository
    private val sampler = OpsCounterSampler()
    private var samplingJob: Job? = null
    private var samplingVisible: Boolean = false

    fun setIndexFlags(unique: Boolean? = null, sparse: Boolean? = null) {
        state.update {
            it.copy(
                indexUnique = unique ?: it.indexUnique,
                indexSparse = sparse ?: it.indexSparse,
            )
        }
    }

    fun updateText(field: ClensViewModel.Field, value: String) {
        state.update { current ->
            when (field) {
                ClensViewModel.Field.IndexKeys -> current.copy(indexKeysJson = value)
                ClensViewModel.Field.IndexName -> current.copy(indexName = value)
                ClensViewModel.Field.IndexExpire -> current.copy(indexExpireAfterSeconds = value)
                else -> current
            }
        }
    }

    fun refreshIndexes() {
        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持索引管理。", indexes = emptyList()) }
            return
        }
        ctx.actions.run("刷新索引") {
            loadIndexes()
        }
    }

    fun createIndex() {
        ctx.ensureWritable("创建索引")
        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持创建索引。") }
            return
        }
        ctx.actions.run("创建索引") {
            val current = state.value
            val expire = current.indexExpireAfterSeconds.trim()
                .takeIf { it.isNotBlank() }
                ?.toLongOrNull()
            val name = repository.createIndex(
                database = current.selectedDatabase,
                collection = current.selectedCollection,
                keysJson = current.indexKeysJson,
                name = current.indexName,
                unique = current.indexUnique,
                sparse = current.indexSparse,
                expireAfterSeconds = expire,
            )
            loadIndexes()
            state.update { it.copy(status = "索引已创建：" + name) }
        }
    }

    fun requestDropIndex(name: String) {
        ctx.ensureWritable("删除索引")
        if (state.value.isSelectedView) {
            state.update { it.copy(error = "视图不支持删除索引。") }
            return
        }
        if (name.isBlank() || name == "_id_") return
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.DropIndex,
                    target = name,
                    message = "将永久删除索引 `" + name + "`。此操作不可恢复，请长按 3 秒确认。",
                    confirmToken = name,
                    confirmMode = DestructiveConfirmMode.LongPress,
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun dropIndexConfirmed() {
        ctx.actions.run("删除索引") {
            val current = state.value
            val name = current.pendingDestructive?.target.orEmpty()
            repository.dropIndex(current.selectedDatabase, current.selectedCollection, name)
            state.update { it.copy(pendingDestructive = null, destructiveConfirmInput = "") }
            loadIndexes()
            state.update { it.copy(status = "索引已删除：" + name) }
        }
    }

    fun refreshServerOverview() {
        ctx.actions.run("刷新服务器信息") {
            val overview = repository.serverOverview()
            val usersResult = runCatching {
                repository.listUsers(state.value.activeProfile?.authDatabase ?: "admin")
            }
            val opsResult = runCatching { repository.currentOps() }
            state.update {
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

    fun refreshCurrentOps() {
        ctx.actions.run("刷新当前操作") {
            loadCurrentOps(updateStatus = true)
        }
    }

    /**
     * Start/stop the ~5s Ops Counter sampling loop based on Admin panel visibility.
     */
    fun setOpsCounterVisible(visible: Boolean, scope: CoroutineScope) {
        samplingVisible = visible
        if (!visible) {
            stopOpsCounterSampling(keepHistory = true)
            return
        }
        if (!state.value.isConnected) {
            stopOpsCounterSampling(keepHistory = true)
            return
        }
        startOpsCounterSampling(scope)
    }

    fun stopOpsCounterSampling(keepHistory: Boolean = false) {
        samplingJob?.cancel()
        samplingJob = null
        if (!keepHistory) {
            sampler.reset()
            state.update {
                it.copy(
                    opsCounterState = null,
                    opsCounterSampling = false,
                    opsCounterError = null,
                )
            }
        } else {
            state.update { it.copy(opsCounterSampling = false) }
        }
    }

    private fun startOpsCounterSampling(scope: CoroutineScope) {
        if (samplingJob?.isActive == true) {
            state.update { it.copy(opsCounterSampling = true) }
            return
        }
        state.update { it.copy(opsCounterSampling = true, opsCounterError = null) }
        samplingJob = scope.launch {
            while (isActive && samplingVisible && state.value.isConnected) {
                val snapshotResult = runCatching { repository.fetchOpCounters() }
                snapshotResult.onSuccess { snapshot ->
                    val sample = sampler.onSnapshot(snapshot)
                    if (sample != null) {
                        state.update {
                            it.copy(
                                opsCounterState = sample,
                                opsCounterError = null,
                                opsCounterSampling = true,
                            )
                        }
                    } else {
                        // Baseline only; preserve previous sample state if any.
                        state.update {
                            it.copy(opsCounterError = null, opsCounterSampling = true)
                        }
                    }
                }.onFailure { error ->
                    state.update {
                        it.copy(
                            opsCounterError = error.message ?: "opcounters 采样失败",
                            opsCounterSampling = true,
                        )
                    }
                }
                delay(OpsCounterSampler.DEFAULT_INTERVAL_MS)
            }
            state.update { it.copy(opsCounterSampling = false) }
        }
    }

    private suspend fun loadCurrentOps(updateStatus: Boolean = false) {
        val ops = runCatching { repository.listCurrentOps() }
        state.update { current ->
            current.copy(
                currentOps = ops.getOrDefault(emptyList())
                    .sortedByDescending { it.secsRunning ?: -1L },
                currentOpsListError = ops.exceptionOrNull()?.message,
                currentOpsJson = if (ops.isSuccess) {
                    ops.getOrNull()?.joinToString(
                        separator = ",\n",
                        prefix = "[\n",
                        postfix = "\n]",
                    ) { summary -> summary.rawJson } ?: "[]"
                } else {
                    current.currentOpsJson
                },
                status = if (updateStatus && ops.isSuccess) {
                    "当前操作 " + (ops.getOrNull()?.size ?: 0) + " 条"
                } else {
                    current.status
                },
            )
        }
    }

    fun requestKillOp(opId: String) {
        ctx.ensureWritable("killOp")
        state.update {
            it.copy(
                pendingDestructive = PendingDestructiveAction(
                    action = DestructiveAction.KillOp,
                    target = opId,
                    message = "将 killOp opid=`" + opId + "`。请长按 3 秒确认。",
                    confirmToken = opId,
                    confirmMode = DestructiveConfirmMode.LongPress,
                ),
                destructiveConfirmInput = "",
            )
        }
    }

    fun killOpConfirmed() {
        val opId = state.value.pendingDestructive?.target.orEmpty()
        ctx.actions.run("killOp") {
            val result = repository.killOp(opId)
            state.update {
                it.copy(
                    pendingDestructive = null,
                    destructiveConfirmInput = "",
                    maintenanceResultJson = result,
                    status = "已发送 killOp：" + opId,
                )
            }
            ctx.recordAudit("killOp", opId, result)
            loadCurrentOps()
        }
    }

    private suspend fun loadIndexes() {
        val current = state.value
        val indexes = repository.listIndexes(current.selectedDatabase, current.selectedCollection)
        state.update {
            it.copy(
                indexes = indexes,
                status = "已加载 " + indexes.size + " 个索引",
            )
        }
    }
}


