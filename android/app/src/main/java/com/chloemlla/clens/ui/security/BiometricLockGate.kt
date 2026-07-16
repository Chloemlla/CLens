package com.chloemlla.clens.ui.security

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chloemlla.clens.core.storage.SecurityPrefsStore

/**
 * Gates app content behind biometric unlock when enabled.
 * Locks again after background timeout or process restart.
 */
@Composable
fun BiometricLockGate(
    securityPrefs: SecurityPrefsStore,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var biometricEnabled by remember { mutableStateOf(securityPrefs.isBiometricEnabled()) }
    var promptSeen by remember { mutableStateOf(securityPrefs.isBiometricPromptSeen()) }
    var unlocked by remember { mutableStateOf(!biometricEnabled) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showFirstLaunchPrompt by remember {
        mutableStateOf(!securityPrefs.isBiometricPromptSeen())
    }
    var backgroundedAt by remember { mutableLongStateOf(0L) }
    var promptRequestId by remember { mutableStateOf(0) }

    fun refreshPrefs() {
        biometricEnabled = securityPrefs.isBiometricEnabled()
        promptSeen = securityPrefs.isBiometricPromptSeen()
        if (!biometricEnabled) {
            unlocked = true
            authError = null
        }
    }

    DisposableEffect(lifecycleOwner, securityPrefs) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    refreshPrefs()
                    val timeoutMs = securityPrefs.getBackgroundLockTimeoutMs()
                    if (
                        biometricEnabled &&
                        backgroundedAt > 0L &&
                        System.currentTimeMillis() - backgroundedAt >= timeoutMs
                    ) {
                        unlocked = false
                        authError = null
                        promptRequestId += 1
                    }
                    backgroundedAt = 0L
                }
                Lifecycle.Event.ON_STOP -> {
                    if (securityPrefs.isBiometricEnabled() && unlocked) {
                        backgroundedAt = System.currentTimeMillis()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(biometricEnabled, unlocked, promptRequestId, activity) {
        if (!biometricEnabled || unlocked || activity == null || showFirstLaunchPrompt) return@LaunchedEffect
        if (!BiometricAuthHelper.canAuthenticate(activity)) {
            authError = BiometricAuthHelper.statusMessage(activity)
            return@LaunchedEffect
        }
        BiometricAuthHelper.authenticate(
            activity = activity,
            onSuccess = {
                unlocked = true
                authError = null
            },
            onError = { message ->
                authError = message
            },
            onCancel = {
                authError = "已取消解锁"
            },
        )
    }

    if (showFirstLaunchPrompt && !promptSeen) {
        AlertDialog(
            onDismissRequest = {
                securityPrefs.setBiometricPromptSeen(true)
                securityPrefs.setBiometricEnabled(false)
                showFirstLaunchPrompt = false
                promptSeen = true
                biometricEnabled = false
                unlocked = true
            },
            title = { Text("启用生物识别锁定？") },
            text = {
                Text(
                    "可在启动与后台返回时要求指纹/面容验证，保护本地连接配置。" +
                        "默认关闭，可稍后在「设置」中开启。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        securityPrefs.setBiometricPromptSeen(true)
                        if (BiometricAuthHelper.canAuthenticate(context)) {
                            securityPrefs.setBiometricEnabled(true)
                            biometricEnabled = true
                            unlocked = false
                            promptRequestId += 1
                        } else {
                            securityPrefs.setBiometricEnabled(false)
                            biometricEnabled = false
                            unlocked = true
                            authError = BiometricAuthHelper.statusMessage(context)
                        }
                        showFirstLaunchPrompt = false
                        promptSeen = true
                    },
                ) {
                    Text("启用")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        securityPrefs.setBiometricPromptSeen(true)
                        securityPrefs.setBiometricEnabled(false)
                        showFirstLaunchPrompt = false
                        promptSeen = true
                        biometricEnabled = false
                        unlocked = true
                    },
                ) {
                    Text("暂不")
                }
            },
        )
    }

    if (!biometricEnabled || unlocked) {
        content()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "CLens 已锁定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = authError ?: "请使用生物识别或设备凭据解锁",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    authError = null
                    promptRequestId += 1
                },
                enabled = activity != null,
            ) {
                Text("解锁")
            }
            if (activity == null) {
                Text(
                    text = "当前界面不支持生物识别（需要 FragmentActivity）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Convenience for non-Fragment hosts: cast when possible.
 */
fun ComponentActivity.asFragmentActivityOrNull(): FragmentActivity? = this as? FragmentActivity
