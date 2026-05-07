package org.galaxio.gatling.feeders

import io.gatling.core.CoreDsl
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.feeder._
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class FeedersBaseSpec extends AnyWordSpec with Matchers with CoreDsl with ScalaCheckDrivenPropertyChecks {

  override implicit def configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private val feederName: Gen[String] =
    Gen.identifier.suchThat(_.nonEmpty)

  "feeder" should {
    "create an infinite Gatling feeder for a constant value" in {
      forAll(feederName, Gen.alphaNumStr) { (name, value) =>
        val generated = feeder(name)(value)

        generated.hasNext shouldBe true
        generated.take(10).toList should contain only Map(name -> value)
      }
    }
  }

  "Feeder zip syntax" should {
    "merge records from two feeders" in {
      forAll(feederName, feederName, Gen.alphaNumStr, Gen.alphaNumStr) { (leftName, rightName, leftValue, rightValue) =>
        whenever(leftName != rightName) {
          val left: Feeder[String]  = Iterator.continually(Map(leftName -> leftValue))
          val right: Feeder[String] = Iterator.continually(Map(rightName -> rightValue))

          (left ** right).next() shouldBe Map(leftName -> leftValue, rightName -> rightValue)
        }
      }
    }
  }

  "Feeder finite-length syntax" should {
    "materialize the requested number of records" in {
      forAll(feederName, Gen.choose(1, 100)) { (name, size) =>
        RandomDigitFeeder(name).toFiniteLength(size).readRecords should have size size
      }
    }
  }

  "Collection feeder syntax" should {
    "turn every collection item into a named feeder record" in {
      forAll(feederName, Gen.listOfN(25, Gen.alphaNumStr)) { (name, values) =>
        values.toFeeder(name).readRecords shouldBe values.map(value => Map(name -> value))
      }
    }
  }
}
