package com.chloemlla.clens.core.storage

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pending remote write types accepted by the local staging queue.
 * Import codecs should split batches using [StagingQueueRules.IMPORT_CHUNK_SIZE].
 */
enum class StagingOpType {
    INSERT,
    REPLACE,
    IMPORT_CHUNK,
}

enum class StagingStatus {
    PENDING,
    IN_FLIGHT,
    FAILED,
}

/**
 * One queued write / import chunk waiting for network sync.
 * Large payloads live in files; the on-disk index keeps metadata only.
 */
data class StagingItem(
    val id: String,
    val type: StagingOpType,
    val connectionId: String,
    val database: String,
    val collection: String,
    val payloadJson: String,
    val filterJson: String? = null,
    val dropBeforeImport: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val status: StagingStatus = StagingStatus.PENDING,
    val chunkIndex: Int = 0,
    val chunkCount: Int = 1,
)

/**
 * Capacity and import-chunk constants shared by store and pure helpers.
 */
object StagingQueueRules {
    const val MAX_QUEUE_ITEMS: Int = 50

    /** Import codecs should split batch imports into chunks of this size before enqueue. */
    const val IMPORT_CHUNK_SIZE: Int = 50

    fun canEnqueue(currentCount: Int): Boolean = currentCount < MAX_QUEUE_ITEMS

    fun ensureCanEnqueue(currentCount: Int) {
        if (!canEnqueue(currentCount)) {
            throw IllegalStateException(
                "暂存队列已满（最多 ${MAX_QUEUE_ITEMS} 项），请先清理后再试",
            )
        }
    }
}

/**
 * Pure JSON codec for staging index metadata and full items.
 * Index rows omit payloadJson so metadata stays small on disk.
 */
object StagingModelsCodec {
    fun itemToIndexJson(item: StagingItem): JSONObject {
        return JSONObject()
            .put("id", item.id)
            .put("type", item.type.name)
            .put("connectionId", item.connectionId)
            .put("database", item.database)
            .put("collection", item.collection)
            .put("filterJson", item.filterJson)
            .put("dropBeforeImport", item.dropBeforeImport)
            .put("createdAtMillis", item.createdAtMillis)
            .put("updatedAtMillis", item.updatedAtMillis)
            .put("attemptCount", item.attemptCount)
            .put("lastError", item.lastError)
            .put("status", item.status.name)
            .put("chunkIndex", item.chunkIndex)
            .put("chunkCount", item.chunkCount)
    }

    fun indexArrayToString(items: List<StagingItem>): String {
        val array = JSONArray()
        items.forEach { array.put(itemToIndexJson(it)) }
        return array.toString()
    }

    fun parseIndexArray(raw: String): List<StagingItem> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(parseIndexItem(obj))
            }
        }
    }

    fun parseIndexItem(obj: JSONObject): StagingItem {
        val filterRaw = obj.optString("filterJson", "")
        val filterJson = filterRaw.takeIf { it.isNotBlank() && it != "null" }
        val lastErrorRaw = obj.optString("lastError", "")
        val lastError = lastErrorRaw.takeIf { it.isNotBlank() && it != "null" }
        return StagingItem(
            id = obj.optString("id"),
            type = parseType(obj.optString("type", "INSERT")),
            connectionId = obj.optString("connectionId"),
            database = obj.optString("database"),
            collection = obj.optString("collection"),
            payloadJson = "",
            filterJson = filterJson,
            dropBeforeImport = obj.optBoolean("dropBeforeImport", false),
            createdAtMillis = obj.optLong("createdAtMillis", 0L),
            updatedAtMillis = obj.optLong("updatedAtMillis", 0L),
            attemptCount = obj.optInt("attemptCount", 0),
            lastError = lastError,
            status = parseStatus(obj.optString("status", "PENDING")),
            chunkIndex = obj.optInt("chunkIndex", 0),
            chunkCount = obj.optInt("chunkCount", 1),
        )
    }

    fun withPayload(meta: StagingItem, payloadJson: String): StagingItem =
        meta.copy(payloadJson = payloadJson)

    fun itemToFullJson(item: StagingItem): JSONObject {
        return itemToIndexJson(item).put("payloadJson", item.payloadJson)
    }

    fun parseFullItem(raw: String): StagingItem {
        val obj = JSONObject(raw)
        val base = parseIndexItem(obj)
        return base.copy(payloadJson = obj.optString("payloadJson", ""))
    }

    fun parseType(raw: String): StagingOpType {
        return runCatching { StagingOpType.valueOf(raw.trim().uppercase()) }
            .getOrDefault(StagingOpType.INSERT)
    }

    fun parseStatus(raw: String): StagingStatus {
        return runCatching { StagingStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(StagingStatus.PENDING)
    }
}
