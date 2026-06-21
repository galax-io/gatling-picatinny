package org.galaxio.performance.picatinny.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.feeders.faker.{Faker, GeneratedFeeder}
import org.galaxio.gatling.feeders.faker.Predef._

/** Feeder data for the feeder-validation e2e (test-model layer 4). Data only (galaxio-gatling-pro boundaries).
  *
  * `all` is a single ZIPPED picatinny feeder (`GeneratedFeeder` with every field) — one `.feed()` increments ALL values at
  * once, so the scenario pulls one record carrying every generated value rather than feeding each feeder separately. `patterns`
  * lists the EXPECTED value pattern per field; it drives the request headers, the mock echo body, and the response checks from
  * one source of truth.
  */
object FeederValidationFeeders {

  val patterns: List[(String, String)] = List(
    "uuid"       -> "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", // RFC-4122 v4, lowercase
    "str"        -> "[a-zA-Z0-9]{12}",
    "innPerson"  -> "\\d{12}", // natural person = 12 digits
    "innCompany" -> "\\d{10}", // legal entity = 10 digits
    "snils"      -> "\\d{11}",
    "pan"        -> "\\d{16}",
    "ogrn"       -> "\\d{13}",
    "ogrnip"     -> "\\d{15}",
    "kpp"        -> "\\d{9}",
    "passport"   -> "\\d{10}",
    "date"       -> "\\d{8}",
  )

  /** One zipped feeder: a single `.feed(all)` advances every generator together (one record, all fields). */
  val all: Feeder[Any] = GeneratedFeeder(
    "uuid"       -> Faker.uuid.string,
    "str"        -> Faker.string.alphanumeric(12),
    "innPerson"  -> Faker.ru.inn.person(),
    "innCompany" -> Faker.ru.inn.company(),
    "snils"      -> Faker.ru.snils(),
    "pan"        -> Faker.finance.pan(),
    "ogrn"       -> Faker.ru.ogrn(),
    "ogrnip"     -> Faker.ru.ogrnip(),
    "kpp"        -> Faker.ru.kpp(),
    "passport"   -> Faker.passport.ru(),
    "date"       -> Faker.date.today().format("yyyyMMdd"),
  )
}
