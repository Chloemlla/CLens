package com.chloemlla.clens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun QueryPanel(state: ClensUiState, viewModel: ClensViewModel) {
    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "查询控制台",
            subtitle = "Find / Aggregate / Explain，结果以 JSON 展示。",
            icon = Icons.AutoMirrored.Outlined.ManageSearch,
        )
        if (!state.isConnected) {
            InfoCard(title = "尚未连接", lines = listOf("先连接并在「浏览」中选择数据库与集合。"))
            return@PanelColumn
        }
        Text("当前目标：" + state.selectedDatabase.ifBlank { "-" } + "." + state.selectedCollection.ifBlank { "-" })
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
            Button(onClick = { viewModel.runQuery(false) }, enabled = !state.loading) { Text("执行") }
            OutlinedButton(
                onClick = { viewModel.runQuery(true) },
                enabled = !state.loading && !state.queryModeAggregate,
            ) { Text("Find + Explain") }
        }
        state.queryDurationMillis?.let { duration ->
            Text(
                text = "耗时 " + duration + "ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.queryResults.forEachIndexed { index, doc ->
            DocumentSnippet(title = "结果 #" + (index + 1), json = doc, selected = false, onClick = {})
        }
        if (state.explainJson.isNotBlank()) {
            JsonField("Explain", state.explainJson, enabled = false, minLines = 8) {}
        }
    }
}
