package com.chloemlla.clens.core.platform

/**
 * Android 13+ permission surface policy for CLens.
 * Product does not post notifications, scan nearby Wi-Fi, or read shared media libraries.
 * Keep Manifest permissions on this allowlist unless a feature PR deliberately expands it.
 */
object PlatformPermissionSurface {
    val allowed: Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        // Android 17 local-network gate for LAN Mongo (not Android 13 NEARBY_WIFI_DEVICES).
        "android.permission.ACCESS_LOCAL_NETWORK",
        "android.permission.USE_BIOMETRIC",
        "android.permission.CAMERA",
    )

    /**
     * Surfaces called out by vivo Android 13 doc 586 that CLens must not gain "by accident".
     */
    val forbidden: Set<String> = setOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.BODY_SENSORS",
        "android.permission.BODY_SENSORS_BACKGROUND",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.USE_EXACT_ALARM",
        "android.permission.FOREGROUND_SERVICE",
    )

    fun validateDeclared(permissions: Collection<String>): List<String> {
        val problems = mutableListOf<String>()
        val declared = permissions.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        declared.forEach { name ->
            if (name !in allowed) {
                problems += "undeclared-in-allowlist:$name"
            }
            if (name in forbidden) {
                problems += "forbidden:$name"
            }
        }
        return problems
    }

    fun extractUsesPermissions(manifestXml: String): List<String> {
        val regex = Regex("""<uses-permission\b[^>]*\bandroid:name\s*=\s*"([^"]+)"""")
        return regex.findAll(manifestXml).map { it.groupValues[1] }.toList()
    }

    fun hasSharedUserId(manifestXml: String): Boolean {
        return Regex("""\bandroid:sharedUserId\s*=""").containsMatchIn(manifestXml)
    }
}
