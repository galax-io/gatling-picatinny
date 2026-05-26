package org.galaxio.gatling.feeders.faker

import com.mifmif.common.regex.Generex
import org.galaxio.gatling.utils.{RandomDataGenerators, RandomPhoneGenerator}
import org.galaxio.gatling.utils.phone.{PhoneFormat, TypePhone}

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import scala.annotation.tailrec
import scala.math.BigDecimal.RoundingMode

/** Faker facade for generating load-test data.
  *
  * The API is intentionally domain-oriented: use `Faker.internet.email()` or `Faker.ru.inn.person()` in scenarios, then pass
  * generators into `GeneratedFeeder` when Gatling needs session records.
  */
object Faker {

  /** UUID generators. */
  object uuid {
    def value: Generator[UUID]    = Generator.delay(UUID.randomUUID())
    def string: Generator[String] = value.map(_.toString)
  }

  /** Primitive and JVM-friendly number generators. */
  object number {
    def int(min: Int, max: Int): Generator[Int] = {
      require(min <= max, s"min must be <= max: $min > $max")
      Generator.delay(if (min == max) min else ThreadLocalRandom.current().nextLong(min.toLong, max.toLong + 1L).toInt)
    }

    def long(min: Long, max: Long): Generator[Long] = {
      require(min <= max, s"min must be <= max: $min > $max")
      Generator.delay(nextLongInclusive(min, max))
    }

    /** Generates a double in [min, max). Note: max is exclusive per JDK ThreadLocalRandom contract. */
    def double(min: Double, max: Double): Generator[Double] = {
      require(min <= max, s"min must be <= max: $min > $max")
      Generator.delay(if (min == max) min else ThreadLocalRandom.current().nextDouble(min, max))
    }

    def float(min: Float, max: Float): Generator[Float] =
      double(min.toDouble, max.toDouble).map(_.toFloat)

    def byte(min: Byte = Byte.MinValue, max: Byte = Byte.MaxValue): Generator[Byte] =
      int(min.toInt, max.toInt).map(_.toByte)

    def short(min: Short = Short.MinValue, max: Short = Short.MaxValue): Generator[Short] =
      int(min.toInt, max.toInt).map(_.toShort)

    def char(min: Char = 'A', max: Char = 'z'): Generator[Char] =
      int(min.toInt, max.toInt).map(_.toChar)

    def bigInt(min: BigInt, max: BigInt): Generator[BigInt] = {
      require(min <= max, s"min must be <= max: $min > $max")
      val range = max - min + 1
      Generator.delay(min + randomBigInt(range))
    }

    def bigDecimal(min: BigDecimal, max: BigDecimal, scale: Int = 2): Generator[BigDecimal] = {
      require(scale >= 0, s"scale must be >= 0: $scale")
      require(min <= max, s"min must be <= max: $min > $max")
      val factor   = BigDecimal(10).pow(scale)
      val minUnits = (min.setScale(scale, RoundingMode.CEILING) * factor).toBigInt
      val maxUnits = (max.setScale(scale, RoundingMode.FLOOR) * factor).toBigInt
      require(minUnits <= maxUnits, s"range has no values at scale $scale: $min..$max")
      bigInt(minUnits, maxUnits).map(units => (BigDecimal(units) / factor).setScale(scale))
    }

    def boolean: Generator[Boolean] =
      Generator.delay(ThreadLocalRandom.current().nextBoolean())

    def positiveInt: Generator[Int]   = int(1, Int.MaxValue)
    def positiveLong: Generator[Long] = long(1L, Long.MaxValue)
    def negativeInt: Generator[Int]   = int(Int.MinValue, -1)
    def negativeLong: Generator[Long] = long(Long.MinValue, -1L)
    def percentage: Generator[Int]    = int(0, 100)

    private def nextLongInclusive(min: Long, max: Long): Long =
      if (min == max) min
      else if (BigInt(max) - BigInt(min) + 1 <= BigInt(Long.MaxValue)) {
        val bound = (BigInt(max) - BigInt(min) + 1).toLong
        min + ThreadLocalRandom.current().nextLong(bound)
      } else if (min == Long.MinValue) ThreadLocalRandom.current().nextLong()
      else {
        @tailrec
        def retry(): Long = {
          val value = ThreadLocalRandom.current().nextLong()
          if (value >= min) value else retry()
        }
        retry()
      }

