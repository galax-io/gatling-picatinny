package org.galaxio.gatling.utils

import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneId}

class RandomDataGeneratorsTest extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  private val nonEmptyAlphabet: Gen[String] =
    Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString.distinct).suchThat(_.nonEmpty)

  "RandomDataGenerators.randomString" should {
    "generate strings of the requested length using only the supplied alphabet" in {
      forAll(nonEmptyAlphabet, Gen.choose(1, 50)) { (alphabet, length) =>
        val generated = RandomDataGenerators.randomString(alphabet)(length)

        generated should have length length
        generated.foreach { char =>
          withClue(s"generated=$generated alphabet=$alphabet") {
            alphabet should include(char.toString)
          }
        }
      }
    }
  }

  "RandomDataGenerators.randomUUID" should {
    "generate canonical lowercase UUID strings" in {
      RandomDataGenerators.randomUUID should fullyMatch regex "[a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8}"
    }
  }

  "RandomDataGenerators.randomValue" should {
    "stay within the requested inclusive range" in {
      forAll(Gen.choose(0L, 999_999L), Gen.choose(1L, 1_000L)) { (min, width) =>
        val max       = min + width
        val generated = RandomDataGenerators.randomValue(min, max)

        generated should (be >= min and be <= max)
      }
    }
  }

  "RandomDataGenerators.randomDate" should {
    "render dates with the requested formatter" in {
      val dateFrom = LocalDateTime.of(2026, 5, 7, 12, 0)

      forAll(Gen.choose(1, 100), Gen.choose(1, 100)) { (positiveOffset, negativeOffset) =>
        RandomDataGenerators
          .randomDate(
            positiveOffset,
            negativeOffset,
            "yyyy-MM-dd'T'HH:mm",
            dateFrom,
            ChronoUnit.DAYS,
            ZoneId.of("UTC"),
          ) should fullyMatch regex raw"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}"
      }
    }
  }
}
