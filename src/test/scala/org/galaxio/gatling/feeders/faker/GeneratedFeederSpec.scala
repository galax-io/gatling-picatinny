package org.galaxio.gatling.feeders.faker

import org.galaxio.gatling.feeders.faker.Predef._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class GeneratedFeederSpec extends AnyWordSpec with Matchers {

  "GeneratedFeeder" should {
    "create a heterogeneous Gatling feeder from generators" in {
      val feeder = GeneratedFeeder(
        "email"  -> Faker.internet.email(),
        "amount" -> Faker.finance.amount(BigDecimal(10), BigDecimal(20)),
        "active" -> Faker.number.boolean,
      )

      val record = feeder.next()

      record("email") shouldBe a[String]
      record("amount") shouldBe a[BigDecimal]
      record("active") shouldBe a[Boolean]
    }

    "support dependent fields through record generators" in {
      val userRecord = for {
        gender <- Faker.person.gender()
        name   <- Faker.person.fullName(gender)
        email  <- Faker.internet.email(name, "example.com")
      } yield Map("gender" -> gender.value, "name" -> name, "email" -> email)

      val record = GeneratedFeeder.records(userRecord).next()

      record("name").toString should not be empty
      record("email").toString should endWith("@example.com")
    }
  }

  "Collection syntax" should {
    "convert collections into Gatling-compatible records" in {
      val records = List("RU", "AR", "BR").toFeeder("country")

      records shouldBe Vector(
        Map("country" -> "RU"),
        Map("country" -> "AR"),
        Map("country" -> "BR"),
      )
    }

    "convert domain objects into records" in {
      final case class User(id: String, email: String)

      val records = List(User("1", "a@example.com")).toFeeder(user => Map("id" -> user.id, "email" -> user.email))

      records.head shouldBe Map("id" -> "1", "email" -> "a@example.com")
    }
  }

  "Feeder enrichment syntax" should {
    "add generated fields to an existing feeder" in {
      val existing = Iterator.single(Map("id" -> "42"))
      val enriched = existing.withGenerated("traceId", Faker.uuid.string)

      val record = enriched.next()

      record("id") shouldBe "42"
      record("traceId").toString should fullyMatch regex "[a-f0-9\\-]{36}"
    }
  }

  "Date generators" should {
    "format generated local dates for Gatling session values" in {
      val date = Faker.date
        .between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        .format("yyyy-MM-dd")
        .sample()

      date should fullyMatch regex "2026-01-\\d{2}"
    }
  }

  "Government identifiers" should {
    "generate formatted Brazilian CPF values" in {
      Faker.br.cpf(formatted = true).sample() should fullyMatch regex "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}"
    }

    "generate Argentinian DNI values" in {
      Faker.ar.dni().sample() should fullyMatch regex "\\d{8}"
    }
  }

  "Additional faker domains" should {
    "generate internet, location, finance, commerce, and weather values" in {
      Faker.internet.url().sample() should startWith("https://")
      Faker.location.postalCode(Country.RU).sample() should fullyMatch regex "\\d{6}"
      Faker.finance.iban(Country.DE).sample() should startWith("DE89")
      Faker.commerce.orderId().sample() should startWith("ord-")
      Faker.weather.humidityPercent().sample() should (be >= 0 and be <= 100)
    }
  }
}