    private def randomBigInt(exclusiveUpperBound: BigInt): BigInt = {
      require(exclusiveUpperBound > 0, s"exclusiveUpperBound must be > 0: $exclusiveUpperBound")
      val bytes = new Array[Byte]((exclusiveUpperBound.bitLength + 7) / 8)

      @tailrec
      def retry(): BigInt = {
        ThreadLocalRandom.current().nextBytes(bytes)
        val candidate = BigInt(1, bytes)
        if (candidate < exclusiveUpperBound) candidate else retry()
      }

      retry()
    }
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

    /** Generates strings matching a finite regex pattern.
      *
      * This is the generator-based replacement for the legacy `RegexFeeder`. Keep the returned generator in a `val` and reuse
      * it; each generator holds a thread-local automaton instance.
      *
      * The pattern must be compatible with `Generex`/dk.brics.automaton syntax. Lookarounds, backreferences, and other
      * unsupported regex features are rejected.
      */
    def matching(pattern: String): Generator[String] =
      RegexSampler.generator(pattern)
  }

  private object RegexSampler {
    def generator(pattern: String): Generator[String] = {
      val normalizedPattern =
        Option(pattern).getOrElse(throw new IllegalArgumentException("Regex pattern must be non-null"))
      require(normalizedPattern.nonEmpty, "Regex pattern must be non-empty")
      require(Generex.isValidPattern(normalizedPattern), s"Invalid regex pattern: $normalizedPattern")

      val perThread = ThreadLocal.withInitial(() => new Generex(normalizedPattern))
      Generator.delay(perThread.get().random())
    }
  }

  /** Person data generators. */
  object person {
    def gender(): Generator[Gender] =
      oneOf(Gender.Male, Gender.Female, Gender.Unspecified)

    def firstName(gender: Gender = Gender.Unspecified): Generator[String] = gender match {
      case Gender.Male        => oneOf(FakerData.maleFirstNames)
      case Gender.Female      => oneOf(FakerData.femaleFirstNames)
      case Gender.Unspecified => oneOf(FakerData.maleFirstNames ++ FakerData.femaleFirstNames)
    }

    def lastName(): Generator[String] =
      oneOf(FakerData.lastNames)

    def prefix(): Generator[String] =
      oneOf(FakerData.personPrefixes)

    def fullName(gender: Gender = Gender.Unspecified): Generator[String] =
      for {
        first <- firstName(gender)
        last  <- lastName()
      } yield s"$first $last"

    def jobTitle(): Generator[String] =
      oneOf(FakerData.jobTitles)

    def companyEmailName(): Generator[String] =
      fullName().map(_.toLowerCase.replaceAll("[^a-z0-9]+", ".").stripPrefix(".").stripSuffix("."))
  }

  /** Internet-oriented generators. */
  object internet {
    def domain(): Generator[String] =
      oneOf(FakerData.domains)

    def email(domain: String = "example.com"): Generator[String] =
      for {
        name   <- person.fullName()
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
      oneOf(FakerData.userAgents)

    def ipv4(): Generator[String] =
      (number.int(1, 254), number.int(0, 255), number.int(0, 255), number.int(1, 254)).mapN { (a, b, c, d) =>
        s"$a.$b.$c.$d"
      }

    def ipv6(): Generator[String] =
      Generator.delay(Vector.fill(8)(RandomDataGenerators.hexString(4)).mkString(":"))

    private def emailFromName(name: String, domain: String, suffix: String): String = {
      require(domain.nonEmpty, "Email domain must be non-empty")
      val localPart           = name.toLowerCase.replaceAll("[^a-z0-9]+", ".").stripPrefix(".").stripSuffix(".")
      val normalizedLocalPart = if (localPart.isEmpty) "user" else localPart
      s"$normalizedLocalPart.$suffix@$domain"
    }
  }

  /** Location and address generators for common payload fields. */
  object location {
    def country(): Generator[Country] =
      oneOf(
        Country.RU,
        Country.AR,
        Country.BR,
        Country.US,
        Country.GB,
        Country.DE,
        Country.FR,
        Country.ES,
        Country.IT,
        Country.AE,
        Country.JP,
        Country.CN,
        Country.IN,
        Country.CA,
        Country.AU,
        Country.MX,
      )

    def countryCode(): Generator[String] =
      country().map(_.iso2)

    def city(country: Country = Country.US): Generator[String] =
      oneOf(FakerData.citiesByCountry.getOrElse(country, FakerData.citiesByCountry(Country.US)))

