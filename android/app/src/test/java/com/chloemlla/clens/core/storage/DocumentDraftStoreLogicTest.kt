package com.chloemlla.clens.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentDraftStoreLogicTest {
    @Test
    fun draftKeyUsesNewWhenDocumentIdMissing() {
        assertEquals(
            "conn::db::coll::abc",
            DocumentDraftStore.buildKey("conn", "db", "coll", "abc"),
        )
        assertEquals(
            "conn::db::coll::new",
            DocumentDraftStore.buildKey("conn", "db", "coll", null),
        )
        assertEquals(
            "conn::db::coll::new",
            DocumentDraftStore.buildKey("conn", "db", "coll", "  "),
        )
    }

    @Test
    fun maxCodeCharsConstant() {
        assertEquals(200_000, DocumentDraftStore.MAX_CODE_CHARS)
    }
}
