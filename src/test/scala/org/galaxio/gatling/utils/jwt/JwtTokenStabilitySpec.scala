package org.galaxio.gatling.utils.jwt

import io.gatling.commons.validation.Failure
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Session
import io.gatling.core.session.el.ElParserException
import org.galaxio.gatling.transactions.FakeEventLoop
import org.galaxio.gatling.utils.{jwt => jwtPkg}
import org.json4s.JValue
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually.{eventually, interval, timeout}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import pdi.jwt.{Jwt => PdiJwt, JwtAlgorithm}

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}

/** Pins the exact bytes of generated tokens and the exact errors. Every expected value below was recorded by running this
  * spec's builders on the code before EL expressions were compiled once per builder; the spec must stay green unchanged.
  */
class JwtTokenStabilitySpec extends AnyWordSpec with Matchers {

  GatlingConfiguration.loadForTest()

  private val secret = "stability-secret"

  private val s1: Session = Session("test", 1L, new FakeEventLoop).setAll(
    "uid"    -> "user-1",
    "tenant" -> "acme",
    "kid"    -> "k-1",
    "n"      -> 42,
    "big"    -> 9000000000L,
    "ratio"  -> 1.25,
    "flag"   -> true,
    "numStr" -> "42",
    "dec"    -> BigDecimal("1.50"),
    "decInt" -> BigDecimal(100),
    "list"   -> Seq(1, 2),
    "obj"    -> Map("a" -> 1),
    "aud"    -> """["a","b"]""",
    "nested" -> """{"b":2}""",
    "opt"    -> None,
  )
  private val s2: Session = s1.set("uid", "user-2")

  private val defaultHeaderJson = """{"alg":"HS256","typ":"JWT"}"""
  private val missingMessage    = "No attribute named 'missing' is defined"
  private val malformedMessage  = "Failed to parse #{uid with error ''}' expected but end of source found'"

  private val mixedUser1Payload =
    """{"role":"admin","tenant":"acme","sub":"user-1","aud":"https://api.example.com","iss":"my-service","active":true}"""
  private val mixedUser1Token   =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYWRtaW4iLCJ0ZW5hbnQiOiJhY21lIiwic3ViIjoidXNlci0xIiwiYXVkIjoiaHR0cHM6Ly9hcGkuZXhhbXBsZS5jb20iLCJpc3MiOiJteS1zZXJ2aWNlIiwiYWN0aXZlIjp0cnVlfQ.Y0sslWbQ-Wa0d2xByd9N03grO8uChiuE0A7I0jcfiu8"
  private val mixedUser2Payload =
    """{"role":"admin","tenant":"acme","sub":"user-2","aud":"https://api.example.com","iss":"my-service","active":true}"""
  private val mixedUser2Token   =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYWRtaW4iLCJ0ZW5hbnQiOiJhY21lIiwic3ViIjoidXNlci0yIiwiYXVkIjoiaHR0cHM6Ly9hcGkuZXhhbXBsZS5jb20iLCJpc3MiOiJteS1zZXJ2aWNlIiwiYWN0aXZlIjp0cnVlfQ.1UTnm5brxeUi4PJ9xBP6SepHgl38MBDZ4LeKzWogohQ"

  private def hs256: JwtGeneratorBuilder = jwtPkg.jwt("HS256", secret)

  /** Six claims: more than four, so the payload field order is the hash order of the claim maps. A new instance per call. */
  private def mixedClaimsGenerator: JwtGeneratorBuilder =
    hs256.defaultHeader.claims(
      ClaimsBuilder()
        .issuer("my-service")
        .subject("#{uid}")
        .audience("https://api.example.com")
        .claim("role", "admin")
        .claimFromSession("tenant", "#{tenant}")
        .claim("active", true),
    )

  private def tokenOf(gen: JwtGeneratorBuilder, session: Session): String = session.setJwt(gen, "jwt")("jwt").as[String]

  private def bearerOf(gen: JwtGeneratorBuilder, session: Session): String =
    session.setJwtAsBearer(gen)("Authorization").as[String]

  private def decodePart(part: String): String = new String(Base64.getUrlDecoder.decode(part), StandardCharsets.UTF_8)

  private def nowSeconds(): Long = System.currentTimeMillis() / 1000

