package org.galaxio.gatling.feeders.faker

import org.galaxio.gatling.utils.{RandomDataGenerators, RandomPhoneGenerator}
import org.galaxio.gatling.utils.phone.{PhoneFormat, TypePhone}

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import scala.math.BigDecimal.RoundingMode

/** Faker facade for generating load-test data.
  *
  * The API is intentionally domain-oriented: use `Faker.internet.email()` or
  * `Faker.ru.inn.person()` in scenarios, then pass generators into
  * `GeneratedFeeder` when Gatling needs session records.
  */
object Faker {

  /** UUID generators. */
  object uuid {
    def value: Generator[UUID]   = Generator.delay(UUID.randomUUID())
    def string: Generator[String] = value.map(_.toString)
  }

  /** Primitive and JVM-friendly number generators. */
  object number {
    def int(min: Int, max: Int): Generator[Int] = {
      require(min <= max, s"min must be <= max: $min > $max")
      Generator.delay(if (min == max) min else ThreadLocalRandom.current().nextInt(min, max + 1))
    }

    def long(min: Long, max: Long): Generator[Long] = {
      require(min <= max, s"min must be <= max: $min > $max")
      Generator.delay(if (min == max) min else ThreadLocalRandom.current().nextLong(min, max + 1))
    }

    def double(min: Double, max: Double): Generator[Double] = {
      require(min <= max, s"min must be <= max: $min > $max")
      Generator.delay(if (min == max) min else ThreadLocalRandom.current().nextDouble(min, max))
    }

    def float(min: Float, max: Float): Generator[Float] =
      double(min.toDouble, max.toDouble).map(_.toFloat)

    def boolean: Generator[Boolean] =
      Generator.delay(ThreadLocalRandom.current().nextBoolean())
  }

  /** String generators for identifiers, payload fields, and templates. */
  object string {
    def alphabetic(length: Int): Generator[String] =
      Generator.delay(RandomDataGenerators.lettersString(length))

    def alphanumeric(length: Int): Generator[String] =
      Generator.delay(RandomDataGenerators.alphanumericString(length))

    def numeric(length: Int): Generator[String] =
      Generator.delay(RandomDataGenerators.digitString(length))

    def hex(length: Int): Generator[String] =
      Generator.delay(RandomDataGenerators.hexString(length))

    def cyrillic(length: Int): Generator[String] =
      Generator.delay(RandomDataGenerators.cyrillicString(length))

    def fromAlphabet(alphabet: String, length: Int): Generator[String] =
      Generator.delay(RandomDataGenerators.randomString(alphabet)(length))

    def lengthBetween(min: Int, max: Int, alphabet: String): Generator[String] =
      number.int(min, max).flatMap(fromAlphabet(alphabet, _))
  }

  /** Person data generators. */
  object person {
    private val MaleFirstNames =
      Vector("Ivan", "Alexey", "Dmitry", "Sergey", "Nicolas", "John", "Pedro", "Lucas", "Martin", "Andres")
    private val FemaleFirstNames =
      Vector("Anna", "Maria", "Elena", "Sofia", "Camila", "Julia", "Lucia", "Valentina", "Fernanda", "Olga")
    private val LastNames =
      Vector("Ivanov", "Petrov", "Sidorov", "Garcia", "Silva", "Smith", "Brown", "Martinez", "Fernandez", "Volkov")
    private val JobTitles =
      Vector(
        "Performance Engineer",
        "QA Engineer",
        "Backend Developer",
        "SRE",
        "Product Analyst",
        "Data Engineer",
        "Security Engineer",
      )
    private val Prefixes = Vector("Mr.", "Mrs.", "Ms.", "Dr.")

    def gender(): Generator[Gender] =
      oneOf(Gender.Male, Gender.Female, Gender.Unspecified)

    def firstName(gender: Gender = Gender.Unspecified): Generator[String] = gender match {
      case Gender.Male        => oneOf(MaleFirstNames)
      case Gender.Female      => oneOf(FemaleFirstNames)
      case Gender.Unspecified => oneOf(MaleFirstNames ++ FemaleFirstNames)
    }

    def lastName(): Generator[String] =
      oneOf(LastNames)

    def prefix(): Generator[String] =
      oneOf(Prefixes)

    def fullName(gender: Gender = Gender.Unspecified): Generator[String] =
      for {
        first <- firstName(gender)
        last  <- lastName()
      } yield s"$first $last"

