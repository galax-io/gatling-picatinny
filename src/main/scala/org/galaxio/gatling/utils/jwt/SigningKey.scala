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

  /** HMAC secret as a plain string. Used with HS256, HS384, HS512. */
  final case class StringSecret(value: String) extends SigningKey

  /** Asymmetric private key for RSA or EC algorithms. Used with RS256, RS384, RS512, ES256, ES384, ES512. Load keys via
    * [[JwtKeys]] helpers.
    */
  final case class AsymmetricKey(value: PrivateKey) extends SigningKey
}
