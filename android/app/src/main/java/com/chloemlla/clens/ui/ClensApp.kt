package com.chloemlla.clens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.clens.ui.connection.SessionHealthBanner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import com.chloemlla.clens.core.storage.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClensApp(
    viewModel: ClensViewModel,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeCallback by rememberUpdatedState(onThemeModeChanged)
    LaunchedEffect(state.themeMode) {
        themeCallback(state.themeMode)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.onAppForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            val disconnectNotice = state.disconnectNotice
            if (!disconnectNotice.isNullOrBlank() || state.reconnecting) {
                SessionHealthBanner(
                    notice = disconnectNotice ?: "正在尝试恢复连接…",
                    reconnecting = state.reconnecting,
                    onReconnect = viewModel::reconnectManually,
                    onDismiss = viewModel::dismissDisconnectNotice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding(),
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
    val expectedToken = pending.confirmToken.ifBlank { pending.target }
    val needsTypedName = pending.confirmMode == DestructiveConfirmMode.TypeName
    val needsLongPress = pending.confirmMode == DestructiveConfirmMode.LongPress
    val typeNameMatched = !needsTypedName || confirmInput == expectedToken
    var pressProgress by remember(pending) { mutableFloatStateOf(0f) }
    var isPressing by remember(pending) { mutableStateOf(false) }
    val onConfirmState by rememberUpdatedState(onConfirm)

    LaunchedEffect(isPressing, pending, loading) {
        if (!needsLongPress || loading) {
            pressProgress = 0f
            return@LaunchedEffect
        }
        if (!isPressing) {
            pressProgress = 0f
            return@LaunchedEffect
        }
        val totalMs = 3_000
        val stepMs = 50
        var elapsed = 0
        while (isActive && isPressing && elapsed < totalMs) {
            delay(stepMs.toLong())
            elapsed += stepMs
            pressProgress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
        }
        if (isPressing && elapsed >= totalMs && !loading) {
            onConfirmState()
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("确认危险操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pending.message)
                when (pending.confirmMode) {
                    DestructiveConfirmMode.TypeName -> {
                        Text(
                            text = "请输入 `" + expectedToken + "` 以确认。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = confirmInput,
                            onValueChange = onConfirmInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("输入目标名称确认") },
                            enabled = !loading,
                        )
                    }
                    DestructiveConfirmMode.LongPress -> {
                        Text(
                            text = "请长按下方按钮 3 秒确认执行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (pressProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { pressProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (needsLongPress) {
                Box(
                    modifier = Modifier
                        .pointerInput(pending, loading) {
                            detectTapGestures(
                                onPress = {
                                    if (loading) return@detectTapGestures
                                    isPressing = true
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        isPressing = false
                                    }
                                },
                            )
                        },
                ) {
                    Button(
                        onClick = {},
                        enabled = !loading,
                    ) {
                        Text(if (isPressing) "继续按住…" else "长按 3 秒确认")
                    }
                }
            } else {
                Button(
                    onClick = onConfirm,
                    enabled = !loading && typeNameMatched,
                ) {
                    Text("确认执行")
                }
            }
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
