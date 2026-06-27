package org.galaxio.gatling.config

import com.typesafe.config.ConfigFactory
import org.galaxio.gatling.testutil.LogCapture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class SimulationConfigUtilsSpec extends AnyWordSpec with Matchers {

  "SimulationConfigUtils" should {
    "return optional values" in {
      val config = SimulationConfigUtils(ConfigFactory.parseString("""existing = "value""""))

      config.getOpt[String]("existing") shouldBe Some("value")
      config.getOpt[String]("missing") shouldBe None
    }

    "read string lists and nested config blocks" in {
      val config = SimulationConfigUtils(
        ConfigFactory.parseString("""
            |hosts = ["one", "two"]
            |http {
            |  headers {
            |    accept = "application/json"
            |  }
            |}
            |""".stripMargin),
      )

      config.get[List[String]]("hosts") shouldBe List("one", "two")
      config.get[com.typesafe.config.Config]("http").getString("headers.accept") shouldBe "application/json"
    }

    "prefer system property overrides" in {
      val property = "picatinny.config.override.test"

      System.setProperty(property, "from-system-property")
      ConfigFactory.invalidateCaches()

      try {
        val config = SimulationConfigUtils(
          ConfigFactory.systemProperties().withFallback(ConfigFactory.parseString(s"""$property = "from-file"""")),
        )

        config.get[String](property) shouldBe "from-system-property"
      } finally {
        System.clearProperty(property)
        ConfigFactory.invalidateCaches()
      }
    }

    "throw a clear exception for missing required values" in {
      val config = SimulationConfigUtils(ConfigFactory.parseString("""existing = "value""""))

      val thrown = the[SimulationConfigException] thrownBy {
        config.get[String]("missing")
      }

      thrown.getMessage should include("Missing required simulation config value: missing")
      thrown.getMessage should include("simulation.conf")
      thrown.getMessage should include("-Dmissing=<value>")
    }

    "throw a clear exception for wrong value types" in {
      val config = SimulationConfigUtils(ConfigFactory.parseString("""count = "not-an-int""""))

      val thrown = the[SimulationConfigException] thrownBy {
        config.get[Int]("count")
      }

      thrown.getMessage should include("Invalid simulation config value at count")
      thrown.getMessage should include("Int")
    }

    "validate positive numbers and durations" in {
      val config = SimulationConfigUtils(ConfigFactory.empty())

      config.requirePositive("stagesNumber", 1) shouldBe 1
      config.requirePositive("intensity", 1.0) shouldBe 1.0
      config.requirePositive("stageDuration", 1.second) shouldBe 1.second

      all(
        Seq(
          the[SimulationConfigException] thrownBy config.requirePositive("stagesNumber", 0),
          the[SimulationConfigException] thrownBy config.requirePositive("intensity", 0.0),
          the[SimulationConfigException] thrownBy config.requirePositive("stageDuration", Duration.Zero),
        ).map(_.getMessage),
      ) should include("must be greater than 0")
    }

    "validate non-negative durations" in {
      val config = SimulationConfigUtils(ConfigFactory.empty())

      config.requireNonNegative("rampDuration", Duration.Zero) shouldBe Duration.Zero
      config.requireNonNegative("rampDuration", 1.second) shouldBe 1.second

      val thrown = the[SimulationConfigException] thrownBy {
        config.requireNonNegative("rampDuration", -1.second)
      }

      thrown.getMessage should include("rampDuration")
      thrown.getMessage should include("0 or greater")
    }
  }
}

class ConfigValueMaskingSpec extends AnyWordSpec with Matchers {

  "ConfigValueMasking" should {
    "mask sensitive config paths" in {
      val sensitivePaths = Seq(
        "db.password",
        "service.passwd",
        "service.pwd",
        "oauth.secret",
        "auth.token",
        "client.apiKey",
        "client.api-key",
        "client.api_key",
        "aws.credential",
        "vault.private_key",
        "vault.client_secret",
        "aws.access_key",
        "secrets.secret_key",
      )

      sensitivePaths.foreach { path =>
        ConfigValueMasking.displayValue(path, "raw-value") shouldBe "******"
        ConfigValueMasking.isSensitive(path) shouldBe true
      }
    }

    "mask representative hocon secret keys" in {
      val config = ConfigFactory.parseString("""
          |secrets {
          |  private_key = "private"
          |  client_secret = "client"
          |  access_key = "access"
          |  secret_key = "secret"
          |}
          |""".stripMargin)

      val secretPaths = Seq(
        "secrets.private_key",
        "secrets.client_secret",
        "secrets.access_key",
        "secrets.secret_key",
      )

      secretPaths.foreach { path =>
        ConfigValueMasking.displayValue(path, config.getString(path)) shouldBe "******"
      }
    }

    "leave non-sensitive values visible" in {
      ConfigValueMasking.displayValue("baseUrl", "http://localhost") shouldBe "http://localhost"
      ConfigValueMasking.isSensitive("baseUrl") shouldBe false
    }

    "handle null display inputs defensively" in {
      ConfigValueMasking.displayValue(null, "value") shouldBe "value"
      ConfigValueMasking.displayValue("token", null) shouldBe "******"
    }

    "match required sensitive terms by whole word on the last path segment (FR-003)" in {
      val sensitive = Seq(
        "authorization",
        "bearerToken",
        "passphrase",
        "client.apiKey",
        "vault.clientSecret",
        "db.password",
        "auth.token",
        // prefix-secret compounds: the secret word is the HEAD, not the tail
        "passwordHash",
        "tokenValue",
        "secretValue",
        "vault.secret_id",
      )
      sensitive.foreach(p => withClue(p) { ConfigValueMasking.isSensitive(p) shouldBe true })
    }

    // Regression guard: the old substring matcher masked separator-less keys; whole-word matching must not LEAK them.
    // Covered by the suffix floor (the secret noun is the tail) so the over-match fix for tokenBucketSize/secretariat holds.
    "mask separator-less and plural secret keys (no masking regression vs substring)" in {
      val sensitive = Seq(
        "dbpassword",
        "apisecret",
        "accesstoken",
        "apitoken",
        "authtoken",
        "adminpwd",
        "userpassword",
        "jwtsecret",
        "myapikey",
        "credentials",
        "app.credentials",
      )
      sensitive.foreach(p => withClue(p) { ConfigValueMasking.isSensitive(p) shouldBe true })
    }

    "not mask a strong term followed by a benign structural tail (FR-003 boundary)" in {
      val benignTails = Seq(
        "tokenBucketSize",
        "passwordLength",
        "secretRotation",
        "tokenTtl",
      )
      benignTails.foreach(p => withClue(p) { ConfigValueMasking.isSensitive(p) shouldBe false })
    }

    "not over-match non-secret identifiers or benign compounds (FR-003 negatives)" in {
      val benign = Seq(
        "roleId",
        "roleIdPrefix",
        "tokenBucketSize",
        "apiKeyboard",
        "secretariat",
        "baseUrl",
      )
      benign.foreach(p => withClue(p) { ConfigValueMasking.isSensitive(p) shouldBe false })
    }

    "merge user-supplied sensitive keys without dropping built-ins (FR-012)" in {
      val masking = ConfigValueMasking.fromConfig(
        ConfigFactory.parseString("""picatinny.redaction.additionalSensitiveKeys = ["tenantRef"]"""),
      )
      masking.displayValue("tenantRef", "v") shouldBe "******"   // user-added
      masking.displayValue("db.password", "v") shouldBe "******" // built-in still masks (merge, not replace)
      masking.displayValue("baseUrl", "http://x") shouldBe "http://x"

      val defaults = ConfigValueMasking.fromConfig(ConfigFactory.empty())
      defaults.displayValue("db.password", "v") shouldBe "******" // absent block → built-ins apply
      defaults.displayValue("tenantRef", "v") shouldBe "v"        // not a built-in
    }

    "mask secret leaves inside a benignly-named nested block (FR-004)" in {
      val cfg = ConfigFactory.parseString(
        """url = "http://h"
          |token = "abc"
          |nested { inner { secret = "deep" } }
          |size = 10
          |""".stripMargin,
      )
      val out = ConfigValueMasking.displayConfig(cfg)
      out should include("url = http://h")
      out should include("token = ******")
      out should include("nested.inner.secret = ******")
      out should include("size = 10")
      out should not include "abc"
      out should not include "deep"
    }

    "strip URL userinfo fail-safely (FR-007)" in {
      ConfigValueMasking.redactUserInfo("https://user:pass@host:8080/p") shouldBe "https://******@host:8080/p"
      ConfigValueMasking.redactUserInfo("https://host/p") shouldBe "https://host/p"
      ConfigValueMasking.redactUserInfo("mailto:a@b") shouldBe "mailto:a@b"
      val malformed = ConfigValueMasking.redactUserInfo("https://user:p%ss@host/path")
      malformed should include("******")
      malformed should not include "p%ss"
      malformed should not include "user:p"
    }

    "mask the logged param value at the config sink and leave others visible (FR-001)" in {
      val utils   = SimulationConfigUtils(
        ConfigFactory.parseString("""db { password = "s3cr3t", url = "http://localhost" }"""),
      )
      val logs    = LogCapture
        .infoEvents("org.galaxio.gatling.config.SimulationConfigUtils") {
          utils.get[String]("db.password")
          utils.get[String]("db.url")
        }
        .map(_.getFormattedMessage)
      val pwdLine = logs.find(_.contains("db.password")).getOrElse("")
      pwdLine should include("******")
      pwdLine should not include "s3cr3t"
      val urlLine = logs.find(_.contains("db.url")).getOrElse("")
      urlLine should include("http://localhost")
    }
  }
}
