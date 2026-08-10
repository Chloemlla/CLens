package com.chloemlla.clens.ui.security

import android.content.Context
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
            val pid = readTracerPid()
            if (pid != null && pid > 0) {
                threats += "process-traced:pid=$pid"
            }
        }

        return threats
    }

    private fun readTracerPid(): Int? {
        return runCatching {
            val file = File("/proc/self/status")
            if (!file.exists() || !file.canRead()) return@runCatching null

            BufferedReader(FileReader(file)).use { reader ->
                var pid: Int? = null
                for (line in reader) {
                    if (line.startsWith("TracerPid:")) {
                        val trimmed = line.removePrefix("TracerPid:").trim()
                        pid = trimmed.toIntOrNull()
                        break
                    }
                }
                pid
            }
        }.getOrNull()
    }
}