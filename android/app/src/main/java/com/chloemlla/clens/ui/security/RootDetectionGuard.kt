package com.chloemlla.clens.ui.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

object RootDetectionGuard {
    fun assess(context: Context): List<String> {
        val findings = mutableListOf<String>()

        runCatching {
            for (path in SU_PATHS) {
                if (File(path).exists()) {
                    findings.add("su-binary:$path")
                }
            }
        }

        runCatching {
            val tags = Build.TAGS
            if (tags != null && tags.contains("test-keys")) {
                findings.add("test-keys")
            }
        }

        runCatching {
            if (File("/system/app/Superuser.apk").exists()) {
                findings.add("superuser-apk")
            }
        }

        runCatching {
            if (File("/sbin/.magisk").exists() || File("/cache/magisk.log").exists()) {
                findings.add("magisk")
            }
        }

        runCatching {
            File("/proc/mounts").useLines { lines ->
                for (line in lines) {
                    if ((line.startsWith("/dev/block/") || line.contains(" /system ") || line.contains(" /vendor ")) &&
                        line.contains(" rw ")
                    ) {
                        // Mounted writable often indicates root was granted via remount.
                        findings.add("system-rw-mount")
                        break
                    }
                }
            }
        }

        runCatching {
            if (File("/data/adb/magisk").exists() ||
                File("/data/adb/magisk.img").exists() ||
                File("/data/adb/magisk.db").exists()
            ) {
                findings.add("magisk-v2")
            }
        }

        runCatching {
            if (File("/system/xbin/busybox").exists() || File("/system/bin/busybox").exists()) {
                findings.add("busybox")
            }
        }

        runCatching {
            System.getenv("PATH")?.split(":")?.forEach { dir ->
                if (File("$dir/su").exists()) {
                    findings.add("su-on-path")
                    return@forEach
                }
            }
        }

        runCatching {
            context.packageManager.getPackageInfo("com.topjohnwu.magisk", 0)
            findings.add("magisk-package")
        }

        return findings
    }

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/bin/.ext/.su",
        "/system/xbin/sugote",
        "/su/bin/su",
        "/vendor/bin/su",
        "/system/sbin/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
    )
}