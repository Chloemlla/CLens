package com.chloemlla.clens.core.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class PlatformPermissionSurfaceTest {
    @Test
    fun appManifestStaysOnAndroid13SafeAllowlist() {
        val manifest = readAppManifest()
        assertFalse("sharedUserId must not be present", PlatformPermissionSurface.hasSharedUserId(manifest))
        val permissions = PlatformPermissionSurface.extractUsesPermissions(manifest)
        assertTrue("expected at least INTERNET", permissions.contains("android.permission.INTERNET"))
        val problems = PlatformPermissionSurface.validateDeclared(permissions)
        if (problems.isNotEmpty()) {
            fail("Manifest permission surface drift: " + problems.joinToString(", "))
        }
    }

    @Test
    fun forbiddenNotificationAndMediaPermissionsAreRejectedByPolicy() {
        val problems = PlatformPermissionSurface.validateDeclared(
            listOf(
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.READ_MEDIA_IMAGES",
            ),
        )
        assertTrue(problems.any { it.startsWith("forbidden:android.permission.POST_NOTIFICATIONS") })
        assertTrue(problems.any { it.startsWith("forbidden:android.permission.READ_MEDIA_IMAGES") })
    }

    private fun readAppManifest(): String {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("android/app/src/main/AndroidManifest.xml"),
            File("../src/main/AndroidManifest.xml"),
            File("../../src/main/AndroidManifest.xml"),
        )
        val hit = candidates.firstOrNull { it.isFile }
        if (hit == null) {
            fail("AndroidManifest.xml not found from cwd=${File(".").absolutePath}")
        }
        return hit!!.readText(Charsets.UTF_8)
    }
}