  private def checkDecoded(token: String, header: String, payload: String): Assertion = {
    val parts = token.split('.')
    parts should have length 3
    decodePart(parts(0)) shouldBe header
    decodePart(parts(1)) shouldBe payload
    PdiJwt.isValid(token, secret, Seq(JwtAlgorithm.HS256)) shouldBe true
  }

  /** The token `setJwt` stores and the value `setJwtAsBearer` stores for the same generator and session. */
  private def checkToken(
      gen: JwtGeneratorBuilder,
      session: Session,
      header: String,
      payload: String,
      expected: String,
  ): Assertion = {
    val token = tokenOf(gen, session)
    checkDecoded(token, header, payload)
    token shouldBe expected
    bearerOf(gen, session) shouldBe s"Bearer $expected"
  }

  "FR-003: setJwt" should {
    "keep the exact token of static claims" in {
      val gen = hs256.defaultHeader.claims(
        ClaimsBuilder().issuer("my-service").subject("static-user").claim("role", "admin").claim("level", 3L),
      )
      checkToken(
        gen,
        s1,
        defaultHeaderJson,
        """{"iss":"my-service","sub":"static-user","role":"admin","level":3}""",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJteS1zZXJ2aWNlIiwic3ViIjoic3RhdGljLXVzZXIiLCJyb2xlIjoiYWRtaW4iLCJsZXZlbCI6M30._KNaz4sNI-FnnhuU8SpGazbTd-rB3KS64bf7UV2oPrM",
      )
    }

    "keep the exact token of more than four mixed claims in their hash order" in {
      checkToken(mixedClaimsGenerator, s1, defaultHeaderJson, mixedUser1Payload, mixedUser1Token)
    }

    "keep the exact token of every session claim type" in {
      val claims = ClaimsBuilder()
        .claimFromSession("n", "#{n}")
        .claimFromSession("big", "#{big}")
        .claimFromSession("ratio", "#{ratio}")
        .claimFromSession("flag", "#{flag}")
        .claimFromSession("numStr", "#{numStr}")
        .claimFromSession("dec", "#{dec}")
        .claimFromSession("decInt", "#{decInt}")
        .claimFromSession("list", "#{list}")
        .claimFromSession("obj", "#{obj}")
        .claimFromSession("opt", "#{opt}")
        .claimFromSession("nAsString", "#{n}")
        .as[String]
        .claimFromSession("numStrAsLong", "#{numStr}")
        .as[Long]
        .claimFromSession("numStrAsDouble", "#{numStr}")
        .as[Double]
        .claimFromSession("flagAsBoolean", "#{flag}")
        .as[Boolean]
        .claimFromSession("aud", "#{aud}")
        .as[JValue]
      // BigDecimal 1.50 leaves the claims as 1.50 but reaches the token as 1.5: the payload takes the claims as parsing
      // their text gives them.
      checkToken(
        hs256.defaultHeader.claims(claims),
        s1,
        defaultHeaderJson,
        """{"flagAsBoolean":true,"big":9000000000,"numStr":"42","numStrAsLong":42,"obj":{"a":1},"nAsString":"42","ratio":1.25,"flag":true,"numStrAsDouble":42.0,"dec":1.5,"list":[1,2],"opt":null,"n":42,"decInt":100,"aud":["a","b"]}""",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJmbGFnQXNCb29sZWFuIjp0cnVlLCJiaWciOjkwMDAwMDAwMDAsIm51bVN0ciI6IjQyIiwibnVtU3RyQXNMb25nIjo0Miwib2JqIjp7ImEiOjF9LCJuQXNTdHJpbmciOiI0MiIsInJhdGlvIjoxLjI1LCJmbGFnIjp0cnVlLCJudW1TdHJBc0RvdWJsZSI6NDIuMCwiZGVjIjoxLjUsImxpc3QiOlsxLDJdLCJvcHQiOm51bGwsIm4iOjQyLCJkZWNJbnQiOjEwMCwiYXVkIjpbImEiLCJiIl19.WYR-9ZpP2MIPNUCLzU3HycXwrjKS3z2ysBfQfrLEWAE",
      )
    }

    "keep the exact token of a header with EL" in {
      val gen = hs256
        .header("""{"alg":"HS256","typ":"JWT","kid":"#{kid}"}""")
        .payload("""{"sub":"static-user","scope":"api"}""")
      checkToken(
        gen,
        s1,
        """{"alg":"HS256","typ":"JWT","kid":"k-1"}""",
        """{"sub":"static-user","scope":"api"}""",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImstMSJ9.eyJzdWIiOiJzdGF0aWMtdXNlciIsInNjb3BlIjoiYXBpIn0.ThzQRoAjr0qsaOqWpqASELXxbl7jxFyvd98_4worgwg",
      )
    }

    "keep the exact token of a payload with EL and no claims" in {
      val gen = hs256.defaultHeader.payload("""{"sub":"#{uid}","scope":"api"}""")
      checkToken(
        gen,
        s1,
        defaultHeaderJson,
        """{"sub":"user-1","scope":"api"}""",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEiLCJzY29wZSI6ImFwaSJ9.0D4uWLFbSFYhaQl3lZ17I4Mpy7bjI7aAK84nXs96ddQ",
      )
    }

    "keep the exact token of a payload with EL merged with conflicting claims" in {
      val gen = hs256.defaultHeader
        .payload("""{"scope":"base","sub":"#{uid}","nested":{"a":1},"keep":"x"}""")
        .claims(ClaimsBuilder().claim("scope", "claims").claimFromSession("nested", "#{nested}").asJson)
      checkToken(
        gen,
        s1,
        defaultHeaderJson,
        """{"scope":"claims","sub":"user-1","nested":{"a":1,"b":2},"keep":"x"}""",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzY29wZSI6ImNsYWltcyIsInN1YiI6InVzZXItMSIsIm5lc3RlZCI6eyJhIjoxLCJiIjoyfSwia2VlcCI6IngifQ.ljtO3dIOJ3uUoN5up4bovpsOiHiQXVCuO8EuI7hWvhY",
      )
    }

    "keep the exact token of BigDecimal claims inside an array and inside an object in an array" in {
      val session = s1.setAll("decs" -> Seq(BigDecimal("1.50")), "decObjs" -> Seq(Map("v" -> BigDecimal("1.50"))))
      val gen     = hs256.defaultHeader.claims(
        ClaimsBuilder().claimFromSession("measurements", "#{decs}").claimFromSession("readings", "#{decObjs}"),
      )
      checkToken(
        gen,
        session,
        defaultHeaderJson,
        """{"measurements":[1.5],"readings":[{"v":1.5}]}""",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJtZWFzdXJlbWVudHMiOlsxLjVdLCJyZWFkaW5ncyI6W3sidiI6MS41fV19.wRnzAka0MxFWIjSLQc5ji6RS8FIlrubMkIgTE4X4mwM",
      )
    }

    "keep the exact token of integer arrays merged into the arrays of the payload" in {
      // A merged array keeps one of the elements that equal: the claims' integers must be the payload's kind of integer.
      val session = s1.setAll("ids" -> Seq(1, 2), "group" -> Map("ids" -> Seq(2, 3)))
      val gen     = hs256.defaultHeader
        .payload("""{"ids":[1],"group":{"ids":[1,2]}}""")
        .claims(ClaimsBuilder().claimFromSession("ids", "#{ids}").claimFromSession("group", "#{group}"))
      checkToken(
        gen,
        session,
        defaultHeaderJson,
        """{"ids":[1,2],"group":{"ids":[1,2,3]}}""",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZHMiOlsxLDJdLCJncm91cCI6eyJpZHMiOlsxLDIsM119fQ.XOwYY1FihBQ4gOUp58qHWuH1imka5kRgyt7bUipaEOc",
      )
    }

    "keep the exact payload of time claims set at the second of the call" in {
      val gen                  = hs256.defaultHeader.claims(ClaimsBuilder().subject("#{uid}").issuedAtNow.notBeforeNow.expiresIn(300.seconds))
      val sameSecond           = Iterator.continually {
        val start = nowSeconds()
        val token = tokenOf(gen, s1)
        (start, token, bearerOf(gen, s1), nowSeconds())
      }
        .take(3)
        .collectFirst { case (start, token, bearer, end) if start == end => (start, token, bearer) }
      val (now, token, bearer) = sameSecond.getOrElse(fail("the second changed during each of three calls"))
      val payload              = s"""{"nbf":$now,"exp":${now + 300},"iat":$now,"sub":"user-1"}"""
      // No literal can hold a token of the current second: the whole string is the HS256 token of the exact header and payload.
      val expected             = PdiJwt.encode(defaultHeaderJson, payload, secret, JwtAlgorithm.HS256)
      checkDecoded(token, defaultHeaderJson, payload)
      token shouldBe expected
      bearer shouldBe s"Bearer $expected"
    }

    "give the same token on a second call and each session its own token" in {
      val gen = mixedClaimsGenerator
      tokenOf(gen, s1) shouldBe mixedUser1Token
      tokenOf(gen, s1) shouldBe mixedUser1Token
      checkToken(gen, s2, defaultHeaderJson, mixedUser2Payload, mixedUser2Token)
    }

    "give each virtual user its own token when they generate their first tokens at once" in {
      val users                                          =
        Vector.tabulate(8)(i => if (i % 2 == 0) (s1, mixedUser1Token) else (s2, mixedUser2Token))
      implicit val pool: ExecutionContextExecutorService =
        ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(users.size))
      // A fresh generator per round, so that every round races on its first token.
      def round(): Vector[String]                        = {
        val gen     = mixedClaimsGenerator
        val ready   = new CountDownLatch(users.size)
        val started = new CountDownLatch(1)
        val tokens  = users.map { case (session, _) =>
          Future {
            ready.countDown()
            started.await()
            tokenOf(gen, session)
          }
        }
        // Release the users only once every one of them waits at the start.
        ready.await(30, TimeUnit.SECONDS) shouldBe true
        started.countDown()
        Await.result(Future.sequence(tokens), 30.seconds)
      }
      try (1 to 5).foreach(_ => round() shouldBe users.map(_._2))
      // shutdownNow, so that a failed round does not leave users waiting at the start.
      finally pool.shutdownNow()
    }
  }

  "FR-003: one generator" should {
    "give every token the EL values of its own session and the iat and exp of its own second" in {
      // One instance for every token: EL in the header, the payload and a claim, and iat and exp from the second of the call.
      val gen      = hs256
        .header("""{"alg":"HS256","typ":"JWT","kid":"#{kid}"}""")
        .payload("""{"sub":"#{uid}","scope":"api"}""")
        .claims(ClaimsBuilder().claimFromSession("tenant", "#{tenant}").issuedAtNow.expiresIn(300.seconds))
      val users    = Vector(("k-1", "user-1", "acme"), ("k-2", "user-2", "globex"), ("k-3", "user-3", "initech"))
      val sessions = users.map { case (kid, uid, tenant) => s1.setAll("kid" -> kid, "uid" -> uid, "tenant" -> tenant) }

      // The token of every session, all within one second, and that second.
      def round(): (Long, Vector[String]) =
        Iterator.continually {
          val start  = nowSeconds()
          val tokens = sessions.map(tokenOf(gen, _))
          (start, tokens, nowSeconds())
        }
          .take(3)
          .collectFirst { case (start, tokens, end) if start == end => (start, tokens) }
          .getOrElse(fail("the second changed during each of three rounds"))

      def checkRound(second: Long, tokens: Vector[String]): Unit =
        tokens.zip(users).foreach { case (token, (kid, uid, tenant)) =>
          val header  = s"""{"alg":"HS256","typ":"JWT","kid":"$kid"}"""
          val payload = s"""{"sub":"$uid","scope":"api","exp":${second + 300},"iat":$second,"tenant":"$tenant"}"""
          checkDecoded(token, header, payload)
          token shouldBe PdiJwt.encode(header, payload, secret, JwtAlgorithm.HS256)
        }

      val (firstSecond, firstTokens)   = round()
      // Poll the clock until the next second, so that the second round runs in a later second.
      eventually(timeout(Span(3, Seconds)), interval(Span(10, Millis)))(nowSeconds() should be > firstSecond)
      val (secondSecond, secondTokens) = round()
      secondSecond should be > firstSecond
      checkRound(firstSecond, firstTokens)
      checkRound(secondSecond, secondTokens)
    }
  }

  "FR-003: setJwtAsBearer" should {
    "store Bearer and the same exact token" in {
      s1.setJwtAsBearer(mixedClaimsGenerator)("Authorization").as[String] shouldBe s"Bearer $mixedUser1Token"
    }

    "throw the same IllegalStateException for an unresolvable claim" in {
      val gen = hs256.defaultHeader.claims(ClaimsBuilder().claimFromSession("uid", "#{missing}"))
      val ex  = the[IllegalStateException] thrownBy s1.setJwtAsBearer(gen)
      ex.getMessage shouldBe s"JWT generation failed: $missingMessage"
    }
  }

  "FR-003: an unresolvable EL" should {
    "give the same Failure from resolve" in {
      ClaimsBuilder().claimFromSession("uid", "#{missing}").resolve(s1) shouldBe Failure(missingMessage)
    }

    "make setJwt throw the same IllegalStateException for a claim" in {
      val gen = hs256.defaultHeader.claims(ClaimsBuilder().claimFromSession("uid", "#{missing}"))
      val ex  = the[IllegalStateException] thrownBy s1.setJwt(gen, "jwt")
      ex.getMessage shouldBe s"JWT generation failed: $missingMessage"
    }

    "make setJwt throw the same IllegalStateException for a header and a payload whose EL the JSON brace closes" in {
      // `#{kid"}`: the brace closing the JSON object also closes the EL, so the attribute name is `kid"`.
      val badHeader  = hs256.header("""{"kid":"#{kid"}""").payload("""{"sub":"x"}""")
      (the[IllegalStateException] thrownBy s1.setJwt(badHeader, "jwt")).getMessage shouldBe
        """JWT generation failed: No attribute named 'kid"' is defined"""
      val badPayload = hs256.defaultHeader.payload("""{"sub":"#{uid"}""")
      (the[IllegalStateException] thrownBy s1.setJwt(badPayload, "jwt")).getMessage shouldBe
        """JWT generation failed: No attribute named 'uid"' is defined"""
    }
  }

  "FR-003: a malformed EL" should {
    // `#{uid` with nothing after it: the EL parser fails on a missing closing brace.
    def badClaimGenerator: JwtGeneratorBuilder         = hs256.defaultHeader.claims(ClaimsBuilder().subject("#{uid"))
    def badHeaderGenerator: JwtGeneratorBuilder        = hs256.header("""{"kid":"#{uid""").payload("""{"sub":"x"}""")
    def badPayloadGenerator: JwtGeneratorBuilder       = hs256.defaultHeader.payload("""{"sub":"#{uid""")
    def badPayloadClaimsGenerator: JwtGeneratorBuilder =
      hs256.defaultHeader.payload("""{"sub":"#{uid""").claims(ClaimsBuilder().issuer("my-service"))

    def throwsOnEveryToken(gen: JwtGeneratorBuilder, message: String): Assertion = {
      (the[ElParserException] thrownBy s1.setJwt(gen, "jwt")).getMessage shouldBe message
      (the[ElParserException] thrownBy s1.setJwt(gen, "jwt")).getMessage shouldBe message
      (the[ElParserException] thrownBy s1.setJwtAsBearer(gen)).getMessage shouldBe message
    }

    "not throw when the builder is built" in {
      noException should be thrownBy {
        badClaimGenerator
        badHeaderGenerator
        badPayloadGenerator
        badPayloadClaimsGenerator
      }
    }

    "throw the same ElParserException on every token for a claim" in {
      throwsOnEveryToken(badClaimGenerator, malformedMessage)
    }

    "throw the same ElParserException on every token for a header" in {
      throwsOnEveryToken(
        badHeaderGenerator,
        """Failed to parse {"kid":"#{uid with error ''}' expected but end of source found'""",
      )
    }

    "throw the same ElParserException on every token for a payload without claims" in {
      throwsOnEveryToken(
        badPayloadGenerator,
        """Failed to parse {"sub":"#{uid with error ''}' expected but end of source found'""",
      )
    }

    "throw the same ElParserException on every token for a payload merged with claims" in {
      throwsOnEveryToken(
        badPayloadClaimsGenerator,
        """Failed to parse {"sub":"#{uid with error ''}' expected but end of source found'""",
      )
    }
  }

  "FR-003: claim order" should {
    "give the Failure when an unresolvable claim comes before a malformed one" in {
      ClaimsBuilder().claimFromSession("a", "#{missing}").subject("#{uid").resolve(s1) shouldBe Failure(missingMessage)
    }

    "throw the ElParserException when the malformed claim comes first" in {
      val claims = ClaimsBuilder().subject("#{uid").claimFromSession("a", "#{missing}")
      (the[ElParserException] thrownBy claims.resolve(s1)).getMessage shouldBe malformedMessage
    }
  }

}
