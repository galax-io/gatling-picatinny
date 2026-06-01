package org.galaxio.gatling.utils.jwt

import java.nio.file.{Files, Path}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security.{KeyFactory, PrivateKey, PublicKey}
import java.util.Base64
import scala.io.Source
import scala.util.Using

/** Utility for loading PEM-encoded keys for JWT signing and verification.
  *
  * Keys must be in PKCS#8 (private) or X.509 (public) DER format, PEM-encoded.
  *
  * Scala:
  * {{{
  * val pk = JwtKeys.rsaPrivateKeyFromResource("keys/private.pem")
  * val gen = jwt("RS256", pk).defaultHeader.claims(...)
  * }}}
  *
  * Java/Kotlin — use [[org.galaxio.gatling.javaapi.utils.JwtKeysJ]]:
  * {{{
  * PrivateKey pk = JwtKeysJ.rsaPrivateKeyFromResource("keys/private.pem");
  * }}}
  */
object JwtKeys {

  /** Load RSA private key from classpath resource. */
  def rsaPrivateKeyFromResource(path: String): PrivateKey =
    privateKeyFromPem(readResourceString(path), "RSA")

  /** Load RSA private key from filesystem path. */
  def rsaPrivateKeyFromFile(path: String): PrivateKey =
    privateKeyFromPem(Files.readString(Path.of(path)), "RSA")

  /** Load EC private key from classpath resource. */
  def ecPrivateKeyFromResource(path: String): PrivateKey =
    privateKeyFromPem(readResourceString(path), "EC")

  /** Load EC private key from filesystem path. */
  def ecPrivateKeyFromFile(path: String): PrivateKey =
    privateKeyFromPem(Files.readString(Path.of(path)), "EC")

  /** Load RSA public key from classpath resource. */
  def rsaPublicKeyFromResource(path: String): PublicKey =
    publicKeyFromPem(readResourceString(path), "RSA")

  /** Load RSA public key from filesystem path. */
  def rsaPublicKeyFromFile(path: String): PublicKey =
    publicKeyFromPem(Files.readString(Path.of(path)), "RSA")

  /** Load EC public key from classpath resource. */
  def ecPublicKeyFromResource(path: String): PublicKey =
    publicKeyFromPem(readResourceString(path), "EC")

  /** Load EC public key from filesystem path. */
  def ecPublicKeyFromFile(path: String): PublicKey =
    publicKeyFromPem(Files.readString(Path.of(path)), "EC")

  private def privateKeyFromPem(pem: String, algorithm: String): PrivateKey = {
    val stripped = pem
      .replaceAll("-----BEGIN .*-----", "")
      .replaceAll("-----END .*-----", "")
      .replaceAll("\\s", "")
    val bytes    = Base64.getDecoder.decode(stripped)
    val spec     = new PKCS8EncodedKeySpec(bytes)
    KeyFactory.getInstance(algorithm).generatePrivate(spec)
  }

  private def publicKeyFromPem(pem: String, algorithm: String): PublicKey = {
    val stripped = pem
      .replaceAll("-----BEGIN .*-----", "")
      .replaceAll("-----END .*-----", "")
      .replaceAll("\\s", "")
    val bytes    = Base64.getDecoder.decode(stripped)
    val spec     = new X509EncodedKeySpec(bytes)
    KeyFactory.getInstance(algorithm).generatePublic(spec)
  }

  private def readResourceString(path: String): String = {
    val url = getClass.getClassLoader.getResource(path)
    if (url == null) throw new IllegalArgumentException(s"Resource not found: $path")
    Using.resource(Source.fromURL(url))(_.mkString)
  }

}