    def jobTitle(): Generator[String] =
      oneOf(JobTitles)

    def companyEmailName(): Generator[String] =
      fullName().map(_.toLowerCase.replaceAll("[^a-z0-9]+", ".").stripPrefix(".").stripSuffix("."))
  }

  /** Internet-oriented generators. */
  object internet {
    private val Domains = Vector("example.com", "test.local", "load.test", "picatinny.dev")
    private val UserAgents = Vector(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) AppleWebKit/605.1.15 Version/17.4 Safari/605.1.15",
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    )

    def domain(): Generator[String] =
      oneOf(Domains)

    def email(domain: String = "example.com"): Generator[String] =
      for {
        name <- person.fullName()
        suffix <- string.alphanumeric(6)
      } yield emailFromName(name, domain, suffix)

    def email(name: String, domain: String): Generator[String] =
      string.alphanumeric(6).map(emailFromName(name, domain, _))

    def username(): Generator[String] =
      person.fullName().map(_.toLowerCase.replace(' ', '.'))

    def url(protocol: String = "https"): Generator[String] =
      for {
        host <- domain()
        path <- string.alphanumeric(12)
      } yield s"$protocol://$host/$path"

    def password(length: Int = 16): Generator[String] =
      string.fromAlphabet("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*", length)

    def userAgent(): Generator[String] =
      oneOf(UserAgents)

    def ipv4(): Generator[String] =
      (number.int(1, 254), number.int(0, 255), number.int(0, 255), number.int(1, 254)).mapN { (a, b, c, d) =>
        s"$a.$b.$c.$d"
      }

    def ipv6(): Generator[String] =
      Generator.delay(Vector.fill(8)(RandomDataGenerators.hexString(4)).mkString(":"))

