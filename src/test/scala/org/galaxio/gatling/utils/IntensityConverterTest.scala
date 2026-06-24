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

    // Issue #93: multi-digit decimals must NOT be truncated.
    "preserve arbitrary-precision decimals (regression for #93)" in {
      getIntensityFromString("0.25 rps") shouldBe 0.25
      getIntensityFromString("123.55 rph") shouldBe 123.55 / 3600.0
      getIntensityFromString("1.234567 rpm") shouldBe 1.234567 / 60.0
    }

    "tolerate extra inner whitespace between value and unit" in {
      getIntensityFromString("1.5   rps") shouldBe 1.5
    }

    "trim leading and trailing whitespace" in {
      getIntensityFromString(" 0.25 rps ") shouldBe 0.25
    }

    "accept units case-insensitively" in {
      getIntensityFromString("100 RPS") shouldBe 100.0
      getIntensityFromString("50 Rps") shouldBe 50.0
    }

    "reject malformed intensities instead of silently truncating" in {
      Seq(".5", "1.", "1.2.3", "abc", "", "-5").foreach { bad =>
        withClue(s"input '$bad': ") {
          an[IllegalArgumentException] shouldBe thrownBy(getIntensityFromString(bad))
        }
      }
    }

    "reject null input" in {
      an[IllegalArgumentException] shouldBe thrownBy(getIntensityFromString(null))
    }

    "reject out-of-range values that overflow to a non-finite Double" in {
      an[IllegalArgumentException] shouldBe thrownBy(getIntensityFromString(("9" * 400) + " rps"))
    }

    // SC-001: 100% of valid decimal inputs round-trip exactly (no truncation).
    "round-trip every generated decimal exactly" in {
      forAll(Gen.chooseNum(0L, 1_000_000L), Gen.chooseNum(0, 999_999)) { (whole, frac) =>
        val s = s"$whole.$frac"
        getIntensityFromString(s"$s rps") shouldBe s.toDouble
      }
    }
  }
}
