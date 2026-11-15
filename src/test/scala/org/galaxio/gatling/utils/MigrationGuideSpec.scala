package org.galaxio.gatling.utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source
import scala.util.Using

/** Guards the v1.22.0 migration notes against the deprecation pins on `RandomDataGenerators` (BCKLG-498; test-model layer 5).
  *
  * A replacement table that disagrees with the compiler's warning is worse than none: a reader trusts it. The analogues are
  * therefore read from the pins themselves, by reflection, not restated here.
  */
class MigrationGuideSpec extends AnyWordSpec with Matchers {
  import RandomDataGeneratorsDeprecationSpec._

  private val doc: List[String] =
    Using(Source.fromFile("docs/migration.md", "UTF-8"))(_.getLines().toList).getOrElse(fail("cannot read docs/migration.md"))

  /** The lines after the first heading `starts` matches, up to the next heading `ends` matches. */
  private def section(lines: List[String], starts: String => Boolean, ends: String => Boolean): List[String] =
    lines.dropWhile(!starts(_)).drop(1).takeWhile(!ends(_))

  private def flat(lines: List[String]): String = lines.mkString(" ").replaceAll("\\s+", " ")

  private val v1220: List[String] = section(doc, _.startsWith("## v1.22.0"), _.startsWith("## "))

  private val upperBound: List[String] =
    section(v1220, line => line.startsWith("### ") && line.contains("randomValue"), _.startsWith("### "))

  /** The code of the first fenced block after the line that opens with `After`. */
  private val afterExample: String = upperBound
    .dropWhile(!_.startsWith("After"))
    .dropWhile(!_.startsWith("```"))
    .drop(1)
    .takeWhile(!_.startsWith("```"))
    .mkString("\n")

  private val randomDateFeeder: List[String] =
    section(doc, line => line.startsWith("### ") && line.contains("RandomDateFeeder"), _.matches("#{2,3} .*"))

  /** Each table row of the v1.22.0 section with its first cell, the deprecated method it is about. */
  private val rows: List[(String, String)] =
    v1220.filter(_.startsWith("|")).map(row => row.split('|').lift(1).getOrElse("").trim -> row)

  /** The analogues the compiler names for `method`, read from its pins. */
  private def pinnedAnalogues(method: String): List[String] =
    deprecations(RandomDataGeneratorsType, method).flatMap(_._2).map(_.message).flatMap(fakerNames).distinct

  "the v1.22.0 section of docs/migration.md" should {

    "FR-009, FR-010: give a row for each deprecated overload, naming every analogue its pin names" in {
      val overloads = List("randomString" -> 1, "randomUUID" -> 1, "randomValue" -> 3, "randomDate" -> 2)
      overloads.foreach { case (method, count) =>
        val analogues = pinnedAnalogues(method)
        val own       = rows.collect { case (first, row) if first.contains(method) => row }
        withClue(s"$method carries no pin naming a Faker analogue: ")(analogues should not be empty)
        withClue(s"rows for $method in the v1.22.0 table: ")(own.size should be >= count)
        own.foreach { row =>
          withClue(s"row `$row` misses analogues of $method: ") {
            analogues.filterNot(row.contains) shouldBe empty
          }
        }
      }
    }

    "FR-009: show Faker.number.int(1, 10), not randomValue, as the After example of the upper-bound note" in {
      upperBound should not be empty
      afterExample should include("Faker.number.int(1, 10)")
      afterExample should not include "randomValue"
    }

    "FR-009: call randomValue deprecated, since faker-api, where the upper-bound note names it" in {
      val note = flat(upperBound)
      note should include("deprecated")
      note should include("faker-api")
    }

    "FR-009: no longer say the helpers still work today" in {
      v1220.filter(_.contains("still work today")) shouldBe empty
    }
  }

  "docs/migration.md" should {

    "FR-009: no longer call the RandomDataGenerators helpers slated for deprecation" in {
      doc.filter(_.contains("slated for deprecation")) shouldBe empty
    }

    "FR-009: call randomValue deprecated where the RandomDateFeeder note refers to it" in {
      randomDateFeeder should not be empty
      flat(randomDateFeeder) should include("deprecated `randomValue`")
    }
  }
}
