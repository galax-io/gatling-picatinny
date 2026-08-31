package org.galaxio.gatling.assertions.opennfr

import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.assertion.AssertionPathParts
import io.gatling.core.config.GatlingConfiguration
import org.galaxio.gatling.assertions.AssertionsBuilder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The standing guard for the deprecated path (FR-015).
  *
  * The OpenNFR renderer is built beside `AssertionsBuilder`, never on top of it. This suite pins what that builder produces
  * today, as an exact set, so that any change to it fails here rather than in review. It is the first thing written and must
  * stay green for the whole feature.
  *
  * The enumeration is `specs/013-opennfr-assertions/contracts/parity.md` § 3.
  */
class OracleBaselineSpec extends AnyWordSpec with Matchers {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private def grp(parts: String*): AssertionPathParts = AssertionPathParts(parts.toList)

  private lazy val oracle: Set[Assertion] =
    AssertionsBuilder.assertionsFrom("src/test/resources/nfr.yml").toSet

  /** The one assertion OpenNFR refuses — a group-only scope. Named here so the parity suite can subtract it by reference rather
    * than by shrinking an expected set.
    */
  private[opennfr] lazy val groupOnly: Assertion =
    details(grp("myGroup")).responseTime.percentile(95).lt(1600)

  private lazy val expected: Set[Assertion] = Set(
    global.responseTime.percentile(99).lt(1500),
    details(grp("myGroup", "GET /test/id")).responseTime.percentile(99).lt(1500),
    details(grp("GET /test/email")).responseTime.percentile(99).lt(400),
    global.responseTime.percentile(95).lt(1200),
    details(grp("myGroup", "GET /test/id")).responseTime.percentile(95).lt(1200),
    groupOnly,
    details(grp("GET /test/email")).responseTime.percentile(95).lt(320),
    global.failedRequests.percent.lt(5.0),
    details(grp("GET /test/uuid")).failedRequests.percent.lt(1.0),
    global.responseTime.max.lt(2000),
    details(grp("GET /test/uuid")).responseTime.max.lt(1000),
  )

  "the deprecated NFR-YAML builder, which this feature must not touch" should {

    "build exactly the eleven assertions contracts/parity.md enumerates" in {
      oracle shouldBe expected
    }

    "include the group-only scope OpenNFR refuses" in {
      oracle should contain(groupOnly)
    }

    "still ignore the two keys it has always ignored, so neither is a parity obligation" in {
      // nfr.yml carries APDEX and a throughput key; the builder recognises neither, so eleven is the whole set.
      oracle should have size 11
    }
  }
}
