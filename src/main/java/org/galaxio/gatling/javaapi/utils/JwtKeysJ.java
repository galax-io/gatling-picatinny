package org.galaxio.gatling.javaapi.utils;

import org.galaxio.gatling.utils.jwt.JwtKeys$;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Java-friendly wrapper for loading PEM-encoded keys for JWT signing.
 *
 * <p>Keys must be in PKCS#8 (private) or X.509 (public) format, PEM-encoded.
 *
 * <p>Example:
 * <pre>{@code
 * PrivateKey pk = JwtKeysJ.rsaPrivateKeyFromResource("keys/private.pem");
 * JwtGeneratorBuilder gen = Jwt.jwt("RS256", pk)
 *     .defaultHeader()
 *     .claims(Jwt.claims().issuer("my-service").expiresIn(Duration.ofMinutes(5)));
 * }</pre>
 */
public final class JwtKeysJ {

    private JwtKeysJ() {}

    /** Load RSA private key from classpath resource. */
    public static PrivateKey rsaPrivateKeyFromResource(String path) {
        return JwtKeys$.MODULE$.rsaPrivateKeyFromResource(path);
    }

    /** Load RSA private key from filesystem path. */
    public static PrivateKey rsaPrivateKeyFromFile(String path) {
        return JwtKeys$.MODULE$.rsaPrivateKeyFromFile(path);
    }

    /** Load EC private key from classpath resource. */
    public static PrivateKey ecPrivateKeyFromResource(String path) {
        return JwtKeys$.MODULE$.ecPrivateKeyFromResource(path);
    }

    /** Load EC private key from filesystem path. */
    public static PrivateKey ecPrivateKeyFromFile(String path) {
        return JwtKeys$.MODULE$.ecPrivateKeyFromFile(path);
    }

    /** Load RSA public key from classpath resource. */
    public static PublicKey rsaPublicKeyFromResource(String path) {
        return JwtKeys$.MODULE$.rsaPublicKeyFromResource(path);
    }

    /** Load RSA public key from filesystem path. */
    public static PublicKey rsaPublicKeyFromFile(String path) {
        return JwtKeys$.MODULE$.rsaPublicKeyFromFile(path);
    }

    /** Load EC public key from classpath resource. */
    public static PublicKey ecPublicKeyFromResource(String path) {
        return JwtKeys$.MODULE$.ecPublicKeyFromResource(path);
    }

    /** Load EC public key from filesystem path. */
    public static PublicKey ecPublicKeyFromFile(String path) {
        return JwtKeys$.MODULE$.ecPublicKeyFromFile(path);
    }
}
