package org.galaxio.gatling.config

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

/** Unit tests for the [[SimulationConfig]] facade (test-model layer 1), driven against the test `simulation.conf` resource.
  * Asserts exact values for the required/optional/default getters and the derived lazy vals, plus the negative case (a missing
  * required path throws [[SimulationConfigException]]).
  */
class SimulationConfigSpec extends AnyWordSpec with Matchers {

  // Namespaced, collision-proof absent path: `simulationConfig` overlays JVM system properties, so a bare key like "missing"
  // could in theory be set via -Dmissing=... and flip these negative assertions.
  private val Absent = "picatinny.test.definitely.absent"

  "SimulationConfig required getters" should {
    "read typed values from simulation.conf" in {
      SimulationConfig.getStringParam("stringParam") shouldBe "testString"
      SimulationConfig.getIntParam("intParam") shouldBe 10
      SimulationConfig.getDoubleParam("doubleParam") shouldBe 10.1
      SimulationConfig.getBooleanParam("booleanParam") shouldBe true
      SimulationConfig.getDurationParam("durationParam") shouldBe 10.seconds
      SimulationConfig.getStringListParam("stringListParam") shouldBe List("first", "second")
      SimulationConfig.getConfigParam("nestedParam").getString("child") shouldBe "value"
    }

    "throw SimulationConfigException for a missing required path (negative)" in {
      a[SimulationConfigException] should be thrownBy SimulationConfig.getStringParam("definitely.missing.path")
    }
  }

  "SimulationConfig optional getters" should {
    "return Some for present and None for missing paths" in {
      SimulationConfig.getOptStringParam("stringParam") shouldBe Some("testString")
      SimulationConfig.getOptIntParam("intParam") shouldBe Some(10)
      SimulationConfig.getOptBooleanParam("booleanParam") shouldBe Some(true)
      SimulationConfig.getOptStringParam(Absent) shouldBe None
      SimulationConfig.getOptIntParam(Absent) shouldBe None
      SimulationConfig.getOptDurationParam(Absent) shouldBe None
    }
  }

  "SimulationConfig default getters" should {
    "return the configured value when present and the default when missing" in {
      SimulationConfig.getStringParam("stringParam", "fallback") shouldBe "testString"
      SimulationConfig.getStringParam(Absent, "fallback") shouldBe "fallback"
      SimulationConfig.getIntParam(Absent, 42) shouldBe 42
      SimulationConfig.getBooleanParam(Absent, default = true) shouldBe true
    }
  }

  "SimulationConfig derived settings" should {
    "expose the base URLs" in {
      SimulationConfig.baseUrl shouldBe "http://testbaseurl.org/"
      SimulationConfig.baseAuthUrl shouldBe "http://testbaseauthurl.org/"
      SimulationConfig.wsBaseUrl shouldBe "http://testwsbaseurl.org/"
    }

    "expose workload stages and durations" in {
      SimulationConfig.stagesNumber shouldBe 5
      SimulationConfig.rampDuration shouldBe 10.minutes
      SimulationConfig.stageDuration shouldBe 30.minutes
    }

    "default testDuration to (ramp + stage) * stages" in {
      SimulationConfig.testDuration shouldBe (10.minutes + 30.minutes) * 5 // = 200 minutes
    }

    "convert intensity to requests per second (3600 rph == 1.0 rps)" in {
      SimulationConfig.intensity shouldBe 1.0
    }
  }
}
