package com.chloemlla.clens.core.storage

import com.chloemlla.clens.core.mongo.MongoAdminException
import org.junit.Assert.assertTrue
import org.junit.Test

class MongoConnectionStoreFailClosedTest {
    @Test
    fun validationExceptionAcceptsCause() {
        val ex = MongoAdminException.Validation("安全存储不可用", IllegalStateException("keystore"))
        assertTrue(ex.message!!.contains("安全存储不可用"))
        assertTrue(ex.cause is IllegalStateException)
    }
}
