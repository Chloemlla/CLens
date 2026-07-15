package com.chloemlla.clens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun QueryPanel(state: ClensUiState, viewModel: ClensViewModel) {
    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "查询控制台",
            subtitle = "Find / Aggregate / Explain，结果支持 JSON 与表格。",
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.runQuery(false) }, enabled = !state.loading && state.selectedCollection.isNotBlank()) { Text("执行") }
            OutlinedButton(
                onClick = { viewModel.runQuery(true) },
                enabled = !state.loading && state.selectedCollection.isNotBlank() && !state.queryModeAggregate,
            ) { Text("Find + Explain") }
            OutlinedButton(
                onClick = viewModel::explainAggregate,
                enabled = !state.loading && state.selectedCollection.isNotBlank() && state.queryModeAggregate,
            ) { Text("Aggregate Explain") }
        }
        state.queryDurationMillis?.let { duration ->
            Text(
                text = "耗时 " + duration + "ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionTitle(text = "查询历史", subtitle = "最近 30 条本地保存。")
        OutlinedButton(onClick = viewModel::refreshQueryHistory, enabled = !state.loading) { Text("刷新历史") }
        if (state.queryHistory.isEmpty()) {
            InfoCard(title = "暂无历史", lines = listOf("执行 find/aggregate 后会自动记录。"))
        } else {
            state.queryHistory.take(10).forEach { item ->
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
