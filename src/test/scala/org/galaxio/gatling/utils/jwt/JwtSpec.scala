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

}
