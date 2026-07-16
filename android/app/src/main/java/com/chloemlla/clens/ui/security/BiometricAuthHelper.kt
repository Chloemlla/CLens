package com.chloemlla.clens.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(AUTHENTICATORS)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun statusMessage(context: Context): String {
        return when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "设备生物识别可用"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "尚未录入指纹/面容，请先在系统设置中添加"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "生物识别硬件暂不可用"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "当前设备不支持生物识别"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "需要先完成系统安全更新"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "当前认证方式不受支持"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "生物识别状态未知"
            else -> "生物识别不可用"
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "解锁 CLens",
        subtitle: String = "使用生物识别或设备凭据验证身份",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // Keep prompt open; user may retry.
                }
            },
        )

        // DEVICE_CREDENTIAL cannot be combined with setNegativeButtonText.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(info)
    }

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
}
