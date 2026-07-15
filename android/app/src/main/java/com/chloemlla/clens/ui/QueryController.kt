package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.QueryHistoryEntry
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

    fun updateText(field: ClensViewModel.Field, value: String) {
        state.update { current ->
            when (field) {
                ClensViewModel.Field.QueryFilter -> current.copy(queryFilterJson = value)
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
                    filterJson = current.queryFilterJson,
                    sortJson = current.querySortJson,
                    projectionJson = current.queryProjectionJson,
                    limit = current.documentLimit,
                    skip = 0,
                )
                val explain = if (withExplain) {
                    repository.explainFind(
                        current.selectedDatabase,
                        current.selectedCollection,
                        current.queryFilterJson,
                        current.querySortJson,
                        current.queryProjectionJson,
                    )
                } else {
                    ""
                }
                state.update {
                    it.copy(
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
        state.update {
            it.copy(
                queryModeAggregate = entry.modeAggregate,
                selectedDatabase = entry.database.ifBlank { it.selectedDatabase },
                selectedCollection = entry.collection.ifBlank { it.selectedCollection },
                queryFilterJson = entry.filterJson,
                querySortJson = entry.sortJson,
                queryProjectionJson = entry.projectionJson,
                queryPipelineJson = entry.pipelineJson,
                status = "已恢复查询历史：" + entry.title,
            )
        }
    }

    private fun saveHistory(aggregate: Boolean) {
        val current = state.value
        val entry = QueryHistoryEntry(
            id = UUID.randomUUID().toString(),
            modeAggregate = aggregate,
            database = current.selectedDatabase,
            collection = current.selectedCollection,
            filterJson = current.queryFilterJson,
            sortJson = current.querySortJson,
            projectionJson = current.queryProjectionJson,
            pipelineJson = current.queryPipelineJson,
        )
        ctx.localStore.addQueryHistory(entry)
        ctx.refreshLocalLists()
    }
}
