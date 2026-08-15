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
            for (path in MAGISK_PATHS) {
                if (File(path).exists()) {
                    findings.add("magisk:$path")
                }
            }
        }

        runCatching {
            for (path in KERNEL_SU_PATHS) {
                if (File(path).exists()) {
                    findings.add("kernelsu:$path")
                }
            }
        }

        runCatching {
            for (path in APATCH_PATHS) {
                if (File(path).exists()) {
                    findings.add("apatch:$path")
                }
            }
        }

        runCatching {
            for (path in ZYGISK_PATHS) {
                if (File(path).exists()) {
                    findings.add("zygisk:$path")
                }
            }
        }

        runCatching {
            File("/proc/self/mountinfo").useLines { lines ->
                for (line in lines) {
                    val marker = MOUNT_MARKERS.firstOrNull { line.contains(it) }
                    if (marker != null) {
                        findings.add("suspicious-mount:$marker")
                        break
                    }
                }
            }
        }

        runCatching {
            File("/proc/mounts").useLines { lines ->
                for (line in lines) {
                    if ((line.contains(" /system ") ||
                            line.contains(" /vendor ") || line.contains(" /product ")) &&
                        line.contains(" rw ")
                    ) {
                        findings.add("system-rw-mount")
                        break
                    }
                }
            }
        }

        runCatching {
            val enforce = File("/sys/fs/selinux/enforce")
            if (enforce.canRead() && enforce.readText().trim() == "0") {
                findings.add("selinux-permissive")
            }
        }

        runCatching {
            if (systemProperty("ro.secure") == "0") {
                findings.add("ro.secure=0")
            }
        }

        runCatching {
            if (systemProperty("ro.debuggable") == "1") {
                findings.add("ro.debuggable=1")
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
            for (pkg in ROOT_MANAGER_PACKAGES) {
                try {
                    context.packageManager.getPackageInfo(pkg, 0)
                    findings.add("root-manager:$pkg")
                } catch (_: PackageManager.NameNotFoundException) {
                }
            }
        }

        return findings
    }

    private fun systemProperty(key: String): String? {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        }.getOrNull()
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
        "/system/bin/daemonsu",
        "/system/xbin/daemonsu",
        "/data/local/su",
        "/system/sd/xbin/su",
    )

    private val MAGISK_PATHS = listOf(
        "/data/adb/magisk",
        "/data/adb/magisk.img",
        "/data/adb/magisk.db",
        "/dev/magisk",
    )

    private val KERNEL_SU_PATHS = listOf(
        "/data/adb/ksu",
        "/data/adb/ksu.img",
        "/dev/kernelsu",
    )

    private val APATCH_PATHS = listOf(
        "/data/adb/ap",
        "/data/adb/apatch",
        "/data/adb/ap.img",
    )

    private val ZYGISK_PATHS = listOf(
        "/data/adb/zygisk",
        "/data/adb/modules/zygisk",
    )

    private val MOUNT_MARKERS = listOf("magisk", "zygisk", "kernelsu", "apatch", "riru")

    private val ROOT_MANAGER_PACKAGES = listOf(
        "com.topjohnwu.magisk",
        "com.github.kr328.magisk",
        "com.kernel.su",
        "me.weishu.kernelsu",
        "com.alpha.apatch",
        "com.koushikdutta.superuser",
        "eu.chainfire.supersu",
        "com.thirdparty.superuser",
    )
}
