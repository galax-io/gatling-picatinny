package org.galaxio.gatling.diagnostics

import io.gatling.core.controller.inject.closed._
import io.gatling.core.controller.inject.open._
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class InjectionProfileParserSpec extends AnyWordSpec with Matchers with OptionValues {

  "InjectionProfileParser.requireArity" should {
    "throw IllegalArgumentException when a step has fewer fields than required" in {
      val thrown = intercept[IllegalArgumentException] {
        InjectionProfileParser.requireArity(Tuple1("only-one-field"), 2)
      }
      thrown.getMessage should include("productArity=1")
      thrown.getMessage should include("at least 2")
    }

    "accept a step whose arity meets the requirement" in {
      noException should be thrownBy InjectionProfileParser.requireArity(("a", "b"), 2)
    }

    "accept a step whose arity exceeds the requirement" in {
      noException should be thrownBy InjectionProfileParser.requireArity(("a", "b", "c"), 2)
    }
  }

  "InjectionProfileParser.fromClosed" should {
    "produce WorkloadSettings from a ramp concurrent-users step" in {
      val settings = InjectionProfileParser.fromClosed(List(RampConcurrentUsersInjection(10, 50, 60.seconds))).value

      settings.unit shouldBe "users"
      settings.intensityRps shouldBe 50.0
      settings.testDuration shouldBe 60.seconds
    }

    "return None for an empty step list" in {
      InjectionProfileParser.fromClosed(Nil) shouldBe None
    }
  }

  "InjectionProfileParser.fromOpen" should {
    "produce WorkloadSettings from a ramp-rate open step" in {
      val settings = InjectionProfileParser.fromOpen(List(RampRateOpenInjection(1.0, 10.0, 60.seconds))).value

      settings.unit shouldBe "rps"
      settings.intensityRps shouldBe 10.0
      settings.testDuration shouldBe 60.seconds
    }

    "return None for an empty step list" in {
      InjectionProfileParser.fromOpen(Nil) shouldBe None
    }
  }
}
