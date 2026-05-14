package org.galaxio.gatling.utils.jwt

import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.Predef.Session
import io.gatling.core.session.el._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.duration.FiniteDuration

/** Builder for JWT claims with support for standard registered claims (RFC 7519), custom claims, Gatling EL expressions, and
  * time-based claims.
  *
  * EL expressions use Gatling's `#&#123;varName&#125;` syntax and are resolved per virtual user from the Gatling session.
  *
  * Scala:
  * {{{
  * ClaimsBuilder()
  *   .issuer("my-service")
  *   .subject("#{userId}")
  *   .expiresIn(5.minutes)
  *   .issuedAtNow
  *   .claim("role", "admin")
  * }}}
  *
  * Java/Kotlin — use [[org.galaxio.gatling.javaapi.utils.Jwt.claims]] factory:
  * {{{
  * Jwt.claims()
  *   .issuer("my-service")
  *   .subject("#{userId}")
  *   .expiresIn(Duration.ofMinutes(5))
  *   .issuedAtNow()
  *   .claim("role", "admin")
  * }}}
  */
final case class ClaimsBuilder(
    staticClaims: Map[String, JValue] = Map.empty,
    elClaims: Map[String, String] = Map.empty,
    ttl: Option[FiniteDuration] = None,
    setIat: Boolean = false,
    setNbf: Boolean = false,
) {

  /** Set the `iss` (issuer) claim. Supports Gatling EL: `"#{varName}"`. */
  def issuer(iss: String): ClaimsBuilder = withClaim("iss", iss)

  /** Set the `sub` (subject) claim. Supports Gatling EL: `"#{varName}"`. */
  def subject(sub: String): ClaimsBuilder = withClaim("sub", sub)

  /** Set the `aud` (audience) claim. Supports Gatling EL: `"#{varName}"`. */
  def audience(aud: String): ClaimsBuilder = withClaim("aud", aud)

  /** Set token TTL. The `exp` claim will be set to `now + duration` at generation time. Scala: use `scala.concurrent.duration`
    * literals like `5.minutes`.
    */
  def expiresIn(duration: FiniteDuration): ClaimsBuilder = copy(ttl = Some(duration))

  /** Set token TTL from `java.time.Duration`. Java/Kotlin-friendly overload. */
  def expiresIn(duration: java.time.Duration): ClaimsBuilder =
    copy(ttl = Some(FiniteDuration(duration.toMillis, scala.concurrent.duration.MILLISECONDS)))

  /** Enable automatic `iat` (issued at) claim set to current epoch seconds at generation time. */
  def issuedAtNow: ClaimsBuilder = copy(setIat = true)

  /** Enable automatic `nbf` (not before) claim set to current epoch seconds at generation time. */
  def notBeforeNow: ClaimsBuilder = copy(setNbf = true)

  /** Add a custom string claim. */
  def claim(name: String, value: String): ClaimsBuilder =
    copy(staticClaims = staticClaims + (name -> JString(value)))

  /** Add a custom numeric claim. */
  def claim(name: String, value: Long): ClaimsBuilder =
    copy(staticClaims = staticClaims + (name -> JLong(value)))

  /** Add a custom boolean claim. */
  def claim(name: String, value: Boolean): ClaimsBuilder =
    copy(staticClaims = staticClaims + (name -> JBool(value)))

  /** Add a claim resolved from the Gatling session via EL expression.
    * @param name
    *   claim name in the JWT payload
    * @param el
    *   Gatling EL expression, e.g. `"#{userId}"`
    */
  def claimFromSession(name: String, el: String): ClaimsBuilder =
    copy(elClaims = elClaims + (name -> el))

  private def withClaim(name: String, value: String): ClaimsBuilder =
    if (value.contains("#{"))
      copy(elClaims = elClaims + (name -> value))
    else
      copy(staticClaims = staticClaims + (name -> JString(value)))

  private[jwt] def resolve(session: Session): Validation[String] = {
    val now = System.currentTimeMillis() / 1000

    val timeClaims: Map[String, JValue] = {
      val m = scala.collection.mutable.Map.empty[String, JValue]
      if (setIat) m += ("iat" -> JLong(now))
      if (setNbf) m += ("nbf" -> JLong(now))
      ttl.foreach(d => m += ("exp" -> JLong(now + d.toSeconds)))
      m.toMap
    }

    val resolvedEl: Validation[Map[String, JValue]] = elClaims.foldLeft[Validation[Map[String, JValue]]](Success(Map.empty)) {
      case (Success(acc), (name, elExpr)) =>
        elExpr.el[String].apply(session) match {
          case Success(v)   => Success(acc + (name -> JString(v)))
          case Failure(msg) => Failure(msg)
        }
      case (f @ Failure(_), _)            => f
    }

    resolvedEl.map { resolved =>
      val merged = staticClaims ++ timeClaims ++ resolved
      compact(render(JObject(merged.map { case (k, v) => JField(k, v) }.toList)))
    }
  }

}
