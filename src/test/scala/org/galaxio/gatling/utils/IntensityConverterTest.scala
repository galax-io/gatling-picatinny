package org.galaxio.gatling.utils

import org.galaxio.gatling.utils.IntensityConverter._
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class IntensityConverterTest extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  private val finiteDouble: Gen[Double] =
    Gen.chooseNum(-1_000_000.0, 1_000_000.0)

  "IntensityConverter syntax" should {
    "convert hourly rates to requests per second" in {
      forAll(finiteDouble) { value =>
        value.rph shouldBe value / 3600.0
      }
    }

    "convert minute rates to requests per second" in {
      forAll(finiteDouble) { value =>
        value.rpm shouldBe value / 60.0
      }
    }

    "leave requests per second unchanged" in {
      forAll(finiteDouble) { value =>
        value.rps shouldBe value
      }
    }
  }

  "getIntensityFromString" should {
    "parse explicit rps values" in {
      getIntensityFromString("3600.0 rps") shouldBe 3600.0
    }

    "parse plain numeric values as requests per second" in {
      getIntensityFromString("30") shouldBe 30.0
    }

    "reject unsupported units" in {
      an[IllegalArgumentException] shouldBe thrownBy {
        getIntensityFromString("3600.0 jpeg")
      }
    }
  }
}
