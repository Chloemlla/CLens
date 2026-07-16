package com.chloemlla.clens.ui

import com.chloemlla.clens.core.mongo.QueryHistoryEntry
import com.chloemlla.clens.core.mongo.QueryFavoriteEntry
import com.chloemlla.clens.core.mongo.QueryFieldInferencer
import com.chloemlla.clens.core.mongo.SqlToMongoTranslator
import com.chloemlla.clens.core.mongo.SqlTranslateException
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

    fun setQueryInputMode(mode: QueryInputMode) {
        state.update { current ->
            if (mode == current.queryInputMode) return@update current
            when (mode) {
                QueryInputMode.Visual -> {
                    val clauses = VisualFilterBuilder.fromFilterJson(current.queryFilterJson)
                        .ifEmpty { listOf(VisualFilterClause()) }
                    current.copy(
                        queryInputMode = QueryInputMode.Visual,
                        queryVisualClauses = clauses,
                        querySqlPreview = "",
                        querySqlLimit = null,
                        querySqlSkip = null,
                    )
                }
                QueryInputMode.Json -> {
                    val filterJson = if (current.queryInputMode == QueryInputMode.Visual) {
                        VisualFilterBuilder.toFilterJson(current.queryVisualClauses, pretty = true)
                    } else {
                        current.queryFilterJson
                    }
                    current.copy(
                        queryInputMode = QueryInputMode.Json,
                        queryFilterJson = filterJson,
                        querySqlPreview = "",
                        querySqlLimit = null,
                        querySqlSkip = null,
                    )
                }
                QueryInputMode.Sql -> {
                    val filterJson = if (current.queryInputMode == QueryInputMode.Visual) {
                        VisualFilterBuilder.toFilterJson(current.queryVisualClauses, pretty = true)
                    } else {
                        current.queryFilterJson
                    }
                    current.copy(
                        queryInputMode = QueryInputMode.Sql,
                        queryFilterJson = filterJson,
                        querySqlPreview = "",
                        querySqlLimit = null,
                        querySqlSkip = null,
                        error = null,
                    )
                }
            }
        }
    }

    /** Backward-compatible toggle used by older call sites. */
    fun setQueryVisualMode(enabled: Boolean) {
        setQueryInputMode(if (enabled) QueryInputMode.Visual else QueryInputMode.Json)
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

    fun updateSqlInput(value: String) {
        state.update {
            it.copy(
                querySqlInput = value,
                querySqlPreview = if (value == it.querySqlInput) it.querySqlPreview else "",
            )
        }
    }

    fun translateSql(showStatus: Boolean = true): Boolean {
        val current = state.value
        return try {
            val translated = SqlToMongoTranslator.translate(current.querySqlInput)
            val clauses = VisualFilterBuilder.fromFilterJson(translated.filterJson)
                .ifEmpty { listOf(VisualFilterClause()) }
            state.update {
                it.copy(
                    selectedCollection = translated.collection?.takeIf { name -> name.isNotBlank() }
                        ?: it.selectedCollection,
                    queryFilterJson = translated.filterJson,
                    queryProjectionJson = translated.projectionJson,
                    querySortJson = translated.sortJson,
                    querySqlPreview = translated.shellPreview,
                    querySqlLimit = translated.limit,
                    querySqlSkip = translated.skip,
                    queryVisualClauses = clauses,
                    status = if (showStatus) "SQL 已翻译为 Mongo find" else it.status,
                    error = null,
                )
            }
            true
        } catch (error: SqlTranslateException) {
            state.update {
                it.copy(
                    error = error.message ?: "SQL 翻译失败",
                    querySqlPreview = "",
                    querySqlLimit = null,
                    querySqlSkip = null,
                )
            }
            false
        } catch (error: Exception) {
            state.update {
                it.copy(
                    error = error.message ?: "SQL 翻译失败",
                    querySqlPreview = "",
                    querySqlLimit = null,
                    querySqlSkip = null,
                )
            }
            false
        }
    }

    fun runSqlQuery() {
        if (!translateSql(showStatus = false)) return
        val selected = state.value.selectedCollection
        if (selected.isBlank()) {
            state.update {
                it.copy(error = "请在 SQL 中写 FROM <集合>，或先在浏览页选择集合。")
            }
            return
        }
        runQuery(withExplain = false, fromSql = true)
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
        val filterJson = activeFilterJson(current)
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
                queryInputMode = if (entry.modeAggregate) it.queryInputMode else QueryInputMode.Json,
                querySqlPreview = "",
                querySqlLimit = null,
                querySqlSkip = null,
                status = "已恢复收藏：" + entry.name,
                error = null,
            )
        }
        // One-tap reuse: restore then execute when a collection is selected.
        val current = state.value
        if (current.isConnected && current.selectedCollection.isNotBlank()) {
            runQuery(withExplain = false, fromSql = false)
        }
    }

    fun deleteQueryFavorite(id: String) {
        ctx.localStore.deleteQueryFavorite(id)
        ctx.refreshLocalLists()
        state.update { it.copy(status = "已删除收藏") }
    }

    fun suggestedQueryFields(): List<String> {
        val current = state.value
        val samples = (current.queryResults + current.documents).distinct().take(12)
        return QueryFieldInferencer.inferFieldNames(
            sampleDocumentsJson = samples,
            indexKeysJson = current.indexes.map { it.keysJson },
        )
    }

    fun updateText(field: ClensViewModel.Field, value: String) {
        state.update { current ->
            when (field) {
                ClensViewModel.Field.QueryFilter -> {
                    val clauses = if (current.queryInputMode == QueryInputMode.Visual) {
                        VisualFilterBuilder.fromFilterJson(value).ifEmpty { listOf(VisualFilterClause()) }
                    } else {
                        current.queryVisualClauses
                    }
                    current.copy(queryFilterJson = value, queryVisualClauses = clauses)
                }
                ClensViewModel.Field.QuerySort -> current.copy(querySortJson = value)
                ClensViewModel.Field.QueryProjection -> current.copy(queryProjectionJson = value)
                ClensViewModel.Field.QueryPipeline -> current.copy(queryPipelineJson = value)
                ClensViewModel.Field.QuerySql -> current.copy(querySqlInput = value, querySqlPreview = "")
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

    fun runQuery(withExplain: Boolean = false, fromSql: Boolean = false) {
        if (!fromSql &&
            !withExplain &&
            !state.value.queryModeAggregate &&
            state.value.queryInputMode == QueryInputMode.Sql
        ) {
            runSqlQuery()
            return
        }
        ctx.actions.run(if (withExplain) "Explain" else "执行查询") {
            val current = state.value
            val filterJson = activeFilterJson(current)
            val limit = if (fromSql) {
                current.querySqlLimit?.coerceIn(1, 500) ?: current.documentLimit
            } else {
                current.documentLimit
            }
            val skip = if (fromSql) {
                current.querySqlSkip?.coerceAtLeast(0) ?: 0
            } else {
                0
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
                    limit = limit,
                    skip = skip,
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
                        status = buildString {
                            append("查询返回 ")
                            append(page.documents.size)
                            append(" 条")
                            if (withExplain) append("（含 explain）")
                            if (fromSql) append(" · SQL")
                        },
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
                queryInputMode = when {
                    entry.modeAggregate -> it.queryInputMode
                    it.queryInputMode == QueryInputMode.Sql -> QueryInputMode.Json
                    else -> it.queryInputMode
                },
                querySqlPreview = "",
                querySqlLimit = null,
                querySqlSkip = null,
                status = "已恢复查询历史：" + entry.title,
            )
        }
        // PRD: history supports one-tap re-run.
        val current = state.value
        if (current.isConnected && current.selectedCollection.isNotBlank()) {
            runQuery(withExplain = false, fromSql = false)
        }
    }

    private fun saveHistory(aggregate: Boolean) {
        val current = state.value
        val filterJson = activeFilterJson(current)
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

    private fun activeFilterJson(current: ClensUiState): String {
        return if (!current.queryModeAggregate && current.queryInputMode == QueryInputMode.Visual) {
            VisualFilterBuilder.toFilterJson(current.queryVisualClauses, pretty = true)
        } else {
            current.queryFilterJson
        }
    }
}
