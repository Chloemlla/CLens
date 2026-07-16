package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.QueryHistoryEntry
import com.chloemlla.clens.core.mongo.QueryFavoriteEntry
import com.chloemlla.clens.core.mongo.QueryFieldInferencer
import com.chloemlla.clens.core.mongo.VisualFilterBuilder
import com.chloemlla.clens.core.mongo.VisualFilterClause
import java.util.UUID
import kotlinx.coroutines.flow.update

class QueryController(
    private val ctx: ClensSessionContext,
) {
    private val state get() = ctx.state
    private val repository get() = ctx.repository

    fun setQueryModeAggregate(enabled: Boolean) {
        state.update { it.copy(queryModeAggregate = enabled) }
    }

    fun setQueryVisualMode(enabled: Boolean) {
        state.update { current ->
            if (enabled == current.queryVisualMode) return@update current
            if (enabled) {
                val clauses = VisualFilterBuilder.fromFilterJson(current.queryFilterJson)
                    .ifEmpty { listOf(VisualFilterClause()) }
                current.copy(queryVisualMode = true, queryVisualClauses = clauses)
            } else {
                current.copy(
                    queryVisualMode = false,
                    queryFilterJson = VisualFilterBuilder.toFilterJson(current.queryVisualClauses, pretty = true),
                )
            }
        }
    }

    fun updateVisualClause(index: Int, clause: VisualFilterClause) {
        state.update { current ->
            val next = current.queryVisualClauses.toMutableList()
            if (index !in next.indices) return@update current
            next[index] = clause
            current.copy(
                queryVisualClauses = next,
                queryFilterJson = VisualFilterBuilder.toFilterJson(next, pretty = true),
            )
        }
    }

    fun addVisualClause() {
        state.update { current ->
            val next = current.queryVisualClauses + VisualFilterClause()
            current.copy(
                queryVisualClauses = next,
                queryFilterJson = VisualFilterBuilder.toFilterJson(next, pretty = true),
            )
        }
    }

    fun removeVisualClause(index: Int) {
        state.update { current ->
            if (current.queryVisualClauses.size <= 1 || index !in current.queryVisualClauses.indices) {
                return@update current
            }
            val next = current.queryVisualClauses.toMutableList().also { it.removeAt(index) }
            current.copy(
                queryVisualClauses = next,
                queryFilterJson = VisualFilterBuilder.toFilterJson(next, pretty = true),
            )
        }
    }

    fun updateFavoriteNameInput(value: String) {
        state.update { it.copy(queryFavoriteNameInput = value) }
    }

    fun saveCurrentQueryFavorite() {
        val current = state.value
        val name = current.queryFavoriteNameInput.trim()
        if (name.isBlank()) {
            state.update { it.copy(error = "请先填写收藏名称") }
            return
        }
        val filterJson = if (!current.queryModeAggregate && current.queryVisualMode) {
            VisualFilterBuilder.toFilterJson(current.queryVisualClauses, pretty = true)
        } else {
            current.queryFilterJson
        }
        val entry = QueryFavoriteEntry(
            id = UUID.randomUUID().toString(),
            name = name,
            database = current.selectedDatabase,
            collection = current.selectedCollection,
            filterJson = filterJson,
            sortJson = current.querySortJson,
            projectionJson = current.queryProjectionJson,
            modeAggregate = current.queryModeAggregate,
            pipelineJson = current.queryPipelineJson,
        )
        runCatching {
            ctx.localStore.saveQueryFavorite(entry)
            ctx.refreshLocalLists()
            state.update {
                it.copy(
                    queryFavoriteNameInput = "",
                    queryFilterJson = filterJson,
                    status = "已收藏查询：$name",
                    error = null,
                )
            }
        }.onFailure { error ->
            state.update { it.copy(error = error.message ?: "保存收藏失败") }
        }
    }

    fun restoreQueryFavorite(id: String) {
        val entry = state.value.queryFavorites.firstOrNull { it.id == id } ?: return
        val clauses = if (!entry.modeAggregate) {
            VisualFilterBuilder.fromFilterJson(entry.filterJson).ifEmpty { listOf(VisualFilterClause()) }
        } else {
            listOf(VisualFilterClause())
        }
        state.update {
            it.copy(
                queryModeAggregate = entry.modeAggregate,
                selectedDatabase = entry.database.ifBlank { it.selectedDatabase },
                selectedCollection = entry.collection.ifBlank { it.selectedCollection },
                queryFilterJson = entry.filterJson,
                querySortJson = entry.sortJson,
                queryProjectionJson = entry.projectionJson,
                queryPipelineJson = entry.pipelineJson,
                queryVisualClauses = clauses,
                queryVisualMode = !entry.modeAggregate,
                status = "已恢复收藏：" + entry.name,
                error = null,
            )
        }
    }

    fun deleteQueryFavorite(id: String) {
        ctx.localStore.deleteQueryFavorite(id)
        ctx.refreshLocalLists()
        state.update { it.copy(status = "已删除收藏") }
    }

    fun suggestedQueryFields(): List<String> {
        val current = state.value
        return QueryFieldInferencer.inferFieldNames(
            sampleDocumentsJson = current.queryResults,
            indexKeysJson = current.indexes.map { it.keysJson },
        )
    }

    fun updateText(field: ClensViewModel.Field, value: String) {
        state.update { current ->
            when (field) {
                ClensViewModel.Field.QueryFilter -> {
                    val clauses = if (current.queryVisualMode) {
                        VisualFilterBuilder.fromFilterJson(value).ifEmpty { listOf(VisualFilterClause()) }
                    } else {
                        current.queryVisualClauses
                    }
                    current.copy(queryFilterJson = value, queryVisualClauses = clauses)
                }
                ClensViewModel.Field.QuerySort -> current.copy(querySortJson = value)
                ClensViewModel.Field.QueryProjection -> current.copy(queryProjectionJson = value)
                ClensViewModel.Field.QueryPipeline -> current.copy(queryPipelineJson = value)
                else -> current
            }
        }
    }

    fun explainAggregate() {
        ctx.actions.run("Aggregate Explain") {
            val current = state.value
            val explain = repository.explainAggregate(
                current.selectedDatabase,
                current.selectedCollection,
                current.queryPipelineJson,
            )
            state.update {
                it.copy(
                    explainJson = explain,
                    status = "聚合 explain 完成",
                )
            }
        }
    }

    fun setResultViewMode(mode: ResultViewMode) {
        state.update { it.copy(resultViewMode = mode) }
    }

    fun runQuery(withExplain: Boolean = false) {
        ctx.actions.run(if (withExplain) "Explain" else "执行查询") {
            val current = state.value
            val filterJson = if (!current.queryModeAggregate && current.queryVisualMode) {
                VisualFilterBuilder.toFilterJson(current.queryVisualClauses, pretty = true)
            } else {
                current.queryFilterJson
            }
            if (current.queryModeAggregate) {
                if (withExplain) {
                    val explain = repository.explainAggregate(
                        current.selectedDatabase,
                        current.selectedCollection,
                        current.queryPipelineJson,
                    )
                    state.update {
                        it.copy(
                            explainJson = explain,
                            status = "聚合 explain 完成",
                        )
                    }
                } else {
                    val result = repository.aggregate(
                        current.selectedDatabase,
                        current.selectedCollection,
                        current.queryPipelineJson,
                    )
                    state.update {
                        it.copy(
                            queryResults = result.documents,
                            queryDurationMillis = result.durationMillis,
                            explainJson = "",
                            status = "聚合返回 " + result.documents.size + " 条 · " + result.durationMillis + "ms",
                        )
                    }
                    saveHistory(aggregate = true)
                }
            } else {
                val page = repository.findDocuments(
                    database = current.selectedDatabase,
                    collection = current.selectedCollection,
                    filterJson = filterJson,
                    sortJson = current.querySortJson,
                    projectionJson = current.queryProjectionJson,
                    limit = current.documentLimit,
                    skip = 0,
                )
                val explain = if (withExplain) {
                    repository.explainFind(
                        current.selectedDatabase,
                        current.selectedCollection,
                        filterJson,
                        current.querySortJson,
                        current.queryProjectionJson,
                    )
                } else {
                    ""
                }
                state.update {
                    it.copy(
                        queryFilterJson = filterJson,
                        queryResults = page.documents,
                        queryDurationMillis = null,
                        explainJson = explain,
                        status = "查询返回 " + page.documents.size + " 条" +
                            if (withExplain) "（含 explain）" else "",
                    )
                }
                saveHistory(aggregate = false)
            }
        }
    }

    fun refreshQueryHistory() {
        ctx.refreshLocalLists()
    }

    fun restoreQueryHistory(id: String) {
        val entry = state.value.queryHistory.firstOrNull { it.id == id } ?: return
        val clauses = if (!entry.modeAggregate) {
            VisualFilterBuilder.fromFilterJson(entry.filterJson).ifEmpty { listOf(VisualFilterClause()) }
        } else {
            listOf(VisualFilterClause())
        }
        state.update {
            it.copy(
                queryModeAggregate = entry.modeAggregate,
                selectedDatabase = entry.database.ifBlank { it.selectedDatabase },
                selectedCollection = entry.collection.ifBlank { it.selectedCollection },
                queryFilterJson = entry.filterJson,
                querySortJson = entry.sortJson,
                queryProjectionJson = entry.projectionJson,
                queryPipelineJson = entry.pipelineJson,
                queryVisualClauses = clauses,
                queryVisualMode = !entry.modeAggregate && it.queryVisualMode,
                status = "已恢复查询历史：" + entry.title,
            )
        }
    }

    private fun saveHistory(aggregate: Boolean) {
        val current = state.value
        val filterJson = if (!aggregate && current.queryVisualMode) {
            VisualFilterBuilder.toFilterJson(current.queryVisualClauses, pretty = true)
        } else {
            current.queryFilterJson
        }
        val entry = QueryHistoryEntry(
            id = UUID.randomUUID().toString(),
            modeAggregate = aggregate,
            database = current.selectedDatabase,
            collection = current.selectedCollection,
            filterJson = filterJson,
            sortJson = current.querySortJson,
            projectionJson = current.queryProjectionJson,
            pipelineJson = current.queryPipelineJson,
        )
        ctx.localStore.addQueryHistory(entry)
        ctx.refreshLocalLists()
    }
}
