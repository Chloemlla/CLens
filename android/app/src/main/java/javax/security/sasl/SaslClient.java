package javax.security.sasl;

/**
 * Minimal Android stub for the JDK SASL client contract.
 *
 * Android does not ship {@code javax.security.sasl.*}. The MongoDB Java driver
 * implements SCRAM via classes that implement this interface
 * ({@code SaslAuthenticator$SaslClientImpl} / {@code ScramShaSaslClient}).
 * Without the interface on the classpath those driver classes fail to load
 * with ClassNotFoundException / NoClassDefFoundError during connect.
 *
 * This stub is intentionally API-shaped only. Mongo provides the real SCRAM
 * implementation; CLens does not implement a SASL provider here.
 */
public interface SaslClient {
    String getMechanismName();

    boolean hasInitialResponse();

    byte[] evaluateChallenge(byte[] challenge) throws SaslException;

    boolean isComplete();

    byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException;

    byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException;

    Object getNegotiatedProperty(String propName);

    void dispose() throws SaslException;
}
