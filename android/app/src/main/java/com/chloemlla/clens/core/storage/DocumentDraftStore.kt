package com.chloemlla.clens.core.storage

import android.content.Context
import androidx.core.content.edit
import java.util.UUID
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
 * MVP draft store backed by SharedPreferences JSON.
 * Keyed by connection + database + collection + documentId/new.
 */
class DocumentDraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun draftKey(
        connectionId: String,
        database: String,
        collection: String,
        documentId: String?,
    ): String {
        return buildKey(connectionId, database, collection, documentId)
    }

    fun save(draft: DocumentDraft) {
        val key = draftKey(draft.connectionId, draft.database, draft.collection, draft.documentId)
        val payload = JSONObject()
            .put("draftId", draft.draftId.ifBlank { UUID.randomUUID().toString() })
            .put("connectionId", draft.connectionId)
            .put("database", draft.database)
            .put("collection", draft.collection)
            .put("documentId", draft.documentId)
            .put("updatedAtMillis", draft.updatedAtMillis)
            .put("mode", draft.mode)
            .put("codeText", draft.codeText)
            .put("source", draft.source)
            .toString()
        prefs.edit { putString(key, payload) }
    }

    fun load(
        connectionId: String,
        database: String,
        collection: String,
        documentId: String?,
    ): DocumentDraft? {
        val key = draftKey(connectionId, database, collection, documentId)
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { parse(raw) }.getOrNull()
    }

    fun clear(
        connectionId: String,
        database: String,
        collection: String,
        documentId: String?,
    ) {
        val key = draftKey(connectionId, database, collection, documentId)
        prefs.edit { remove(key) }
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
