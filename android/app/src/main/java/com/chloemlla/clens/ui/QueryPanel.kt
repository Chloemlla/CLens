package com.chloemlla.clens.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.ui.query.VisualQueryBuilder

@Composable
internal fun QueryPanel(state: ClensUiState, viewModel: ClensViewModel) {
    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "查询控制台",
            subtitle = "Find / Aggregate / SQL-to-NoSQL，结果支持 JSON 与表格。",
            icon = Icons.AutoMirrored.Outlined.ManageSearch,
        )
        if (!state.isConnected) {
            InfoCard(title = "尚未连接", lines = listOf("先连接并在「浏览」中选择数据库与集合。"))
            return@PanelColumn
        }
        Text("当前目标：" + state.selectedDatabase.ifBlank { "-" } + "." + state.selectedCollection.ifBlank { "-" })
        if (state.isSelectedView) {
            InfoCard(title = "查询视图", lines = listOf("当前目标是 view，可查询，不可在管理页维护索引。"))
        }
        FlagRow("聚合模式", state.queryModeAggregate, !state.loading, viewModel::setQueryModeAggregate)
        if (state.queryModeAggregate) {
            JsonField("Pipeline JSON 数组", state.queryPipelineJson, !state.loading, minLines = 8) {
                viewModel.updateText(ClensViewModel.Field.QueryPipeline, it)
            }
        } else {
            Text(
                text = "Find 输入模式",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QueryInputMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.queryInputMode == mode,
                        onClick = { viewModel.setQueryInputMode(mode) },
                        enabled = !state.loading,
                        label = {
                            Text(
                                when (mode) {
                                    QueryInputMode.Visual -> "可视化"
                                    QueryInputMode.Json -> "JSON"
                                    QueryInputMode.Sql -> "SQL"
                                },
                            )
                        },
                    )
                }
            }
            when (state.queryInputMode) {
                QueryInputMode.Visual -> {
                    VisualQueryBuilder(
                        clauses = state.queryVisualClauses,
                        suggestedFields = viewModel.suggestedQueryFields(),
                        enabled = !state.loading,
                        onClauseChange = viewModel::updateVisualClause,
                        onAddClause = viewModel::addVisualClause,
                        onRemoveClause = viewModel::removeVisualClause,
                    )
                    Text(
                        text = "预览 Filter JSON",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    JsonField("Filter 预览", state.queryFilterJson, enabled = false, minLines = 3) {}
                    JsonField("Sort", state.querySortJson, !state.loading) {
                        viewModel.updateText(ClensViewModel.Field.QuerySort, it)
                    }
                    JsonField("Projection", state.queryProjectionJson, !state.loading) {
                        viewModel.updateText(ClensViewModel.Field.QueryProjection, it)
                    }
                }
                QueryInputMode.Json -> {
                    JsonField("Filter", state.queryFilterJson, !state.loading) {
                        viewModel.updateText(ClensViewModel.Field.QueryFilter, it)
                    }
                    JsonField("Sort", state.querySortJson, !state.loading) {
                        viewModel.updateText(ClensViewModel.Field.QuerySort, it)
                    }
                    JsonField("Projection", state.queryProjectionJson, !state.loading) {
                        viewModel.updateText(ClensViewModel.Field.QueryProjection, it)
                    }
                }
                QueryInputMode.Sql -> {
                    SqlUsageGuide(
                        expanded = state.querySqlGuideExpanded,
                        enabled = !state.loading,
                        onExpandedChange = viewModel::setSqlGuideExpanded,
                        onApplyExample = viewModel::applySqlExample,
                    )
                    OutlinedTextField(
                        value = state.querySqlInput,
                        onValueChange = viewModel::updateSqlInput,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                        minLines = 5,
                        maxLines = 10,
                        label = { Text("SQL") },
                        placeholder = { Text("SELECT * FROM users WHERE age > 18") },
                    )
                    ActionRow {
                        OutlinedButton(
                            onClick = viewModel::translateSql,
                            enabled = !state.loading && state.querySqlInput.isNotBlank(),
                        ) { Text("翻译预览") }
                        Button(
                            onClick = viewModel::runSqlQuery,
                            enabled = !state.loading && state.querySqlInput.isNotBlank(),
                        ) { Text("执行 SQL") }
                    }
                    if (state.querySqlPreview.isNotBlank()) {
                        JsonField("Mongo 预览", state.querySqlPreview, enabled = false, minLines = 2) {}
                        JsonField("Filter", state.queryFilterJson, enabled = false, minLines = 3) {}
                        JsonField("Sort", state.querySortJson, enabled = false, minLines = 2) {}
                        JsonField("Projection", state.queryProjectionJson, enabled = false, minLines = 2) {}
                        val limitText = state.querySqlLimit?.toString() ?: "沿用 documentLimit(${state.documentLimit})"
                        val skipText = state.querySqlSkip?.toString() ?: "0"
                        Text(
                            text = "LIMIT=$limitText · OFFSET=$skipText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (state.queryInputMode != QueryInputMode.Sql || state.queryModeAggregate) {
            ActionRow {
                Button(
                    onClick = { viewModel.runQuery(false) },
                    enabled = !state.loading && state.selectedCollection.isNotBlank(),
                ) { Text("执行") }
                OutlinedButton(
                    onClick = { viewModel.runQuery(true) },
                    enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.queryModeAggregate,
                ) { Text("Find + Explain") }
                OutlinedButton(
                    onClick = viewModel::explainAggregate,
                    enabled = !state.loading && state.selectedCollection.isNotBlank() && state.queryModeAggregate,
                ) { Text("Aggregate Explain") }
            }
        }
        state.queryDurationMillis?.let { duration ->
            Text(
                text = "耗时 " + duration + "ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionTitle(text = "命名收藏", subtitle = "保存当前 filter/sort/projection，一键恢复。")
        OutlinedTextField(
            value = state.queryFavoriteNameInput,
            onValueChange = viewModel::updateFavoriteNameInput,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            singleLine = true,
            label = { Text("收藏名称") },
            placeholder = { Text("例如 活跃用户") },
        )
        ActionRow {
            Button(
                onClick = viewModel::saveCurrentQueryFavorite,
                enabled = !state.loading && state.queryFavoriteNameInput.isNotBlank(),
            ) { Text("保存收藏") }
            OutlinedButton(onClick = viewModel::refreshQueryHistory, enabled = !state.loading) { Text("刷新列表") }
        }
        if (state.queryFavorites.isEmpty()) {
            InfoCard(title = "暂无收藏", lines = listOf("填写名称后可保存当前查询条件。"))
        } else {
            state.queryFavorites.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.restoreQueryFavorite(item.id) },
                        enabled = !state.loading,
                        modifier = Modifier.weight(1f),
                    ) { Text(item.title, maxLines = 1) }
                    OutlinedButton(
                        onClick = { viewModel.deleteQueryFavorite(item.id) },
                        enabled = !state.loading,
                    ) { Text("删除") }
                }
            }
        }

        SectionTitle(text = "查询历史", subtitle = "最近 20 条本地保存，点按即可恢复。")
        OutlinedButton(onClick = viewModel::refreshQueryHistory, enabled = !state.loading) { Text("刷新历史") }
        if (state.queryHistory.isEmpty()) {
            InfoCard(title = "暂无历史", lines = listOf("执行 find/aggregate 后会自动记录。"))
        } else {
            state.queryHistory.take(20).forEach { item ->
                OutlinedButton(
                    onClick = { viewModel.restoreQueryHistory(item.id) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(item.title) }
            }
        }

        ResultViewModeToggle(
            mode = state.resultViewMode,
            enabled = !state.loading,
            onChange = viewModel::setResultViewMode,
        )
        DocumentResultList(
            documents = state.queryResults,
            mode = state.resultViewMode,
            titlePrefix = "结果",
            startIndex = 1,
        )
        if (state.explainJson.isNotBlank()) {
            JsonField("Explain", state.explainJson, enabled = false, minLines = 8) {}
        }
    }
}

private data class SqlExample(
    val label: String,
    val sql: String,
)

@Composable
private fun SqlUsageGuide(
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onApplyExample: (String) -> Unit,
) {
    val examples = listOf(
        SqlExample(
            label = "基础比较",
            sql = "SELECT * FROM users WHERE age > 18",
        ),
        SqlExample(
            label = "投影排序",
            sql = "SELECT name, age FROM users WHERE status = 'active' ORDER BY age DESC LIMIT 20",
        ),
        SqlExample(
            label = "IN + LIKE",
            sql = "SELECT * FROM logs WHERE tag IN ('a', 'b') AND name LIKE 'cli%'",
        ),
        SqlExample(
            label = "空值判断",
            sql = "SELECT _id, owner FROM docs WHERE deletedAt IS NULL AND owner IS NOT NULL",
        ),
    )

    InfoCard(
        title = "SQL 使用教程",
        lines = if (expanded) {
            listOf(
                "1. 关闭聚合模式，选择 Find 输入模式里的 SQL。",
                "2. 先在「浏览」选数据库；FROM 可指定集合，也可省略并沿用当前集合。",
                "3. 写完 SQL 后点「翻译预览」核对 Mongo 语句，再点「执行 SQL」。",
                "4. 支持：SELECT / WHERE / ORDER BY / LIMIT / OFFSET。",
                "5. 条件支持：= != <> > >= < <=、AND、IN、LIKE、IS NULL / IS NOT NULL。",
                "6. 不支持：JOIN、GROUP BY、NOT、聚合函数、子查询、写操作。",
                "7. 翻译在本地完成，仅在预览/执行时解析，避免边输入边卡顿。",
                "8. 执行成功后会写入查询历史（保存的是 Mongo 条件，不是原始 SQL）。",
            )
        } else {
            listOf("已收起。需要时点下方「展开教程」查看步骤与示例。")
        },
    )

    ActionRow {
        if (expanded) {
            OutlinedButton(
                onClick = { onExpandedChange(false) },
                enabled = enabled,
            ) { Text("收起教程") }
        } else {
            OutlinedButton(
                onClick = { onExpandedChange(true) },
                enabled = enabled,
            ) { Text("展开教程") }
        }
    }

    if (expanded) {
        Text(
            text = "示例（点选填入）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            examples.forEach { example ->
                FilterChip(
                    selected = false,
                    onClick = { onApplyExample(example.sql) },
                    enabled = enabled,
                    label = { Text(example.label) },
                )
            }
        }
    }
}
