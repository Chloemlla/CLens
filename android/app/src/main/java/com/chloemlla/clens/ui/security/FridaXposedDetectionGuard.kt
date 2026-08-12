package com.chloemlla.clens.ui.security

import android.content.Context
import android.content.pm.PackageManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object FridaXposedDetectionGuard {
    fun assess(context: Context): List<String> {
        val findings = mutableSetOf<String>()
        val mapsLines = readProc("/proc/self/maps")

        // Injected Frida gum/agent libraries show up in this process's maps.
        val fridaMapsPatterns = listOf("frida", "gum-js-loop", "gadget", "gum.cc", "linjector")
        if (findings.none { it == "frida-injected" }) {
            val found = mapsLines?.any { line ->
                fridaMapsPatterns.any { line.contains(it, ignoreCase = true) }
            } ?: false
            if (found) findings.add("frida-injected")
        }

        if (findings.none { it == "frida-server" }) {
            runCatching { if (File("/data/local/tmp/frida-server").exists()) findings.add("frida-server") }
        }
        if (findings.none { it == "frida-server" }) {
            runCatching { if (File("/data/local/tmp/re.frida.server").exists()) findings.add("frida-server") }
        }

        // Any frida-named binary on disk (frida-server-*, gadget payloads, ...).
        runCatching {
            val entries = File("/data/local/tmp").listFiles() ?: emptyArray()
            if (entries.any { it.name.contains("frida", ignoreCase = true) }) {
                findings.add("frida-on-disk")
            }
        }

        // frida-server listens on TCP 27042 (0x69A2) / 27043 (0x69A3) by default.
        runCatching {
            for (net in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
                val file = File(net)
                if (!file.canRead()) continue
                val listening = file.readText().lines().any { line ->
                    val upper = line.uppercase()
                    upper.contains(" 0A ") && (upper.contains("69A2") || upper.contains("69A3"))
                }
                if (listening) {
                    findings.add("frida-listener")
                    break
                }
            }
        }

        // Zygisk / Riru / LSPosed / EdXposed hooking runtimes.
        val hookRuntimes = listOf("zygisk", "zygiskd", "lspd", "riru", "edxposed", "riru_edxposed", "zygisk_lsposed")
        val hookMapped = mapsLines?.any { line ->
            hookRuntimes.any { line.contains(it, ignoreCase = true) }
        } ?: false
        if (hookMapped) findings.add("zygisk-injected")

        runCatching {
            val hookPaths = listOf(
                "/data/adb/zygisk",
                "/data/adb/lspd",
                "/data/adb/riru",
                "/data/adb/modules/riru_edxposed",
                "/data/adb/modules/zygisk_lsposed",
            )
            if (hookPaths.any { File(it).exists() }) {
                findings.add("hooking-runtime")
            }
        }

        if (findings.none { it == "xposed-injected" }) {
            val xposedMapped = mapsLines?.any { it.contains("xposed", ignoreCase = true) } ?: false
            if (xposedMapped) findings.add("xposed-injected")
        }

        runCatching {
            if (File("/system/framework/XposedBridge.jar").exists()) findings.add("xposed-bridge")
        }
        runCatching {
            if (File("/system/lib/libxposed_art.so").exists() || File("/system/lib64/libxposed_art.so").exists()) {
                findings.add("xposed-art-lib")
            }
        }
        runCatching {
            if (File("/data/data/de.robv.android.xposed").exists()) findings.add("xposed-app")
        }

        runCatching {
            for (pkg in HOOK_PACKAGES) {
                try {
                    context.packageManager.getPackageInfo(pkg, 0)
                    findings.add("hook-pkg:$pkg")
                } catch (_: PackageManager.NameNotFoundException) {
                }
            }
        }

        // Virtualization / dual-app containers wrap the app in a hooking sandbox.
        val containerPatterns = listOf("virtualapp", "com.lody.virtual", "dualspace", "virtualxposed", "io.va.exposed")
        val containerMapped = mapsLines?.any { line ->
            containerPatterns.any { line.contains(it, ignoreCase = true) }
        } ?: false
        if (containerMapped) findings.add("virtual-container")

        return findings.toList()
    }

    private fun readProc(path: String): List<String>? {
        return runCatching {
            val file = File(path)
            if (file.canRead()) {
                BufferedReader(FileReader(file)).use { reader -> reader.lineSequence().toList() }
            } else null
        }.getOrNull()
    }

    private val HOOK_PACKAGES = listOf(
        "de.robv.android.xposed.installer",
        "org.lsposed.manager",
        "io.va.exposed",
        "com.lody.virtual",
    )
}