    private def emailFromName(name: String, domain: String, suffix: String): String = {
      require(domain.nonEmpty, "Email domain must be non-empty")
      val localPart = name.toLowerCase.replaceAll("[^a-z0-9]+", ".").stripPrefix(".").stripSuffix(".")
      val normalizedLocalPart = if (localPart.isEmpty) "user" else localPart
      s"$normalizedLocalPart.$suffix@$domain"
    }
  }

  /** Location and address generators for common payload fields. */
  object location {
    private val Cities: Map[Country, Vector[String]] = Map(
      Country.RU -> Vector("Moscow", "Saint Petersburg", "Kazan", "Novosibirsk"),
      Country.AR -> Vector("Buenos Aires", "Cordoba", "Rosario", "Mendoza"),
      Country.BR -> Vector("Sao Paulo", "Rio de Janeiro", "Brasilia", "Curitiba"),
      Country.US -> Vector("New York", "Austin", "Seattle", "Chicago"),
      Country.DE -> Vector("Berlin", "Munich", "Hamburg", "Frankfurt"),
    )
    private val Streets = Vector("Main Street", "Performance Avenue", "Load Test Road", "Central Boulevard", "Liberty Street")

    def country(): Generator[Country] =
      oneOf(Country.RU, Country.AR, Country.BR, Country.US, Country.GB, Country.DE, Country.FR, Country.ES, Country.IT, Country.AE)

    def countryCode(): Generator[String] =
      country().map(_.iso2)

    def city(country: Country = Country.US): Generator[String] =
      oneOf(Cities.getOrElse(country, Cities(Country.US)))

    def streetName(): Generator[String] =
      oneOf(Streets)

    def streetAddress(country: Country = Country.US): Generator[String] =
      for {
        number <- number.int(1, 9999)
        street <- streetName()
      } yield s"$number $street"

    def postalCode(country: Country = Country.US): Generator[String] = country match {
      case Country.RU => string.numeric(6)
      case Country.AR => string.numeric(4)
      case Country.BR => string.numeric(8)
      case Country.US => string.numeric(5)
      case Country.DE => string.numeric(5)
      case _          => string.alphanumeric(6)
    }

    def latitude(): Generator[Double] =
      number.double(-90.0, 90.0)

    def longitude(): Generator[Double] =
      number.double(-180.0, 180.0)
  }

  /** Localization helpers for locale-sensitive test data choices. */
  object localization {
    def country(): Generator[Country] =
      location.country()

    def currency(country: Country): Generator[String] = country match {
      case Country.RU => Generator.const("RUB")
      case Country.AR => Generator.const("ARS")
      case Country.BR => Generator.const("BRL")
      case Country.GB => Generator.const("GBP")
      case Country.AE => Generator.const("AED")
      case Country.DE | Country.FR | Country.ES | Country.IT => Generator.const("EUR")
      case _ => Generator.const("USD")
    }

    def languageCode(country: Country): Generator[String] = country match {
      case Country.RU => Generator.const("ru")
      case Country.AR => Generator.const("es")
      case Country.BR => Generator.const("pt")
      case Country.DE => Generator.const("de")
      case Country.FR => Generator.const("fr")
      case Country.ES => Generator.const("es")
      case Country.IT => Generator.const("it")
      case _ => Generator.const("en")
    }
  }

  /** Date and time generators. */
  object date {
    def today(zone: ZoneId = ZoneId.systemDefault()): Generator[LocalDate] =
      Generator.delay(LocalDate.now(zone))

    def now(zone: ZoneId = ZoneId.systemDefault()): Generator[LocalDateTime] =
      Generator.delay(LocalDateTime.now(zone))

    def between(from: LocalDate, to: LocalDate): Generator[LocalDate] = {
      require(!from.isAfter(to), s"from must be <= to: $from > $to")
      val days = ChronoUnit.DAYS.between(from, to)
      number.long(0L, days).map(from.plusDays)
    }

    def between(from: LocalDateTime, to: LocalDateTime): Generator[LocalDateTime] = {
      require(!from.isAfter(to), s"from must be <= to: $from > $to")
      val seconds = ChronoUnit.SECONDS.between(from, to)
      number.long(0L, seconds).map(from.plusSeconds)
    }

    def past(days: Long, from: LocalDate = LocalDate.now()): Generator[LocalDate] = {
      require(days >= 0, s"days must be >= 0: $days")
      between(from.minusDays(days), from)
    }

    def future(days: Long, from: LocalDate = LocalDate.now()): Generator[LocalDate] = {
      require(days >= 0, s"days must be >= 0: $days")
      between(from, from.plusDays(days))
    }

    def offset(from: LocalDate, minDays: Long, maxDays: Long): Generator[LocalDate] = {
      require(minDays <= maxDays, s"minDays must be <= maxDays: $minDays > $maxDays")
      number.long(minDays, maxDays).map(from.plusDays)
    }

    def range(
        from: LocalDate,
        to: LocalDate,
        minLengthDays: Long = 0L,
        maxLengthDays: Long = 30L,
    ): Generator[(LocalDate, LocalDate)] = {
      require(minLengthDays >= 0, s"minLengthDays must be >= 0: $minLengthDays")
      require(minLengthDays <= maxLengthDays, s"minLengthDays must be <= maxLengthDays")
      for {
        start  <- between(from, to)
        length <- number.long(minLengthDays, maxLengthDays)
      } yield {
        val end = start.plusDays(length)
        start -> (if (end.isAfter(to)) to else end)
      }
    }

    def formatDate(localDate: Generator[LocalDate], pattern: String): Generator[String] = {
      val formatter = DateTimeFormatter.ofPattern(pattern)
      localDate.map(_.format(formatter))
    }

    def formatDateTime(localDateTime: Generator[LocalDateTime], pattern: String): Generator[String] = {
      val formatter = DateTimeFormatter.ofPattern(pattern)
      localDateTime.map(_.format(formatter))
    }
  }

  /** Finance and commerce-friendly generators. */
  object finance {
    def pan(bins: String*): Generator[String] =
      Generator.delay(RandomDataGenerators.randomPAN(bins: _*))

    def amount(min: BigDecimal, max: BigDecimal, scale: Int = 2): Generator[BigDecimal] = {
      require(min <= max, s"min must be <= max: $min > $max")
      number.double(min.toDouble, max.toDouble).map(value => BigDecimal(value).setScale(scale, RoundingMode.HALF_UP))
    }

    def money(min: BigDecimal, max: BigDecimal, currency: String = "USD"): Generator[Money] =
      amount(min, max).map(Money(_, currency))

    def currency(): Generator[String] =
      oneOf("USD", "EUR", "RUB", "BRL", "ARS", "GBP", "AED")

    def accountNumber(length: Int = 20): Generator[String] =
      string.numeric(length)

    def bic(): Generator[String] =
      for {
        bank <- string.alphabetic(4).map(_.toUpperCase)
        country <- location.countryCode()
        locationCode <- string.alphanumeric(2).map(_.toUpperCase)
        branch <- string.alphanumeric(3).map(_.toUpperCase)
      } yield s"$bank$country$locationCode$branch"

    def iban(country: Country = Country.DE): Generator[String] = country match {
      case Country.DE => string.numeric(18).map(value => s"DE89$value")
      case Country.GB => string.alphanumeric(4).map(_.toUpperCase).zip(string.numeric(14)).map { case (bank, digits) => s"GB82$bank$digits" }
      case Country.FR => string.numeric(23).map(value => s"FR14$value")
      case _          => string.alphanumeric(20).map(value => s"${country.iso2}00${value.toUpperCase}")
    }

    def transactionId(prefix: String = "txn"): Generator[String] =
      string.alphanumeric(18).map(value => s"$prefix-$value")
  }

  /** Commerce generators for request bodies and order scenarios. */
  object commerce {
    private val Products   = Vector("Laptop", "Phone", "Subscription", "Support package", "Gift card")
    private val Categories = Vector("electronics", "services", "finance", "books", "travel")

    def productName(): Generator[String] = oneOf(Products)
    def category(): Generator[String]    = oneOf(Categories)
    def sku(prefix: String = "SKU"): Generator[String] =
      string.alphanumeric(10).map(value => s"$prefix-$value")
    def orderId(prefix: String = "ord"): Generator[String] =
      string.alphanumeric(16).map(value => s"$prefix-$value")
    def price(min: BigDecimal = BigDecimal(1), max: BigDecimal = BigDecimal(1000)): Generator[Money] =
      finance.money(min, max, "USD")
  }

  /** Phone generators. Country metadata is intentionally compact and will be expanded. */
  object phone {
    def mobile(country: Country, format: PhoneFormatMode = PhoneFormatMode.E164): Generator[String] =
      Generator.delay(RandomPhoneGenerator.randomPhone(countryFormats(country), toLegacyPhoneType(format)))

    def tollFree(country: Country = Country.US): Generator[String] =
      Generator.delay(RandomPhoneGenerator.randomPhone(countryFormats(country), TypePhone.TollFreePhoneNumber))

    def fromFormats(format: PhoneFormatMode, formats: PhoneFormat*): Generator[String] =
      Generator.delay(RandomPhoneGenerator.randomPhone(formats, toLegacyPhoneType(format)))

    private def toLegacyPhoneType(format: PhoneFormatMode): TypePhone.TypePhone = format match {
      case PhoneFormatMode.E164          => TypePhone.E164PhoneNumber
      case PhoneFormatMode.National      => TypePhone.PhoneNumber
      case PhoneFormatMode.International => TypePhone.PhoneNumber
      case PhoneFormatMode.TollFree      => TypePhone.TollFreePhoneNumber
    }

    private def countryFormats(country: Country): Seq[PhoneFormat] = country match {
      case Country.RU => Seq(PhoneFormat("+7", 10, Seq("903", "906", "908", "926", "999"), "+X XXX XXX-XX-XX"))
      case Country.AR => Seq(PhoneFormat("+54", 10, Seq("11", "221", "351", "261"), "+XX XXX XXX-XXXX"))
      case Country.BR => Seq(PhoneFormat("+55", 11, Seq("11", "21", "31", "41"), "+XX XX XXXXX-XXXX"))
      case Country.GB => Seq(PhoneFormat("+44", 10, Seq("7400", "7500", "7700"), "+XX XXXX XXXXXX"))
      case Country.DE => Seq(PhoneFormat("+49", 10, Seq("151", "152", "160", "170"), "+XX XXX XXXXXXX"))
      case Country.FR => Seq(PhoneFormat("+33", 9, Seq("6", "7"), "+XX X XX XX XX XX"))
      case Country.ES => Seq(PhoneFormat("+34", 9, Seq("6", "7"), "+XX XXX XXX XXX"))
      case Country.IT => Seq(PhoneFormat("+39", 10, Seq("320", "328", "333"), "+XX XXX XXX XXXX"))
      case Country.AE => Seq(PhoneFormat("+971", 9, Seq("50", "52", "54", "55"), "+XXX XX XXX XXXX"))
      case _          => Seq.empty
    }
  }

  /** Passport generators. */
  object passport {
    def ru(): Generator[String] =
      Generator.delay(RandomDataGenerators.randomRusPassport())

    def number(country: Country): Generator[String] = country match {
      case Country.RU => ru()
      case Country.AR => string.alphanumeric(9).map(_.toUpperCase)
      case Country.BR => string.alphanumeric(8).map(_.toUpperCase)
      case Country.US => string.numeric(9)
      case _          => string.alphanumeric(9).map(_.toUpperCase)
    }
  }

  /** Russian government and finance identifiers. */
  object ru {
    object inn {
      def person(): Generator[String]  = Generator.delay(RandomDataGenerators.randomNatITN())
      def company(): Generator[String] = Generator.delay(RandomDataGenerators.randomJurITN())
    }

    def kpp(): Generator[String]    = Generator.delay(RandomDataGenerators.randomKPP())
    def ogrn(): Generator[String]   = Generator.delay(RandomDataGenerators.randomOGRN())
    def ogrnip(): Generator[String] = Generator.delay(RandomDataGenerators.randomPSRNSP())
    def snils(): Generator[String]  = Generator.delay(RandomDataGenerators.randomSNILS())
  }

  /** Brazilian identifiers. */
  object br {
    def cpf(formatted: Boolean = false): Generator[String] =
      Generator.delay {
        val base   = Vector.fill(9)(ThreadLocalRandom.current().nextInt(10))
        val first  = cpfDigit(base, 10)
        val second = cpfDigit(base :+ first, 11)
        val raw    = (base :+ first :+ second).mkString
        if (formatted) raw.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4") else raw
      }

    private def cpfDigit(digits: Seq[Int], startWeight: Int): Int = {
      val sum = digits.zip(startWeight to 2 by -1).map { case (digit, weight) => digit * weight }.sum
      val mod = (sum * 10) % 11
      if (mod == 10) 0 else mod
    }
  }

  /** Argentinian identifiers. */
  object ar {
    def dni(formatted: Boolean = false): Generator[String] =
      number.int(10_000_000, 99_999_999).map { value =>
        val raw = value.toString
        if (formatted) raw.replaceFirst("(\\d{2})(\\d{3})(\\d{3})", "$1.$2.$3") else raw
      }
  }

  /** Weather values for synthetic telemetry and environment-like payloads. */
  object weather {
    private val Conditions = Vector("clear", "cloudy", "rain", "storm", "snow", "fog", "wind")

    def condition(): Generator[String] =
      oneOf(Conditions)

    def temperatureCelsius(min: Double = -30.0, max: Double = 45.0): Generator[Double] =
      number.double(min, max).map(value => BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble)

    def humidityPercent(): Generator[Int] =
      number.int(0, 100)

    def pressureHPa(): Generator[Int] =
      number.int(950, 1050)
  }

  /** Lorem ipsum generators for payload text. */
  object lorem {
    private val Words = Vector(
      "lorem",
      "ipsum",
      "dolor",
      "sit",
      "amet",
      "consectetur",
      "adipiscing",
      "elit",
      "sed",
      "do",
      "eiusmod",
      "tempor",
    )

    def word(): Generator[String] =
      oneOf(Words)

    def words(count: Int): Generator[String] = {
      require(count > 0, s"count must be > 0: $count")
      Generator.delay(Vector.fill(count)(word().sample()).mkString(" "))
    }

    def sentence(wordsCount: Int = 8): Generator[String] =
      words(wordsCount).map(text => text.capitalize + ".")
  }

  /** Picks one item from a non-empty sequence. */
  def oneOf[A](items: Seq[A]): Generator[A] = {
    require(items.nonEmpty, "oneOf requires at least one item")
    Generator.delay(items(ThreadLocalRandom.current().nextInt(items.size)))
  }

  /** Picks one item from a non-empty varargs list. */
  def oneOf[A](first: A, second: A, rest: A*): Generator[A] =
    oneOf(first +: second +: rest)

  implicit final class GeneratorTuple4Ops[A, B, C, D](private val tuple: (Generator[A], Generator[B], Generator[C], Generator[D]))
      extends AnyVal {
    def mapN[E](f: (A, B, C, D) => E): Generator[E] =
      for {
        a <- tuple._1
        b <- tuple._2
        c <- tuple._3
        d <- tuple._4
      } yield f(a, b, c, d)
  }
}
