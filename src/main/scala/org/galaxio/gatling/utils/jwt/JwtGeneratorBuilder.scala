package org.galaxio.gatling.utils.jwt

import pdi.jwt.JwtAlgorithm
import pdi.jwt.algorithms.JwtUnknownAlgorithm
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import java.util.Locale
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
  * @param header
  *   JWT header (JSON string)
  * @param payload
  *   JWT payload (JSON string), resolved via Gatling EL at generation time
  * @param algorithm
  *   JWT algorithm name (e.g. "HS256", "RS256", "ES256")
  * @param signingKey
  *   signing credential — string secret or asymmetric private key
  * @param claimsBuilder
  *   optional claims builder for standard/dynamic claims
  */
final case class JwtGeneratorBuilder(
    header: Header,
    payload: Payload,
    algorithm: String,
    signingKey: SigningKey,
    claimsBuilder: Option[ClaimsBuilder] = None,
) {

  /** Heuristic check for Gatling EL markers (`#&#123;`). A literal `#&#123;` substring inside JSON values will also match and
    * cause [[validateJson]] to skip parsing — accepted trade-off since such strings are also treated as EL by Gatling at
    * resolution time, so the runtime behavior is consistent.
    */
  private def containsEl(s: String): Boolean = s.contains("#{")

  /** Validate JSON syntax, but skip validation when the string contains Gatling EL markers (`#&#123;...&#125;`), since EL
    * placeholders are not legal JSON and are resolved at token generation time.
    */
  private def validateJson(json: String): String =
    if (containsEl(json)) json
    else
      try compact(render(parse(json)))
      catch {
        case e: Exception =>
          throw new IllegalArgumentException(s"Invalid JSON: $json", e)
      }

  private def readResource(path: String): String = {
    val url    = getClass.getClassLoader.getResource(path)
    if (url == null) throw new IllegalArgumentException(s"Resource not found: $path")
    val source = Source.fromURL(url)
    try validateJson(source.mkString)
    finally source.close()
  }

  /** Load JWT header from a classpath resource (must be valid JSON). Resources containing Gatling EL markers
    * (`#&#123;...&#125;`) bypass JSON validation and are resolved at token generation time.
    */
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

  /** Load JWT payload from a classpath resource. Supports Gatling EL expressions (`#&#123;varName&#125;`) resolved at
    * generation time; payloads containing EL markers bypass JSON validation.
    */
  def payloadFromResource(path: String): JwtGeneratorBuilder =
    copy(payload = Payload(readResource(path)))

  /** Set JWT payload from a JSON string. Supports Gatling EL expressions (`#&#123;varName&#125;`) resolved at generation time.
    */
  def payload(p: String): JwtGeneratorBuilder =
    copy(payload = Payload(validateJson(p)))

  /** Attach a [[ClaimsBuilder]] for standard JWT claims and dynamic session-based claims. When both
    * `payload`/`payloadFromResource` and `claims` are used, they are merged with claims taking precedence on conflict.
    */
  def claims(builder: ClaimsBuilder): JwtGeneratorBuilder =
    copy(claimsBuilder = Some(builder))

  /** Resolved [[JwtAlgorithm]] for this builder. Validation is deferred to first token generation so that builders can be
    * constructed eagerly (e.g. at simulation setup) without forcing the algorithm name to be known statically. Throws
    * [[IllegalArgumentException]] when the configured `algorithm` is not supported.
    */
  private[jwt] lazy val jwtAlgorithm: JwtAlgorithm = {
    val alg = JwtAlgorithm.fromString(algorithm.toUpperCase(Locale.ROOT))
    alg match {
      case _: JwtUnknownAlgorithm =>
        throw new IllegalArgumentException(s"Unsupported JWT algorithm: $algorithm")
      case _                      => alg
    }
  }

}
