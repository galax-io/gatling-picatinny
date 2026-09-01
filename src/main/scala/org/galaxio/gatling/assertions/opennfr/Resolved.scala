package org.galaxio.gatling.assertions.opennfr

/** What one predicate resolves to, before any DSL is touched.
  *
  * This exists so that the Java facade can re-issue a decision without re-making it (research R2). `io.gatling.javaapi.core`'s
  * assertion wrapper cannot be built from a core one — its constructor is package-private — so the final DSL call happens
  * twice, once per surface. Everything *decided* happens once, here, and both surfaces read this.
  *
  * A `Resolved` is by construction renderable: every refusal happens while producing it, so applying it cannot fail. That is
  * enforced by the compiler rather than asserted — every case carries the target in the type Gatling's builder for it takes, so
  * neither surface needs a catch-all and adding a case fails to compile until both surfaces handle it.
  */
private[opennfr] sealed trait Scope

private[opennfr] object Scope {
  case object Global                            extends Scope
  case object ForAll                            extends Scope
  final case class Details(parts: List[String]) extends Scope

  /** A hierarchy with NO request name, which resolves to the GROUP itself (upstream `v0.8.0`).
    *
    * Renders through the very same `details(...)` path as [[Details]] — Gatling exposes one time metric and picks the group's
    * cumulated statistic purely by path resolution, so nothing about the emitted assertion distinguishes the two. This is a
    * separate case anyway because it is the only scope whose ADMISSIBLE METRIC differs: `loadtest.group.duration` here and
    * nowhere else, `loadtest.request.duration` everywhere else and not here. Keeping it in the type is what makes the compiler
    * demand a pairing decision when a metric name is added, rather than letting a new one silently inherit the wrong
    * admissibility.
    */
  final case class Group(parts: List[String]) extends Scope
}

/** The comparison, resolved from the document's `op`. A closed set, so each surface's application is exhaustive: widening it —
  * `neq`, if Gatling ever gains a negating condition — stops compiling until every application is updated, rather than being
  * silently absorbed into `is`.
  */
private[opennfr] sealed trait Op

private[opennfr] object Op {
  case object Lt  extends Op
  case object Lte extends Op
  case object Gt  extends Op
  case object Gte extends Op
  case object Eq  extends Op
}

/** The statistic and the value it is compared against, paired. Response times are whole milliseconds, counts are whole longs,
  * shares and throughput are fractional — and pairing them here is what makes a mismatched pair unrepresentable rather than a
  * runtime error.
  */
private[opennfr] sealed trait Measure

private[opennfr] object Measure {
  final case class Percentile(n: Double, millis: Int) extends Measure
  final case class Max(millis: Int)                   extends Measure
  final case class Min(millis: Int)                   extends Measure
  final case class Mean(millis: Int)                  extends Measure
  final case class StdDev(millis: Int)                extends Measure
  final case class FailedPercent(share: Double)       extends Measure
  final case class FailedCount(count: Long)           extends Measure
  final case class AllCount(count: Long)              extends Measure
  final case class RequestsPerSec(perSec: Double)     extends Measure
}

private[opennfr] final case class Resolved(scope: Scope, measure: Measure, op: Op)
