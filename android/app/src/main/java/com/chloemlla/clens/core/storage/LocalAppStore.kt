package com.chloemlla.clens.core.storage

import android.content.Context
import androidx.core.content.edit
import com.chloemlla.clens.core.mongo.AuditLogEntry
import com.chloemlla.clens.core.mongo.ConnectionHealthData
import com.chloemlla.clens.core.mongo.QueryHistoryEntry
import com.chloemlla.clens.core.mongo.QueryFavoriteEntry
import com.chloemlla.clens.core.mongo.AggregateTemplateEntry
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class LocalAppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Build the storage key for per-collection sort preference.
     * Format: browse_sort:<connId>:<db>:<coll>
     */
    private fun browseSortKey(connectionId: String, database: String, collection: String): String {
        return "$KEY_BROWSE_SORT_PREFIX$connectionId:$database:$collection"
    }

    /**
     * Load saved sort for a collection, or null if none.
     * Returns a pair of (field, directionInt) where directionInt is 1 for asc, -1 for desc.
     */
    fun getBrowseSort(connectionId: String, database: String, collection: String): Pair<String, Int>? {
        val key = browseSortKey(connectionId, database, collection)
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val field = o.getString("field")
            val dir = o.getInt("direction")
            field to dir
        }.getOrNull()
    }

    /**
     * Save sort preference for a collection.
     */
    fun setBrowseSort(connectionId: String, database: String, collection: String, field: String, direction: Int) {
        val key = browseSortKey(connectionId, database, collection)
        val o = JSONObject()
            .put("field", field)
            .put("direction", direction)
        prefs.edit { putString(key, o.toString()) }
    }

    /**
     * Clear saved sort for a collection.
     */
    fun clearBrowseSort(connectionId: String, database: String, collection: String) {
        val key = browseSortKey(connectionId, database, collection)
        prefs.edit { remove(key) }
    }

    fun listQueryHistory(): List<QueryHistoryEntry> {
        val raw = prefs.getString(KEY_QUERY_HISTORY, "[]").orEmpty()
        return runCatching { parseHistory(raw) }.getOrDefault(emptyList())
    }

    fun addQueryHistory(entry: QueryHistoryEntry) {
        val next = (listOf(entry) + listQueryHistory())
            .distinctBy { it.modeAggregate to it.database to it.collection to it.filterJson to it.pipelineJson }
            .take(MAX_HISTORY)
        writeHistory(next)
    }

    fun listQueryFavorites(): List<QueryFavoriteEntry> {
        val raw = prefs.getString(KEY_QUERY_FAVORITES, "[]").orEmpty()
        return runCatching { parseFavorites(raw) }.getOrDefault(emptyList())
    }

    fun saveQueryFavorite(entry: QueryFavoriteEntry) {
        val name = entry.name.trim()
        require(name.isNotBlank()) { "收藏名称不能为空" }
        val normalized = entry.copy(name = name)
        val existing = listQueryFavorites()
        val withoutSameId = existing.filterNot { it.id == normalized.id }
        val next = (listOf(normalized) + withoutSameId)
            .distinctBy { it.name.lowercase() to it.database to it.collection to it.filterJson to it.sortJson to it.projectionJson to it.modeAggregate to it.pipelineJson }
            .take(MAX_FAVORITES)
        writeFavorites(next)
    }

    fun deleteQueryFavorite(id: String) {
        writeFavorites(listQueryFavorites().filterNot { it.id == id })
    }

    fun listAuditLog(): List<AuditLogEntry> {
        val raw = prefs.getString(KEY_AUDIT_LOG, "[]").orEmpty()
        return runCatching { parseAudit(raw) }.getOrDefault(emptyList())
    }

    fun addAudit(action: String, target: String, detail: String = "") {
        val entry = AuditLogEntry(
            id = UUID.randomUUID().toString(),
            action = action,
            target = target,
            detail = detail,
        )
        val next = (listOf(entry) + listAuditLog()).take(MAX_AUDIT)
        writeAudit(next)
    }

    fun clearAuditLog() {
        prefs.edit { putString(KEY_AUDIT_LOG, "[]") }
    }

    fun listAggregateTemplates(): List<AggregateTemplateEntry> {
        val raw = prefs.getString(KEY_SAVED_AGGREGATES, "[]").orEmpty()
        return runCatching { parseAggregateTemplates(raw) }.getOrDefault(emptyList())
    }

    fun saveAggregateTemplate(entry: AggregateTemplateEntry) {
        val normalized = entry.copy(updatedAtMillis = System.currentTimeMillis())
        val existing = listAggregateTemplates()
        val withoutSameId = existing.filterNot { it.id == normalized.id }
        val next = (listOf(normalized) + withoutSameId).take(MAX_AGGREGATE_TEMPLATES)
        writeAggregateTemplates(next)
    }

    fun deleteAggregateTemplate(id: String) {
        writeAggregateTemplates(listAggregateTemplates().filterNot { it.id == id })
    }

    fun updateAggregateTemplate(entry: AggregateTemplateEntry) {
        val updated = entry.copy(updatedAtMillis = System.currentTimeMillis())
        saveAggregateTemplate(updated)
    }

    private fun writeAggregateTemplates(items: List<AggregateTemplateEntry>) {
        val top = JSONObject().put("version", AGGREGATE_TEMPLATES_VERSION)
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("description", item.description)
                    .put("connectionId", item.connectionId ?: JSONObject.NULL)
                    .put("pipelineJson", item.pipelineJson)
                    .put("createdAtMillis", item.createdAtMillis)
                    .put("updatedAtMillis", item.updatedAtMillis),
            )
        }
        top.put("templates", array)
        prefs.edit { putString(KEY_SAVED_AGGREGATES, top.toString()) }
    }

    private fun parseAggregateTemplates(raw: String): List<AggregateTemplateEntry> {
        return try {
            val top = JSONObject(raw)
            val array = if (top.has("templates")) {
                top.getJSONArray("templates")
            } else {
                // Legacy flat array
                JSONArray(raw)
            }
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        AggregateTemplateEntry(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            name = o.optString("name"),
                            description = o.optString("description", ""),
                            connectionId = if (o.has("connectionId") && !o.isNull("connectionId")) {
                                o.getString("connectionId")
                            } else {
                                null
                            },
                            pipelineJson = o.optString("pipelineJson", "[]"),
                            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                            updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isVerticalCatalogListsEnabled(): Boolean =
        prefs.getBoolean(KEY_VERTICAL_CATALOG_LISTS, false)

    fun setVerticalCatalogListsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_VERTICAL_CATALOG_LISTS, enabled) }
    }

    fun isSqlGuideSeen(): Boolean =
        prefs.getBoolean(KEY_SQL_GUIDE_SEEN, false)

    fun setSqlGuideSeen(seen: Boolean) {
        prefs.edit { putBoolean(KEY_SQL_GUIDE_SEEN, seen) }
    }

    /**
     * Returns the last measured latency (ms) for [connectionId], or null if not yet measured.
     * The timestamp is embedded in the stored value as a suffix after a pipe.
     */
    fun getLatencyMs(connectionId: String): LatencyMeasurement? {
        val raw = prefs.getString(keyLatency(connectionId), null) ?: return null
        return runCatching { parseLatency(raw) }.getOrNull()
    }

    /**
     * Stores [latencyMs] for [connectionId] with the current timestamp.
     */
    fun setLatencyMs(connectionId: String, latencyMs: Long) {
        prefs.edit {
            putString(keyLatency(connectionId), "${latencyMs}|${System.currentTimeMillis()}")
        }
    }

    /**
     * Returns true when the stored latency for [connectionId] is older than [maxAgeMs].
     * Returns false if no measurement exists.
     */
    fun isLatencyStale(connectionId: String, maxAgeMs: Long = STALE_LATENCY_THRESHOLD_MS): Boolean {
        val measurement = getLatencyMs(connectionId) ?: return false
        return System.currentTimeMillis() - measurement.timestampMs > maxAgeMs
    }

    private fun keyLatency(connectionId: String) = "connection_latency:$connectionId"

    private fun parseLatency(raw: String): LatencyMeasurement? {
        val parts = raw.split('|', limit = 2)
        if (parts.size != 2) return null
        val latencyMs = parts[0].toLongOrNull() ?: return null
        val timestampMs = parts[1].toLongOrNull() ?: return null
        return LatencyMeasurement(latencyMs = latencyMs, timestampMs = timestampMs)
    }

    private fun writeHistory(items: List<QueryHistoryEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("modeAggregate", item.modeAggregate)
                    .put("database", item.database)
                    .put("collection", item.collection)
                    .put("filterJson", item.filterJson)
                    .put("sortJson", item.sortJson)
                    .put("projectionJson", item.projectionJson)
                    .put("pipelineJson", item.pipelineJson)
                    .put("createdAtMillis", item.createdAtMillis),
            )
        }
        prefs.edit { putString(KEY_QUERY_HISTORY, array.toString()) }
    }

    private fun parseHistory(raw: String): List<QueryHistoryEntry> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    QueryHistoryEntry(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        modeAggregate = o.optBoolean("modeAggregate", false),
                        database = o.optString("database"),
                        collection = o.optString("collection"),
                        filterJson = o.optString("filterJson", "{}"),
                        sortJson = o.optString("sortJson", "{}"),
                        projectionJson = o.optString("projectionJson", "{}"),
                        pipelineJson = o.optString("pipelineJson", "[]"),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun writeFavorites(items: List<QueryFavoriteEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("database", item.database)
                    .put("collection", item.collection)
                    .put("filterJson", item.filterJson)
                    .put("sortJson", item.sortJson)
                    .put("projectionJson", item.projectionJson)
                    .put("modeAggregate", item.modeAggregate)
                    .put("pipelineJson", item.pipelineJson)
                    .put("createdAtMillis", item.createdAtMillis),
            )
        }
        prefs.edit { putString(KEY_QUERY_FAVORITES, array.toString()) }
    }

    private fun parseFavorites(raw: String): List<QueryFavoriteEntry> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    QueryFavoriteEntry(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name"),
                        database = o.optString("database"),
                        collection = o.optString("collection"),
                        filterJson = o.optString("filterJson", "{}"),
                        sortJson = o.optString("sortJson", "{}"),
                        projectionJson = o.optString("projectionJson", "{}"),
                        modeAggregate = o.optBoolean("modeAggregate", false),
                        pipelineJson = o.optString("pipelineJson", "[]"),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun writeAudit(items: List<AuditLogEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("action", item.action)
                    .put("target", item.target)
                    .put("detail", item.detail)
                    .put("createdAtMillis", item.createdAtMillis),
            )
        }
        prefs.edit { putString(KEY_AUDIT_LOG, array.toString()) }
    }

    private fun parseAudit(raw: String): List<AuditLogEntry> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    AuditLogEntry(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        action = o.optString("action"),
                        target = o.optString("target"),
                        detail = o.optString("detail"),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    fun getConnectionHealthData(connectionId: String): ConnectionHealthData {
        val raw = prefs.getString(keyHealth(connectionId), null) ?: return ConnectionHealthData(connectionId)
        return runCatching { parseHealthData(connectionId, raw) }.getOrDefault(ConnectionHealthData(connectionId))
    }

    fun saveConnectionHealthData(data: ConnectionHealthData) {
        val json = buildHealthJson(data)
        prefs.edit { putString(keyHealth(data.connectionId), json) }
    }

    fun clearConnectionHealthData(connectionId: String) {
        prefs.edit { remove(keyHealth(connectionId)) }
    }

    private fun keyHealth(connectionId: String) = "connection_health:$connectionId"

    private fun buildHealthJson(data: ConnectionHealthData): String {
        val obj = JSONObject()
            .put("connectionId", data.connectionId)
            .put("latency", JSONArray(data.latencySamples))
            .put("errors", JSONArray(data.errorSamples))
            .put("timestamps", JSONArray(data.timestamps))
            .put("connectedAtMillis", data.connectedAtMillis)
        return obj.toString()
    }

    private fun parseHealthData(connectionId: String, raw: String): ConnectionHealthData {
        val o = JSONObject(raw)
        val latencyArray = o.optJSONArray("latency") ?: JSONArray()
        val errorsArray = o.optJSONArray("errors") ?: JSONArray()
        val timestampsArray = o.optJSONArray("timestamps") ?: JSONArray()
        val latency = mutableListOf<Long>()
        val errors = mutableListOf<Boolean>()
        val timestamps = mutableListOf<Long>()
        for (i in 0 until latencyArray.length()) {
            latency.add(latencyArray.getLong(i))
        }
        for (i in 0 until errorsArray.length()) {
            errors.add(errorsArray.getBoolean(i))
        }
        for (i in 0 until timestampsArray.length()) {
            timestamps.add(timestampsArray.getLong(i))
        }
        return ConnectionHealthData(
            connectionId = o.optString("connectionId", connectionId),
            latencySamples = latency,
            errorSamples = errors,
            timestamps = timestamps,
            connectedAtMillis = o.optLong("connectedAtMillis", System.currentTimeMillis()),
        )
    }

    private companion object {
        const val PREFS = "clens_local_app_store"
        const val KEY_QUERY_HISTORY = "query_history_json"
        const val KEY_QUERY_FAVORITES = "query_favorites_json"
        const val KEY_AUDIT_LOG = "audit_log_json"
        const val KEY_SAVED_AGGREGATES = "saved_aggregates"
        const val KEY_VERTICAL_CATALOG_LISTS = "vertical_catalog_lists"
        const val KEY_SQL_GUIDE_SEEN = "sql_query_guide_seen"
        const val KEY_BROWSE_SORT_PREFIX = "browse_sort:"
        const val MAX_HISTORY = 20
        const val MAX_FAVORITES = 50
        const val MAX_AUDIT = 100
        const val MAX_AGGREGATE_TEMPLATES = 100
        const val AGGREGATE_TEMPLATES_VERSION = 1
        const val STALE_LATENCY_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }

    data class LatencyMeasurement(
        val latencyMs: Long,
        val timestampMs: Long,
    )
}
