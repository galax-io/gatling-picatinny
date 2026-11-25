package org.galaxio.gatling.assertions.opennfr

import io.circe.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{EitherValues, OptionValues}

import scala.io.Source
import scala.util.Using

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

  private val criteria    =
    "      criteria:\n        - {metric: loadtest.request.duration, aggregation: p95, op: lte, threshold: 500, unit: ms}\n"
  private val requirement = "    - name: r\n      selector: {}\n" + criteria

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

    // BCKLG-233: each valid fixture decodes into the same model, whole, as it did on circe 0.15.0-M1 and circe-yaml 1.15.0.
    "FR-008: decode each valid OpenNFR fixture into the same model, whole" in {
      def decoded(name: String): RequirementSet =
        RequirementSet.decode(Using.resource(Source.fromFile(s"src/test/resources/opennfr/$name", "UTF-8"))(_.mkString)).value

      val duration                                                = Some("loadtest.request.duration")
      def latency(aggregation: String, threshold: Int): Predicate =
        Predicate(None, duration, aggregation, "lt", BigDecimal(threshold), "ms", None, None)
      def errors(threshold: Int): Predicate                       =
        Predicate(None, None, "rate", "lt", BigDecimal(threshold), "%", Some(Map("error.type" -> Json.fromString("*"))), None)
      val myGroup                                                 = "loadtest.group.name"   -> Json.arr(Json.fromString("myGroup"))
      def request(name: String): (String, Json)                   = "loadtest.request.name" -> Json.fromString(name)
      val groupItself                                             = Requirement(
        "mygroup-itself",
        Map(myGroup),
        List(Predicate(None, Some("loadtest.group.duration"), "p95", "lt", BigDecimal(1600), "ms", None, None)),
        None,
      )

      decoded("nfr.yaml") shouldBe RequirementSet(
        "opennfr.io/v1",
        "RequirementSet",
        List(
          Requirement(
            "whole-run",
            Map.empty,
            List(latency("p99", 1500), latency("p95", 1200), latency("max", 2000), errors(5)),
            None,
          ),
          Requirement(
            "get-test-id",
            Map(myGroup, request("GET /test/id")),
            List(latency("p99", 1500), latency("p95", 1200)),
            None,
          ),
          Requirement("get-test-email", Map(request("GET /test/email")), List(latency("p99", 400), latency("p95", 320)), None),
          Requirement("get-test-uuid", Map(request("GET /test/uuid")), List(latency("max", 1000), errors(1)), None),
          groupItself,
        ),
      )
      decoded("group-only.yaml") shouldBe RequirementSet("opennfr.io/v1", "RequirementSet", List(groupItself))
      decoded("retired-metric.yaml") shouldBe RequirementSet(
        "opennfr.io/v1",
        "RequirementSet",
        List(
          Requirement(
            "whole-run",
            Map.empty,
            List(Predicate(None, Some("http.client.request.duration"), "p95", "lt", BigDecimal(500), "ms", None, None)),
            None,
          ),
        ),
      )
    }

    // BCKLG-233: the reasons are recorded whole, circe's part included, as they were on circe 0.15.0-M1 and circe-yaml 1.15.0,
    // so that a change of circe cannot change them unnoticed.
    "FR-008: refuse a document missing a required field with exactly the reasons it gave before" in {
      val doc = minimal.replace("      selector: {}\n", "")
      RequirementSet.decode(doc).left.value shouldBe List(
        "not an OpenNFR document: Attempt to decode value on failed cursor: " +
          "DownField(selector),DownArray,DownField(requirements),DownField(spec) at .spec.requirements[0].selector",
      )
    }

    "FR-008: refuse a field of the wrong type with exactly the reasons it gave before" in {
      def reasons(doc: String) = RequirementSet.decode(doc).left.value
      reasons(minimal.replace("apiVersion: opennfr.io/v1", "apiVersion: 123")) shouldBe List(
        "not an OpenNFR document: String: DownField(apiVersion) at .apiVersion",
      )
      reasons(minimal.replace(criteria, "      criteria: {}\n")) shouldBe List(
        "not an OpenNFR document: C[A]: " +
          "DownField(criteria),DownArray,DownField(requirements),DownField(spec) at .spec.requirements[0].criteria",
      )
      reasons(minimal.replace("selector: {}", "selector: []")) shouldBe List(
        "not an OpenNFR document: [K, V]Map[K, V]: " +
          "DownField(selector),DownArray,DownField(requirements),DownField(spec) at .spec.requirements[0].selector",
      )
      reasons(minimal.replace("threshold: 500", "threshold: abc")) shouldBe List(
        "not an OpenNFR document: BigDecimal: DownField(threshold),DownArray,DownField(criteria),DownArray," +
          "DownField(requirements),DownField(spec) at .spec.requirements[0].criteria[0].threshold",
      )
    }

    "FR-008: refuse something that is not an object where one is due with exactly the reasons it gave before" in {
      val failed  = "not an OpenNFR document: Attempt to decode value on failed cursor: "
      RequirementSet.decode("apiVersion: opennfr.io/v1\nkind: RequirementSet\nspec: 123\n").left.value shouldBe List(
        failed + "DownField(requirements),DownField(spec) at .spec.requirements",
      )
      RequirementSet.decode("- a\n").left.value shouldBe List(
        failed + "DownField(apiVersion) at .apiVersion",
        failed + "DownField(kind) at .kind",
        failed + "DownField(spec) at .spec",
      )
      val element = "DownArray,DownField(requirements),DownField(spec) at .spec.requirements[0]"
      RequirementSet.decode(minimal.replace(requirement, "    - bad\n")).left.value shouldBe List(
        failed + s"DownField(name),${element.replace(" at .spec.requirements[0]", " at .spec.requirements[0].name")}",
        failed + s"DownField(selector),${element.replace(" at .spec.requirements[0]", " at .spec.requirements[0].selector")}",
        failed + s"DownField(criteria),${element.replace(" at .spec.requirements[0]", " at .spec.requirements[0].criteria")}",
        "not an OpenNFR document: [A]Option[A]: " +
          s"DownField(guards),${element.replace(" at .spec.requirements[0]", " at .spec.requirements[0].guards")}",
      )
    }

    "FR-008: refuse something that is not YAML with exactly the reasons it gave before" in {
      RequirementSet.decode("\t: : not: [yaml").left.value shouldBe List(
        "not an OpenNFR document: while scanning for the next token\n" +
          "found character '\\t(TAB)' that cannot start any token. (Do not use \\t(TAB) for indentation)\n" +
          " in 'reader', line 1, column 1:\n" +
          "    \t: : not: [yaml\n" +
          "    ^\n",
      )
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