    def streetName(): Generator[String] =
      oneOf(FakerData.streetNames)

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
      case Country.JP => string.numeric(7)
      case Country.CN => string.numeric(6)
      case Country.IN => string.numeric(6)
      case Country.CA => string.alphanumeric(6).map(_.toUpperCase)
      case Country.AU => string.numeric(4)
      case Country.MX => string.numeric(5)
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
      case Country.RU                                        => Generator.const("RUB")
      case Country.AR                                        => Generator.const("ARS")
      case Country.BR                                        => Generator.const("BRL")
      case Country.GB                                        => Generator.const("GBP")
      case Country.AE                                        => Generator.const("AED")
      case Country.JP                                        => Generator.const("JPY")
      case Country.CN                                        => Generator.const("CNY")
      case Country.IN                                        => Generator.const("INR")
      case Country.CA                                        => Generator.const("CAD")
      case Country.AU                                        => Generator.const("AUD")
      case Country.MX                                        => Generator.const("MXN")
      case Country.DE | Country.FR | Country.ES | Country.IT => Generator.const("EUR")
      case _                                                 => Generator.const("USD")
    }

    def languageCode(country: Country): Generator[String] = country match {
      case Country.RU => Generator.const("ru")
      case Country.AR => Generator.const("es")
      case Country.BR => Generator.const("pt")
      case Country.DE => Generator.const("de")
      case Country.FR => Generator.const("fr")
      case Country.ES => Generator.const("es")
      case Country.IT => Generator.const("it")
      case Country.JP => Generator.const("ja")
      case Country.CN => Generator.const("zh")
      case Country.IN => Generator.const("hi")
      case Country.MX => Generator.const("es")
      case _          => Generator.const("en")
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
      require(!from.isAfter(to), s"from must be <= to: $from > $to")
      val totalDays   = ChronoUnit.DAYS.between(from, to)
      require(
        minLengthDays <= totalDays,
        s"minLengthDays must fit inside the date range: $minLengthDays > $totalDays",
      )
      val latestStart = to.minusDays(minLengthDays)
      for {
        start       <- between(from, latestStart)
        maxEndLength = math.min(maxLengthDays, ChronoUnit.DAYS.between(start, to))
        length      <- number.long(minLengthDays, maxEndLength)
      } yield {
        start -> start.plusDays(length)
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
      number.bigDecimal(min, max, scale)
    }

    def money(min: BigDecimal, max: BigDecimal, currency: String = "USD"): Generator[Money] =
      amount(min, max).map(Money(_, currency))

    def currency(): Generator[String] =
      oneOf(FakerData.currencies)

    def accountNumber(length: Int = 20): Generator[String] =
      string.numeric(length)

    def bic(): Generator[String] =
      for {
        bank         <- string.alphabetic(4).map(_.toUpperCase)
        country      <- location.countryCode()
        locationCode <- string.alphanumeric(2).map(_.toUpperCase)
        branch       <- string.alphanumeric(3).map(_.toUpperCase)
      } yield s"$bank$country$locationCode$branch"

    /** Generates IBAN-like strings. Check digits are hardcoded placeholders — values will not pass strict ISO 7064 validation
      * but are structurally correct for load-test payloads.
      */
    def iban(country: Country = Country.DE): Generator[String] = country match {
      case Country.DE => string.numeric(18).map(value => s"DE89$value")
      case Country.GB =>
        string.alphanumeric(4).map(_.toUpperCase).zip(string.numeric(14)).map { case (bank, digits) => s"GB82$bank$digits" }
      case Country.FR => string.numeric(23).map(value => s"FR14$value")
      case Country.ES => string.numeric(20).map(value => s"ES91$value")
      case Country.IT =>
        string.alphabetic(1).map(_.toUpperCase).zip(string.numeric(22)).map { case (check, digits) => s"IT60$check$digits" }
      case Country.RU => string.numeric(29).map(value => s"RU33$value")
      case Country.BR => string.numeric(23).map(value => s"BR18$value")
      case _          => string.alphanumeric(20).map(value => s"${country.iso2}00${value.toUpperCase}")
    }

    def transactionId(prefix: String = "txn"): Generator[String] =
      string.alphanumeric(18).map(value => s"$prefix-$value")
  }

  /** Commerce generators for request bodies and order scenarios. */
  object commerce {
    def productName(): Generator[String]                                                             = oneOf(FakerData.products)
    def category(): Generator[String]                                                                = oneOf(FakerData.categories)
    def sku(prefix: String = "SKU"): Generator[String]                                               =
      string.alphanumeric(10).map(value => s"$prefix-$value")
    def orderId(prefix: String = "ord"): Generator[String]                                           =
      string.alphanumeric(16).map(value => s"$prefix-$value")
    def price(min: BigDecimal = BigDecimal(1), max: BigDecimal = BigDecimal(1000)): Generator[Money] =
      finance.money(min, max, "USD")
  }

