package org.galaxio.gatling.assertions.opennfr

import io.circe.Json
import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.assertion.{AssertionPathParts, AssertionWithPath, AssertionWithPathAndTarget}
import io.gatling.core.config.GatlingConfiguration

/** Renders one OpenNFR predicate into one Gatling assertion, or says why it cannot.
  *
  * The rules are `specs/013-opennfr-assertions/contracts/reach.md`, which transcribes upstream's `README.md` § "What any tool
  * can actually run" — the tables' only home. This file implements that transcription and derives nothing of its own: where the
  * two disagree, the table is right and the disagreement is a finding to report upstream (FR-018).
  *
  * Every refusal carries the words of the row that refused it, so a message points back at the rule (FR-009).
  */
private[opennfr] object Reach {

  private val Hierarchy = "loadtest.group.name"
  private val Request   = "loadtest.request.name"
  private val Duration  = "http.client.request.duration"
  private val Star      = "*"

  /** The response-time statistic every duration-unit decision below is about. */
  private val ResponseTime = "responseTime"

  /** The four duration units the published Units table excludes and `responseTime` in fact reaches. */
  private val ExcludedDurationUnits = Set("ns", "us", "min", "h")

  /** The two aggregations the published table refuses over a metric and Gatling in fact computes. */
  private val RefusedOverMetric = Set("count", "rate")

  /** The one numerator that has a Gatling correspondence: `failedRequests` counts KO and nothing else. */
  private val KoFraction: Map[String, Json] = Map("error.type" -> Json.fromString(Star))

  /** One place where this library follows a published rule it has reason to think wrong (FR-011).
    *
    * The reach tables are upstream's and are followed by default. Where following them costs something, the cost is recorded
    * here with the upstream issue rather than silently paid or silently avoided — and the refusal message points here, so a
    * user who hits it can see it is a known disagreement and not a bug in this library.
    *
    * A note is attached only where the row it names is the row that actually refused: a typo is a typo, not a disagreement.
    *
    * @param what
    *   the shape affected
    * @param upstream
    *   where the disagreement is recorded upstream
    * @param why
    *   what following the published row costs
    */
  final case class LocalDecision(what: String, upstream: String, why: String)

  private val OverMetric      = "`count` and `rate` over a metric"
  private val DurationUnitRow = "the duration units `ns`, `us`, `min` and `h`"

  val localDecisions: List[LocalDecision] = List(
    LocalDecision(
      what = OverMetric,
      upstream = "opennfr — reported from here, not yet filed",
      why = "The published row refuses both. Gatling computes them from the same buffer that produces the percentiles: every " +
        "request record contributes one response-time observation, so allRequests.count IS the count of observations of " +
        "http.client.request.duration. The row looks over-refusing, and the published table is followed until upstream settles it.",
    ),
    LocalDecision(
      what = DurationUnitRow,
      upstream = "opennfr#74",
      why = "The published Units table calls them unreachable. responseTime reaches all four exactly, by the same conversion " +
        "that makes `s` renderable. The published table is followed until upstream settles it.",
    ),
  )

  private def decided(shape: String): String =
    localDecisions
      .find(_.what == shape)
      .map(d => s" (a known disagreement with the published table — see ${d.upstream})")
      .getOrElse("")

  private val Percentile = """^p(\d{1,2}(?:\.\d+)?)$""".r

  // Units are per statistic, not a shared pool: a unit valid for one is not thereby valid for another.
  private val Time   = Map("ms" -> BigDecimal(1), "s" -> BigDecimal(1000))
  private val Share  = Map("%" -> BigDecimal(1), "1" -> BigDecimal(100))
  private val Count  = Map("{request}" -> BigDecimal(1))
  private val PerSec = Map("{request}/s" -> BigDecimal(1))

  /** One part of an assertion path. A part is a string, and an empty one names nothing: `details("" / "checkout")` matches no
    * recorded request, so it would fail the run for a reason unrelated to what the author asserted — the failure mode upstream
    * closed for `"*"` in `opennfr#55`, which the schema leaves open here because it sets no `minLength`.
    */
  private def pathPart(key: String, value: Json): Either[String, String] =
    value.asString
      .toRight(s"an assertion path part must be a string: `$key` carries a value that is not")
      .flatMap(part =>
        Either.cond(part.nonEmpty, part, s"an assertion path part names a request or a group: `$key` carries an empty one"),
      )

  /** Which requests, per § Selection. `{}` is every request pooled; a bare request name is root-anchored; a hierarchy binds
    * whole at any depth; `"*"` on the request name quantifies and reaches the per-request scope, which carries no path.
    */
  private def scope(selector: Map[String, Json]): Either[String, Scope] = {
    val keys = selector.keySet

    if (keys.isEmpty) Right(Scope.Global)
    else if (!keys.subsetOf(Set(Hierarchy, Request)))
      Left(
        s"selector ${keys.toList.sorted.mkString("[", ", ", "]")} is not an assertion path: Gatling addresses assertions by " +
          "recorded group and request names only",
      )
    else
      // The key set is non-empty and drawn from the two above, so an absent request name means the hierarchy is present:
      // there is no fourth combination to handle, and none is invented here.
      selector.get(Request) match {
        case None =>
          Left(
            "a selector naming only a hierarchy denotes the requests whose hierarchy is exactly those groups, and no Gatling " +
              "scope denotes the requests a path encloses: a group path resolves to the group, whose statistics are its own",
          )

        case Some(r) if r.asString.contains(Star) && !selector.contains(Hierarchy) => Right(Scope.ForAll)

        case Some(r) =>
          for {
            groups <- selector
                        .get(Hierarchy)
                        .fold[Either[String, List[String]]](Right(Nil))(hierarchy)
            name   <- pathPart(Request, r)
            _      <- Either.cond(
                        name != Star,
                        (),
                        "a quantified selection cannot carry a path: `\"*\"` reaches the per-request scope, which takes none",
                      )
          } yield Scope.Details(groups :+ name)
      }
  }

  private def hierarchy(h: Json): Either[String, List[String]] =
    h.asArray
      .toRight(s"`$Hierarchy` must be an ordered list of group names")
      .flatMap { parts =>
        if (parts.isEmpty)
          Left(s"`$Hierarchy` must have at least one element; omit the key to say there is no enclosing group")
        else
          parts.toList.partitionMap(pathPart(Hierarchy, _)) match {
            case (Nil, groups)    =>
              Either.cond(
                !groups.contains(Star),
                groups,
                s"a `\"$Star\"` element is a group at that position with any name, and no scope carries a wildcard path part",
              )
            case (reason :: _, _) => Left(reason)
          }
      }

  /** Exact decimal arithmetic on the literal as written. Binary floating point rejects ordinary thresholds such as `1.001 s`
    * that exact arithmetic accepts, and upstream states no arithmetic model (research R4, `opennfr#59`).
    */
  private def canonical(p: Predicate, native: String, factors: Map[String, BigDecimal]): Either[String, BigDecimal] =
    factors
      .get(p.unit)
      .map(p.threshold * _)
      .toRight(
        s"unit `${p.unit}` is not a unit of $native" +
          (if (native == ResponseTime && ExcludedDurationUnits.contains(p.unit)) decided(DurationUnitRow) else ""),
      )

  /** Where the target is a whole number, the converted threshold must be one — refused, never rounded, because rounding moves
    * the bar the author wrote.
    */
  private def whole(p: Predicate, native: String, factors: Map[String, BigDecimal]): Either[String, BigDecimal] =
    canonical(p, native, factors).flatMap { v =>
      Either.cond(
        v.isWhole,
        v,
        s"threshold ${p.threshold} ${p.unit} is $v for $native, whose target is a whole number, and rounding it would move the bar",
      )
    }

  /** Response-time targets are `Int` milliseconds. */
  private def wholeInt(p: Predicate, native: String, factors: Map[String, BigDecimal]): Either[String, Int] =
    whole(p, native, factors).flatMap(v => Either.cond(v.isValidInt, v.toIntExact, outOfRange(p, native, v, "32-bit")))

  /** Count targets are `Long`: a whole count too large for an `Int` is renderable, and refusing it for rounding would be false
    * of it.
    */
  private def wholeLong(p: Predicate, native: String, factors: Map[String, BigDecimal]): Either[String, Long] =
    whole(p, native, factors).flatMap(v => Either.cond(v.isValidLong, v.toLongExact, outOfRange(p, native, v, "64-bit")))

  private def outOfRange(p: Predicate, native: String, converted: BigDecimal, width: String): String =
    s"threshold ${p.threshold} ${p.unit} is $converted for $native, whose target is a $width whole number, and it does not fit"

  /** The operators the tables admit. Validated while resolving, so applying a `Resolved` cannot fail. */
  private def operator(op: String): Either[String, Op] =
    op match {
      case "lt"  => Right(Op.Lt)
      case "lte" => Right(Op.Lte)
      case "gt"  => Right(Op.Gt)
      case "gte" => Right(Op.Gte)
      case "eq"  => Right(Op.Eq)
      case "neq" => Left("operator `neq` has no equivalent: Gatling has no negating condition")
      case other => Left(s"operator `$other` has no equivalent")
    }

  /** Resolve one predicate to the decision both surfaces re-issue, or to the one reason it cannot be made (research R2). */
  def resolve(r: Requirement, p: Predicate): Either[String, Resolved] =
    for {
      sc <- scope(r.selector)
      op <- operator(p.op)
      m  <- measure(p)
    } yield Resolved(sc, m, op)

  private def measure(p: Predicate): Either[String, Measure] =
    (p.metric, p.bad, p.good) match {
      // The schema forbids these three. Nothing here validates against the schema, so its rules are enforced (FR-004).
      case (_, Some(_), Some(_)) => Left("at most one side of a fraction: `bad` and `good` cannot both be present")
      case (Some(_), Some(_), _) => Left("a metric is measured; a fraction is counted — `metric` cannot be carried with `bad`")
      case (Some(_), _, Some(_)) => Left("a metric is measured; a fraction is counted — `metric` cannot be carried with `good`")

      case (_, _, Some(_)) =>
        Left("`good` has no expressible numerator: a selector matches presence and never absence")

      case (Some(Duration), None, None) =>
        val millis = wholeInt(p, ResponseTime, Time)
        p.aggregation match {
          case Percentile(n) => millis.map(v => Measure.Percentile(n.toDouble, v))
          case "max"         => millis.map(v => Measure.Max(v))
          case "min"         => millis.map(v => Measure.Min(v))
          case "avg"         => millis.map(v => Measure.Mean(v))
          case "stddev"      => millis.map(v => Measure.StdDev(v))
          case "sum"         => Left("aggregation `sum` over a metric has no equivalent: responseTime offers no sum")
          case other         =>
            Left(
              s"aggregation `$other` over a metric has no equivalent" +
                (if (RefusedOverMetric.contains(other)) decided(OverMetric) else ""),
            )
        }

      case (Some(metric), None, None) =>
        Left(s"metric `$metric` is not addressable: the assertion DSL reaches response time and request counts only")

      case (None, Some(bad), None) if bad == KoFraction =>
        p.aggregation match {
          case "rate"  => canonical(p, "failedRequests.percent", Share).map(v => Measure.FailedPercent(v.toDouble))
          case "count" => wholeLong(p, "failedRequests.count", Count).map(v => Measure.FailedCount(v))
          case other   => Left(s"aggregation `$other` over a fraction has no equivalent: only a share or a count")
        }

      case (None, Some(bad), None) =>
        Left(
          s"`bad` ${bad.keys.toList.sorted.mkString("{", ", ", "}")} is not `{error.type: \"*\"}`: failedRequests counts KO " +
            "and nothing else, so a filtered numerator has no correspondence",
        )

      case (None, None, None) =>
        p.aggregation match {
          case "count" => wholeLong(p, "allRequests.count", Count).map(v => Measure.AllCount(v))
          case "rate"  => canonical(p, "requestsPerSec", PerSec).map(v => Measure.RequestsPerSec(v.toDouble))
          case other   => Left(s"aggregation `$other` over the requests themselves has no equivalent: only a count or a rate")
        }
    }

  /** Applies a resolved decision through the Scala DSL. Total by construction: every `Measure` carries the target its builder
    * takes, so this needs no catch-all and a new case will not compile until it is handled.
    */
  private[opennfr] def apply(d: Resolved)(implicit configuration: GatlingConfiguration): Assertion = {
    val path: AssertionWithPath = d.scope match {
      case Scope.Global         => global
      case Scope.ForAll         => forAll
      case Scope.Details(parts) => details(AssertionPathParts(parts))
    }

    def cmpInt(t: AssertionWithPathAndTarget[Int], v: Int): Assertion          = d.op match {
      case Op.Lt => t.lt(v); case Op.Lte => t.lte(v); case Op.Gt => t.gt(v); case Op.Gte => t.gte(v); case Op.Eq => t.is(v)
    }
    def cmpLong(t: AssertionWithPathAndTarget[Long], v: Long): Assertion       = d.op match {
      case Op.Lt => t.lt(v); case Op.Lte => t.lte(v); case Op.Gt => t.gt(v); case Op.Gte => t.gte(v); case Op.Eq => t.is(v)
    }
    def cmpDouble(t: AssertionWithPathAndTarget[Double], v: Double): Assertion = d.op match {
      case Op.Lt => t.lt(v); case Op.Lte => t.lte(v); case Op.Gt => t.gt(v); case Op.Gte => t.gte(v); case Op.Eq => t.is(v)
    }

    d.measure match {
      case Measure.Percentile(n, v)  => cmpInt(path.responseTime.percentile(n), v)
      case Measure.Max(v)            => cmpInt(path.responseTime.max, v)
      case Measure.Min(v)            => cmpInt(path.responseTime.min, v)
      case Measure.Mean(v)           => cmpInt(path.responseTime.mean, v)
      case Measure.StdDev(v)         => cmpInt(path.responseTime.stdDev, v)
      case Measure.FailedPercent(v)  => cmpDouble(path.failedRequests.percent, v)
      case Measure.FailedCount(v)    => cmpLong(path.failedRequests.count, v)
      case Measure.AllCount(v)       => cmpLong(path.allRequests.count, v)
      case Measure.RequestsPerSec(v) => cmpDouble(path.requestsPerSec, v)
    }
  }

  def render(r: Requirement, p: Predicate)(implicit configuration: GatlingConfiguration): Either[String, Assertion] =
    resolve(r, p).map(apply)
}
