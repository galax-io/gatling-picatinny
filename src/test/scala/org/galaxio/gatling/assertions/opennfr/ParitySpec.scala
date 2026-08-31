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

  /** The eleventh: a group-only scope, which OpenNFR refuses by decision. Named, so the comparison subtracts it by reference —
    * never by shrinking the expected set, which would turn this suite into one that proves nothing.
    */
  private lazy val groupOnly: Assertion = details(grp("myGroup")).responseTime.percentile(95).lt(1600)

  private lazy val rendered: List[Assertion] =
    OpenNfrAssertions.build(translated).fold(e => fail(s"the translation must render:\n${e.mkString("\n")}"), identity)

  "the OpenNFR translation of nfr.yml" should {

    "build assertions equal to the deprecated path's, over the ten OpenNFR can state" in {
      oracle should have size 11
      val expected = oracle - groupOnly
      expected should have size 10

      rendered.toSet shouldBe expected
    }

    "produce exactly ten, with no duplicates" in {
      rendered should have size 10
      rendered.distinct should have size 10
    }

    "not contain the eleventh" in {
      oracle should contain(groupOnly)
      rendered should not contain groupOnly
    }
  }

  "the eleventh assertion" should {

    "be refused for the reason upstream decided, not merely be absent" in {
      val groupOnlyDoc =
        """apiVersion: opennfr.io/v1
          |kind: RequirementSet
          |metadata: {name: group-only}
          |spec:
          |  requirements:
          |    - name: mygroup-itself
          |      selector: {loadtest.group.name: [myGroup]}
          |      criteria:
          |        - {metric: http.client.request.duration, aggregation: p95, op: lt, threshold: 1600, unit: ms}
          |""".stripMargin
      val f            = java.nio.file.Files.createTempFile("opennfr", ".yaml")
      f.toFile.deleteOnExit()
      java.nio.file.Files.write(f, groupOnlyDoc.getBytes("UTF-8"))

      OpenNfrAssertions.build(f.toAbsolutePath.toString).left.value.mkString should
        include("no Gatling scope denotes the requests a path encloses")
    }

    "not be recoverable by calling the group a request — the workaround FR-007 forbids" in {
      // `{loadtest.request.name: myGroup}` renders the identical Gatling call, because a one-part path is one-part
      // whatever produced it. It is still a document that says *request* about a group, so the renderer must not be
      // the thing that makes it look legitimate: it renders, and only the migration table may say why not to write it.
      val r = Requirement("r", Map("loadtest.request.name" -> io.circe.Json.fromString("myGroup")), Nil, None)
      val p = Predicate(None, Some("http.client.request.duration"), "p95", "lt", BigDecimal(1600), "ms", None, None)
      Reach.render(r, p).value shouldBe groupOnly
    }
  }

  "the deprecated path" should {
    "still build all eleven, the excluded one included, so the two coexist" in {
      AssertionsBuilder.assertionsFrom(deprecated).toSet shouldBe oracle
      oracle should contain(groupOnly)
    }
  }
}
