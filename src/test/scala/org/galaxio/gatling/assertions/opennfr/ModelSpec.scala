package org.galaxio.gatling.assertions.opennfr

import io.circe.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{EitherValues, OptionValues}

/** Decoding and envelope checks (FR-002, FR-008), the layer everything else stands on.
  *
  * The shape decoded here is `specs/013-opennfr-assertions/data-model.md`. A selector is kept as raw JSON per attribute because
  * its values are heterogeneous — a string, a number, a boolean, or an ordered list of strings for the hierarchy.
  */
class ModelSpec extends AnyWordSpec with Matchers with EitherValues with OptionValues {

  private val minimal =
    """apiVersion: opennfr.io/v1
      |kind: RequirementSet
      |metadata: {name: minimal}
      |spec:
      |  requirements:
      |    - name: r
      |      selector: {}
      |      criteria:
      |        - {metric: loadtest.request.duration, aggregation: p95, op: lte, threshold: 500, unit: ms}
      |""".stripMargin

  "RequirementSet.decode" should {

    "decode the smallest document the format accepts" in {
      val d = RequirementSet.decode(minimal).value
      d.apiVersion shouldBe "opennfr.io/v1"
      d.kind shouldBe "RequirementSet"
      d.requirements should have size 1

      val r = d.requirements.head
      r.name shouldBe "r"
      r.selector shouldBe empty
      r.criteria should have size 1

      val p = r.criteria.head
      p.metric shouldBe Some("loadtest.request.duration")
      p.aggregation shouldBe "p95"
      p.op shouldBe "lte"
      p.unit shouldBe "ms"
      p.threshold shouldBe BigDecimal(500)
    }

    "keep a heterogeneous selector's values distinct — a string, a number, a boolean and a list" in {
      val doc = minimal.replace(
        "selector: {}",
        """selector: {loadtest.group.name: [a, b], loadtest.request.name: "GET /x", http.response.status_code: 500, flag: true}""",
      )
      val s   = RequirementSet.decode(doc).value.requirements.head.selector

      s("loadtest.request.name") shouldBe Json.fromString("GET /x")
      s("http.response.status_code").asNumber.flatMap(_.toInt) shouldBe Some(500)
      s("flag").asBoolean shouldBe Some(true)
      s("loadtest.group.name").asArray.map(_.toList.flatMap(_.asString)) shouldBe Some(List("a", "b"))
    }

    "carry guards beside criteria, and expose both as the predicates that render" in {
      val doc = minimal + """      guards:
                            |        - {aggregation: rate, op: gte, threshold: 200, unit: "{request}/s"}
                            |""".stripMargin
      val r   = RequirementSet.decode(doc).value.requirements.head
      r.criteria should have size 1
      r.guards.value should have size 1
      r.predicates should have size 2
    }

    "keep a threshold as an exact decimal, which a binary float would not" in {
      // 1.001 as a Double is 1.000999999999999889865875957184471189975738525390625.
      val doc = minimal.replace("threshold: 500, unit: ms", "threshold: 1.001, unit: s")
      val t   = RequirementSet.decode(doc).value.requirements.head.criteria.head.threshold
      t shouldBe BigDecimal("1.001")
      (t * 1000) shouldBe BigDecimal(1001)
      (t * 1000).isWhole shouldBe true
    }

    "refuse a document missing a required field, naming it" in {
      val doc = minimal.replace("      selector: {}\n", "")
      RequirementSet.decode(doc).left.value.mkString should include("selector")
    }

    "refuse something that is not YAML at all" in {
      RequirementSet.decode("\t: : not: [yaml").isLeft shouldBe true
    }
  }

  "the envelope check" should {

    "refuse an apiVersion it does not read, naming both values" in {
      val doc = minimal.replace("opennfr.io/v1", "opennfr.io/v2")
      val e   = RequirementSet.decode(doc).flatMap(RequirementSet.envelope).left.value
      e.mkString should (include("opennfr.io/v2") and include("opennfr.io/v1"))
    }

    "refuse a kind that is not RequirementSet" in {
      val doc = minimal.replace("kind: RequirementSet", "kind: Requirements")
      RequirementSet.decode(doc).flatMap(RequirementSet.envelope).left.value.mkString should include("Requirements")
    }

    "refuse a document that states nothing" in {
      val doc = minimal.replace(
        """  requirements:
          |    - name: r
          |      selector: {}
          |      criteria:
          |        - {metric: loadtest.request.duration, aggregation: p95, op: lte, threshold: 500, unit: ms}
          |""".stripMargin,
        "  requirements: []\n",
      )
      RequirementSet.decode(doc).flatMap(RequirementSet.envelope).left.value.mkString should include("states nothing")
    }

    "accept the exact supported envelope" in {
      RequirementSet.decode(minimal).flatMap(RequirementSet.envelope).isRight shouldBe true
    }
  }
}
