package com.chloemlla.clens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClensApp(viewModel: ClensViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(text = "CLens", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (state.isConnected) {
                                    (state.connectedProfile?.name ?: "已连接") +
                                        if (state.connectedReadOnly) " · 只读" else ""
                                } else {
                                    "MongoDB 管理客户端"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp,
            ) {
                ClensTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Icon(imageVector = tab.icon(), contentDescription = tab.label) },
                        text = { Text(tab.label) },
                    )
                }
            }

            val cleartextWarning = state.cleartextWarning
            if (!cleartextWarning.isNullOrBlank()) {
                WarningBanner(
                    text = cleartextWarning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (state.selectedTab) {
                    ClensTab.Connections -> ConnectionsPanel(state, viewModel)
                    ClensTab.Browse -> BrowsePanel(state, viewModel)
                    ClensTab.Query -> QueryPanel(state, viewModel)
                    ClensTab.Admin -> AdminPanel(state, viewModel)
                    ClensTab.Advanced -> AdvancedPanel(state, viewModel)
                    ClensTab.Settings -> SettingsPanel(state, viewModel)
                }
            }
        }
    }

    state.pendingDestructive?.let { pending ->
        DestructiveConfirmDialog(
            pending = pending,
            confirmInput = state.destructiveConfirmInput,
            loading = state.loading,
            onConfirmInputChange = viewModel::updateDestructiveConfirmInput,
            onConfirm = viewModel::confirmDestructive,
            onCancel = viewModel::cancelDestructive,
        )
    }
}

@Composable
private fun DestructiveConfirmDialog(
    pending: PendingDestructiveAction,
    confirmInput: String,
    loading: Boolean,
    onConfirmInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val needsTypedName = pending.action == DestructiveAction.DropDatabase
    val confirmEnabled = !loading && (!needsTypedName || confirmInput == pending.target)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("确认危险操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pending.message)
                if (needsTypedName) {
                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = onConfirmInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("输入数据库名确认") },
                        enabled = !loading,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmEnabled) { Text("确认执行") }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !loading) { Text("取消") }
        },
    )
}

private fun ClensTab.icon(): ImageVector = when (this) {
    ClensTab.Connections -> Icons.Outlined.Cable
    ClensTab.Browse -> Icons.Outlined.TravelExplore
    ClensTab.Query -> Icons.AutoMirrored.Outlined.ManageSearch
    ClensTab.Admin -> Icons.Outlined.AdminPanelSettings
    ClensTab.Advanced -> Icons.Outlined.Build
    ClensTab.Settings -> Icons.Outlined.Settings
}
