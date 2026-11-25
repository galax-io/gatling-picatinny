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

  /** The upstream release this renderer is written against, in one place. */
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

  /** A structural failure as a reader sees it: its reason, its cursor history, and the field it happened at, so that a
    * sixty-line document does not leave the reader to bisect.
    */
  private def describe(failure: DecodingFailure): String = {
    val at = CursorOp.opsToPath(failure.history)
    s"not an OpenNFR document: ${reason(failure)}" + (if (at.isEmpty || at == ".") "" else s" at $at")
  }

  /** The optional fields of an element, by the list it sits in: an optional field of an element that is not an object is the
    * one failure circe 0.14 reports as a missing field where 0.15.0-M1 named the `Option` decoder.
    */
  private val optionalFields: Map[String, Set[String]] = {
    val predicate = Set("name", "metric", "bad", "good")
    Map("requirements" -> Set("guards"), "criteria" -> predicate, "guards" -> predicate)
  }

  private def optionalOfNonObject(history: List[CursorOp]): Boolean = history match {
    case CursorOp.DownField(field) :: CursorOp.DownArray :: CursorOp.DownField(list) :: _ =>
      optionalFields.get(list).exists(_.contains(field))
    case _                                                                                => false
  }

  /** The reason as Picatinny has always written it (BCKLG-233, FR-008): the text circe 0.15.0-M1 gave and the cursor history.
    * circe 0.14 words each reason anew ("Missing required field", "Got value … with wrong type, expecting string"), which would
    * change what a reader sees, so each one is written as before. The match is over every reason circe has, with no default, so
    * a reason a later circe adds is a compile error here rather than a silent change of the text.
    */
  private def reason(failure: DecodingFailure): String = {
    val message = failure.reason match {
      case DecodingFailure.Reason.MissingField if optionalOfNonObject(failure.history) => "[A]Option[A]"
      case DecodingFailure.Reason.MissingField                                         => "Attempt to decode value on failed cursor"
      case DecodingFailure.Reason.WrongTypeExpectation("string", _)                    => "String"
      case DecodingFailure.Reason.WrongTypeExpectation("array", _)                     => "C[A]"
      case DecodingFailure.Reason.WrongTypeExpectation("object", _)                    => "[K, V]Map[K, V]"
      case DecodingFailure.Reason.WrongTypeExpectation(_, _)                           => failure.message
      case DecodingFailure.Reason.CustomReason(text)                                   => text
    }
    if (failure.history.isEmpty) message else s"$message: ${failure.history.mkString(",")}"
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
