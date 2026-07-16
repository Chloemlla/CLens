package com.chloemlla.clens.core.mongo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) local-network runtime permission helpers.
 */
object LocalNetworkPermission {
    /**
     * Platform permission name from Android 17 local-network protection.
     * Kept as a string so older compile stubs without the field still build if needed.
     */
    const val PERMISSION: String = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Platforms below API 37 do not enforce ACCESS_LOCAL_NETWORK. */
    fun isEnforcedOnThisDevice(): Boolean = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean {
        if (!isEnforcedOnThisDevice()) return true
        return ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun deniedMessage(): String =
        "需要「本地网络」权限才能连接局域网/内网 MongoDB。请在系统设置中允许后重试。"

    /**
     * Optional alias for call sites that prefer Manifest constants when present.
     * Falls back to [PERMISSION].
     */
    fun permissionOrManifest(): String {
        return runCatching {
            val field = Manifest.permission::class.java.getField("ACCESS_LOCAL_NETWORK")
            field.get(null) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: PERMISSION
    }
}
