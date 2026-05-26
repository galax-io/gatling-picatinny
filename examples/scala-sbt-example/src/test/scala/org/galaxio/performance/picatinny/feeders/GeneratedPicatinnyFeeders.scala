package org.galaxio.performance.picatinny.feeders

import io.gatling.core.Predef._
import io.gatling.core.feeder.{Feeder, FeederBuilderBase}
import org.galaxio.gatling.feeders.faker.Predef._
import org.galaxio.gatling.feeders.faker._

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object GeneratedPicatinnyFeeders {

  val generatedUsers: Feeder[Any] =
    GeneratedFeeder(
      "userId"   -> Faker.uuid.string,
      "email"    -> Faker.internet.email(),
      "phone"    -> Faker.phone.mobile(Country.RU, PhoneFormatMode.E164),
      "city"     -> Faker.location.city(Country.RU),
      "jobTitle" -> Faker.person.jobTitle(),
    )

  val governmentIds: Feeder[Any] =
    GeneratedFeeder(
      "inn"   -> Faker.ru.inn.person(),
      "kpp"   -> Faker.ru.kpp(),
      "ogrn"  -> Faker.ru.ogrn(),
      "snils" -> Faker.ru.snils(),
      "cpf"   -> Faker.br.cpf(formatted = true),
      "dni"   -> Faker.ar.dni(),
    )

  val dates: Feeder[Any] =
    GeneratedFeeder(
      "createdAt" -> Faker.date.past(days = 30).format("yyyy-MM-dd"),
      "validFrom" -> Faker.date.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)).format("yyyy-MM-dd"),
      "validTo"   -> Faker.date.future(days = 90).format("yyyy-MM-dd"),
    )

  val finance: Feeder[Any] =
    GeneratedFeeder(
      "pan"           -> Faker.finance.pan("421345", "541673"),
      "amount"        -> Faker.finance.amount(BigDecimal(100), BigDecimal(5000)),
      "currency"      -> Faker.finance.currency(),
      "iban"          -> Faker.finance.iban(Country.DE),
      "transactionId" -> Faker.finance.transactionId(),
    )

  val countries: FeederBuilderBase[Any] =
    List("RU", "AR", "BR").toFeeder("country").circular

  val enrichedStaticUsers: Feeder[Any] =
    Iterator
      .continually(Map[String, Any]("sourceUser" -> "demo-user"))
      .withGenerated("traceId", Faker.uuid.string)
      .withGenerated("sessionEmail", Faker.internet.email())

  // --- Faker.number ---

  val numbers: Feeder[Any] =
    GeneratedFeeder(
      "randomInt"         -> Faker.number.int(1, 1000),
      "randomLong"        -> Faker.number.long(1L, 1000000L),
      "randomDouble"      -> Faker.number.double(0.0, 100.0),
      "randomFloat"       -> Faker.number.float(0.0f, 1.0f),
      "randomByte"        -> Faker.number.byte(0, 127),
      "randomShort"       -> Faker.number.short(1, 10000),
      "randomChar"        -> Faker.number.char('A', 'Z'),
      "randomBigDecimal"  -> Faker.number.bigDecimal(BigDecimal(0), BigDecimal(10000), 4),
      "randomBoolean"     -> Faker.number.boolean,
      "randomPercentage"  -> Faker.number.percentage,
      "randomPositiveInt" -> Faker.number.positiveInt,
    )

  // --- Faker.string ---

  val strings: Feeder[Any] =
    GeneratedFeeder(
      "alphabeticStr"     -> Faker.string.alphabetic(10),
      "alphanumericStr"   -> Faker.string.alphanumeric(12),
      "matchingStr"       -> Faker.string.matching("[A-Z]{2}[0-9]{4}"),
      "numericStr"        -> Faker.string.numeric(8),
      "hexStr"            -> Faker.string.hex(16),
      "cyrillicStr"       -> Faker.string.cyrillic(6),
      "customAlphabetStr" -> Faker.string.fromAlphabet("ACGT", 20),
      "variableLengthStr" -> Faker.string.lengthBetween(5, 15, "abcdef0123456789"),
    )

  // --- Faker.person ---

  val persons: Feeder[Any] =
    GeneratedFeeder(
      "gender"           -> Faker.person.gender().map(_.value),
      "firstName"        -> Faker.person.firstName(),
      "lastName"         -> Faker.person.lastName(),
      "fullName"         -> Faker.person.fullName(),
      "personPrefix"     -> Faker.person.prefix(),
      "companyEmailName" -> Faker.person.companyEmailName(),
    )

  // --- Faker.internet ---

  val internetData: Feeder[Any] =
    GeneratedFeeder(
      "username"  -> Faker.internet.username(),
      "url"       -> Faker.internet.url(),
      "password"  -> Faker.internet.password(20),
      "userAgent" -> Faker.internet.userAgent(),
      "ipv4"      -> Faker.internet.ipv4(),
      "ipv6"      -> Faker.internet.ipv6(),
      "domain"    -> Faker.internet.domain(),
    )

  // --- Faker.location ---

  val locations: Feeder[Any] =
    GeneratedFeeder(
      "locationCountry" -> Faker.location.country().map(_.iso2),
      "countryCode"     -> Faker.location.countryCode(),
      "streetAddress"   -> Faker.location.streetAddress(),
      "postalCode"      -> Faker.location.postalCode(Country.US),
      "latitude"        -> Faker.location.latitude(),
      "longitude"       -> Faker.location.longitude(),
      "streetName"      -> Faker.location.streetName(),
    )

  // --- Faker.date extended: today, now, offset, formatDate, formatDateTime ---

  val extendedDates: Feeder[Any] =
    GeneratedFeeder(
      "today"        -> Faker.date.formatDate(Faker.date.today(), "yyyy-MM-dd"),
      "nowFormatted" -> Faker.date.formatDateTime(Faker.date.now(), "yyyy-MM-dd'T'HH:mm:ss"),
      "offsetDate"   -> Faker.date.offset(LocalDate.of(2026, 6, 1), -30, 30).format("yyyy-MM-dd"),
    )

  // --- Faker.date.range (tuple) + GeneratedFeeder.records ---

  val dateRangeTuple: Feeder[Any] = GeneratedFeeder.records(
    Faker.date
      .range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 7, 30)
      .map { case (start, end) =>
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        Map[String, Any]("rangeStart" -> start.format(fmt), "rangeEnd" -> end.format(fmt))
      },
  )

  // --- Faker.finance extended: accountNumber, bic, money ---

  val extendedFinance: Feeder[Any] =
    GeneratedFeeder(
      "accountNumber" -> Faker.finance.accountNumber(20),
      "bic"           -> Faker.finance.bic(),
      "moneyAmount"   -> Faker.finance.money(BigDecimal(10), BigDecimal(500), "EUR").map(_.amount.toString()),
      "moneyCurrency" -> Faker.finance.money(BigDecimal(10), BigDecimal(500)).map(_.currency),
    )

  // --- Faker.commerce ---

  val commerce: Feeder[Any] =
    GeneratedFeeder(
      "productName" -> Faker.commerce.productName(),
      "category"    -> Faker.commerce.category(),
      "sku"         -> Faker.commerce.sku("ITEM"),
      "orderId"     -> Faker.commerce.orderId("ORD"),
      "price"       -> Faker.commerce.price().map(_.amount.toString()),
    )

  // --- Faker.weather ---

  val weatherData: Feeder[Any] =
    GeneratedFeeder(
      "weatherCondition" -> Faker.weather.condition(),
      "temperature"      -> Faker.weather.temperatureCelsius(),
      "humidity"         -> Faker.weather.humidityPercent(),
      "pressure"         -> Faker.weather.pressureHPa(),
    )

  // --- Faker.lorem ---

  val loremText: Feeder[Any] =
    GeneratedFeeder(
      "loremWord"     -> Faker.lorem.word(),
      "loremWords"    -> Faker.lorem.words(5),
      "loremSentence" -> Faker.lorem.sentence(8),
    )

  // --- Faker.localization + Faker.oneOf + GeneratedFeeder.records + for-comprehension ---

  val localized: Feeder[Any] = GeneratedFeeder.records(
    for {
      c    <- Faker.oneOf(Country.RU, Country.US, Country.DE, Country.JP, Country.BR)
      curr <- Faker.localization.currency(c)
      lang <- Faker.localization.languageCode(c)
    } yield Map[String, Any]("localCountry" -> c.iso2, "localCurrency" -> curr, "localLanguage" -> lang),
  )

  // --- Country-specific identifiers ---

  val countrySpecificIds: Feeder[Any] =
    GeneratedFeeder(
      "usSSN"           -> Faker.us.ssn(),
      "gbNINO"          -> Faker.gb.nino(),
      "frNIR"           -> Faker.fr.nir(),
      "esNIF"           -> Faker.es.nif(),
      "itCodiceFiscale" -> Faker.it.codiceFiscale(),
      "deTIN"           -> Faker.de.steueridentifikationsnummer(),
      "ruInnCompany"    -> Faker.ru.inn.company(),
      "ruOgrnip"        -> Faker.ru.ogrnip(),
      "passportRU"      -> Faker.passport.ru(),
      "passportUS"      -> Faker.passport.number(Country.US),
    )

  // --- Faker.phone: tollFree + builder ---

  val phones: Feeder[Any] =
    GeneratedFeeder(
      "phoneTollFree" -> Faker.phone.tollFree(Country.US),
      "phoneBuilder"  -> Faker.phone.builder.forCountry(Country.DE).withFormat(PhoneFormatMode.National).build,
    )

  // --- Generator combinators: const, listOf, tupleOf, mapOf, filter ---

  val combinators: Feeder[Any] = GeneratedFeeder.records(
    for {
      constVal   <- Generator.const("fixed-value")
      tags       <- Generator.listOf(3, Faker.lorem.word())
      evenNumber <- Faker.number.int(1, 100).filter(_ % 2 == 0)
      coords     <- Generator.tupleOf(Faker.location.latitude(), Faker.location.longitude())
      kvPairs    <- Generator.mapOf(
                      (Faker.string.alphabetic(4), Faker.number.int(1, 100)),
                      (Faker.string.alphabetic(4), Faker.number.int(1, 100)),
                    )
    } yield Map[String, Any](
      "constVal"   -> constVal,
      "tags"       -> tags.mkString(","),
      "evenNumber" -> evenNumber,
      "coordLat"   -> coords._1,
      "coordLon"   -> coords._2,
      "kvPairs"    -> kvPairs.map { case (k, v) => s"$k=$v" }.mkString(";"),
    ),
  )

  // --- GeneratedFeeder.single ---

  val singleFieldFeeder: Feeder[Int] = GeneratedFeeder.single("singleInt", Faker.number.int(1, 999))

  // --- Syntax feeder ops: rename, dropKeys, prefixKeys, withDefaults, requireKeys, mapRecord ---

  val transformedFeeder: Feeder[Any] =
    GeneratedFeeder(
      "raw_name"  -> Faker.person.fullName(),
      "raw_email" -> Faker.internet.email(),
      "debug"     -> Generator.const("internal"),
    ).rename("raw_name", "tfName")
      .rename("raw_email", "tfEmail")
      .dropKeys("debug")
      .withDefaults("tfRole" -> "user", "tfActive" -> true)
      .requireKeys("tfName", "tfEmail")
      .mapRecord(r => r + ("tfNameUpper" -> r("tfName").toString.toUpperCase))

  // --- Syntax feeder ops: renameKeys, suffixKeys, selectKeys ---

  val keyOpsDemo: Feeder[Any] =
    GeneratedFeeder(
      "alpha" -> Faker.string.alphabetic(4),
      "beta"  -> Faker.number.int(1, 100),
      "gamma" -> Faker.number.boolean,
    ).renameKeys(Map("alpha" -> "kAlpha", "beta" -> "kBeta", "gamma" -> "kGamma"))
      .suffixKeys("_demo")
      .selectKeys("kAlpha_demo", "kBeta_demo")

  // --- Map.toSingleRecordFeeder ---

  val singleRecordDemo: IndexedSeq[Map[String, Any]] =
    Map[String, Any]("configKey" -> "configValue", "configFlag" -> true).toSingleRecordFeeder
}
