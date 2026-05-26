package org.galaxio.gatling.config

import com.typesafe.config.ConfigFactory
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
  }
}
