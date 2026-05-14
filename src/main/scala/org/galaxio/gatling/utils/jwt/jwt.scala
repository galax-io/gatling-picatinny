package org.galaxio.gatling.utils

import io.gatling.commons.validation.{Failure, Success}
import io.gatling.core.Predef.Session
import io.gatling.core.session.el._
import org.json4s.jackson.JsonMethods._
import org.json4s._
import pdi.jwt.{Jwt => PdiJwt, JwtAlgorithm}
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm

import java.security.PrivateKey

/** JWT token generation DSL for Gatling.
  *
  * ==Quick start (Scala)==
  * {{{
  * import org.galaxio.gatling.utils.jwt._
  *
  * val gen = jwt("HS256", "secret").defaultHeader
  *   .payload("""{"sub":"#{userId}"}""")
  *
  * exec(session => session.setJwt(gen, "jwt"))
  * }}}
  *
  * ==Standard claims with TTL==
  * {{{
  * val gen = jwt("HS256", "secret").defaultHeader
  *   .claims(ClaimsBuilder()
  *     .issuer("my-service")
  *     .subject("#{userId}")
  *     .expiresIn(5.minutes)
  *     .issuedAtNow)
  * }}}
  *
  * ==RSA signing==
  * {{{
  * val pk = JwtKeys.rsaPrivateKeyFromResource("keys/private.pem")
  * val gen = jwt("RS256", pk).defaultHeader
  *   .claims(ClaimsBuilder().issuer("auth").expiresIn(1.hour))
  * }}}
  *
  * ==Authorization header==
  * {{{
  * exec(session => session.setJwtAsBearer(gen))
  * }}}
  *
  * ==Java/Kotlin==
  * Use [[org.galaxio.gatling.javaapi.utils.Jwt]] for Java/Kotlin-friendly static methods.
  */
package object jwt {

  /** Create a JWT generator with HMAC string secret (HS256, HS384, HS512). */
  def jwt(algorithm: String, secret: String): JwtGeneratorBuilder =
    JwtGeneratorBuilder(Header(), Payload(), algorithm, SigningKey.StringSecret(secret))

  /** Create a JWT generator with an asymmetric private key (RS256, ES256, etc.). */
  def jwt(algorithm: String, privateKey: PrivateKey): JwtGeneratorBuilder =
    JwtGeneratorBuilder(Header(), Payload(), algorithm, SigningKey.AsymmetricKey(privateKey))

  /** Implicit enrichment adding JWT operations to Gatling [[Session]]. */
  implicit class SessionAppender(s: Session) {

    /** Generate a JWT token and store it in the session under `tokenName`. */
    def setJwt(generator: JwtGeneratorBuilder, tokenName: String): Session =
      resolveAndEncode(generator) match {
        case Success(token) => s.set(tokenName, token)
        case Failure(msg)   => throw new IllegalStateException(s"JWT generation failed: $msg")
      }

    /** Generate a JWT token and store `"Bearer <token>"` in the session.
      * @param tokenName session attribute name, defaults to `"Authorization"`
      */
    def setJwtAsBearer(generator: JwtGeneratorBuilder, tokenName: String = "Authorization"): Session =
      resolveAndEncode(generator) match {
        case Success(token) => s.set(tokenName, s"Bearer $token")
        case Failure(msg)   => throw new IllegalStateException(s"JWT generation failed: $msg")
      }

    private def resolveAndEncode(generator: JwtGeneratorBuilder): io.gatling.commons.validation.Validation[String] =
      for {
        header  <- generator.header.json.el[String].apply(s)
        payload <- resolvePayload(generator)
      } yield encode(header, payload, generator.signingKey, generator.jwtAlgorithm)

    private def resolvePayload(generator: JwtGeneratorBuilder): io.gatling.commons.validation.Validation[String] =
      generator.claimsBuilder match {
        case Some(cb) =>
          val basePayloadValidation = if (generator.payload.json.nonEmpty)
            generator.payload.json.el[String].apply(s)
          else
            Success("{}")

          basePayloadValidation.flatMap { baseJson =>
            cb.resolve(s).map { claimsJson =>
              val base   = parse(baseJson)
              val claims = parse(claimsJson)
              compact(render(base merge claims))
            }
          }

        case None =>
          generator.payload.json.el[String].apply(s)
      }
  }

  private def encode(header: String, payload: String, key: SigningKey, algorithm: JwtAlgorithm): String =
    key match {
      case SigningKey.StringSecret(secret) => PdiJwt.encode(header, payload, secret, algorithm)
      case SigningKey.AsymmetricKey(pk)    => PdiJwt.encode(header, payload, pk, algorithm.asInstanceOf[JwtAsymmetricAlgorithm])
    }

}
