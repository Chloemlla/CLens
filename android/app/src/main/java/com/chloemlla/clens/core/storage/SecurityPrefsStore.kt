package com.chloemlla.clens.core.storage

import android.content.Context
import androidx.core.content.edit

enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * Isolated security / appearance preferences.
 * Kept separate from [LocalAppStore] so concurrent feature work does not collide.
 */
class SecurityPrefsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    }

    fun isBiometricPromptSeen(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_PROMPT_SEEN, false)

    fun setBiometricPromptSeen(seen: Boolean) {
        prefs.edit { putBoolean(KEY_BIOMETRIC_PROMPT_SEEN, seen) }
    }

    fun getThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.System.name).orEmpty()
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.System)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    /** Milliseconds the app may stay in background before re-locking. */
    fun getBackgroundLockTimeoutMs(): Long =
        prefs.getLong(KEY_BACKGROUND_LOCK_TIMEOUT_MS, DEFAULT_BACKGROUND_LOCK_TIMEOUT_MS)
            .coerceAtLeast(0L)

    fun setBackgroundLockTimeoutMs(timeoutMs: Long) {
        prefs.edit {
            putLong(KEY_BACKGROUND_LOCK_TIMEOUT_MS, timeoutMs.coerceAtLeast(0L))
        }
    }

    private companion object {
        const val PREFS = "clens_security_prefs"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_BIOMETRIC_PROMPT_SEEN = "biometric_prompt_seen"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_BACKGROUND_LOCK_TIMEOUT_MS = "background_lock_timeout_ms"
        const val DEFAULT_BACKGROUND_LOCK_TIMEOUT_MS = 60_000L
    }
}
