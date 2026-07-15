package javax.security.sasl

import java.io.IOException

/**
 * Minimal Android stub of `javax.security.sasl.SaslException`.
 * Required so MongoDB driver SCRAM classes can load and throw SASL failures.
 */
class SaslException : IOException {
    constructor() : super()

    constructor(detail: String?) : super(detail)

    constructor(detail: String?, ex: Throwable?) : super(detail, ex)

    companion object {
        private const val serialVersionUID = 4579784287902078538L
    }
}
