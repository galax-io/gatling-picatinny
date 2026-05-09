package org.galaxio.gatling.feeders.faker

import java.math.{BigDecimal => JBigDecimal}

/** A generated Gatling field with a stable session key and typed value generator.
  *
  * Heterogeneous records are widened to `Any` only at the feeder boundary, where Gatling itself stores session values in a map.
  */
final case class Field[+A](name: String, generator: Generator[A]) {
  require(name.nonEmpty, "Generated feeder field name must be non-empty")

  private[faker] def sampleAny: (String, Any) =
    name -> generator.sample()
}

/** ISO-like country identifiers supported by the faker API.
  *
  * The catalog will grow over time. Unknown countries can still be represented with `Country.custom`.
  */
sealed abstract class Country(val iso2: String, val displayName: String)

object Country {
  case object RU extends Country("RU", "Russia")
  case object AR extends Country("AR", "Argentina")
  case object BR extends Country("BR", "Brazil")
  case object US extends Country("US", "United States")
  case object GB extends Country("GB", "United Kingdom")
  case object DE extends Country("DE", "Germany")
  case object FR extends Country("FR", "France")
  case object ES extends Country("ES", "Spain")
  case object IT extends Country("IT", "Italy")
  case object AE extends Country("AE", "United Arab Emirates")

  final case class Custom private[faker] (override val iso2: String, override val displayName: String)
      extends Country(iso2, displayName)

  /** Creates a custom country identifier for projects that need a country before Picatinny ships built-in metadata. */
  def custom(iso2: String, displayName: String = ""): Country = {
    require(iso2.nonEmpty, "Country iso2 must be non-empty")
    Custom(iso2.toUpperCase, if (displayName.nonEmpty) displayName else iso2.toUpperCase)
  }
}

/** Gender value used by person-oriented generators. */
sealed abstract class Gender(val value: String)

object Gender {
  case object Male        extends Gender("male")
  case object Female      extends Gender("female")
  case object Unspecified extends Gender("unspecified")
}

/** Phone number formatting modes exposed by the faker API. */
sealed abstract class PhoneFormatMode

object PhoneFormatMode {
  case object E164          extends PhoneFormatMode
  case object National      extends PhoneFormatMode
  case object International extends PhoneFormatMode
  case object TollFree      extends PhoneFormatMode
}

/** Generated money amount with explicit ISO currency. */
final case class Money(amount: BigDecimal, currency: String) {
  require(currency.nonEmpty, "Currency must be non-empty")

  /** Java-friendly amount accessor. */
  def javaAmount: JBigDecimal = amount.bigDecimal
}
