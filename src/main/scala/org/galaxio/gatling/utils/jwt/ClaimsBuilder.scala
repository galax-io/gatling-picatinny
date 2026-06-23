package org.galaxio.gatling.utils.jwt

import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.Predef.Session
import io.gatling.core.session.el._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.duration.FiniteDuration

/** Forced JSON type for a session-sourced claim. Selected via [[ClaimsBuilder.SessionClaim.as]] or the `as*` terminals. */
sealed trait ClaimType
object ClaimType {
  case object AsString  extends ClaimType
  case object AsLong    extends ClaimType
  case object AsDouble  extends ClaimType
  case object AsBoolean extends ClaimType
  case object AsJson    extends ClaimType
}

/** Type-class mapping a Scala type to the JSON [[ClaimType]] it forces, enabling `claimFromSession(...).as[Long]`. */
final class ClaimTypeOf[T] private (val claimType: ClaimType)
object ClaimTypeOf {
  implicit val string: ClaimTypeOf[String]            = new ClaimTypeOf(ClaimType.AsString)
  implicit val long: ClaimTypeOf[Long]                = new ClaimTypeOf(ClaimType.AsLong)
  implicit val int: ClaimTypeOf[Int]                  = new ClaimTypeOf(ClaimType.AsLong)
  implicit val double: ClaimTypeOf[Double]            = new ClaimTypeOf(ClaimType.AsDouble)
  implicit val boolean: ClaimTypeOf[Boolean]          = new ClaimTypeOf(ClaimType.AsBoolean)
  implicit val jvalue: ClaimTypeOf[org.json4s.JValue] = new ClaimTypeOf(ClaimType.AsJson)
}

/** Builder for JWT claims with support for standard registered claims (RFC 7519), custom claims, Gatling EL expressions, and
  * time-based claims.
  *
  * EL expressions use Gatling's `#&#123;varName&#125;` syntax and are resolved per virtual user from the Gatling session.
  *
  * Session-sourced claims preserve the JSON type of the session value: a numeric value becomes a JSON number, a boolean a JSON
  * boolean, and anything else (including numeric-looking strings) a JSON string. To force a specific JSON type, chain `.as[T]`
  * (Scala) or an `as*` terminal (Java/Kotlin):
  *
  * {{{
  * ClaimsBuilder()
  *   .claimFromSession("user_id", "#{user_id}")            // auto-detect -> JSON number/boolean/string
  *   .claimFromSession("user_id", "#{user_id}").as[String] // force JSON string
  *   .claimFromSession("aud", "#{audiences}").as[JValue]   // parse a JSON string -> JSON array/object
  * }}}
  *
  * Java/Kotlin — use `Jwt.claims()` and the `as*` terminals:
  * {{{
  * Jwt.claims()
  *   .claimFromSession("user_id", "#{user_id}").asLong()
  *   .claimFromSession("aud", "#{audiences}").asJson()
  * }}}
  */
