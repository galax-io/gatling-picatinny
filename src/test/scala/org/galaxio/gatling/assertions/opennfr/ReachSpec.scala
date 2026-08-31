package org.galaxio.gatling.assertions.opennfr

import io.circe.Json
import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.assertion.AssertionPathParts
import io.gatling.core.config.GatlingConfiguration
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** One predicate in, one assertion or one reason out (FR-005, FR-006, FR-008, FR-009).
  *
  * The rows under test are `specs/013-opennfr-assertions/contracts/reach.md`, which is upstream's published table transcribed
  * as data. These tests are the cross-product of it: every **can** row renders, every **cannot** row refuses with that row's
  * reason.
  */
class ReachSpec extends AnyWordSpec with Matchers with EitherValues {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private val Duration = "http.client.request.duration"
  private val Ko       = Map("error.type" -> Json.fromString("*"))

  private def s(v: String): Json                     = Json.fromString(v)
  private def list(vs: String*): Json                = Json.arr(vs.map(Json.fromString): _*)
  private def grp(parts: String*)                    = AssertionPathParts(parts.toList)
  private def req(sel: (String, Json)*): Requirement = Requirement("r", sel.toMap, Nil, None)

  private def pred(
      aggregation: String,
      op: String = "lte",
      threshold: BigDecimal = BigDecimal(500),
      unit: String = "ms",
      metric: Option[String] = Some(Duration),
      bad: Option[Map[String, Json]] = None,
      good: Option[Map[String, Json]] = None,
  ): Predicate = Predicate(None, metric, aggregation, op, threshold, unit, bad, good)

  private def render(r: Requirement, p: Predicate): Either[String, Assertion] = Reach.render(r, p)

  private val anywhere = req()

  "Reach, on the selection axis" should {

    "render {} as the global scope" in {
      render(req(), pred("p95")).value shouldBe global.responseTime.percentile(95).lte(500)
    }

    "render a bare request name as the root-anchored one-part path" in {
      render(req("loadtest.request.name" -> s("GET /x")), pred("p95")).value shouldBe
        details(grp("GET /x")).responseTime.percentile(95).lte(500)
    }

    "render a hierarchy of depth one with a name as the ordered path" in {
      render(req("loadtest.group.name" -> list("G"), "loadtest.request.name" -> s("X")), pred("p95")).value shouldBe
        details(grp("G", "X")).responseTime.percentile(95).lte(500)
    }

    "render a hierarchy of any depth, whole and never as a prefix" in {
      render(req("loadtest.group.name" -> list("a", "b", "c"), "loadtest.request.name" -> s("r")), pred("p95")).value shouldBe
        details(grp("a", "b", "c", "r")).responseTime.percentile(95).lte(500)
    }

    "render `*` on the request name as the per-request scope" in {
      render(req("loadtest.request.name" -> s("*")), pred("p95")).value shouldBe
        forAll.responseTime.percentile(95).lte(500)
    }

    "not depend on the order the selector's keys happen to be in" in {
      val a = req("loadtest.group.name" -> list("G"), "loadtest.request.name" -> s("X"))
      val b = req("loadtest.request.name" -> s("X"), "loadtest.group.name" -> list("G"))
      render(a, pred("p95")).value shouldBe render(b, pred("p95")).value
    }
  }

  "Reach, on the statistic axis" should {

    "render every response-time aggregation the tables admit" in {
      render(anywhere, pred("p99")).value shouldBe global.responseTime.percentile(99).lte(500)
      render(anywhere, pred("p99.9")).value shouldBe global.responseTime.percentile(99.9).lte(500)
      render(anywhere, pred("p1")).value shouldBe global.responseTime.percentile(1).lte(500)
      render(anywhere, pred("max")).value shouldBe global.responseTime.max.lte(500)
      render(anywhere, pred("min")).value shouldBe global.responseTime.min.lte(500)
      render(anywhere, pred("avg")).value shouldBe global.responseTime.mean.lte(500)
      render(anywhere, pred("stddev")).value shouldBe global.responseTime.stdDev.lte(500)
    }

    "render a KO fraction as the failed-request share and count" in {
      render(anywhere, pred("rate", threshold = 5, unit = "%", metric = None, bad = Some(Ko))).value shouldBe
        global.failedRequests.percent.lte(5.0)
      render(anywhere, pred("count", threshold = 20, unit = "{request}", metric = None, bad = Some(Ko))).value shouldBe
        global.failedRequests.count.lte(20L)
    }

    "keep a fractional share target fractional rather than truncating it" in {
      render(anywhere, pred("rate", threshold = BigDecimal("5.5"), unit = "%", metric = None, bad = Some(Ko))).value shouldBe
        global.failedRequests.percent.lte(5.5)
    }

    "render a predicate carrying neither metric nor fraction as the requests themselves" in {
      render(anywhere, pred("count", threshold = 1000, unit = "{request}", metric = None)).value shouldBe
        global.allRequests.count.lte(1000L)
      render(anywhere, pred("rate", threshold = 200, unit = "{request}/s", metric = None)).value shouldBe
        global.requestsPerSec.lte(200.0)
    }
  }

