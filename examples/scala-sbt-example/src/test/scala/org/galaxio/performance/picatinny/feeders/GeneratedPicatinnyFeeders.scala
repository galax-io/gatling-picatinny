package org.galaxio.performance.picatinny.feeders

import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.feeders.faker.Predef._
import org.galaxio.gatling.feeders.faker._

import java.time.LocalDate

object GeneratedPicatinnyFeeders {

  /** Basic generated user data for request payloads. */
  val generatedUsers: Feeder[Any] =
    GeneratedFeeder(
      "userId" -> Faker.uuid.string,
      "email" -> Faker.internet.email(),
      "phone" -> Faker.phone.mobile(Country.RU, PhoneFormatMode.E164),
      "city" -> Faker.location.city(Country.RU),
      "jobTitle" -> Faker.person.jobTitle(),
    )

  /** Government identifiers are grouped separately from generic person data. */
  val governmentIds: Feeder[Any] =
    GeneratedFeeder(
      "inn" -> Faker.ru.inn.person(),
      "kpp" -> Faker.ru.kpp(),
      "ogrn" -> Faker.ru.ogrn(),
      "snils" -> Faker.ru.snils(),
      "cpf" -> Faker.br.cpf(formatted = true),
      "dni" -> Faker.ar.dni(),
    )

  /** Date generation stays focused on date semantics: ranges, offsets, and formatting. */
  val dates: Feeder[Any] =
    GeneratedFeeder(
      "createdAt" -> Faker.date.past(days = 30).format("yyyy-MM-dd"),
      "validFrom" -> Faker.date.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)).format("yyyy-MM-dd"),
      "validTo" -> Faker.date.future(days = 90).format("yyyy-MM-dd"),
    )

  /** Finance data is grouped separately from dates. */
  val finance: Feeder[Any] =
    GeneratedFeeder(
      "pan" -> Faker.finance.pan("421345", "541673"),
      "amount" -> Faker.finance.amount(BigDecimal(100), BigDecimal(5000)),
      "currency" -> Faker.finance.currency(),
      "iban" -> Faker.finance.iban(Country.DE),
      "transactionId" -> Faker.finance.transactionId(),
    )

  /** In-memory collections can become feeders without replacing Gatling CSV/JDBC feeders. */
  val countries: Feeder[Any] =
    List("RU", "AR", "BR").toFeeder("country").circular

  /** Existing Gatling feeders can be enriched with generated fields. */
  val enrichedStaticUsers: Feeder[Any] =
    Array(Map("sourceUser" -> "demo-user")).iterator
      .withGenerated("traceId", Faker.uuid.string)
      .withGenerated("sessionEmail", Faker.internet.email())
}
