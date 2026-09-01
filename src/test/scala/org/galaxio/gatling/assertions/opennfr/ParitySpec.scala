package org.galaxio.gatling.assertions.opennfr

import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.assertion.AssertionPathParts
import io.gatling.core.config.GatlingConfiguration
import org.galaxio.gatling.assertions.AssertionsBuilder
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The strongest check in the feature (FR-015, SC-001, User Story 3).
  *
  * No OpenNFR renderer exists anywhere, so there is no reference implementation to compare against. The deprecated builder is:
  * it is a known-good producer of the exact `Assertion` values Gatling evaluates. The same requirements written both ways must
  * build the SAME assertions — compared as sets, so a wrong scope, a wrong condition, a truncated target or an extra assertion
  * all fail.
  *
  * The enumeration is `specs/013-opennfr-assertions/contracts/parity.md`.
  */
class ParitySpec extends AnyWordSpec with Matchers with EitherValues {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private val deprecated = "src/test/resources/nfr.yml"
  private val translated = "src/test/resources/opennfr/nfr.yaml"

  private def grp(parts: String*): AssertionPathParts = AssertionPathParts(parts.toList)

  private lazy val oracle: Set[Assertion] = AssertionsBuilder.assertionsFrom(deprecated).toSet

  /** The eleventh: a group-only scope. Refused until upstream `v0.8.0` minted `loadtest.group.duration`, the metric name the
    * old refusal was waiting on — so the comparison below is now WHOLE, with nothing subtracted by name.
    *
    * Note what it renders to: `details(...).responseTime`, the very same call a request path makes. Gatling has one time metric
    * and picks the group's cumulated statistic by resolving the path, so this value is indistinguishable from a request's. The
    * distinction exists only in the document.
    */
  private lazy val groupOnly: Assertion = details(grp("myGroup")).responseTime.percentile(95).lt(1600)

  private lazy val rendered: List[Assertion] =
    OpenNfrAssertions.build(translated).fold(e => fail(s"the translation must render:\n${e.mkString("\n")}"), identity)

  "the OpenNFR translation of nfr.yml" should {

    "build assertions equal to the deprecated path's — the WHOLE set, nothing subtracted" in {
      oracle should have size 11
      rendered.toSet shouldBe oracle
    }

    "produce exactly eleven, with no duplicates" in {
      rendered should have size 11
      rendered.distinct should have size 11
    }

    "contain the eleventh, which upstream v0.8.0 made renderable" in {
      oracle should contain(groupOnly)
      rendered should contain(groupOnly)
    }
  }

  "the group-only scope" should {

    /** The pairing binds in BOTH directions, which is the half most easily got wrong: v0.8.0 did not merely permit a new row,
      * it also made the request metric inadmissible under that row.
      */
    "refuse the request-duration metric, which has no meaning against a group" in {
      val r = Requirement("r", Map("loadtest.group.name" -> io.circe.Json.arr(io.circe.Json.fromString("myGroup"))), Nil, None)
      val p = Predicate(None, Some("loadtest.request.duration"), "p95", "lt", BigDecimal(1600), "ms", None, None)
      Reach.render(r, p).left.value should include("resolves to the group")
    }

    "refuse a predicate carrying no metric at all" in {
      val r = Requirement("r", Map("loadtest.group.name" -> io.circe.Json.arr(io.circe.Json.fromString("myGroup"))), Nil, None)
      val p = Predicate(None, None, "count", "lt", BigDecimal(10), "{request}", None, None)
      Reach.render(r, p).left.value should include("loadtest.group.duration")
    }

    "refuse the group metric anywhere else — the reciprocal direction" in {
      val r = Requirement("r", Map("loadtest.request.name" -> io.circe.Json.fromString("GET /x")), Nil, None)
      val p = Predicate(None, Some("loadtest.group.duration"), "p95", "lt", BigDecimal(1600), "ms", None, None)
      Reach.render(r, p).left.value should include("only under a selector naming a group hierarchy")
    }
  }

  "the retired metric name" should {

    "be refused, and the refusal must name the replacement to write instead" in {
      val r      = Requirement("r", Map.empty, Nil, None)
      val p      = Predicate(None, Some("http.client.request.duration"), "p95", "lt", BigDecimal(500), "ms", None, None)
      val reason = Reach.render(r, p).left.value
      reason should include("retired in OpenNFR v0.8.0")
      reason should include("loadtest.request.duration")
      reason should include("loadtest.group.duration")
    }
  }

  "the workaround the migration table warns against" should {

    /** `{loadtest.request.name: myGroup}` + the request metric renders the IDENTICAL Assertion as the group spelling, because a
      * one-part path is one-part whatever produced it and Gatling has only one time metric. So the prohibition on writing it
      * survives ONLY as a documentation rule — the renderer cannot tell the two apart and must not pretend to.
      */
    "render the identical value as the legitimate group spelling" in {
      val r = Requirement("r", Map("loadtest.request.name" -> io.circe.Json.fromString("myGroup")), Nil, None)
      val p = Predicate(None, Some("loadtest.request.duration"), "p95", "lt", BigDecimal(1600), "ms", None, None)
      Reach.render(r, p).value shouldBe groupOnly
    }
  }

  "the deprecated path" should {
    "still build all eleven, so the two paths coexist" in {
      AssertionsBuilder.assertionsFrom(deprecated).toSet shouldBe oracle
      oracle should contain(groupOnly)
    }
  }
}
