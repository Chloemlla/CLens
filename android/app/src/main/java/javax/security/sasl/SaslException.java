package javax.security.sasl;

import java.io.IOException;

/**
 * Minimal Android stub of {@code javax.security.sasl.SaslException}.
 * Required so MongoDB driver SCRAM classes can load and throw SASL failures.
 */
public class SaslException extends IOException {
    private static final long serialVersionUID = 4579784287902078538L;

    private Throwable exception;

    public SaslException() {
        super();
    }

    public SaslException(String detail) {
        super(detail);
    }

    public SaslException(String detail, Throwable ex) {
        super(detail);
        if (ex != null) {
            initCause(ex);
        }
        this.exception = ex;
    }

    @Override
    public Throwable getCause() {
        return exception;
    }

    @Override
    public Throwable initCause(Throwable cause) {
        super.initCause(cause);
        this.exception = cause;
        return this;
    }
}
