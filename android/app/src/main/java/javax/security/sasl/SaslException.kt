package javax.security.sasl

import java.io.IOException

/**
 * Minimal Android stub of `javax.security.sasl.SaslException`.
 * Required so MongoDB driver SCRAM classes can load and throw SASL failures.
 */
class SaslException : IOException {
    private var exception: Throwable? = null

    constructor() : super()

    constructor(detail: String?) : super(detail)

    constructor(detail: String?, ex: Throwable?) : super(detail) {
        if (ex != null) {
            initCause(ex)
        }
        this.exception = ex
    }

    override fun getCause(): Throwable? = exception

    override fun initCause(cause: Throwable?): Throwable {
        super.initCause(cause)
        this.exception = cause
        return this
    }

    companion object {
        private const val serialVersionUID = 4579784287902078538L
    }
}
