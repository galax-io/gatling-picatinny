package org.galaxio.gatling.assertions.opennfr

import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.config.GatlingConfiguration

import scala.io.Source
import scala.util.Using

/** Builds Gatling assertions from an [[https://github.com/galax-io/opennfr OpenNFR]] `RequirementSet`.
  *
  * '''Experimental.''' OpenNFR is pre-1.0 and moves; this tracks release `v0.8.0` of it, and the surface is outside the
  * binary-compatibility guarantee the rest of this library keeps. The deprecated NFR-YAML path
  * ([[org.galaxio.gatling.assertions.AssertionsBuilder]]) is untouched and keeps its guarantees in full.
  *
  * Used in a simulation as `setUp(...).assertions(OpenNfrAssertions.fromYaml("nfr.yaml"))`.
  *
  * '''A refusal is total and loud.''' A document carrying one unrenderable predicate produces no assertions at all — a
  * simulation that silently ran nine of ten checks is the failure this exists to prevent — and it carries every reason rather
  * than the first, so one build run surfaces every mistake.
  */
object OpenNfrAssertions {

  /** Builds the assertions an OpenNFR document denotes.
    *
    * Inside a running simulation the implicit `io.gatling.core.Predef.configuration` resolves the configuration, as it does for
    * the deprecated path. Unit tests cannot use that implicit — it throws outside a running simulation — so they call the
    * `assertionsFrom` seam with `GatlingConfiguration.loadForTest()`.
    *
    * @throws OpenNfrException
    *   if the document cannot be read, is not an OpenNFR document, or carries a predicate Gatling cannot assert.
    */
  def fromYaml(path: String): Iterable[Assertion] = assertionsFrom(path)

  /** Test seam: the same builder with the [[GatlingConfiguration]] taken explicitly. */
  private[assertions] def assertionsFrom(path: String)(implicit configuration: GatlingConfiguration): Iterable[Assertion] =
    build(path).fold(reasons => throw new OpenNfrException(path, reasons), identity)

  /** The whole pipeline as a value: read, decode, check the envelope, render every predicate — accumulating every reason a
    * predicate could not be rendered rather than stopping at the first (FR-003).
    */
  private[opennfr] def build(
      path: String,
  )(implicit configuration: GatlingConfiguration): Either[List[String], List[Assertion]] =
    for {
      text     <- read(path)
      decoded  <- RequirementSet.decode(text)
      document <- RequirementSet.envelope(decoded)
      built    <- renderAll(document)
    } yield built

  /** The resolved decisions a document denotes, before any DSL is applied — what the Java facade re-issues (research R2). */
  private[opennfr] def decisions(path: String): Either[List[String], List[Resolved]] =
    for {
      text     <- read(path)
      decoded  <- RequirementSet.decode(text)
      document <- RequirementSet.envelope(decoded)
      resolved <- resolveAll(document)
    } yield resolved

  private def resolveAll(d: RequirementSet): Either[List[String], List[Resolved]] = {
    val attempted = for {
      requirement <- d.requirements
      predicate   <- requirement.predicates
    } yield Reach
      .resolve(requirement, predicate)
      .left
      .map(reason => s"${requirement.name}/${predicate.identity}: $reason")

    attempted.partitionMap(identity) match {
      case (Nil, resolved) => Right(resolved)
      case (reasons, _)    => Left(reasons)
    }
  }

  private def read(path: String): Either[List[String], String] =
    Using(Source.fromFile(path, "UTF-8"))(_.mkString).toEither.left
      .map(e => List(s"cannot read $path: ${e.getMessage}"))

  /** Rendering is resolution applied: `Reach.render` is `resolve` followed by `apply`, so the accumulation and the wording of
    * every reason live once, in `resolveAll`, and the two surfaces cannot drift in what they say.
    */
  private def renderAll(
      d: RequirementSet,
  )(implicit configuration: GatlingConfiguration): Either[List[String], List[Assertion]] =
    resolveAll(d).map(_.map(Reach.apply))
}

/** Raised when an OpenNFR document cannot be turned into assertions. Carries every reason, not the first. */
final class OpenNfrException(path: String, val reasons: List[String]) extends IllegalArgumentException(
      s"$path is not a renderable OpenNFR document:${reasons.map("\n  - " + _).mkString}",
    )