  "Reach, converting a threshold" should {

    "convert seconds to whole milliseconds exactly, where binary floating point would not" in {
      // 1.001 * 1000 is 1000.9999999999999 as a Double and exactly 1001 as a decimal.
      render(anywhere, pred("p95", threshold = BigDecimal("1.001"), unit = "s")).value shouldBe
        global.responseTime.percentile(95).lte(1001)
    }

    "convert a half-second and a fraction in `1`" in {
      render(anywhere, pred("p95", threshold = BigDecimal("0.5"), unit = "s")).value shouldBe
        global.responseTime.percentile(95).lte(500)
      render(anywhere, pred("rate", threshold = BigDecimal("0.05"), unit = "1", metric = None, bad = Some(Ko))).value shouldBe
        global.failedRequests.percent.lte(5.0)
    }
  }

  "Reach, on the operator axis" should {

    "map every operator the tables admit, `eq` to Gatling's `is`" in {
      render(anywhere, pred("p95", op = "lt")).value shouldBe global.responseTime.percentile(95).lt(500)
      render(anywhere, pred("p95", op = "lte")).value shouldBe global.responseTime.percentile(95).lte(500)
      render(anywhere, pred("p95", op = "gt")).value shouldBe global.responseTime.percentile(95).gt(500)
      render(anywhere, pred("p95", op = "gte")).value shouldBe global.responseTime.percentile(95).gte(500)
      render(anywhere, pred("p95", op = "eq")).value shouldBe global.responseTime.percentile(95).is(500)
    }
  }

  "Reach, refusing what Gatling cannot assert" should {

    "refuse a hierarchy-only selector with the reason upstream decided" in {
      render(req("loadtest.group.name" -> list("G")), pred("p95")).left.value should
        include("no Gatling scope denotes the requests a path encloses")
    }

    "refuse a `*` hierarchy element, which no scope carries as a path part" in {
      render(req("loadtest.group.name" -> list("*"), "loadtest.request.name" -> s("X")), pred("p95")).left.value should
        include("no scope carries a wildcard path part")
    }

    "refuse `*` on the request name beside a hierarchy: no scope both quantifies and carries a path" in {
      render(req("loadtest.group.name" -> list("G"), "loadtest.request.name" -> s("*")), pred("p95")).left.value should
        include("quantified selection cannot carry a path")
    }

    "refuse a non-string path part, because a path part is a string" in {
      render(req("loadtest.request.name" -> Json.fromInt(200)), pred("p95")).left.value should
        include("must be a string")
      render(
        req("loadtest.group.name" -> Json.arr(Json.fromInt(1)), "loadtest.request.name" -> s("X")),
        pred("p95"),
      ).left.value should
        include("must be a string")
    }

    "refuse an empty path part, which names nothing and would fail the run for an unrelated reason" in {
      render(req("loadtest.request.name" -> s("")), pred("p95")).left.value should include("carries an empty one")
      render(
        req("loadtest.group.name" -> list(""), "loadtest.request.name" -> s("X")),
        pred("p95"),
      ).left.value should include("carries an empty one")
    }

    "refuse an empty hierarchy: no enclosing group is said by omitting the key" in {
      render(req("loadtest.group.name" -> Json.arr(), "loadtest.request.name" -> s("X")), pred("p95")).left.value should
        include("at least one element")
    }

    "refuse an attribute Gatling cannot address" in {
      render(req("http.route" -> s("/api")), pred("p95")).left.value should include("is not an assertion path")
    }

    "refuse `neq`, which has no negating condition" in {
      render(anywhere, pred("p95", op = "neq")).left.value should include("no negating condition")
    }

    "refuse `sum` over a metric" in {
      render(anywhere, pred("sum")).left.value should include("responseTime offers no sum")
    }

    "refuse a metric that is not addressable" in {
      render(anywhere, pred("p95", metric = Some("http.client.request.body.size"), unit = "By")).left.value should
        include("is not addressable")
    }

    "refuse a `bad` narrower than the KO fraction, whose numerator has no correspondence" in {
      val narrower = Map("http.response.status_code" -> Json.fromInt(500))
      render(anywhere, pred("rate", threshold = 5, unit = "%", metric = None, bad = Some(narrower))).left.value should
        include("filtered numerator has no correspondence")
    }

    "refuse `good` in any form" in {
      render(anywhere, pred("rate", threshold = 5, unit = "%", metric = None, good = Some(Ko))).left.value should
        include("never absence")
    }

    "refuse a unit that does not fit its statistic — a check the schema deliberately omits" in {
      render(anywhere, pred("p95", unit = "%")).left.value should include("is not a unit of responseTime")
      render(anywhere, pred("rate", threshold = 5, unit = "ms", metric = None, bad = Some(Ko))).left.value should
        include("is not a unit of failedRequests.percent")
    }

    "refuse a threshold that is not whole in the native unit rather than rounding it" in {
      render(anywhere, pred("p95", threshold = BigDecimal("0.5"), unit = "ms")).left.value should
        include("rounding it would move the bar")
    }

    "render a count past Int.MaxValue, because a count target is a Long" in {
      val big = BigDecimal("3000000000")
      render(anywhere, pred("count", threshold = big, unit = "{request}", metric = None)).value shouldBe
        global.allRequests.count.lte(3000000000L)
    }

    "refuse a response time past Int.MaxValue for not fitting, not for needing rounding" in {
      val reason = render(anywhere, pred("p95", threshold = BigDecimal("3000000000"), unit = "ms")).left.value
      reason should include("does not fit")
      reason should not include "rounding"
    }

    "refuse the three combinations the schema forbids" in {
      render(anywhere, pred("rate", threshold = 5, unit = "%", metric = Some(Duration), bad = Some(Ko))).left.value should
        include("cannot be carried with `bad`")
      render(anywhere, pred("rate", threshold = 5, unit = "%", metric = Some(Duration), good = Some(Ko))).left.value should
        include("cannot be carried with `good`")
      render(
        anywhere,
        pred("rate", threshold = 5, unit = "%", metric = None, bad = Some(Ko), good = Some(Ko)),
      ).left.value should
        include("at most one side of a fraction")
    }
  }

