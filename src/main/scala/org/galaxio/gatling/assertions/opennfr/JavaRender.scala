package org.galaxio.gatling.assertions.opennfr

import io.gatling.javaapi.core.Assertion
import io.gatling.javaapi.core.CoreDsl._

import scala.jdk.CollectionConverters._

/** Applies a resolved decision through the *Java* DSL, for the Java/Kotlin facade.
  *
  * The Java `Assertion` wrapper cannot be built from a core one — its constructor is package-private — so the final DSL call
  * has to happen once per surface (research R2). Keeping the second application here, in Scala, rather than in the facade is
  * what lets `OpenNfrAssertions.java` be a genuine one-line delegation: no rule, no table and no decision lives in Java.
  *
  * Every refusal has already happened by the time a [[Resolved]] exists, so nothing here can fail — and because every
  * [[Measure]] carries its own target, that is checked by the compiler rather than by a catch-all: a new case stops this file
  * compiling until it is handled here too. Note that the Java DSL needs no `GatlingConfiguration`: resolution is pure, and only
  * the Scala DSL's builders take the implicit.
  */
private[assertions] object JavaRender {

  def assertions(path: String): java.util.List[Assertion] =
    OpenNfrAssertions
      .decisions(path)
      .fold(reasons => throw new OpenNfrException(path, reasons), _.map(apply).asJava)

  private def apply(d: Resolved): Assertion = {
    val path = d.scope match {
      case Scope.Global         => global()
      case Scope.ForAll         => forAll()
      case Scope.Details(parts) => details(parts: _*)
      // Same call as Details, for the same reason as the Scala surface: the Java DSL exposes one
      // time metric too, and Gatling picks the group's cumulated statistic by resolving the path.
      // The facade stays thin — it re-issues a decision `Reach` already made, it does not re-make it.
      case Scope.Group(parts)   => details(parts: _*)
    }

    d.measure match {
      case Measure.Percentile(n, v)  => cmpInt(d.op, path.responseTime().percentile(n), v)
      case Measure.Max(v)            => cmpInt(d.op, path.responseTime().max(), v)
      case Measure.Min(v)            => cmpInt(d.op, path.responseTime().min(), v)
      case Measure.Mean(v)           => cmpInt(d.op, path.responseTime().mean(), v)
      case Measure.StdDev(v)         => cmpInt(d.op, path.responseTime().stdDev(), v)
      case Measure.FailedPercent(v)  => cmpDouble(d.op, path.failedRequests().percent(), v)
      case Measure.FailedCount(v)    => cmpLong(d.op, path.failedRequests().count(), v)
      case Measure.AllCount(v)       => cmpLong(d.op, path.allRequests().count(), v)
      case Measure.RequestsPerSec(v) => cmpDouble(d.op, path.requestsPerSec(), v)
    }
  }

  private def cmpInt(op: Op, t: Assertion.WithPathAndTarget[Integer], v: Int): Assertion =
    op match {
      case Op.Lt => t.lt(v); case Op.Lte => t.lte(v); case Op.Gt => t.gt(v); case Op.Gte => t.gte(v); case Op.Eq => t.is(v)
    }

  private def cmpLong(op: Op, t: Assertion.WithPathAndTarget[java.lang.Long], v: Long): Assertion =
    op match {
      case Op.Lt => t.lt(v); case Op.Lte => t.lte(v); case Op.Gt => t.gt(v); case Op.Gte => t.gte(v); case Op.Eq => t.is(v)
    }

  private def cmpDouble(op: Op, t: Assertion.WithPathAndTarget[java.lang.Double], v: Double): Assertion =
    op match {
      case Op.Lt => t.lt(v); case Op.Lte => t.lte(v); case Op.Gt => t.gt(v); case Op.Gte => t.gte(v); case Op.Eq => t.is(v)
    }
}
