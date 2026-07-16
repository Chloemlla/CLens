package com.chloemlla.clens.core.storage.draft

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chloemlla.clens.core.storage.DocumentDraft

@Entity(
    tableName = "document_drafts",
    indices = [
        Index(value = ["connection_id", "database_name", "collection_name", "updated_at"]),
        Index(value = ["connection_id", "database_name", "collection_name", "document_id"], unique = true),
    ],
)
data class DocumentDraftEntity(
    @PrimaryKey
    @ColumnInfo(name = "draft_id")
    val draftId: String,
    @ColumnInfo(name = "connection_id")
    val connectionId: String,
    @ColumnInfo(name = "database_name")
    val database: String,
    @ColumnInfo(name = "collection_name")
    val collection: String,
    @ColumnInfo(name = "document_id")
    val documentId: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAtMillis: Long,
    @ColumnInfo(name = "mode")
    val mode: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "code_text")
    val codeText: String,
) {
    fun toModel(): DocumentDraft {
        return DocumentDraft(
            draftId = draftId,
            connectionId = connectionId,
            database = database,
            collection = collection,
            documentId = documentId,
            updatedAtMillis = updatedAtMillis,
            mode = mode,
            codeText = codeText,
            source = source,
        )
    }

    companion object {
        fun fromModel(draft: DocumentDraft): DocumentDraftEntity {
            return DocumentDraftEntity(
                draftId = draft.draftId,
                connectionId = draft.connectionId,
                database = draft.database,
                collection = draft.collection,
                documentId = draft.documentId,
                updatedAtMillis = draft.updatedAtMillis,
                mode = draft.mode,
                source = draft.source,
                codeText = draft.codeText,
            )
        }
    }
}
