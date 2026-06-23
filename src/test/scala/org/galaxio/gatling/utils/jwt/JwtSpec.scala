package org.galaxio.gatling.utils.jwt

import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.el._
import org.galaxio.gatling.transactions.FakeEventLoop
import org.galaxio.gatling.utils.{jwt => jwtPkg}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pdi.jwt.{Jwt => PdiJwt, JwtAlgorithm}

import java.security.KeyPairGenerator
import scala.concurrent.duration.DurationInt

class JwtSpec extends AnyWordSpec with Matchers {

  private implicit val configuration: GatlingConfiguration  = GatlingConfiguration.loadForTest()
  private val fakeEventLoop                                 = new FakeEventLoop
  private val emptySession: io.gatling.core.session.Session =
    io.gatling.core.session.Session("test", 1L, fakeEventLoop)

  private def initRsaKeyPair() = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    kpg.generateKeyPair()
  }

  private def resolveJson(cb: ClaimsBuilder, s: io.gatling.core.session.Session): String =
    cb.resolve(s) match {
      case io.gatling.commons.validation.Success(json) => json
      case io.gatling.commons.validation.Failure(msg)  => fail(s"resolve failed: $msg")
    }

  "jwt factory" should {
    "create builder with string secret" in {
      val gen = jwtPkg.jwt("HS256", "secret")
      gen.signingKey shouldBe a[SigningKey.StringSecret]
      gen.algorithm shouldBe "HS256"
    }

    "create builder with PrivateKey" in {
      val kp  = initRsaKeyPair()
      val gen = jwtPkg.jwt("RS256", kp.getPrivate)
      gen.signingKey shouldBe a[SigningKey.AsymmetricKey]
    }
  }

  "JwtGeneratorBuilder" should {
    "build default header" in {
      val gen = jwtPkg.jwt("HS256", "secret").defaultHeader
      gen.header.json should include("\"alg\":\"HS256\"")
      gen.header.json should include("\"typ\":\"JWT\"")
    }

    "set payload from JSON string" in {
      val gen = jwtPkg.jwt("HS256", "secret").payload("""{"sub":"user1"}""")
      gen.payload.json should include("\"sub\":\"user1\"")
    }

    "throw on invalid header JSON" in {
      an[IllegalArgumentException] shouldBe thrownBy {
        jwtPkg.jwt("HS256", "secret").header("not json")
      }
    }

    "throw on invalid payload JSON" in {
      an[IllegalArgumentException] shouldBe thrownBy {
        jwtPkg.jwt("HS256", "secret").payload("{broken")
      }
    }

    "defer unsupported algorithm validation to first token generation" in {
      // Builder construction must NOT throw — validation happens at generation time
      val gen = jwtPkg.jwt("INVALID", "secret").defaultHeader.payload("""{"sub":"user1"}""")
      gen.algorithm shouldBe "INVALID"

      // Resolving jwtAlgorithm (used during token generation) throws
      an[IllegalArgumentException] shouldBe thrownBy {
        gen.jwtAlgorithm
      }
    }

    "accept payload containing Gatling EL without parsing as JSON" in {
      // Payloads with EL placeholders (`#{var}`) are not legal JSON and must be accepted by the builder;
      // they are resolved at token generation time.
      val gen = jwtPkg
        .jwt("HS256", "secret")
        .defaultHeader
        .payload("""{"sub":"#{userId}","scope":"#{scope}"}""")
      gen.payload.json should include("#{userId}")
      gen.payload.json should include("#{scope}")
    }

    "accept header containing Gatling EL without parsing as JSON" in {
      val gen = jwtPkg.jwt("HS256", "secret").header("""{"alg":"HS256","kid":"#{keyId}"}""")
      gen.header.json should include("#{keyId}")
    }

    "load header from resource" in {
      val gen = jwtPkg.jwt("HS256", "secret").headerFromResource("jwtTemplates/header.json")
      gen.header.json should not be empty
    }

    "load payload from resource" in {
      val gen = jwtPkg.jwt("HS256", "secret").payloadFromResource("jwtTemplates/payload.json")
      gen.payload.json should include("scope")
    }

    "throw on missing resource" in {
      an[IllegalArgumentException] shouldBe thrownBy {
        jwtPkg.jwt("HS256", "secret").headerFromResource("nonexistent.json")
      }
    }

    "accept ClaimsBuilder" in {
      val gen = jwtPkg
        .jwt("HS256", "secret")
        .defaultHeader
        .claims(ClaimsBuilder().issuer("test"))
      gen.claimsBuilder shouldBe defined
    }
  }

  "setJwt" should {
    "produce valid JWT with HMAC" in {
      val gen = jwtPkg
        .jwt("HS256", "my-secret")
        .defaultHeader
        .payload("""{"sub":"user1","scope":"test"}""")

      val session = emptySession.setJwt(gen, "jwt")
      val token   = session("jwt").as[String]

      token.split("\\.") should have length 3
      PdiJwt.isValid(token, "my-secret", Seq(JwtAlgorithm.HS256)) shouldBe true
    }

    "produce valid JWT with HS384" in {
      val gen     = jwtPkg.jwt("HS384", "secret384").defaultHeader.payload("""{"data":"test"}""")
      val session = emptySession.setJwt(gen, "token")
      val token   = session("token").as[String]

      token.split("\\.") should have length 3
    }

    "produce valid JWT with RSA" in {
      val kp      = initRsaKeyPair()
      val gen     = jwtPkg.jwt("RS256", kp.getPrivate).defaultHeader.payload("""{"sub":"rsa-user"}""")
      val session = emptySession.setJwt(gen, "jwt")
      val token   = session("jwt").as[String]

      token.split("\\.") should have length 3
      PdiJwt.isValid(token, kp.getPublic, Seq(JwtAlgorithm.RS256)) shouldBe true
    }

    "resolve EL expressions in payload" in {
      val sessionWithUser = emptySession.set("userId", "user-42")
      val gen             = jwtPkg
        .jwt("HS256", "secret")
        .defaultHeader
        .payload("""{"sub":"#{userId}"}""")

      val result  = sessionWithUser.setJwt(gen, "jwt")
      val token   = result("jwt").as[String]
      val decoded = PdiJwt.decode(token, "secret", Seq(JwtAlgorithm.HS256))

      decoded.isSuccess shouldBe true
      decoded.get.subject shouldBe Some("user-42")
    }
  }

  "setJwtAsBearer" should {
    "produce Bearer token" in {
      val gen     = jwtPkg.jwt("HS256", "secret").defaultHeader.payload("""{"sub":"user"}""")
      val session = emptySession.setJwtAsBearer(gen)
      val auth    = session("Authorization").as[String]

      auth should startWith("Bearer ")
      auth.stripPrefix("Bearer ").split("\\.") should have length 3
    }

    "use custom token name" in {
      val gen     = jwtPkg.jwt("HS256", "secret").defaultHeader.payload("""{"sub":"user"}""")
      val session = emptySession.setJwtAsBearer(gen, "X-Auth")
      session.contains("X-Auth") shouldBe true
    }
  }

  "ClaimsBuilder" should {
    "resolve static claims" in {
      val cb     = ClaimsBuilder().issuer("my-app").claim("role", "admin")
      val result = cb.resolve(emptySession)

      result.isInstanceOf[io.gatling.commons.validation.Success[_]] shouldBe true
      val json = result.asInstanceOf[io.gatling.commons.validation.Success[String]].value
      json should include("\"iss\":\"my-app\"")
      json should include("\"role\":\"admin\"")
    }

    "resolve EL claims from session" in {
      val session = emptySession.set("uid", "abc-123")
      val cb      = ClaimsBuilder().subject("#{uid}")
      val result  = cb.resolve(session)

      result.isInstanceOf[io.gatling.commons.validation.Success[_]] shouldBe true
      val json = result.asInstanceOf[io.gatling.commons.validation.Success[String]].value
      json should include("\"sub\":\"abc-123\"")
    }

    "set time-based claims" in {
      val cb     = ClaimsBuilder().issuedAtNow.notBeforeNow.expiresIn(300.seconds)
      val result = cb.resolve(emptySession)

      result.isInstanceOf[io.gatling.commons.validation.Success[_]] shouldBe true
      val json = result.asInstanceOf[io.gatling.commons.validation.Success[String]].value
      json should include("\"iat\":")
      json should include("\"nbf\":")
      json should include("\"exp\":")
    }

    "fail on unresolvable EL expression" in {
      val cb     = ClaimsBuilder().claimFromSession("userId", "#{missingKey}")
      val result = cb.resolve(emptySession)

      result.isInstanceOf[io.gatling.commons.validation.Failure] shouldBe true
    }
  }

  "claim merging" should {
    "merge base payload with claims builder" in {
      val gen = jwtPkg
        .jwt("HS256", "secret")
        .defaultHeader
        .payload("""{"scope":"base","data":"keep"}""")
        .claims(ClaimsBuilder().issuer("merged-app"))

      val session = emptySession.setJwt(gen, "jwt")
      val token   = session("jwt").as[String]
      val decoded = PdiJwt.decode(token, "secret", Seq(JwtAlgorithm.HS256))

      decoded.isSuccess shouldBe true
      val claim = decoded.get
      claim.content should include("\"scope\":\"base\"")
      claim.content should include("\"data\":\"keep\"")
      claim.issuer shouldBe Some("merged-app")
    }

    "claims builder overrides base payload on conflict" in {
      val gen = jwtPkg
        .jwt("HS256", "secret")
        .defaultHeader
        .payload("""{"scope":"original"}""")
        .claims(ClaimsBuilder().claim("scope", "overridden"))

      val session = emptySession.setJwt(gen, "jwt")
      val token   = session("jwt").as[String]
      val decoded = PdiJwt.decode(token, "secret", Seq(JwtAlgorithm.HS256))

      decoded.isSuccess shouldBe true
      decoded.get.content should include("\"scope\":\"overridden\"")
      decoded.get.content should not include "\"scope\":\"original\""
    }
  }

  "claimFromSession typing" should {
    "serialize a numeric session value as a JSON number" in {
      val session = emptySession.set("uid", 42L)
      val json    = resolveJson(ClaimsBuilder().claimFromSession("uid", "#{uid}"), session)
      json should include("\"uid\":42")
      json should not include "\"uid\":\"42\""
    }

    "serialize a boolean session value as a JSON boolean" in {
      val session = emptySession.set("flag", true)
      val json    = resolveJson(ClaimsBuilder().claimFromSession("flag", "#{flag}"), session)
      json should include("\"flag\":true")
      json should not include "\"flag\":\"true\""
    }

    "keep a String session value as a JSON string" in {
      val session = emptySession.set("name", "alice")
      resolveJson(ClaimsBuilder().claimFromSession("name", "#{name}"), session) should include("\"name\":\"alice\"")
    }

    "keep a numeric-looking String as a JSON string (no silent retyping)" in {
      val session = emptySession.set("uid", "42")
      resolveJson(ClaimsBuilder().claimFromSession("uid", "#{uid}"), session) should include("\"uid\":\"42\"")
    }

    "force a JSON string via .as[String] on a numeric value" in {
      val session = emptySession.set("uid", 42L)
      resolveJson(ClaimsBuilder().claimFromSession("uid", "#{uid}").as[String], session) should include("\"uid\":\"42\"")
    }

    "force a JSON number via .as[Long] on a numeric string" in {
      val session = emptySession.set("uid", "42")
      val json    = resolveJson(ClaimsBuilder().claimFromSession("uid", "#{uid}").as[Long], session)
      json should include("\"uid\":42")
      json should not include "\"uid\":\"42\""
    }

    "force a JSON number via .as[Double] on a numeric string" in {
      val session = emptySession.set("ratio", "1.5")
      resolveJson(ClaimsBuilder().claimFromSession("ratio", "#{ratio}").as[Double], session) should include("\"ratio\":1.5")
    }

    "force a JSON boolean via .as[Boolean] on a string" in {
      val session = emptySession.set("ok", "true")
      val json    = resolveJson(ClaimsBuilder().claimFromSession("ok", "#{ok}").as[Boolean], session)
      json should include("\"ok\":true")
      json should not include "\"ok\":\"true\""
    }

    "parse a JSON-string session value as a JSON array via .as[JValue]" in {
      val session = emptySession.set("aud", """["svc-1","svc-2"]""")
      val json    = resolveJson(ClaimsBuilder().claimFromSession("aud", "#{aud}").as[org.json4s.JValue], session)
      json should include("\"aud\":[\"svc-1\",\"svc-2\"]")
    }

    "fail generation when forcing a Long on a non-numeric value" in {
      val session = emptySession.set("uid", "not-a-number")
      val result  = ClaimsBuilder().claimFromSession("uid", "#{uid}").as[Long].resolve(session)
      result.isInstanceOf[io.gatling.commons.validation.Failure] shouldBe true
    }

    "not NPE when forcing a type on a null session value" in {
      val session = emptySession.set("opt", null)
      // A null session value must resolve to JSON null (or a clean Failure) — never throw an NPE.
      noException should be thrownBy ClaimsBuilder().claimFromSession("opt", "#{opt}").as[Boolean].resolve(session)
    }
  }

  "string claim output stability (FR-010)" should {
    "serialize a String session claim byte-for-byte as a quoted JSON string" in {
      val session = emptySession.set("uid", "abc-123")
      resolveJson(ClaimsBuilder().claimFromSession("uid", "#{uid}"), session) shouldBe """{"uid":"abc-123"}"""
    }
  }

  "algorithm/key validation" should {
    "throw IllegalArgumentException for an HMAC algorithm with an asymmetric key" in {
      val kp  = initRsaKeyPair()
      val gen = jwtPkg.jwt("HS256", kp.getPrivate).defaultHeader.payload("""{"sub":"x"}""")
      val ex  = the[IllegalArgumentException] thrownBy emptySession.setJwt(gen, "jwt")
      ex.getMessage should include("HS256")
    }

    "throw IllegalArgumentException for an asymmetric algorithm with a string secret" in {
      val gen = jwtPkg.jwt("RS256", "secret").defaultHeader.payload("""{"sub":"x"}""")
      val ex  = the[IllegalArgumentException] thrownBy emptySession.setJwt(gen, "jwt")
      ex.getMessage should include("RS256")
    }
  }

  "setJwt failure path (FR-008)" should {
    "throw IllegalStateException when a claim EL is unresolvable" in {
      val gen = jwtPkg
        .jwt("HS256", "secret")
        .defaultHeader
        .claims(ClaimsBuilder().claimFromSession("uid", "#{missing}"))
      val ex  = the[IllegalStateException] thrownBy emptySession.setJwt(gen, "jwt")
      ex.getMessage should include("JWT generation failed")
    }

    "throw IllegalStateException for setJwtAsBearer with an unresolvable EL" in {
      val gen = jwtPkg
        .jwt("HS256", "secret")
        .defaultHeader
        .claims(ClaimsBuilder().claimFromSession("uid", "#{missing}"))
      an[IllegalStateException] should be thrownBy emptySession.setJwtAsBearer(gen)
    }
  }

}
