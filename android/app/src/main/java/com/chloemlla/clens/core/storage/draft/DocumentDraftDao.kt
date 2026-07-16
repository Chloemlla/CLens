package com.chloemlla.clens.core.storage.draft

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DocumentDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: DocumentDraftEntity)

    @Query(
        """
        SELECT * FROM document_drafts
        WHERE connection_id = :connectionId
          AND database_name = :database
          AND collection_name = :collection
          AND (
                (:documentId IS NULL AND document_id IS NULL)
             OR (:documentId IS NOT NULL AND document_id = :documentId)
          )
        LIMIT 1
        """,
    )
    fun findOne(
        connectionId: String,
        database: String,
        collection: String,
        documentId: String?,
    ): DocumentDraftEntity?

    @Query(
        """
        SELECT * FROM document_drafts
        WHERE (:connectionId IS NULL OR connection_id = :connectionId)
          AND (:database IS NULL OR database_name = :database)
          AND (:collection IS NULL OR collection_name = :collection)
        ORDER BY updated_at DESC
        LIMIT :limit
        """,
    )
    fun list(
        connectionId: String?,
        database: String?,
        collection: String?,
        limit: Int,
    ): List<DocumentDraftEntity>

    @Query(
        """
        DELETE FROM document_drafts
        WHERE connection_id = :connectionId
          AND database_name = :database
          AND collection_name = :collection
          AND (
                (:documentId IS NULL AND document_id IS NULL)
             OR (:documentId IS NOT NULL AND document_id = :documentId)
          )
        """,
    )
    fun deleteOne(
        connectionId: String,
        database: String,
        collection: String,
        documentId: String?,
    )

    @Query("DELETE FROM document_drafts WHERE draft_id = :draftId")
    fun deleteById(draftId: String)

    @Query("SELECT COUNT(*) FROM document_drafts")
    fun count(): Int

    @Query(
        """
        DELETE FROM document_drafts WHERE draft_id IN (
            SELECT draft_id FROM document_drafts
            WHERE connection_id = :connectionId
            ORDER BY updated_at ASC
            LIMIT :overflow
        )
        """,
    )
    fun deleteOldestForConnection(connectionId: String, overflow: Int)

    @Query(
        """
        SELECT COUNT(*) FROM document_drafts WHERE connection_id = :connectionId
        """,
    )
    fun countForConnection(connectionId: String): Int
}
