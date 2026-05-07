package org.galaxio.gatling.config

import org.galaxio.gatling.javaapi.SimulationConfig._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Duration

class JavaConfigTest extends AnyWordSpec with Matchers {

  "Java SimulationConfig facade" should {
    "read base URLs from simulation.conf" in {
      baseUrl() shouldBe "http://testbaseurl.org/"
      baseAuthUrl() shouldBe "http://testbaseauthurl.org/"
      wsBaseUrl() shouldBe "http://testwsbaseurl.org/"
    }

    "read scalar params from simulation.conf" in {
      getStringParam("stringParam") shouldBe "testString"
      getIntParam("intParam") shouldBe 10
      getDoubleParam("doubleParam") shouldBe 10.1
      getBooleanParam("booleanParam") shouldBe true
    }

    "read list and nested params from simulation.conf" in {
      getStringListParam("stringListParam") shouldBe java.util.List.of("first", "second")
      getConfigParam("nestedParam").getString("child") shouldBe "value"
    }

    "read optional params from simulation.conf" in {
      getOptStringParam("stringParam").orElseThrow() shouldBe "testString"
      getOptStringParam("missingParam").isEmpty shouldBe true
      getOptStringListParam("stringListParam").orElseThrow() shouldBe java.util.List.of("first", "second")
    }

    "read duration params from simulation.conf" in {
      getDurationParam("durationParam") shouldBe Duration.ofSeconds(10)
      rampDuration() shouldBe Duration.ofMinutes(10)
      stageDuration() shouldBe Duration.ofMinutes(30)
      testDuration() shouldBe Duration.ofMinutes((10 + 30) * 5)
    }

    "read workload params from simulation.conf" in {
      stagesNumber() shouldBe 5
      intensity() shouldBe 1.0
    }
  }
}
