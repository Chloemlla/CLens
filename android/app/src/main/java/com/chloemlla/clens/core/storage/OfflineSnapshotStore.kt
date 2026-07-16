package com.chloemlla.clens.core.storage

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Named offline read-only snapshot of the first N documents for a filter.
 *
 * Documents are stored as JSONL under [Context.getFilesDir]; metadata index stays
 * in SharedPreferences only (never full documents).
 */
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

class OfflineSnapshotStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val rootDir: File = File(appContext.filesDir, SNAPSHOT_DIR).also { it.mkdirs() }

    /**
     * Persist a new snapshot. Document count is hard-capped at [HARD_CAP].
     * [limit] defaults to [DEFAULT_LIMIT] and is clamped into `1..HARD_CAP`.
     */
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
        require(connectionId.isNotBlank()) { "connectionId 不能为空" }
        require(database.isNotBlank()) { "database 不能为空" }
        require(collection.isNotBlank()) { "collection 不能为空" }
        require(snapshotId.isNotBlank()) { "snapshotId 不能为空" }

        val safeLimit = clampLimit(limit)
        val cappedDocs = validateAndCapDocuments(documents)
        val finalName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultName(database, collection, createdAtMillis)

        val meta = OfflineSnapshotMeta(
            snapshotId = snapshotId,
            name = finalName,
            connectionId = connectionId,
            database = database,
            collection = collection,
            filterJson = filterJson.ifBlank { "{}" },
            limit = safeLimit,
            createdAtMillis = createdAtMillis,
            documentCount = cappedDocs.size,
        )

        val target = documentFile(meta.snapshotId)
        try {
            writeJsonl(target, cappedDocs)
        } catch (e: Exception) {
            target.delete()
            throw IOException("写入离线快照失败: ${e.message}", e)
        }

        try {
            upsertMeta(meta)
        } catch (e: Exception) {
            target.delete()
            throw IOException("写入离线快照索引失败: ${e.message}", e)
        }
        return meta
    }

    fun list(
        connectionId: String? = null,
        database: String? = null,
        collection: String? = null,
    ): List<OfflineSnapshotMeta> {
        return loadAllMeta()
            .asSequence()
            .filter { connectionId == null || it.connectionId == connectionId }
            .filter { database == null || it.database == database }
            .filter { collection == null || it.collection == collection }
            .sortedByDescending { it.createdAtMillis }
            .toList()
    }

    fun loadDocuments(snapshotId: String): List<String> {
        require(snapshotId.isNotBlank()) { "snapshotId 不能为空" }
        val file = documentFile(snapshotId)
        if (!file.exists()) {
            throw IOException("离线快照文件不存在: $snapshotId")
        }
        return try {
            file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        } catch (e: Exception) {
            throw IOException("读取离线快照失败: ${e.message}", e)
        }
    }

    fun delete(snapshotId: String) {
        require(snapshotId.isNotBlank()) { "snapshotId 不能为空" }
        documentFile(snapshotId).delete()
        val remaining = loadAllMeta().filterNot { it.snapshotId == snapshotId }
        writeAllMeta(remaining)
    }

    fun rename(snapshotId: String, newName: String): OfflineSnapshotMeta {
        require(snapshotId.isNotBlank()) { "snapshotId 不能为空" }
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "快照名称不能为空" }
        val current = loadAllMeta()
        val index = current.indexOfFirst { it.snapshotId == snapshotId }
        if (index < 0) {
            throw IllegalArgumentException("找不到离线快照: $snapshotId")
        }
        val updated = current[index].copy(name = trimmed)
        val next = current.toMutableList().also { it[index] = updated }
        writeAllMeta(next)
        return updated
    }

    private fun documentFile(snapshotId: String): File = File(rootDir, documentFileName(snapshotId))

    private fun upsertMeta(meta: OfflineSnapshotMeta) {
        val next = loadAllMeta().filterNot { it.snapshotId == meta.snapshotId } + meta
        writeAllMeta(next)
    }

    private fun loadAllMeta(): List<OfflineSnapshotMeta> {
        val raw = prefs.getString(KEY_INDEX, null) ?: return emptyList()
        return runCatching { parseIndex(raw) }.getOrDefault(emptyList())
    }

    private fun writeAllMeta(items: List<OfflineSnapshotMeta>) {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        prefs.edit { putString(KEY_INDEX, array.toString()) }
    }

    companion object {
        const val PREFS = "clens_offline_snapshots"
        const val KEY_INDEX = "index"
        const val SNAPSHOT_DIR = "offline_snapshots"
        const val DEFAULT_LIMIT = 100
        const val HARD_CAP = 500

        fun clampLimit(limit: Int): Int {
            if (limit < 1) return DEFAULT_LIMIT
            return limit.coerceAtMost(HARD_CAP)
        }

        /**
         * Reject oversized payloads with a clear message; otherwise return the capped list.
         */
        fun validateAndCapDocuments(documents: List<String>, hardCap: Int = HARD_CAP): List<String> {
            if (documents.size > hardCap) {
                throw IllegalArgumentException("文档数量超过上限 $hardCap（当前 ${documents.size}）")
            }
            return documents
        }

        fun defaultName(
            database: String,
            collection: String,
            createdAtMillis: Long,
            locale: Locale = Locale.getDefault(),
        ): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date(createdAtMillis))
            return "$database.$collection $stamp"
        }

        fun documentFileName(snapshotId: String): String = "$snapshotId.jsonl"

        fun documentRelativePath(snapshotId: String): String =
            "$SNAPSHOT_DIR/${documentFileName(snapshotId)}"

        private fun toJson(meta: OfflineSnapshotMeta): JSONObject {
            return JSONObject()
                .put("snapshotId", meta.snapshotId)
                .put("name", meta.name)
                .put("connectionId", meta.connectionId)
                .put("database", meta.database)
                .put("collection", meta.collection)
                .put("filterJson", meta.filterJson)
                .put("limit", meta.limit)
                .put("createdAtMillis", meta.createdAtMillis)
                .put("documentCount", meta.documentCount)
        }

        private fun parseIndex(raw: String): List<OfflineSnapshotMeta> {
            val array = JSONArray(raw)
            val out = ArrayList<OfflineSnapshotMeta>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("snapshotId").orEmpty()
                if (id.isBlank()) continue
                out += OfflineSnapshotMeta(
                    snapshotId = id,
                    name = obj.optString("name", id),
                    connectionId = obj.optString("connectionId"),
                    database = obj.optString("database"),
                    collection = obj.optString("collection"),
                    filterJson = obj.optString("filterJson", "{}").ifBlank { "{}" },
                    limit = obj.optInt("limit", DEFAULT_LIMIT),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L),
                    documentCount = obj.optInt("documentCount", 0),
                )
            }
            return out
        }

        private fun writeJsonl(file: File, documents: List<String>) {
            file.parentFile?.mkdirs()
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                documents.forEachIndexed { index, line ->
                    // Store each document as a single JSONL row (caller supplies JSON text).
                    writer.append(line.replace("\r\n", "\n").replace('\n', ' ').trim())
                    if (index < documents.lastIndex) {
                        writer.append('\n')
                    }
                }
                if (documents.isNotEmpty()) {
                    writer.append('\n')
                }
            }
        }
    }
}
