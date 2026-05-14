package org.galaxio.gatling.javaapi.utils;

import io.gatling.javaapi.core.Session;
import org.galaxio.gatling.utils.jwt.*;
import org.galaxio.gatling.utils.jwt.package$;

import java.security.PrivateKey;
import java.util.function.Function;

/**
 * Java/Kotlin-friendly API for JWT token generation in Gatling scenarios.
 *
 * <h3>Basic usage (HMAC)</h3>
 * <pre>{@code
 * JwtGeneratorBuilder gen = Jwt.jwt("HS256", "my-secret")
 *     .defaultHeader()
 *     .payload("{\"sub\":\"user1\"}");
 *
 * exec(Jwt.setJwt(gen, "jwt"));
 * }</pre>
 *
 * <h3>Standard claims with TTL</h3>
 * <pre>{@code
 * JwtGeneratorBuilder gen = Jwt.jwt("HS256", "secret")
 *     .defaultHeader()
 *     .claims(Jwt.claims()
 *         .issuer("my-service")
 *         .subject("#{userId}")
 *         .expiresIn(Duration.ofMinutes(5))
 *         .issuedAtNow());
 * }</pre>
 *
 * <h3>RSA signing</h3>
 * <pre>{@code
 * PrivateKey pk = JwtKeysJ.rsaPrivateKeyFromResource("keys/private.pem");
 * JwtGeneratorBuilder gen = Jwt.jwt("RS256", pk)
 *     .defaultHeader()
 *     .claims(Jwt.claims().issuer("auth").expiresIn(Duration.ofHours(1)));
 * }</pre>
 *
 * <h3>Bearer token</h3>
 * <pre>{@code
 * exec(Jwt.setJwtAsBearer(gen));
 * }</pre>
 */
public final class Jwt {

    private Jwt() {}

    /**
     * Create a JWT generator with HMAC string secret (HS256, HS384, HS512).
     *
     * @param algorithm JWT algorithm name (e.g. "HS256")
     * @param secret    HMAC secret string
     * @return builder for configuring header, payload, and claims
     */
    public static JwtGeneratorBuilder jwt(String algorithm, String secret) {
        return package$.MODULE$.jwt(algorithm, secret);
    }

    /**
     * Create a JWT generator with an asymmetric private key (RS256, ES256, etc.).
     * Load keys via {@link JwtKeysJ}.
     *
     * @param algorithm  JWT algorithm name (e.g. "RS256", "ES256")
     * @param privateKey RSA or EC private key
     * @return builder for configuring header, payload, and claims
     */
    public static JwtGeneratorBuilder jwt(String algorithm, PrivateKey privateKey) {
        return package$.MODULE$.jwt(algorithm, privateKey);
    }

    /**
     * Create an empty {@link ClaimsBuilder} for specifying standard JWT claims.
     *
     * <pre>{@code
     * Jwt.claims()
     *     .issuer("my-service")
     *     .subject("#{userId}")
     *     .expiresIn(Duration.ofMinutes(30))
     *     .issuedAtNow()
     *     .claim("role", "admin");
     * }</pre>
     *
     * @return empty claims builder
     */
    public static ClaimsBuilder claims() {
        return new ClaimsBuilder(
                scala.collection.immutable.Map$.MODULE$.empty(),
                scala.collection.immutable.Map$.MODULE$.empty(),
                scala.Option.empty(),
                false,
                false
        );
    }

    /**
     * Generate a JWT token and store it in the Gatling session.
     *
     * @param generator configured JWT generator
     * @param tokenName session attribute name to store the token under
     * @return session function for use with {@code exec()}
     */
    public static Function<Session, Session> setJwt(JwtGeneratorBuilder generator, String tokenName) {
        return session -> new Session(
                package$.MODULE$.SessionAppender(session.asScala()).setJwt(generator, tokenName)
        );
    }

    /**
     * Generate a JWT token and store {@code "Bearer <token>"} in the session
     * under the {@code "Authorization"} key.
     *
     * @param generator configured JWT generator
     * @return session function for use with {@code exec()}
     */
    public static Function<Session, Session> setJwtAsBearer(JwtGeneratorBuilder generator) {
        return setJwtAsBearer(generator, "Authorization");
    }

    /**
     * Generate a JWT token and store {@code "Bearer <token>"} in the session.
     *
     * @param generator configured JWT generator
     * @param tokenName session attribute name (e.g. "Authorization", "X-Auth")
     * @return session function for use with {@code exec()}
     */
    public static Function<Session, Session> setJwtAsBearer(JwtGeneratorBuilder generator, String tokenName) {
        return session -> new Session(
                package$.MODULE$.SessionAppender(session.asScala()).setJwtAsBearer(generator, tokenName)
        );
    }
}
