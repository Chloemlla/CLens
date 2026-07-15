package com.chloemlla.clens.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun SettingsPanel(state: ClensUiState, viewModel: ClensViewModel) {
    PanelColumn(state = state, onDismissFeedback = viewModel::clearFeedback) {
        ClensAppHeader(state = state)
        SectionTitle(
            text = "设置",
            subtitle = "本地界面偏好，不影响 Mongo 连接与数据。",
            icon = Icons.Outlined.Settings,
        )

        InfoCard(
            title = "浏览列表",
            lines = listOf(
                "数据库与集合较多时，建议开启竖排列表并配合搜索。",
                "关闭后恢复横向标签流，适合项目较少的场景。",
            ),
        )

        FlagRow(
            label = "数据库/集合使用竖排列表",
            checked = state.verticalCatalogLists,
            enabled = !state.loading,
            onCheckedChange = viewModel::setVerticalCatalogListsEnabled,
        )

        Text(
            text = if (state.verticalCatalogLists) {
                "当前：竖排列表 + 搜索过滤"
            } else {
                "当前：横向标签 + 搜索过滤"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
