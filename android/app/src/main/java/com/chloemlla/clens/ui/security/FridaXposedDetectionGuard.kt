package com.chloemlla.clens.ui.security

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object FridaXposedDetectionGuard {
    fun assess(context: Context): List<String> {
        val findings = mutableSetOf<String>()

        val mapsLines = runCatching {
            val file = File("/proc/self/maps")
            if (file.canRead()) {
                BufferedReader(FileReader(file)).use { reader ->
                    reader.lineSequence().toList()
                }
            } else emptyList()
        }.getOrDefault(emptyList())

        val fridaPatterns = listOf("frida", "frida-agent", "frida-gadget", "gum-js-loop", "gadget")
        if (findings.none { it == "frida-injected" }) {
            val found = mapsLines.any { line ->
                fridaPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) }
            }
            if (found) findings.add("frida-injected")
        }

        if (findings.none { it == "frida-server" }) {
            runCatching {
                if (File("/data/local/tmp/frida-server").exists()) findings.add("frida-server")
            }
        }
        if (findings.none { it == "frida-server" }) {
            runCatching {
                if (File("/data/local/tmp/re.frida.server").exists()) findings.add("frida-server")
            }
        }

        if (findings.none { it == "xposed-injected" }) {
            val found = mapsLines.any { line ->
                line.contains("xposed", ignoreCase = true)
            }
            if (found) findings.add("xposed-injected")
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

        return findings.toList()
    }
}