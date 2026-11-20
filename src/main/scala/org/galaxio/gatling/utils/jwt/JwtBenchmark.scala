package org.galaxio.gatling.utils.jwt

import io.gatling.commons.validation.Validation
import io.gatling.core.session.Session
import org.galaxio.gatling.jmh.JmhBenchmark
import org.galaxio.gatling.utils.{jwt => jwtPkg}
import org.openjdk.jmh.annotations.Benchmark

import java.security.KeyPairGenerator
import scala.concurrent.duration.DurationInt

class JwtBenchmark extends JmhBenchmark {

  // Neither EL evaluation nor token generation touches the event loop, and `FakeEventLoop` lives in src/test/scala, so null
  // stands in for it, as in RedisActionBenchmark.
  private val session: Session = Session("jwt-benchmark", 1L, null).setAll(
    "uid"    -> "user-42",
    "tenant" -> "acme",
    "role"   -> "admin",
    "region" -> "eu-west-1",
    "plan"   -> "premium",
    "device" -> "mobile",
    "locale" -> "en-US",
    "org"    -> "org-7",
    "team"   -> "load",
    "scope"  -> "api",
  )

  private val secret = "jwt-benchmark-secret"

  private val rsaPrivateKey = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    kpg.generateKeyPair().getPrivate
  }

  private val fiveElClaims: ClaimsBuilder = ClaimsBuilder()
    .subject("#{uid}")
    .claimFromSession("tenant", "#{tenant}")
    .claimFromSession("role", "#{role}")
    .claimFromSession("region", "#{region}")
    .claimFromSession("plan", "#{plan}")
    .issuedAtNow
    .expiresIn(5.minutes)

  private val tenElClaims: ClaimsBuilder = fiveElClaims
    .claimFromSession("device", "#{device}")
    .claimFromSession("locale", "#{locale}")
    .claimFromSession("org", "#{org}")
    .claimFromSession("team", "#{team}")
    .claimFromSession("scope", "#{scope}")

  // The claims of `fiveElClaims` with static values, so the two HS256 tokens differ only in EL.
  private val staticClaims: ClaimsBuilder = ClaimsBuilder()
    .subject("user-42")
    .claim("tenant", "acme")
    .claim("role", "admin")
    .claim("region", "eu-west-1")
    .claim("plan", "premium")
    .issuedAtNow
    .expiresIn(5.minutes)

  private val hs256StaticGenerator = jwtPkg.jwt("HS256", secret).defaultHeader.claims(staticClaims)
  private val hs256ElGenerator     = jwtPkg.jwt("HS256", secret).defaultHeader.claims(fiveElClaims)
  private val rs256ElGenerator     = jwtPkg.jwt("RS256", rsaPrivateKey).defaultHeader.claims(fiveElClaims)

  @Benchmark
  def hs256TokenStaticClaims(): Session =
    session.setJwt(hs256StaticGenerator, "jwt")

  @Benchmark
  def hs256TokenFiveElClaims(): Session =
    session.setJwt(hs256ElGenerator, "jwt")

  @Benchmark
  def rs256TokenFiveElClaims(): Session =
    session.setJwt(rs256ElGenerator, "jwt")

  @Benchmark
  def resolveFiveElClaims(): Validation[String] =
    fiveElClaims.resolve(session)

  @Benchmark
  def resolveTenElClaims(): Validation[String] =
    tenElClaims.resolve(session)
}
