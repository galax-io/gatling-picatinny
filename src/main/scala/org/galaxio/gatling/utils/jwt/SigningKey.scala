package org.galaxio.gatling.utils.jwt

import java.security.PrivateKey

/** Signing key for JWT token generation.
  *
  * Use [[SigningKey.StringSecret]] for HMAC algorithms (HS256, HS384, HS512) and [[SigningKey.AsymmetricKey]] for RSA/EC
  * algorithms (RS256, ES256, etc.).
  *
  * Typically constructed via factory methods in the `jwt` package object:
  * {{{
  * jwt("HS256", "my-secret")         // StringSecret
  * jwt("RS256", rsaPrivateKey)       // AsymmetricKey
  * }}}
  */
sealed trait SigningKey

object SigningKey {

  /** HMAC secret as a plain string. Used with HS256, HS384, HS512. `toString` is redacted so the secret never leaks via logs,
    * exceptions, or string interpolation; the `value` accessor still returns the raw secret.
    */
  final case class StringSecret(value: String) extends SigningKey {
    override def toString: String = "StringSecret(******)"
  }

  /** Asymmetric private key for RSA or EC algorithms. Used with RS256, RS384, RS512, ES256, ES384, ES512. Load keys via
    * [[JwtKeys]] helpers. `toString` is redacted so provider-dependent key material never leaks; the `value` accessor still
    * returns the raw key.
    */
  final case class AsymmetricKey(value: PrivateKey) extends SigningKey {
    override def toString: String = "AsymmetricKey(******)"
  }
}