final case class ClaimsBuilder(
    staticClaims: Map[String, JValue] = Map.empty,
    elClaims: Map[String, String] = Map.empty,
    ttl: Option[FiniteDuration] = None,
    setIat: Boolean = false,
    setNbf: Boolean = false,
    forcedTypes: Map[String, ClaimType] = Map.empty,
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

  /** Add a claim resolved from the Gatling session via EL expression. By default the JSON type is inferred from the session
    * value (numeric → number, boolean → boolean, collection → array, map → object, otherwise string). Chain `.as[T]` (Scala) or
    * an `as*` terminal (Java/Kotlin) on the result to force a specific JSON type; leaving it un-chained commits the
    * auto-detected type.
    * @param name
    *   claim name in the JWT payload
    * @param el
    *   Gatling EL expression, e.g. `"#{userId}"`
    */
  def claimFromSession(name: String, el: String): ClaimsBuilder.SessionClaim =
    new ClaimsBuilder.SessionClaim(this, name, el)

  private[jwt] def withForcedType(name: String, el: String, claimType: ClaimType): ClaimsBuilder =
    copy(elClaims = elClaims + (name -> el), forcedTypes = forcedTypes + (name -> claimType))

  private[jwt] def withAutoDetect(name: String, el: String): ClaimsBuilder =
    copy(elClaims = elClaims + (name -> el))

  private def withClaim(name: String, value: String): ClaimsBuilder =
    if (value.contains("#{"))
      copy(elClaims = elClaims + (name -> value))
    else
      copy(staticClaims = staticClaims + (name -> JString(value)))

  /** Infer the JSON type of a resolved session value. Keyed on the value's runtime type, never on string content, so a
    * numeric-looking String stays a JSON string.
    */
  private def autoDetect(raw: Any): JValue = raw match {
    case null                          => JNull
    case None                          => JNull
    case Some(v)                       => autoDetect(v)
    case jv: JValue                    => jv
    case b: Boolean                    => JBool(b)
    case i: Int                        => JLong(i.toLong)
    case l: Long                       => JLong(l)
    case s: Short                      => JLong(s.toLong)
    case b: Byte                       => JLong(b.toLong)
    case bi: BigInt                    => JInt(bi)
    case bi: java.math.BigInteger      => JInt(BigInt(bi))
    case d: Double                     => JDouble(d)
    case f: Float                      => JDouble(f.toDouble)
    case bd: BigDecimal                => JDecimal(bd)
    case bd: java.math.BigDecimal      => JDecimal(BigDecimal(bd))
    case m: scala.collection.Map[_, _] => JObject(m.toList.map { case (k, v) => JField(k.toString, autoDetect(v)) })
    case xs: Iterable[_]               => JArray(xs.toList.map(autoDetect))
    case other                         => JString(other.toString)
  }

  private def coerceLong(raw: Any): Validation[JValue] = raw match {
    case l: Long                => Success(JLong(l))
    case i: Int                 => Success(JLong(i.toLong))
    case s: Short               => Success(JLong(s.toLong))
    case b: Byte                => Success(JLong(b.toLong))
    case bi: BigInt             => Success(JLong(bi.toLong))
    case d: Double if d.isWhole => Success(JLong(d.toLong))
    case other                  =>
      try Success(JLong(other.toString.trim.toLong))
      catch { case _: NumberFormatException => Failure(s"Cannot coerce session value '$other' to a numeric (Long) claim") }
  }

  private def coerceDouble(raw: Any): Validation[JValue] = raw match {
    case d: Double      => Success(JDouble(d))
    case f: Float       => Success(JDouble(f.toDouble))
    case l: Long        => Success(JDouble(l.toDouble))
    case i: Int         => Success(JDouble(i.toDouble))
    case bd: BigDecimal => Success(JDecimal(bd))
    case other          =>
      try Success(JDouble(other.toString.trim.toDouble))
      catch { case _: NumberFormatException => Failure(s"Cannot coerce session value '$other' to a numeric (Double) claim") }
  }

  private def coerceBoolean(raw: Any): Validation[JValue] = raw match {
    case b: Boolean => Success(JBool(b))
    case other      =>
      other.toString.trim.toLowerCase match {
        case "true"  => Success(JBool(true))
        case "false" => Success(JBool(false))
        case _       => Failure(s"Cannot coerce session value '$other' to a boolean claim")
      }
  }

  private def coerceJson(raw: Any): Validation[JValue] = raw match {
    case jv: JValue => Success(jv)
    case s: String  =>
      try Success(parse(s))
      catch { case e: Exception => Failure(s"Cannot parse session value as JSON: ${e.getMessage}") }
    case other      => Success(autoDetect(other))
  }

  private def toJValue(name: String, raw: Any): Validation[JValue] =
    raw match {
      // A null / absent session value is JSON null regardless of any forced type — never NPE on `raw.toString`.
      case null | None =>
        Success(JNull)
      case _           =>
        forcedTypes.get(name) match {
          case Some(ClaimType.AsString)  => Success(JString(raw.toString))
          case Some(ClaimType.AsLong)    => coerceLong(raw)
          case Some(ClaimType.AsDouble)  => coerceDouble(raw)
          case Some(ClaimType.AsBoolean) => coerceBoolean(raw)
          case Some(ClaimType.AsJson)    => coerceJson(raw)
          case None                      => Success(autoDetect(raw))
        }
    }

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
        elExpr.el[Any].apply(session) match {
          case Success(raw) => toJValue(name, raw).map(jv => acc + (name -> jv))
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

object ClaimsBuilder {

  /** Intermediate step returned by [[ClaimsBuilder.claimFromSession]]. Chain `.as[T]` or an `as*` terminal to force a JSON
    * type; otherwise it implicitly commits as an auto-detected claim.
    */
  final class SessionClaim private[jwt] (cb: ClaimsBuilder, name: String, el: String) {

    /** Force the JSON type from a Scala type, e.g. `.as[Long]`, `.as[String]`, `.as[JValue]` (raw JSON). */
    def as[T](implicit ct: ClaimTypeOf[T]): ClaimsBuilder = cb.withForcedType(name, el, ct.claimType)

    /** Force a JSON string. */
    def asString: ClaimsBuilder = cb.withForcedType(name, el, ClaimType.AsString)

    /** Force a JSON number (integral). */
    def asLong: ClaimsBuilder = cb.withForcedType(name, el, ClaimType.AsLong)

    /** Force a JSON number (fractional). */
    def asDouble: ClaimsBuilder = cb.withForcedType(name, el, ClaimType.AsDouble)

    /** Force a JSON boolean. */
    def asBoolean: ClaimsBuilder = cb.withForcedType(name, el, ClaimType.AsBoolean)

    /** Parse the resolved value as raw JSON (array/object/etc.). */
    def asJson: ClaimsBuilder = cb.withForcedType(name, el, ClaimType.AsJson)

    /** Commit with the auto-detected JSON type (the default; called implicitly when no `as*` is chained). */
    def autoDetect: ClaimsBuilder = cb.withAutoDetect(name, el)
  }

  /** A bare `claimFromSession(...)` (no `as*`) commits as an auto-detected claim. */
  implicit def sessionClaimToBuilder(sc: SessionClaim): ClaimsBuilder = sc.autoDetect
}
