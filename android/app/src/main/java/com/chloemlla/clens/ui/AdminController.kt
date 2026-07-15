package com.chloemlla.clens.ui

import kotlinx.coroutines.flow.update

class AdminController(
    private val ctx: ClensSessionContext,
) {
    private val state get() = ctx.state
    private val repository get() = ctx.repository

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

    fun createIndex() {
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
            refreshIndexes()
            state.update { it.copy(status = "索引已创建：" + name) }
        }
    }

    fun requestDropIndex(name: String) {
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
                    message = "将删除索引 `" + name + "`。",
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
            refreshIndexes()
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
}
