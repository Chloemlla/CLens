package com.chloemlla.clens.core.storage

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * File-backed pending sync queue for document insert/replace and import chunks.
 *
 * Layout under app filesDir/staging_queue/:
 * - index.json : small metadata array (no large payloads)
 * - {id}.payload.json : payload body
 *
 * Cap: [StagingQueueRules.MAX_QUEUE_ITEMS] / [MAX_ITEMS].
 * Import chunk size documented as [IMPORT_CHUNK_SIZE] (chunking is done by import codecs).
 */
class StagingQueueStore(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val rootDir: File =
        File(context.applicationContext.filesDir, DIR_NAME).also { it.mkdirs() }
    private val indexFile: File = File(rootDir, INDEX_FILE)
    private val lock = Any()

    fun count(): Int {
        synchronized(lock) {
            return readIndex().size
        }
    }

    fun list(): List<StagingItem> {
        synchronized(lock) {
            return loadAll().sortedBy { it.createdAtMillis }
        }
    }

    fun peekReady(): List<StagingItem> {
        synchronized(lock) {
            return loadAll()
                .filter {
                    it.status == StagingStatus.PENDING || it.status == StagingStatus.FAILED
                }
                .sortedBy { it.createdAtMillis }
        }
    }

    fun enqueue(
        type: StagingOpType,
        connectionId: String,
        database: String,
        collection: String,
        payloadJson: String,
        filterJson: String? = null,
        dropBeforeImport: Boolean = false,
        chunkIndex: Int = 0,
        chunkCount: Int = 1,
    ): StagingItem {
        synchronized(lock) {
            val current = readIndex()
            StagingQueueRules.ensureCanEnqueue(current.size)
            val id = idGenerator().ifBlank { UUID.randomUUID().toString() }
            val now = clock()
            val item = StagingItem(
                id = id,
                type = type,
                connectionId = connectionId,
                database = database,
                collection = collection,
                payloadJson = payloadJson,
                filterJson = filterJson?.takeIf { it.isNotBlank() },
                dropBeforeImport = dropBeforeImport,
                createdAtMillis = now,
                updatedAtMillis = now,
                attemptCount = 0,
                lastError = null,
                status = StagingStatus.PENDING,
                chunkIndex = chunkIndex,
                chunkCount = chunkCount.coerceAtLeast(1),
            )
            writePayload(id, payloadJson)
            writeIndex(current + item.copy(payloadJson = ""))
            return item
        }
    }

    fun get(id: String): StagingItem? {
        synchronized(lock) {
            val meta = readIndex().firstOrNull { it.id == id } ?: return null
            return StagingModelsCodec.withPayload(meta, readPayload(id))
        }
    }

    fun updateStatus(
        id: String,
        status: StagingStatus,
        lastError: String? = null,
        incrementAttempt: Boolean = false,
    ): StagingItem? {
        synchronized(lock) {
            val all = readIndex()
            val index = all.indexOfFirst { it.id == id }
            if (index < 0) return null
            val current = all[index]
            val updated = current.copy(
                status = status,
                lastError = lastError,
                attemptCount = if (incrementAttempt) current.attemptCount + 1 else current.attemptCount,
                updatedAtMillis = clock(),
            )
            val next = all.toMutableList()
            next[index] = updated
            writeIndex(next)
            return StagingModelsCodec.withPayload(updated, readPayload(id))
        }
    }

    fun markInFlight(id: String): StagingItem? {
        return updateStatus(id = id, status = StagingStatus.IN_FLIGHT, lastError = null)
    }

    fun markFailed(id: String, error: String): StagingItem? {
        return updateStatus(
            id = id,
            status = StagingStatus.FAILED,
            lastError = error,
            incrementAttempt = true,
        )
    }

    /** Marks success by deleting the item and payload. */
    fun markSuccess(id: String): Boolean = delete(id)

    fun delete(id: String): Boolean {
        synchronized(lock) {
            val all = readIndex()
            if (all.none { it.id == id }) return false
            writeIndex(all.filterNot { it.id == id })
            payloadFile(id).delete()
            return true
        }
    }

    private fun loadAll(): List<StagingItem> {
        return readIndex().map { meta ->
            StagingModelsCodec.withPayload(meta, readPayload(meta.id))
        }
    }

    private fun readIndex(): List<StagingItem> {
        if (!indexFile.exists()) return emptyList()
        val raw = runCatching { indexFile.readText(Charsets.UTF_8) }.getOrDefault("[]")
        return runCatching { StagingModelsCodec.parseIndexArray(raw) }.getOrDefault(emptyList())
    }

    private fun writeIndex(items: List<StagingItem>) {
        rootDir.mkdirs()
        val text = StagingModelsCodec.indexArrayToString(items)
        val tmp = File(rootDir, "$INDEX_FILE.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun writePayload(id: String, payload: String) {
        rootDir.mkdirs()
        val file = payloadFile(id)
        val tmp = File(rootDir, "$id.payload.json.tmp")
        tmp.writeText(payload, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(payload, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun readPayload(id: String): String {
        val file = payloadFile(id)
        if (!file.exists()) return ""
        return runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
    }

    private fun payloadFile(id: String): File = File(rootDir, "$id.payload.json")

    companion object {
        const val DIR_NAME: String = "staging_queue"
        const val INDEX_FILE: String = "index.json"
        const val MAX_ITEMS: Int = StagingQueueRules.MAX_QUEUE_ITEMS
        const val IMPORT_CHUNK_SIZE: Int = StagingQueueRules.IMPORT_CHUNK_SIZE
    }
}