  /** Phone generators with configurable formatting for all supported countries.
    *
    * Supports 16 countries out of the box. Custom formats can be provided via `fromFormats` or `builder`.
    */
  object phone {
    def mobile(country: Country, format: PhoneFormatMode = PhoneFormatMode.E164): Generator[String] =
      Generator.delay(RandomPhoneGenerator.randomPhone(countryFormats(country), toLegacyPhoneType(format)))

    def tollFree(country: Country = Country.US): Generator[String] =
      Generator.delay(RandomPhoneGenerator.randomPhone(countryFormats(country), TypePhone.TollFreePhoneNumber))

    def fromFormats(format: PhoneFormatMode, formats: PhoneFormat*): Generator[String] = {
      require(formats.nonEmpty, "At least one phone format must be provided")
      Generator.delay(RandomPhoneGenerator.randomPhone(formats, toLegacyPhoneType(format)))
    }

    /** Fluent builder for custom phone number configuration. */
    def builder: PhoneBuilder = PhoneBuilder()

    private def toLegacyPhoneType(format: PhoneFormatMode): TypePhone.TypePhone = format match {
      case PhoneFormatMode.E164          => TypePhone.E164PhoneNumber
      case PhoneFormatMode.National      => TypePhone.PhoneNumber
      case PhoneFormatMode.International => TypePhone.PhoneNumber
      case PhoneFormatMode.TollFree      => TypePhone.TollFreePhoneNumber
    }