  "Reach, on the direction that matters — not refusing too much" should {

    "accept every shape the reach tables list as reachable" in {
      // An allowlist that refuses a listed shape is as much a defect as one that admits an unlisted shape, and is
      // harder to notice. This is the cross-product of contracts/reach.md's **can** rows.
      val selections = List(
        req(),
        req("loadtest.request.name" -> s("X")),
        req("loadtest.request.name" -> s("*")),
        req("loadtest.group.name"   -> list("G"), "loadtest.request.name"           -> s("X")),
        req("loadtest.group.name"   -> list("G", "H", "I"), "loadtest.request.name" -> s("X")),
      )
      val predicates = List(
        pred("p50"),
        pred("p99.9"),
        pred("max"),
        pred("min"),
        pred("avg"),
        pred("stddev"),
        pred("rate", threshold = 5, unit = "%", metric = None, bad = Some(Ko)),
        pred("count", threshold = 20, unit = "{request}", metric = None, bad = Some(Ko)),
        pred("count", threshold = 20, unit = "{request}", metric = None),
        pred("rate", threshold = 20, unit = "{request}/s", metric = None),
      )
      val ops        = List("lt", "lte", "gt", "gte", "eq")

      val refused = for {
        sel <- selections
        p   <- predicates
        op  <- ops
        r    = render(sel, p.copy(op = op))
        if r.isLeft
      } yield s"${sel.selector.keys.mkString(",")} / ${p.aggregation} / $op -> ${r.left.value}"

      withClue(s"these listed shapes were refused:\n${refused.mkString("\n")}\n") {
        refused shouldBe empty
      }
    }
  }

  "Reach, where this library decides what upstream leaves open" should {

    "point every locally-decided refusal at the register that records why" in {
      Reach.localDecisions should not be empty
      Reach.localDecisions.foreach(d => d.upstream should not be empty)
      render(anywhere, pred("count", threshold = 5, unit = "{request}")).left.value should include("opennfr")
      render(anywhere, pred("p95", threshold = 1500000, unit = "us")).left.value should include("opennfr")
    }

    "not mark a rule the published tables do decide" in {
      render(anywhere, pred("p95", op = "neq")).left.value should not include "opennfr"
    }

    "not mark a shape the register does not name — a typo is a typo, not a disagreement" in {
      // p100 is outside the schema's percentile pattern and a natural mistake; the register says nothing about it.
      render(anywhere, pred("p100")).left.value should not include "opennfr"
      render(anywhere, pred("median")).left.value should not include "opennfr"
      // The duration-unit row is about response time; `min` is simply not a unit of a share.
      render(anywhere, pred("rate", threshold = 5, unit = "min", metric = None, bad = Some(Ko))).left.value should
        not include "opennfr"
    }
  }
}
