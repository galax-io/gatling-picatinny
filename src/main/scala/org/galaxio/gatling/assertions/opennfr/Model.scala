package org.galaxio.gatling.assertions.opennfr

import io.circe.{CursorOp, Decoder, DecodingFailure, Json}

/** The OpenNFR document model, as published at the release [[RequirementSet.TracksRelease]] names.
  *
  * Deliberately shallow: the schema is the authority on what a *valid* document is, and this mirrors only the shape the
  * renderer needs in order to decide what a valid document *denotes*. See `specs/013-opennfr-assertions/data-model.md`.
  */
private[opennfr] final case class Predicate(
    name: Option[String],
    metric: Option[String],
    aggregation: String,
    op: String,
    threshold: BigDecimal,
    unit: String,
    bad: Option[Map[String, Json]],
    good: Option[Map[String, Json]],
) {

  /** How a message names this predicate: the format's own identity rule — the `name` where set, the `aggregation` otherwise. */
  def identity: String = name.getOrElse(aggregation)
}

private[opennfr] final case class Requirement(
    name: String,
    selector: Map[String, Json],
    criteria: List[Predicate],
    guards: Option[List[Predicate]],
) {

  /** Criteria and guards render alike: Gatling has one outcome, and a guard that produced no assertion would pass silently. The
    * distinction is what a violation *means*, which the target cannot carry.
    *
    * '''A guard is quantified with the criteria it sits beside''', because the selector is written once and binds both. Under
    * `{loadtest.request.name: "*"}` a guard renders as `forAll()`, which on a run that recorded nothing expands to zero
    * assertions and passes — so the guard that says "the run happened" cannot be attached to a quantified requirement, and has
    * to sit on a `{}` one. That is upstream `opennfr#69`, faithfully implemented here rather than worked around;
    * `docs/opennfr.md` warns a reader, and `OpenNfrAssertionsSpec` pins the behaviour so it cannot change unnoticed.
    */
  def predicates: List[Predicate] = criteria ++ guards.getOrElse(Nil)
}

private[opennfr] final case class RequirementSet(apiVersion: String, kind: String, requirements: List[Requirement])

private[opennfr] object RequirementSet {

  val ApiVersion: String = "opennfr.io/v1"
  val Kind: String       = "RequirementSet"

  /** The upstream release this renderer is written against, in one place. Every document that states it — the two scaladoc
    * headers, the Java facade's javadoc, `docs/opennfr.md` and `contracts/reach.md` — is checked against this by
    * `MigrationTableSpec`, so moving the tracked release cannot leave one of them behind.
    */
  val TracksRelease: String = "v0.8.0"

  private final case class Spec(requirements: List[Requirement])

  // `forProductN` rather than a `for` over `Decoder.instance`: it accumulates every field's failure instead of stopping at the
  // first, which is what FR-003 promises and what the renderer already does for semantic refusals.
  implicit val predicateDecoder: Decoder[Predicate] =
    Decoder.forProduct8("name", "metric", "aggregation", "op", "threshold", "unit", "bad", "good")(Predicate.apply)

  implicit val requirementDecoder: Decoder[Requirement] =
    Decoder.forProduct4("name", "selector", "criteria", "guards")(Requirement.apply)

  private implicit val specDecoder: Decoder[Spec] = Decoder.forProduct1("requirements")(Spec.apply)

  implicit val requirementSetDecoder: Decoder[RequirementSet] =
    Decoder.forProduct3("apiVersion", "kind", "spec")((apiVersion: String, kind: String, spec: Spec) =>
      RequirementSet(apiVersion, kind, spec.requirements),
    )

  /** Parses YAML and decodes it, keeping the failures as values — every one of them, each naming where it happened. A
    * `threshold` arrives as an exact decimal — never a binary float — because the whole-number rule compares against it
    * (FR-008).
    */
  def decode(text: String): Either[List[String], RequirementSet] =
    io.circe.yaml.parser.parse(text) match {
      case Left(parseFailure) => Left(List(s"not an OpenNFR document: ${parseFailure.getMessage}"))
      case Right(json)        =>
        requirementSetDecoder
          .decodeAccumulating(json.hcursor)
          .fold(failures => Left(failures.toList.map(describe)), Right(_))
    }

  /** Names the field a structural failure happened at. `DecodingFailure.getMessage` carries the reason but not the cursor
    * history, so on its own it says "Missing required field" of a sixty-line document and leaves the reader to bisect.
    */
  private def describe(failure: DecodingFailure): String = {
    val at = CursorOp.opsToPath(failure.history)
    s"not an OpenNFR document: ${failure.getMessage}" + (if (at.isEmpty || at == ".") "" else s" at $at")
  }

  /** The envelope: the version read, the kind, and that the document states something at all (FR-002). */
  def envelope(d: RequirementSet): Either[List[String], RequirementSet] = {
    val problems =
      Option
        .when(d.apiVersion != ApiVersion)(s"apiVersion `${d.apiVersion}` is not supported; this library reads `$ApiVersion`")
        .toList ++
        Option.when(d.kind != Kind)(s"kind `${d.kind}` is not `$Kind`") ++
        Option.when(d.requirements.isEmpty)("a document with no requirements states nothing")

    Either.cond(problems.isEmpty, d, problems)
  }
}
