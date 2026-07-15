package javax.security.sasl

/**
 * Minimal Android stub for the JDK SASL client contract.
 *
 * Android does not ship `javax.security.sasl.*`. The MongoDB Java driver
 * implements SCRAM via classes that implement this interface
 * (`SaslAuthenticator$SaslClientImpl` / `ScramShaSaslClient`).
 * Without the interface on the classpath those driver classes fail to load
 * with ClassNotFoundException / NoClassDefFoundError during connect.
 *
 * This stub is intentionally API-shaped only. Mongo provides the real SCRAM
 * implementation; CLens does not implement a SASL provider here.
 */
interface SaslClient {
    fun getMechanismName(): String

    fun hasInitialResponse(): Boolean

    @Throws(SaslException::class)
    fun evaluateChallenge(challenge: ByteArray?): ByteArray?

    fun isComplete(): Boolean

    @Throws(SaslException::class)
    fun unwrap(incoming: ByteArray?, offset: Int, len: Int): ByteArray?

    @Throws(SaslException::class)
    fun wrap(outgoing: ByteArray?, offset: Int, len: Int): ByteArray?

    fun getNegotiatedProperty(propName: String?): Any?

    @Throws(SaslException::class)
    fun dispose()
}
