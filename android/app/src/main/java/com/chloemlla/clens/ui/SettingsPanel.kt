package com.chloemlla.clens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chloemlla.clens.core.storage.ThemeMode

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

        SectionTitle(
            text = "安全",
            subtitle = "生物识别应用锁，默认关闭。",
        )
        InfoCard(
            title = "应用锁",
            lines = listOf(
                "开启后，启动与后台超时返回时需要生物识别或设备凭据。",
                "可随时在此开关，不依赖首次弹窗选择。",
            ),
        )
        FlagRow(
            label = "启用生物识别锁定",
            checked = state.biometricEnabled,
            enabled = !state.loading,
            onCheckedChange = viewModel::setBiometricEnabled,
        )
        Text(
            text = if (state.biometricEnabled) "当前：已启用应用锁" else "当前：应用锁关闭",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(
            text = "外观",
            subtitle = "跟随系统 / 浅色 / 深色。",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    enabled = !state.loading,
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.System -> "系统"
                                ThemeMode.Light -> "浅色"
                                ThemeMode.Dark -> "深色"
                            },
                        )
                    },
                )
            }
        }
        Text(
            text = "当前主题：" + when (state.themeMode) {
                ThemeMode.System -> "跟随系统"
                ThemeMode.Light -> "浅色"
                ThemeMode.Dark -> "深色"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
