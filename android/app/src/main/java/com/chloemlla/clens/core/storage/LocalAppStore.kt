package com.chloemlla.clens.core.storage

import android.content.Context
import androidx.core.content.edit
import com.chloemlla.clens.core.mongo.AuditLogEntry
import com.chloemlla.clens.core.mongo.QueryHistoryEntry
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class LocalAppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    fun isVerticalCatalogListsEnabled(): Boolean =
        prefs.getBoolean(KEY_VERTICAL_CATALOG_LISTS, false)

    fun setVerticalCatalogListsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_VERTICAL_CATALOG_LISTS, enabled) }
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

    private companion object {
        const val PREFS = "clens_local_app_store"
        const val KEY_QUERY_HISTORY = "query_history_json"
        const val KEY_AUDIT_LOG = "audit_log_json"
        const val KEY_VERTICAL_CATALOG_LISTS = "vertical_catalog_lists"
        const val MAX_HISTORY = 30
        const val MAX_AUDIT = 100
    }
}
