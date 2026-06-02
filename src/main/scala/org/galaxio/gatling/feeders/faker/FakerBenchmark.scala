package org.galaxio.gatling.feeders.faker

import org.galaxio.gatling.feeders.{RandomDigitFeeder, RandomPANFeeder, RandomPhoneFeeder}
import org.galaxio.gatling.feeders.faker.Predef._
import org.galaxio.gatling.jmh.JmhBenchmark
import org.galaxio.gatling.utils.phone.TypePhone
import org.openjdk.jmh.annotations.Benchmark

import scala.annotation.nowarn

class FakerBenchmark extends JmhBenchmark {

  // Legacy feeders are deprecated; kept here only to baseline the new Faker/GeneratedFeeder
  // path against the old Random*Feeder path. Suppression is scoped to these three references.
  @nowarn("cat=deprecation") private val legacyDigitFeeder = RandomDigitFeeder("digit")
  @nowarn("cat=deprecation") private val legacyPanFeeder   = RandomPANFeeder("pan", "411111", "555555", "220220")
  @nowarn("cat=deprecation") private val legacyPhoneFeeder = RandomPhoneFeeder("phone", TypePhone.E164PhoneNumber)

  private val emailGenerator      = Faker.internet.email()
  private val usPhoneGenerator    = Faker.phone.mobile(Country.US)
  private val ruCompanyInn        = Faker.ru.inn.company()
  private val transactionId       = Faker.finance.transactionId()
  private val amountGenerator     = Faker.finance.amount(BigDecimal(100), BigDecimal(5000))
  private val generatedUserFeeder = GeneratedFeeder(
    "email"         -> emailGenerator,
    "phone"         -> usPhoneGenerator,
    "companyInn"    -> ruCompanyInn,
    "transactionId" -> transactionId,
    "amount"        -> amountGenerator,
  )
  private val enrichedFeeder      =
    Iterator
      .continually(Map("tenant" -> "perf", "country" -> "US"))
      .withGenerated(
        "traceId" -> Faker.uuid.string,
        "active"  -> Faker.number.boolean,
        "email"   -> emailGenerator,
      )

  @Benchmark
  def legacyRandomDigitFeederNext(): Map[String, Int] =
    legacyDigitFeeder.next()

  @Benchmark
  def legacyRandomPanFeederNext(): Map[String, String] =
    legacyPanFeeder.next()

  @Benchmark
  def legacyRandomPhoneFeederNext(): Map[String, String] =
    legacyPhoneFeeder.next()

  @Benchmark
  def fakerEmailSample(): String =
    emailGenerator.sample()

  @Benchmark
  def fakerUsPhoneSample(): String =
    usPhoneGenerator.sample()

  @Benchmark
  def fakerRuCompanyInnSample(): String =
    ruCompanyInn.sample()

  @Benchmark
  def generatedFeederNext(): Map[String, Any] =
    generatedUserFeeder.next()

  @Benchmark
  def generatedFeederWithEnrichmentNext(): Map[String, Any] =
    enrichedFeeder.next()
}
