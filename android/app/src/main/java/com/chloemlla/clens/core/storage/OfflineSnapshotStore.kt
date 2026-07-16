package com.chloemlla.clens.core.storage

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class OfflineSnapshotMeta(
    val snapshotId: String,
    val name: String,
    val connectionId: String,
    val database: String,
    val collection: String,
    val filterJson: String,
    val limit: Int,
    val createdAtMillis: Long,
    val documentCount: Int,
)

/**
 * File-backed offline snapshots.
 * Documents: filesDir/offline_snapshots/<id>.jsonl
 * Index: filesDir/offline_snapshots/index.json (small metadata only)
 */
class OfflineSnapshotStore(context: Context) {
    private val rootDir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
    private val indexFile = File(rootDir, INDEX_FILE)

    fun save(
        name: String? = null,
        connectionId: String,
        database: String,
        collection: String,
        filterJson: String = "{}",
        limit: Int = DEFAULT_LIMIT,
        documents: List<String>,
        createdAtMillis: Long = System.currentTimeMillis(),
        snapshotId: String = UUID.randomUUID().toString(),
    ): OfflineSnapshotMeta {
        val cappedLimit = clampLimit(limit)
        val cappedDocs = validateAndCapDocuments(documents)
        val toStore = if (cappedDocs.size > cappedLimit) cappedDocs.take(cappedLimit) else cappedDocs
        val meta = OfflineSnapshotMeta(
            snapshotId = snapshotId,
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName(database, collection, createdAtMillis),
            connectionId = connectionId,
            database = database,
            collection = collection,
            filterJson = filterJson.ifBlank { "{}" },
            limit = cappedLimit,
            createdAtMillis = createdAtMillis,
            documentCount = toStore.size,
        )
        File(rootDir, documentFileName(snapshotId)).writeText(toStore.joinToString("\n"), Charsets.UTF_8)
        val all = listAll().filterNot { it.snapshotId == snapshotId }.toMutableList()
        all.add(0, meta)
        writeIndex(all)
        return meta
    }

    fun list(
        connectionId: String? = null,
        database: String? = null,
        collection: String? = null,
    ): List<OfflineSnapshotMeta> {
        return listAll().filter { meta ->
            (connectionId == null || meta.connectionId == connectionId) &&
                (database == null || meta.database == database) &&
                (collection == null || meta.collection == collection)
        }
    }

    fun loadDocuments(snapshotId: String): List<String> {
        val file = File(rootDir, documentFileName(snapshotId))
        if (!file.exists()) {
            throw IllegalArgumentException("快照不存在或文档文件缺失")
        }
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        return text.split('\n').filter { it.isNotBlank() }
    }

    fun delete(snapshotId: String) {
        File(rootDir, documentFileName(snapshotId)).delete()
        writeIndex(listAll().filterNot { it.snapshotId == snapshotId })
    }

    fun rename(snapshotId: String, newName: String): OfflineSnapshotMeta {
        val name = newName.trim()
        if (name.isEmpty()) throw IllegalArgumentException("快照名称不能为空")
        var updated: OfflineSnapshotMeta? = null
        val next = listAll().map {
            if (it.snapshotId == snapshotId) {
                it.copy(name = name).also { meta -> updated = meta }
            } else {
                it
            }
        }
        if (updated == null) throw IllegalArgumentException("快照不存在")
        writeIndex(next)
        return updated!!
    }

    private fun listAll(): List<OfflineSnapshotMeta> {
        if (!indexFile.exists()) return emptyList()
        val raw = indexFile.readText(Charsets.UTF_8)
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val out = ArrayList<OfflineSnapshotMeta>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                OfflineSnapshotMeta(
                    snapshotId = obj.optString("snapshotId"),
                    name = obj.optString("name"),
                    connectionId = obj.optString("connectionId"),
                    database = obj.optString("database"),
                    collection = obj.optString("collection"),
                    filterJson = obj.optString("filterJson", "{}"),
                    limit = obj.optInt("limit", DEFAULT_LIMIT),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L),
                    documentCount = obj.optInt("documentCount", 0),
                ),
            )
        }
        return out
    }

    private fun writeIndex(items: List<OfflineSnapshotMeta>) {
        val array = JSONArray()
        items.forEach { meta ->
            array.put(
                JSONObject()
                    .put("snapshotId", meta.snapshotId)
                    .put("name", meta.name)
                    .put("connectionId", meta.connectionId)
                    .put("database", meta.database)
                    .put("collection", meta.collection)
                    .put("filterJson", meta.filterJson)
                    .put("limit", meta.limit)
                    .put("createdAtMillis", meta.createdAtMillis)
                    .put("documentCount", meta.documentCount),
            )
        }
        indexFile.writeText(array.toString(), Charsets.UTF_8)
    }

    companion object {
        const val DIR_NAME = "offline_snapshots"
        const val INDEX_FILE = "index.json"
        const val DEFAULT_LIMIT = 100
        const val HARD_CAP = 500

        fun clampLimit(limit: Int): Int {
            if (limit <= 0) return DEFAULT_LIMIT
            return limit.coerceAtMost(HARD_CAP)
        }

        fun validateAndCapDocuments(documents: List<String>): List<String> {
            if (documents.size > HARD_CAP) {
                throw IllegalArgumentException(
                    "快照文档数 ${documents.size} 超过硬上限 $HARD_CAP，请降低 limit 后重试",
                )
            }
            return documents
        }

        fun defaultName(
            database: String,
            collection: String,
            createdAtMillis: Long = System.currentTimeMillis(),
            locale: Locale = Locale.getDefault(),
        ): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", locale)
            return database + "." + collection + " " + fmt.format(Date(createdAtMillis))
        }

        fun documentFileName(snapshotId: String): String = "$snapshotId.jsonl"

        fun documentRelativePath(snapshotId: String): String = "$DIR_NAME/${documentFileName(snapshotId)}"
    }
}
