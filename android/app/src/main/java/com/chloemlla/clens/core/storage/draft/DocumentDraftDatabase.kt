package com.chloemlla.clens.core.storage.draft

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentDraftEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DocumentDraftDatabase : RoomDatabase() {
    abstract fun documentDraftDao(): DocumentDraftDao

    companion object {
        @Volatile
        private var instance: DocumentDraftDatabase? = null

        fun get(context: Context): DocumentDraftDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocumentDraftDatabase::class.java,
                    "clens_document_drafts.db",
                ).fallbackToDestructiveMigration()
                    // Existing controller APIs are synchronous; drafts are small and local-only.
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
