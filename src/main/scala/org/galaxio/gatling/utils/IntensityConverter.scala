package org.galaxio.gatling.utils

object IntensityConverter {

  // implicit conversions to rps
  implicit class toRps(val count: Double) extends AnyVal {
    def rph: Double = count / 3600

    def rpm: Double = count / 60

    def rps: Double = count
  }

  // Anchored full-match: arbitrary-precision decimal + optional alphabetic unit. The anchors reject
  // partial/garbage input (".5", "1.", "1.2.3", "abc", "-5") instead of silently truncating it (issue #93).
  private val pattern = """^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)?$""".r

  // Each step is a total function that builds its `Either` directly (no `Option`->`Either` conversion): a guard, a regex
  // extractor, `Either.cond`, and a unit match. The for-comprehension just composes them.

  private def trimmedInput(s: String): Either[String, String] =
    if (s == null) Left("input is null") else Right(s.trim)

  private def split(s: String): Either[String, (String, String)] =
    s match {
      case pattern(number, unit) => Right((number, unit))
      case garbage               => Left(s"not a '<number>[ unit]' value: '$garbage'")
    }

  private def toFinite(number: String): Either[String, Double] = {
    val value = number.toDouble // the regex guarantees a parseable number
    Either.cond(value.isFinite, value, s"value out of range: '$number'")
  }

  private def toPerSecond(value: Double, rawUnit: String): Either[String, Double] =
    Option(rawUnit).map(_.toLowerCase).getOrElse("rps") match {
      case "rps" => Right(value)
      case "rpm" => Right(value / 60)
      case "rph" => Right(value / 3600)
      case unit  => Left(s"unsupported unit '$unit' (use rps, rpm or rph)")
    }

  /** Parse a `"<number>[ unit]"` intensity into requests-per-second by composing the steps above. The only
    * `IllegalArgumentException` is raised at the boundary — keeping the documented `"Simulation param for intensity incorrect"`
    * prefix and appending the specific cause. Rejected: null input, malformed/garbage strings, an out-of-range (non-finite)
    * value, and unsupported units. A missing unit defaults to `rps`.
    */
  def getIntensityFromString(intensity: String): Double =
    (for {
      trimmed    <- trimmedInput(intensity)
      numAndUnit <- split(trimmed)
      value      <- toFinite(numAndUnit._1)
      perSecond  <- toPerSecond(value, numAndUnit._2)
    } yield perSecond).fold(
      reason => throw new IllegalArgumentException(s"Simulation param for intensity incorrect: $reason"),
      identity,
    )
}
