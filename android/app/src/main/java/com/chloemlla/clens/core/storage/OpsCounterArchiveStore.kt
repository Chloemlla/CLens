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
            trimFile(file, MAX_POINTS_PER_CONNECTION)
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
        val points = mutableListOf<OpsCounterPoint>()
        synchronized(this) {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine
                val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return@forEachLine
                val ts = obj.optLong("ts")
                if (sinceMillis != null && ts < sinceMillis) return@forEachLine
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
        }
        if (points.isEmpty()) return null
        val window = if (points.size > maxPoints) points.takeLast(maxPoints) else points
        val current = window.last()
        return OpsCounterSampleState(
            points = window,
            current = current,
            peak = OpsCounterPeak(
                insertQps = window.maxOf { it.insertQps },
                queryQps = window.maxOf { it.queryQps },
                updateQps = window.maxOf { it.updateQps },
                deleteQps = window.maxOf { it.deleteQps },
            ),
            connectionsCurrent = current.connectionsCurrent,
            connectionsActive = current.connectionsActive,
            connectionsAvailable = current.connectionsAvailable,
        )
    }

    fun clear(connectionId: String) {
        if (connectionId.isBlank()) return
        synchronized(this) {
            fileFor(connectionId).delete()
        }
    }

    private fun fileFor(connectionId: String): File {
        val safe = connectionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(root, "$safe.jsonl")
    }

    private fun trimFile(file: File, maxPoints: Int) {
        val lines = file.readLines()
        if (lines.size <= maxPoints) return
        val kept = lines.takeLast(maxPoints)
        file.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    companion object {
        const val MAX_POINTS_PER_CONNECTION = 5_000
        const val DEFAULT_QUERY_POINTS = 288 // ~24h at 5s if continuous; practical window
    }
}
