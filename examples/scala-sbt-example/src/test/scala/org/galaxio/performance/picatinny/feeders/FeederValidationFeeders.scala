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

  /** Faker `GeneratedFeeder` fields — values produced by `all`, one record per `.feed(all)`. */
  val fakerPatterns: List[(String, String)] = List(
    "uuid"       -> "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", // RFC-4122 v4, lowercase
    "str"        -> "[a-zA-Z0-9]{12}",
    "innPerson"  -> "\\d{12}",                                                             // natural person = 12 digits
    "innCompany" -> "\\d{10}",                                                             // legal entity = 10 digits
    "snils"      -> "\\d{11}",
    "pan"        -> "\\d{16}",
    "ogrn"       -> "\\d{13}",
    "ogrnip"     -> "\\d{15}",
    "kpp"        -> "\\d{9}",
    "passport"   -> "\\d{10}",
    "date"       -> "\\d{8}",
  )

  /** Non-deprecated picatinny-core feeders ([[org.galaxio.gatling.feeders]]) — values already in the session, fed by
    * `PicatinnyScenario`. Keys here are the ones that do NOT collide with `fakerPatterns` (uuid/pan/ogrn/kpp/snils/passport are
    * round-tripped under those same keys by the faker set); `RegexFeeder` is deprecated and the zipped/split feeders carry
    * composite keys, so all three are omitted. Loose-format phones assert non-blank round-trip only.
    */
  val picatinnyPatterns: List[(String, String)] = List(
    "currentDate"    -> "\\d{4}-\\d{2}-\\d{2}", // CurrentDateFeeder, ISO_LOCAL_DATE
    "randomDate"     -> "\\d{4}-\\d{2}-\\d{2}", // RandomDateFeeder, default yyyy-MM-dd
    "rangeFrom"      -> "\\d{4}-\\d{2}-\\d{2}", // RandomDateRangeFeeder lower bound
    "rangeTo"        -> "\\d{4}-\\d{2}-\\d{2}", // RandomDateRangeFeeder upper bound
    "digit"          -> "-?\\d+",               // RandomDigitFeeder, Int
    "customValue"    -> "custom-\\d+",          // CustomFeeder
    "phone"          -> ".+",                   // RandomPhoneFeeder, default format
    "e164Phone"      -> ".+",                   // RandomPhoneFeeder, E164
    "formattedPhone" -> ".+",                   // RandomPhoneFeeder, custom format
    "tollFreePhone"  -> ".+",                   // RandomPhoneFeeder, toll-free
    "randomString"   -> "[a-zA-Z0-9]{12}",      // RandomStringFeeder, alphanumeric(12)
    "rangeString"    -> "[abc123]{4,8}",        // RandomRangeStringFeeder
    "sequence"       -> "\\d+",                 // SequentialFeeder, Long
    "csvValue"       -> "alpha|beta|gamma",     // SeparatedValuesFeeder, circular
    "natItn"         -> "\\d{12}",              // RandomNatITNFeeder
    "jurItn"         -> "\\d{10}",              // RandomJurITNFeeder
    "psrnsp"         -> "\\d{15}",              // RandomPSRNSPFeeder
    "lambdaValue"    -> "[0-9a-f]{8}",          // feeder { } lambda, UUID prefix
    "greekLetter"    -> "alpha|beta|gamma",     // IndexedSeq.toFeeder
  )

  /** Every field round-tripped over HTTP: faker generators plus non-deprecated picatinny-core feeders. */
  val patterns: List[(String, String)] = fakerPatterns ++ picatinnyPatterns

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
