package org.galaxio.gatling.assertions.opennfr

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source
import scala.util.Using

/** Guards the published documentation against the code (FR-010, FR-017, SC-006, test-model layer 5).
  *
  * A migration table that silently misses a key is worse than none: a reader trusts it. These read the deprecated builder's
  * source and the shipped docs, so a key added later without a row fails the build.
  */
class MigrationTableSpec extends AnyWordSpec with Matchers {

  private def read(path: String): String =
    Using(Source.fromFile(path, "UTF-8"))(_.mkString).getOrElse(fail(s"cannot read $path"))

  /** Collapses a scaladoc comment to one line — leading asterisks dropped, whitespace squeezed — so a guard over prose survives
    * the formatter reflowing that comment across lines.
    */
  private def flat(s: String): String = s.replaceAll("(?m)^\\s*\\*", " ").replaceAll("\\s+", " ")

  private val builder  = read("src/main/scala/org/galaxio/gatling/assertions/AssertionsBuilder.scala")
  private val doc      = read("docs/opennfr.md")
  private val docLower = doc.toLowerCase

  /** Every metric key the deprecated builder matches on, taken from its source rather than restated here.
    *
    * Sliced to the `record.key match` block first: scraping the whole file would collect any other string literal it grows — a
    * scope key, a unit, a status — and then demand a documentation row for something that is not a metric key.
    */
  private val recognisedKeys: List[String] = {
    val block = builder.linesIterator
      .dropWhile(!_.contains("record.key match"))
      .takeWhile(!_.contains("case other"))
      .mkString("\n")
    """case "([^"]+)"""".r.findAllMatchIn(block).map(_.group(1)).toList
  }

  /** A row of the migration table, not merely the characters somewhere in the page: a short key like `all` would otherwise
    * match any prose that happens to contain it.
    */
  private val tableRows: List[String] = doc.linesIterator.filter(_.startsWith("|")).toList

  /** The section covering the group-only scope, so a guard over it cannot be satisfied by prose elsewhere on the page. */
  private val groupOnlySection: String =
    doc.linesIterator
      .dropWhile(!_.startsWith("### A scope naming only a group"))
      .drop(1)
      .takeWhile(!_.startsWith("### "))
      .mkString("\n")

  "the migration table" should {

    "name every metric key the deprecated builder recognises" in {
      recognisedKeys should have size 6
      val missing = recognisedKeys.filterNot(key => tableRows.exists(_.contains(key)))
      withClue(s"keys with no row in docs/opennfr.md: ${missing.mkString(", ")}\n") {
        missing shouldBe empty
      }
    }

    "carry every scope form, including the one that has no equivalent" in {
      doc should include("loadtest.group.name")
      doc should include("selector: {}")
      doc should include("a group with no request")
    }

    "say WHAT a group assertion measures, now that the case renders (#328)" in {
      // Inverted at upstream v0.8.0: the case is no longer refused, so the guard that asserted it had
      // no equivalent would now pass while the docs and the code disagreed. What a reader needs
      // instead is the quantity — Gatling answers with the SUM of the enclosed operations, not the
      // elapsed traversal, and nothing in Gatling's own output says so.
      docLower should include("cumulated")
      docLower should include("sum of the durations of the operations the group encloses")
      docLower should include("opennfr#89")
      groupOnlySection should include("loadtest.group.duration")
    }

    "warn that the charting flag makes charts and assertions disagree" in {
      // The one setting that suggests an assertion could read wall clock. It reaches the report only,
      // so a reader who does not know this will try to reconcile two numbers that cannot agree.
      doc should include("gatling.charting.useGroupDurationMetric")
      groupOnlySection.toLowerCase should include("never reaches the")
    }

    "state that the old metric name is retired and name its replacement" in {
      doc should include("http.client.request.duration")
      docLower should include("retired")
      doc should include("loadtest.request.duration")
    }

    "warn against the workaround that renders but lies" in {
      doc should include("loadtest.request.name: myGroup")
      docLower should include("do not write")
    }

    "state the experimental status and the upstream release it tracks" in {
      docLower should include("experimental")
      doc should include(RequirementSet.TracksRelease)
      doc should include("binary-compatibility guarantee")
    }

    "warn that a guard under a quantified selector is quantified too" in {
      doc should include("guards")
      docLower should include("forall")
      docLower should include("opennfr#69")
    }
  }

  "the tracked upstream release" should {

    "be stated as the same version everywhere it is stated at all" in {
      val statedIn = List(
        "src/main/scala/org/galaxio/gatling/assertions/opennfr/Model.scala",
        "src/main/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrAssertions.scala",
        "src/main/java/org/galaxio/gatling/javaapi/OpenNfrAssertions.java",
        "docs/opennfr.md",
        "specs/013-opennfr-assertions/contracts/reach.md",
      )
      val stale    = statedIn.filterNot(path => read(path).contains(RequirementSet.TracksRelease))
      withClue(s"files not naming ${RequirementSet.TracksRelease}: ${stale.mkString(", ")}\n") {
        stale shouldBe empty
      }
    }
  }

  "the feature's sources" should {

    "cite the reach tables' only home, and never the redirect that carries no rule" in {
      val sources = List(
        "src/main/scala/org/galaxio/gatling/assertions/opennfr/Reach.scala",
        "src/main/scala/org/galaxio/gatling/assertions/opennfr/Model.scala",
        "src/main/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrAssertions.scala",
        "src/main/scala/org/galaxio/gatling/assertions/opennfr/JavaRender.scala",
      ).map(read)

      sources.foreach(_ should not include "gatling-reach.md")
      flat(read("src/main/scala/org/galaxio/gatling/assertions/opennfr/Reach.scala")) should
        include("What any tool can actually run")
    }
  }
}
