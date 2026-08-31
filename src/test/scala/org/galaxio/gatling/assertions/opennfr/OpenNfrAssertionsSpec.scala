package org.galaxio.gatling.assertions.opennfr

import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.config.GatlingConfiguration
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** The entry point: reading a document, and the contract a refusal keeps (FR-001, FR-003, FR-007).
  *
  * `specs/013-opennfr-assertions/contracts/public-api.md` states that a refusal is total and loud — a document with one
  * unrenderable predicate produces no assertions at all — and that it carries every reason rather than the first.
  */
class OpenNfrAssertionsSpec extends AnyWordSpec with Matchers with EitherValues {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private def write(yaml: String): String = {
    val f = Files.createTempFile("opennfr", ".yaml")
    f.toFile.deleteOnExit()
    Files.write(f, yaml.getBytes("UTF-8")): Path
    f.toAbsolutePath.toString
  }

  private def build(yaml: String): Either[List[String], List[Assertion]] = OpenNfrAssertions.build(write(yaml))

  private def document(body: String): String =
    s"""apiVersion: opennfr.io/v1
       |kind: RequirementSet
       |metadata: {name: probe}
       |spec:
       |  requirements:
       |$body""".stripMargin

  private val ok = "{metric: http.client.request.duration, aggregation: p95, op: lte, threshold: 500, unit: ms}"

  "OpenNfrAssertions" should {

    "return the assertions a valid document denotes" in {
      build(document(s"""    - name: r
                        |      selector: {}
                        |      criteria:
                        |        - $ok
                        |""".stripMargin)).value shouldBe List(global.responseTime.percentile(95).lte(500))
    }

    "render every criterion and every guard, because Gatling has one outcome" in {
      val built = build(document("""    - name: r
                                   |      selector: {}
                                   |      guards:
                                   |        - {aggregation: rate, op: gte, threshold: 200, unit: "{request}/s"}
                                   |      criteria:
                                   |        - {metric: http.client.request.duration, aggregation: p95, op: lte, threshold: 500, unit: ms}
                                   |        - {metric: http.client.request.duration, aggregation: max, op: lte, threshold: 900, unit: ms}
                                   |""".stripMargin)).value
      built should have size 3
      built should contain(global.requestsPerSec.gte(200.0))
    }

    "produce two assertions for two identical predicates, never one" in {
      build(document(s"""    - name: r
                        |      selector: {}
                        |      criteria:
                        |        - $ok
                        |        - $ok
                        |""".stripMargin)).value should have size 2
    }
  }

  "a refusal" should {

    "carry every reason, not the first" in {
      val reasons = build(document("""    - name: one
                                     |      selector: {}
                                     |      criteria:
                                     |        - {metric: http.client.request.duration, aggregation: p95, op: neq, threshold: 500, unit: ms}
                                     |        - {metric: http.client.request.duration, aggregation: sum, op: lte, threshold: 500, unit: ms}
                                     |    - name: two
                                     |      selector: {http.route: /api}
                                     |      criteria:
                                     |        - {metric: http.client.request.duration, aggregation: p95, op: lte, threshold: 500, unit: ms}
                                     |""".stripMargin)).left.value
      reasons should have size 3
    }

    "name the requirement and the predicate identity in each reason" in {
      val reasons = build(document("""    - name: checkout-latency
                                     |      selector: {}
                                     |      criteria:
                                     |        - {name: ninety-ninth, metric: http.client.request.duration, aggregation: p99, op: neq, threshold: 500, unit: ms}
                                     |""".stripMargin)).left.value
      reasons.mkString should (include("checkout-latency") and include("ninety-ninth"))
    }

    "fall back to the aggregation as the predicate's identity when it has no name" in {
      build(document("""    - name: r
                       |      selector: {}
                       |      criteria:
                       |        - {metric: http.client.request.duration, aggregation: p99, op: neq, threshold: 500, unit: ms}
                       |""".stripMargin)).left.value.mkString should include("p99")
    }

    "be total: one unrenderable predicate among nine good ones yields zero assertions, not nine" in {
      val nineGood = (1 to 9)
        .map(i => s"        - {metric: http.client.request.duration, aggregation: p9$i, op: lte, threshold: 500, unit: ms}")
        .mkString("\n")
      val result   = build(document(s"""    - name: r
                                       |      selector: {}
                                       |      criteria:
                                       |$nineGood
                                       |        - {metric: http.client.request.duration, aggregation: p95, op: neq, threshold: 500, unit: ms}
                                       |""".stripMargin))
      result.isLeft shouldBe true
      result.left.value should have size 1
    }

    "accumulate every structural failure, not only the first, and name where each happened" in {
      val reasons = build(document("""    - name: r
                                     |      selector: {}
                                     |      criteria:
                                     |        - {metric: http.client.request.duration, aggregation: p95, op: lte, threshold: 500}
                                     |    - name: s
                                     |      selector: {}
                                     |      criteria:
                                     |        - {metric: http.client.request.duration, aggregation: p95, op: lte, unit: ms}
                                     |""".stripMargin)).left.value

      reasons should have size 2
      reasons.mkString should (include("unit") and include("threshold"))
      // The path is what makes the message actionable: the reason alone does not say which predicate carried it.
      reasons.mkString should (include("requirements[0]") and include("requirements[1]"))
    }

    "render a guard under a quantified selector as forAll, which upstream opennfr#69 makes vacuous on an empty run" in {
      val built = build(document("""    - name: every-endpoint
                                   |      selector: {loadtest.request.name: "*"}
                                   |      guards:
                                   |        - {aggregation: rate, op: gte, threshold: 200, unit: "{request}/s"}
                                   |      criteria:
                                   |        - {metric: http.client.request.duration, aggregation: p99, op: lt, threshold: 500, unit: ms}
                                   |""".stripMargin)).value

      // Pinned, not endorsed: the guard is quantified with the criteria, so it cannot be the thing that catches an empty run.
      // docs/opennfr.md tells a reader to put that guard on a `{}` requirement instead.
      built should contain(forAll.requestsPerSec.gte(200.0))
      built should not contain global.requestsPerSec.gte(200.0)
    }

    "name the path when the document cannot be read" in {
      OpenNfrAssertions
        .build("src/test/resources/opennfr/does-not-exist.yaml")
        .left
        .value
        .mkString should include("does-not-exist.yaml")
    }

    "throw one exception carrying every reason from the public entry point" in {
      val path = write(document("""    - name: r
                                  |      selector: {}
                                  |      criteria:
                                  |        - {metric: http.client.request.duration, aggregation: p95, op: neq, threshold: 500, unit: ms}
                                  |""".stripMargin))
      val e    = intercept[OpenNfrException](OpenNfrAssertions.assertionsFrom(path))
      e.reasons should have size 1
      e.getMessage should (include(path) and include("neq"))
    }
  }
}