    private[faker] def countryFormats(country: Country): Seq[PhoneFormat] = {
      val formats = FakerData.phoneFormatsByCountry.getOrElse(country, Seq.empty)
      require(formats.nonEmpty, s"No phone formats configured for country ${country.iso2}")
      formats
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

  /** Spanish identifiers. */
  object es {

    /** Generates a NIF (Número de Identificación Fiscal) with a valid check letter.
      *
      * Format: 8 digits + 1 letter. The letter is computed as `digits % 23` mapped to the standard NIF letter table.
      */
    def nif(): Generator[String] =
      Generator.delay {
        val digits  = ThreadLocalRandom.current().nextInt(10000000, 100000000)
        val letters = "TRWAGMYFPDXBNJZSQVHLCKE"
        f"$digits%08d${letters.charAt(digits % 23)}"
      }
  }

  /** Italian identifiers. */
  object it {

    /** Generates a structurally valid Codice Fiscale (16 alphanumeric characters).
      *
      * The generated value follows the correct format (6 letters + 2 digits + 1 letter + 2 digits + 1 letter + 3 digits + 1
      * letter) but uses random data rather than real name/date derivation. Suitable for load-test payloads where format matters
      * more than semantic correctness.
      */
    def codiceFiscale(): Generator[String] =
      for {
        surname  <- string.alphabetic(3).map(_.toUpperCase)
        name     <- string.alphabetic(3).map(_.toUpperCase)
        year     <- string.numeric(2)
        month    <- oneOf("A", "B", "C", "D", "E", "H", "L", "M", "P", "R", "S", "T")
        day      <- number.int(1, 71).map(d => f"$d%02d")
        town     <- string.alphabetic(1).map(_.toUpperCase)
        townCode <- string.numeric(3)
        check    <- string.alphabetic(1).map(_.toUpperCase)
      } yield s"$surname$name$year$month$day$town$townCode$check"
  }

  /** German identifiers. */
  object de {

    /** Generates a Steuerliche Identifikationsnummer (11 digits). Uses a simplified generation that produces structurally
      * valid-looking values — real TIN validation is more complex and not needed for load tests.
      */
    def steueridentifikationsnummer(): Generator[String] =
      Generator.delay {
        val first = ThreadLocalRandom.current().nextInt(1, 10)
        val rest  = (1 to 10).map(_ => ThreadLocalRandom.current().nextInt(0, 10))
        (first +: rest).mkString
      }
  }

  /** United States identifiers. */
  object us {

    /** Generates a Social Security Number (SSN) in format XXX-XX-XXXX. Area numbers 000, 666, and 900-999 are excluded. */
    def ssn(formatted: Boolean = true): Generator[String] =
      Generator.delay {
        val area   = {
          var a = 0
          while (a == 0 || a == 666 || a >= 900) a = ThreadLocalRandom.current().nextInt(1, 1000)
          a
        }
        val group  = ThreadLocalRandom.current().nextInt(1, 100)
        val serial = ThreadLocalRandom.current().nextInt(1, 10000)
        if (formatted) f"$area%03d-$group%02d-$serial%04d" else f"$area%03d$group%02d$serial%04d"
      }
  }

  /** United Kingdom identifiers. */
  object gb {

    /** Generates a National Insurance Number (NINO) in format AB123456C.
      *
      * Excludes disallowed prefix combinations (BG, GB, NK, KN, TN, NT, ZZ) and invalid first letters (D, F, I, Q, U, V).
      */
    def nino(): Generator[String] =
      Generator.delay {
        val disallowed    = Set("BG", "GB", "NK", "KN", "TN", "NT", "ZZ")
        val invalidFirst  = "DFIQUV"
        val invalidSecond = "DFIQUVO"
        val letters       = ('A' to 'Z').filterNot(ch => invalidFirst.contains(ch))
        val secondLetters = ('A' to 'Z').filterNot(ch => invalidSecond.contains(ch))
        var prefix        = ""
        do {
          val first  = letters(ThreadLocalRandom.current().nextInt(letters.size))
          val second = secondLetters(ThreadLocalRandom.current().nextInt(secondLetters.size))
          prefix = s"$first$second"
        } while (disallowed.contains(prefix))
        val digits        = (1 to 6).map(_ => ThreadLocalRandom.current().nextInt(10)).mkString
        val suffix        = ('A' to 'D')(ThreadLocalRandom.current().nextInt(4))
        s"$prefix$digits$suffix"
      }
  }

  /** French identifiers. */
  object fr {

    /** Generates a NIR (Numéro d'Inscription au Répertoire) — French social security number.
      *
      * Format: 1 digit sex + 2 digit year + 2 digit month + 5 digit department/commune + 3 digit order + 2 digit key. Key is
      * computed as 97 - (first 13 digits mod 97).
      */
    def nir(): Generator[String] =
      Generator.delay {
        val sex        = ThreadLocalRandom.current().nextInt(1, 3)
        val year       = ThreadLocalRandom.current().nextInt(0, 100)
        val month      = ThreadLocalRandom.current().nextInt(1, 13)
        val department = ThreadLocalRandom.current().nextInt(1, 96)
        val commune    = ThreadLocalRandom.current().nextInt(1, 1000)
        val order      = ThreadLocalRandom.current().nextInt(1, 1000)
        val base       = f"$sex%01d$year%02d$month%02d$department%02d$commune%03d$order%03d"
        val key        = 97 - (base.toLong % 97)
        f"$base$key%02d"
      }
  }

  /** Weather values for synthetic telemetry and environment-like payloads. */
  object weather {
    def condition(): Generator[String] =
      oneOf(FakerData.weatherConditions)

    def temperatureCelsius(min: Double = -30.0, max: Double = 45.0): Generator[Double] =
      number.double(min, max).map(value => BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble)

    def humidityPercent(): Generator[Int] =
      number.int(0, 100)

    def pressureHPa(): Generator[Int] =
      number.int(950, 1050)
  }

  /** Lorem ipsum generators for payload text. */
  object lorem {
    def word(): Generator[String] =
      oneOf(FakerData.loremWords)

    def words(count: Int): Generator[String] = {
      require(count > 0, s"count must be > 0: $count")
      val data = FakerData.loremWords
      Generator.delay(Vector.fill(count)(data(ThreadLocalRandom.current().nextInt(data.size))).mkString(" "))
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

  implicit final class GeneratorTuple4Ops[A, B, C, D](
      private val tuple: (Generator[A], Generator[B], Generator[C], Generator[D]),
  ) extends AnyVal {
    def mapN[E](f: (A, B, C, D) => E): Generator[E] =
      for {
        a <- tuple._1
        b <- tuple._2
        c <- tuple._3
        d <- tuple._4
      } yield f(a, b, c, d)
  }
}

private object RegexSampler {

  def generator(pattern: String): Generator[String] = {
    val normalizedPattern =
      Option(pattern).map(_.trim).getOrElse(throw new IllegalArgumentException("Regex pattern must be non-null"))
    require(normalizedPattern.nonEmpty, "Regex pattern must be non-empty")
    require(Generex.isValidPattern(normalizedPattern), s"Invalid regex pattern: $normalizedPattern")

    val perThread = ThreadLocal.withInitial(() => new Generex(normalizedPattern))
    Generator.delay(perThread.get().random())
  }
}
