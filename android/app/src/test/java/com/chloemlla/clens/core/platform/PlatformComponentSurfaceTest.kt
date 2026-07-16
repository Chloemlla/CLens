package com.chloemlla.clens.core.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class PlatformComponentSurfaceTest {
    @Test
    fun intentFilterComponentsDeclareExportedAndBackupIsClosed() {
        val manifest = readRepoFile(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml",
            "android/app/src/main/AndroidManifest.xml",
            "../src/main/AndroidManifest.xml",
            "../../src/main/AndroidManifest.xml",
        )
        val components = PlatformComponentSurface.extractComponents(manifest)
        val problems = PlatformComponentSurface.validateExported(components)
        if (problems.isNotEmpty()) {
            fail("exported surface drift: " + problems.joinToString(", "))
        }
        assertTrue(
            "MainActivity must remain exported launcher",
            PlatformComponentSurface.mainActivityIsExportedLauncher(components),
        )
        assertTrue(
            "FileProvider must stay unexported",
            PlatformComponentSurface.fileProviderIsNotExported(components),
        )
        assertTrue(
            "allowBackup must be false for credential stores",
            PlatformComponentSurface.isAllowBackupDisabled(manifest),
        )
    }

    @Test
    fun backupRulesDisableExtraction() {
        val backup = readRepoFile(
            "src/main/res/xml/backup_rules.xml",
            "app/src/main/res/xml/backup_rules.xml",
            "android/app/src/main/res/xml/backup_rules.xml",
            "../src/main/res/xml/backup_rules.xml",
            "../../src/main/res/xml/backup_rules.xml",
        )
        val extraction = readRepoFile(
            "src/main/res/xml/data_extraction_rules.xml",
            "app/src/main/res/xml/data_extraction_rules.xml",
            "android/app/src/main/res/xml/data_extraction_rules.xml",
            "../src/main/res/xml/data_extraction_rules.xml",
            "../../src/main/res/xml/data_extraction_rules.xml",
        )
        assertTrue(backup.contains("full-backup-content"))
        assertTrue(backup.contains("""domain="sharedpref""""))
        assertTrue(extraction.contains("""cloud-backup disabled="true""""))
        assertTrue(extraction.contains("""device-transfer disabled="true""""))
    }

    @Test
    fun missingExportedOnFilteredComponentIsDetected() {
        val xml = """
            <manifest>
              <application>
                <activity android:name=".Bad" >
                  <intent-filter>
                    <action android:name="android.intent.action.VIEW" />
                  </intent-filter>
                </activity>
              </application>
            </manifest>
        """.trimIndent()
        val problems = PlatformComponentSurface.validateExported(
            PlatformComponentSurface.extractComponents(xml),
        )
        assertFalse(problems.isEmpty())
        assertTrue(problems.any { it.startsWith("missing-exported:activity:") })
    }

    private fun readRepoFile(vararg relativeCandidates: String): String {
        val hit = relativeCandidates.map { File(it) }.firstOrNull { it.isFile }
        if (hit == null) {
            fail("file not found from cwd=${File(".").absolutePath}; tried=${relativeCandidates.joinToString()}")
        }
        return hit!!.readText(Charsets.UTF_8)
    }
}
