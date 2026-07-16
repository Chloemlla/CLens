package com.chloemlla.clens.core.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class PlatformStorageSurfaceTest {
    @Test
    fun manifestDoesNotEnableLegacyExternalStorage() {
        val manifest = readRepoFile(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml",
            "android/app/src/main/AndroidManifest.xml",
            "../src/main/AndroidManifest.xml",
            "../../src/main/AndroidManifest.xml",
        )
        assertFalse(
            "requestLegacyExternalStorage=true is forbidden under scoped storage policy",
            PlatformStorageSurface.usesLegacyExternalStorage(manifest),
        )
    }

    @Test
    fun fileProviderOnlyExposesCacheRoots() {
        val pathsXml = readRepoFile(
            "src/main/res/xml/file_paths.xml",
            "app/src/main/res/xml/file_paths.xml",
            "android/app/src/main/res/xml/file_paths.xml",
            "../src/main/res/xml/file_paths.xml",
            "../../src/main/res/xml/file_paths.xml",
        )
        val tags = PlatformStorageSurface.extractFileProviderPathTags(pathsXml)
        assertTrue(tags.contains("cache-path"))
        val problems = PlatformStorageSurface.validateFileProviderPaths(tags)
        if (problems.isNotEmpty()) {
            fail("file_paths surface drift: " + problems.joinToString(", "))
        }
    }

    @Test
    fun exportPathHeuristicMatchesCacheExportConvention() {
        assertTrue(
            PlatformStorageSurface.isAppCacheExportPath(
                "/data/user/0/com.chloemlla.clens/cache/export/page.json",
            ),
        )
        assertFalse(
            PlatformStorageSurface.isAppCacheExportPath(
                "/storage/emulated/0/Download/page.json",
            ),
        )
    }

    @Test
    fun forbiddenProviderRootIsDetected() {
        val problems = PlatformStorageSurface.validateFileProviderPaths(
            listOf("cache-path", "external-path"),
        )
        assertTrue(problems.any { it.contains("external-path") })
    }

    private fun readRepoFile(vararg relativeCandidates: String): String {
        val hit = relativeCandidates.map { File(it) }.firstOrNull { it.isFile }
        if (hit == null) {
            fail("file not found from cwd=${File(".").absolutePath}; tried=${relativeCandidates.joinToString()}")
        }
        return hit!!.readText(Charsets.UTF_8)
    }
}
