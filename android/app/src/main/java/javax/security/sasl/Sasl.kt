package javax.security.sasl

import javax.security.auth.callback.CallbackHandler

/**
 * Minimal Android stub of `javax.security.sasl.Sasl`.
 *
 * Mongo SCRAM does not use this factory (it constructs its own SaslClient
 * implementations). GSSAPI/PLAIN paths may call it; those mechanisms remain
 * unsupported on Android and will throw.
 */
object Sasl {
    const val QOP: String = "javax.security.sasl.qop"
    const val STRENGTH: String = "javax.security.sasl.strength"
    const val SERVER_AUTH: String = "javax.security.sasl.server.authentication"
    const val MAX_BUFFER: String = "javax.security.sasl.maxbuffer"
    const val RAW_SEND_SIZE: String = "javax.security.sasl.rawsendsize"
    const val REUSE: String = "javax.security.sasl.reuse"
    const val POLICY_NOPLAINTEXT: String = "javax.security.sasl.policy.noplaintext"
    const val POLICY_NOACTIVE: String = "javax.security.sasl.policy.noactive"
    const val POLICY_NODICTIONARY: String = "javax.security.sasl.policy.nodictionary"
    const val POLICY_NOANONYMOUS: String = "javax.security.sasl.policy.noanonymous"
    const val POLICY_FORWARD_SECRECY: String = "javax.security.sasl.policy.forward"
    const val POLICY_PASS_CREDENTIALS: String = "javax.security.sasl.policy.credentials"
    const val CREDENTIALS: String = "javax.security.sasl.credentials"

    @JvmStatic
    @Throws(SaslException::class)
    fun createSaslClient(
        mechanisms: Array<String>?,
        authorizationId: String?,
        protocol: String?,
        serverName: String?,
        props: java.util.Map<String, *>?,
        cbh: CallbackHandler?,
    ): SaslClient {
        throw SaslException(
            "Android does not provide a javax.security.sasl provider. " +
                "CLens supports Mongo SCRAM via the driver-built SaslClient only.",
        )
    }
}
