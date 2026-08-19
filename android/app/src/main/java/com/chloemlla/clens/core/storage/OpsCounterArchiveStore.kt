package com.chloemlla.clens.core.storage

import android.content.Context
import com.chloemlla.clens.core.mongo.OpsCounterPeak
import com.chloemlla.clens.core.mongo.OpsCounterPoint
import com.chloemlla.clens.core.mongo.OpsCounterSampleState
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cross-session ops counter archive.
 * Append-only JSONL files under app filesDir, retained by max points per connection.
 */
class OpsCounterArchiveStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "ops_archive").apply { mkdirs() }

    /** Known line count per archive file path. Guarded by the same lock as file access. */
    private val lineCounts = mutableMapOf<String, Int>()

    fun append(connectionId: String, point: OpsCounterPoint) {
        if (connectionId.isBlank()) return
        val file = fileFor(connectionId)
        val line = JSONObject()
            .put("ts", point.timestampMillis)
            .put("insertQps", point.insertQps)
            .put("queryQps", point.queryQps)
            .put("updateQps", point.updateQps)
            .put("deleteQps", point.deleteQps)
            .put("connectionsCurrent", point.connectionsCurrent)
            .put("connectionsActive", point.connectionsActive)
            .put("connectionsAvailable", point.connectionsAvailable)
            .toString()
        synchronized(this) {
            file.appendText(line + "\n")
            // Trimming used to run on every append, so each sample read the whole archive
            // back (up to MAX_POINTS_PER_CONNECTION lines) only to learn its size. Track
            // the size in memory instead and rewrite the file only after the overshoot
            // slack is used up, which bounds the file at MAX + TRIM_SLACK lines.
            val key = file.path
            val projected = lineCounts[key]?.let { it + 1 } ?: countLines(file)
            lineCounts[key] = if (projected > MAX_POINTS_PER_CONNECTION + TRIM_SLACK) {
                trimFile(file, MAX_POINTS_PER_CONNECTION)
            } else {
                projected
            }
        }
    }

    fun load(
        connectionId: String,
        maxPoints: Int = DEFAULT_QUERY_POINTS,
        sinceMillis: Long? = null,
    ): OpsCounterSampleState? {
        if (connectionId.isBlank()) return null
        val file = fileFor(connectionId)
        if (!file.exists()) return null
        // Only the newest maxPoints entries can survive into the result, so stream the
        // file into a bounded window of raw lines and parse JSON for that window only.
        // The archive holds up to MAX_POINTS_PER_CONNECTION lines; parsing all of them on
        // every refresh was pure waste. Timestamps grow monotonically in an append-only
        // log, so applying sinceMillis after windowing selects the same entries as before.
        val cap = maxPoints.coerceAtLeast(1)
        val rawWindow = ArrayDeque<String>()
        synchronized(this) {
            file.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    rawWindow.addLast(trimmed)
                    if (rawWindow.size > cap) rawWindow.removeFirst()
                }
            }
        }
        val points = mutableListOf<OpsCounterPoint>()
        for (raw in rawWindow) {
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val ts = obj.optLong("ts")
            if (sinceMillis != null && ts < sinceMillis) continue
            points += OpsCounterPoint(
                timestampMillis = ts,
                insertQps = obj.optDouble("insertQps"),
                queryQps = obj.optDouble("queryQps"),
                updateQps = obj.optDouble("updateQps"),
                deleteQps = obj.optDouble("deleteQps"),
                connectionsCurrent = obj.optInt("connectionsCurrent"),
                connectionsActive = obj.optInt("connectionsActive"),
                connectionsAvailable = obj.optInt("connectionsAvailable"),
            )
        }
        if (points.isEmpty()) return null
        val current = points.last()
        return OpsCounterSampleState(
            points = points,
            current = current,
            peak = OpsCounterPeak(
                insertQps = points.maxOf { it.insertQps },
                queryQps = points.maxOf { it.queryQps },
                updateQps = points.maxOf { it.updateQps },
                deleteQps = points.maxOf { it.deleteQps },
            ),
            connectionsCurrent = current.connectionsCurrent,
            connectionsActive = current.connectionsActive,
            connectionsAvailable = current.connectionsAvailable,
        )
    }

    fun clear(connectionId: String) {
        if (connectionId.isBlank()) return
        synchronized(this) {
            val file = fileFor(connectionId)
            file.delete()
            lineCounts.remove(file.path)
        }
    }

    private fun fileFor(connectionId: String): File {
        val safe = connectionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(root, "$safe.jsonl")
    }

    private fun countLines(file: File): Int {
        if (!file.exists()) return 0
        return file.useLines { lines -> lines.count { it.isNotBlank() } }
    }

    /** Rewrites [file] down to at most [maxPoints] lines and returns the resulting count. */
    private fun trimFile(file: File, maxPoints: Int): Int {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.size <= maxPoints) return lines.size
        val kept = lines.takeLast(maxPoints)
        file.writeText(kept.joinToString("\n", postfix = "\n"))
        return kept.size
    }

    companion object {
        const val MAX_POINTS_PER_CONNECTION = 5_000
        const val DEFAULT_QUERY_POINTS = 288 // ~24h at 5s if continuous; practical window

        /** Lines the archive may exceed the cap by before a trim rewrite is worth its IO. */
        private const val TRIM_SLACK = 256
    }
}
