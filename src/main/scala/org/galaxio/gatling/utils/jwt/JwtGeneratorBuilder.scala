package org.galaxio.gatling.utils.jwt

import pdi.jwt.JwtAlgorithm
import pdi.jwt.algorithms.JwtUnknownAlgorithm
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.io.Source

/** Builder for configuring JWT token generation.
  *
  * Construct via the [[jwt]] package object factory methods:
  * {{{
  * // HMAC
  * val gen = jwt("HS256", "my-secret")
  *   .defaultHeader
  *   .payload("""{"sub":"user1"}""")
  *
  * // RSA
  * val pk = JwtKeys.rsaPrivateKeyFromResource("keys/private.pem")
  * val gen = jwt("RS256", pk)
  *   .defaultHeader
  *   .claims(ClaimsBuilder().issuer("my-service").expiresIn(5.minutes))
  * }}}
  *
  * @param header     JWT header (JSON string)
  * @param payload    JWT payload (JSON string), resolved via Gatling EL at generation time
  * @param algorithm  JWT algorithm name (e.g. "HS256", "RS256", "ES256")
  * @param signingKey signing credential — string secret or asymmetric private key
  * @param claimsBuilder optional claims builder for standard/dynamic claims
  */
final case class JwtGeneratorBuilder(
    header: Header,
    payload: Payload,
    algorithm: String,
    signingKey: SigningKey,
    claimsBuilder: Option[ClaimsBuilder] = None,
) {

  private def validateJson(json: String): String =
    try compact(render(parse(json)))
    catch {
      case e: Exception =>
        throw new IllegalArgumentException(s"Invalid JSON: $json", e)
    }

  private def readResource(path: String): String = {
    val url = getClass.getClassLoader.getResource(path)
    if (url == null) throw new IllegalArgumentException(s"Resource not found: $path")
    val source = Source.fromURL(url)
    try validateJson(source.mkString)
    finally source.close()
  }

  /** Load JWT header from a classpath resource (must be valid JSON). */
  def headerFromResource(path: String): JwtGeneratorBuilder =
    copy(header = Header(readResource(path)))

  /** Set JWT header from a JSON string. */
  def header(h: String): JwtGeneratorBuilder =
    copy(header = Header(validateJson(h)))

  /** Set a default JWT header with `{"alg":"<algorithm>","typ":"JWT"}`. */
  def defaultHeader: JwtGeneratorBuilder = {
    val body: JValue = ("alg" -> algorithm) ~ ("typ" -> "JWT")
    copy(header = Header(compact(render(body))))
  }

  /** Load JWT payload from a classpath resource (must be valid JSON).
    * Supports Gatling EL expressions (`#&#123;varName&#125;`) resolved at generation time.
    */
  def payloadFromResource(path: String): JwtGeneratorBuilder =
    copy(payload = Payload(readResource(path)))

  /** Set JWT payload from a JSON string.
    * Supports Gatling EL expressions (`#&#123;varName&#125;`) resolved at generation time.
    */
  def payload(p: String): JwtGeneratorBuilder =
    copy(payload = Payload(validateJson(p)))

  /** Attach a [[ClaimsBuilder]] for standard JWT claims and dynamic session-based claims.
    * When both `payload`/`payloadFromResource` and `claims` are used,
    * they are merged with claims taking precedence on conflict.
    */
  def claims(builder: ClaimsBuilder): JwtGeneratorBuilder =
    copy(claimsBuilder = Some(builder))

  private[jwt] val jwtAlgorithm: JwtAlgorithm = {
    val alg = JwtAlgorithm.fromString(algorithm.toUpperCase)
    alg match {
      case _: JwtUnknownAlgorithm =>
        throw new IllegalArgumentException(s"Unsupported JWT algorithm: $algorithm")
      case _ => alg
    }
  }

}
