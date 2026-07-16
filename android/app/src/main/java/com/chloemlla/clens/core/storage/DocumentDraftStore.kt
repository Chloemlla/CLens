package com.chloemlla.clens.core.storage

import android.content.Context
import androidx.core.content.edit
import com.chloemlla.clens.core.storage.draft.DocumentDraftDatabase
import com.chloemlla.clens.core.storage.draft.DocumentDraftEntity
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONObject

data class DocumentDraft(
    val draftId: String,
    val connectionId: String,
    val database: String,
    val collection: String,
    val documentId: String?,
    val updatedAtMillis: Long,
    val mode: String,
    val codeText: String,
    val source: String,
)

/**
 * Document draft store backed by Room, with one-shot SharedPreferences import.
 * Public API stays synchronous for existing controllers.
 */
class DocumentDraftStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val db = DocumentDraftDatabase.get(appContext)
    private val dao = db.documentDraftDao()
    private val io = Executors.newSingleThreadExecutor()

    init {
        // Best-effort migration off the main thread; first load/save also dual-reads SP.
        io.execute { migrateSharedPreferencesIfNeeded() }
    }

    fun draftKey(
        connectionId: String,
        database: String,
        collection: String,
        documentId: String?,
    ): String {
        return buildKey(connectionId, database, collection, documentId)
    }

    fun save(draft: DocumentDraft) {
        val draftId = draft.draftId.ifBlank { UUID.randomUUID().toString() }
        val codeText = if (draft.codeText.length > MAX_CODE_CHARS) {
            draft.codeText.take(MAX_CODE_CHARS) + "\n/* draft truncated for local storage */"
        } else {
            draft.codeText
        }
        val normalized = draft.copy(draftId = draftId, codeText = codeText)
        runCatching {
            dao.upsert(DocumentDraftEntity.fromModel(normalized))
            enforcePerConnectionLimit(normalized.connectionId)
        }
        // Keep SP mirror for one release dual-read safety; overwritten by same logical key.
        val key = draftKey(normalized.connectionId, normalized.database, normalized.collection, normalized.documentId)
        val payload = JSONObject()
            .put("draftId", normalized.draftId)
            .put("connectionId", normalized.connectionId)
            .put("database", normalized.database)
            .put("collection", normalized.collection)
            .put("documentId", normalized.documentId)
            .put("updatedAtMillis", normalized.updatedAtMillis)
            .put("mode", normalized.mode)
            .put("codeText", normalized.codeText)
            .put("source", normalized.source)
            .toString()
        prefs.edit { putString(key, payload) }
    }

    fun load(
        connectionId: String,
        database: String,
        collection: String,
        documentId: String?,
    ): DocumentDraft? {
        val fromRoom = runCatching {
            dao.findOne(connectionId, database, collection, documentId)?.toModel()
        }.getOrNull()
        if (fromRoom != null) return fromRoom

        val key = draftKey(connectionId, database, collection, documentId)
        val raw = prefs.getString(key, null) ?: return null
        val parsed = runCatching { parse(raw) }.getOrNull() ?: return null
        // Promote SP draft into Room.
        runCatching { dao.upsert(DocumentDraftEntity.fromModel(parsed)) }
        return parsed
    }

    fun clear(
        connectionId: String,
        database: String,
        collection: String,
        documentId: String?,
    ) {
        runCatching { dao.deleteOne(connectionId, database, collection, documentId) }
        val key = draftKey(connectionId, database, collection, documentId)
        prefs.edit { remove(key) }
    }

    fun deleteById(draftId: String) {
        runCatching { dao.deleteById(draftId) }
        // Best-effort SP cleanup by scanning.
        val doomed = prefs.all.entries.mapNotNull { (k, v) ->
            val raw = v as? String ?: return@mapNotNull null
            val id = runCatching { JSONObject(raw).optString("draftId") }.getOrNull()
            if (id == draftId) k else null
        }
        if (doomed.isNotEmpty()) {
            prefs.edit {
                doomed.forEach { remove(it) }
            }
        }
    }

    fun listDrafts(
        limit: Int = 50,
        connectionId: String? = null,
        database: String? = null,
        collection: String? = null,
    ): List<DocumentDraft> {
        migrateSharedPreferencesIfNeeded()
        val roomItems = runCatching {
            dao.list(connectionId, database, collection, limit.coerceAtLeast(1)).map { it.toModel() }
        }.getOrDefault(emptyList())
        if (roomItems.isNotEmpty()) return roomItems

        // Fallback SP listing before migration completes.
        return prefs.all.entries
            .asSequence()
            .mapNotNull { (_, value) -> value as? String }
            .mapNotNull { raw -> runCatching { parse(raw) }.getOrNull() }
            .filter { draft ->
                (connectionId == null || draft.connectionId == connectionId) &&
                    (database == null || draft.database == database) &&
                    (collection == null || draft.collection == collection)
            }
            .sortedByDescending { it.updatedAtMillis }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    fun draftCount(): Int {
        val roomCount = runCatching { dao.count() }.getOrDefault(0)
        return if (roomCount > 0) roomCount else prefs.all.size
    }

    private fun enforcePerConnectionLimit(connectionId: String) {
        val count = runCatching { dao.countForConnection(connectionId) }.getOrDefault(0)
        val overflow = count - MAX_DRAFTS_PER_CONNECTION
        if (overflow > 0) {
            runCatching { dao.deleteOldestForConnection(connectionId, overflow) }
        }
    }

    @Synchronized
    private fun migrateSharedPreferencesIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val entries = prefs.all
        entries.forEach { (key, value) ->
            if (key == KEY_MIGRATED) return@forEach
            val raw = value as? String ?: return@forEach
            val draft = runCatching { parse(raw) }.getOrNull() ?: return@forEach
            runCatching { dao.upsert(DocumentDraftEntity.fromModel(draft)) }
        }
        prefs.edit { putBoolean(KEY_MIGRATED, true) }
    }

    private fun parse(raw: String): DocumentDraft {
        val obj = JSONObject(raw)
        return DocumentDraft(
            draftId = obj.optString("draftId", UUID.randomUUID().toString()),
            connectionId = obj.optString("connectionId"),
            database = obj.optString("database"),
            collection = obj.optString("collection"),
            documentId = obj.optString("documentId").takeIf { it.isNotBlank() && it != "null" },
            updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
            mode = obj.optString("mode", "tree"),
            codeText = obj.optString("codeText", "{\n  \n}"),
            source = obj.optString("source", "insert"),
        )
    }

    companion object {
        const val PREFS = "clens_document_drafts"
        const val MAX_CODE_CHARS = 2_000_000
        const val MAX_DRAFTS_PER_CONNECTION = 100
        private const val KEY_MIGRATED = "__room_migrated_v1"

        fun buildKey(
            connectionId: String,
            database: String,
            collection: String,
            documentId: String?,
        ): String {
            val docPart = documentId?.takeIf { it.isNotBlank() } ?: "new"
            return listOf(connectionId, database, collection, docPart).joinToString("::")
        }
    }
}
