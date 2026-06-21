package org.galaxio.gatling.feeders

import io.gatling.core.config.GatlingConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for [[SeparatedValuesFeeder]] (test-model layer 1). Covers the String, `Seq[String]` and `Seq[Map]` overloads
  * plus the csv/ssv/tsv helpers with exact expected records, trimming behaviour, prefix handling, and the negative case (an
  * empty sequence source fails fast).
  */
class SeparatedValuesFeederSpec extends AnyWordSpec with Matchers {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  "SeparatedValuesFeeder from a String" should {
    "split on the separator into one record per value" in {
      SeparatedValuesFeeder("v", "a;b;c", ';') shouldBe
        IndexedSeq(Map("v" -> "a"), Map("v" -> "b"), Map("v" -> "c"))
    }

    "trim surrounding whitespace from each value" in {
      SeparatedValuesFeeder("v", "a ; b", ';') shouldBe IndexedSeq(Map("v" -> "a"), Map("v" -> "b"))
    }

    "split on comma, semicolon, and tab separators (the csv/ssv/tsv delimiters)" in {
      SeparatedValuesFeeder("v", "1,2,3", ',') shouldBe IndexedSeq(Map("v" -> "1"), Map("v" -> "2"), Map("v" -> "3"))
      SeparatedValuesFeeder("v", "1;2", ';') shouldBe IndexedSeq(Map("v" -> "1"), Map("v" -> "2"))
      SeparatedValuesFeeder("v", "x\ty", '\t') shouldBe IndexedSeq(Map("v" -> "x"), Map("v" -> "y"))
    }
  }

  "SeparatedValuesFeeder from a Seq[String]" should {
    "split each element and flatten into records" in {
      SeparatedValuesFeeder("v", Seq("1,2", "3"), ',') shouldBe
        IndexedSeq(Map("v" -> "1"), Map("v" -> "2"), Map("v" -> "3"))
    }

    "fail fast on an empty source sequence (negative)" in {
      an[IllegalArgumentException] should be thrownBy SeparatedValuesFeeder("v", Seq.empty[String], ',')
    }
  }

  "SeparatedValuesFeeder from a Seq[Map]" should {
    "split each map value, prefixing the key when a prefix is given" in {
      SeparatedValuesFeeder(Some("p"), Seq(Map("H" -> "h1,h2")), ',') shouldBe
        IndexedSeq(Map("p_H" -> "h1"), Map("p_H" -> "h2"))
    }

    "keep the original key when no prefix is given" in {
      SeparatedValuesFeeder(None, Seq(Map("H" -> "h1,h2")), ',') shouldBe
        IndexedSeq(Map("H" -> "h1"), Map("H" -> "h2"))
    }

    "trim surrounding whitespace from each value (parity with the String/Seq overloads)" in {
      SeparatedValuesFeeder(None, Seq(Map("H" -> "h1 , h2")), ',') shouldBe
        IndexedSeq(Map("H" -> "h1"), Map("H" -> "h2"))
    }

    "fail fast on an empty source (negative)" in {
      an[IllegalArgumentException] should be thrownBy SeparatedValuesFeeder(None, Seq.empty[Map[String, Any]], ',')
    }
  }
}
