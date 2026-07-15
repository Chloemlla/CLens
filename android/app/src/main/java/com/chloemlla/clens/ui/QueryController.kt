package com.chloemlla.clens.ui

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

    fun runQuery(withExplain: Boolean = false) {
        ctx.actions.run(if (withExplain) "Explain" else "执行查询") {
            val current = state.value
            if (current.queryModeAggregate) {
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
            }
        }
    }
}
