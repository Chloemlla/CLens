package com.chloemlla.clens.ui.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object AntiDebugGuard {
    fun assess(context: Context): List<String> {
        val threats = mutableListOf<String>()

        runCatching {
            if (Debug.isDebuggerConnected()) {
                threats += "debugger-connected"
            }
        }

        runCatching {
            if (Debug.waitingForDebugger()) {
                threats += "debugger-waiting"
            }
        }

        runCatching {
            val pid = readTracerPid("/proc/self/status")
            if (pid != null && pid > 0) {
                threats += "process-traced:pid=$pid"
            }
        }

        // A tracer attached to any worker thread is still a live tracer.
        runCatching {
            File("/proc/self/task").listFiles()?.forEach { task ->
                val pid = readTracerPid(File(task, "status").absolutePath)
                if (pid != null && pid > 0) {
                    threats += "thread-traced:${task.name}:pid=$pid"
                }
            }
        }

        // A release app running debuggable has been tampered with.
        runCatching {
            if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                threats += "app-debuggable"
            }
        }

        runCatching {
            if (systemProperty("ro.debuggable") == "1") {
                threats += "system-debuggable"
            }
        }

        // LD_PRELOAD forces a library into every spawned process.
        runCatching {
            val environ = File("/proc/self/environ")
            if (environ.canRead() && environ.readText().contains("LD_PRELOAD=")) {
                threats += "ld-preload"
            }
        }

        return threats
    }

    private fun readTracerPid(path: String): Int? {
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return@runCatching null

            BufferedReader(FileReader(file)).use { reader ->
                var pid: Int? = null
                for (line in reader.lineSequence()) {
                    if (line.startsWith("TracerPid:")) {
                        pid = line.removePrefix("TracerPid:").trim().toIntOrNull()
                        break
                    }
                }
                pid
            }
        }.getOrNull()
    }

    private fun systemProperty(key: String): String? {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        }.getOrNull()
    }
}
