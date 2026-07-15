package com.chloemlla.clens.core.mongo

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MongoSaslBootstrapTest {
    @Test
    fun jdkSaslStubsAreLoadable() {
        val saslClient = Class.forName("javax.security.sasl.SaslClient")
        val saslException = Class.forName("javax.security.sasl.SaslException")
        assertTrue(saslClient.isInterface)
        assertNotNull(saslException)
    }

    @Test
    fun mongoScramClassesLinkAgainstSaslStubs() {
        // Loading these classes previously failed on Android with:
        // ClassNotFoundException: com.mongodb.internal.connection.SaslAuthenticator$SaslClientImpl
        // because SaslClientImpl implements missing javax.security.sasl.SaslClient.
        val classes = listOf(
            "com.mongodb.internal.connection.DefaultAuthenticator",
            "com.mongodb.internal.connection.SaslAuthenticator",
            "com.mongodb.internal.connection.SaslAuthenticator\$SaslClientImpl",
            "com.mongodb.internal.connection.ScramShaAuthenticator",
            "com.mongodb.internal.connection.ScramShaAuthenticator\$ScramShaSaslClient",
        )
        for (name in classes) {
            assertNotNull(name, Class.forName(name))
        }
    }
}
