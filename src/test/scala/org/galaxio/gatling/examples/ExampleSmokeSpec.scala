package org.galaxio.gatling.examples

import io.gatling.core.CoreDsl
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.feeders._
import org.galaxio.gatling.feeders.faker.Predef._
import org.galaxio.gatling.feeders.faker._
import org.galaxio.gatling.utils.IntensityConverter._
import org.galaxio.gatling.utils.jwt._
import org.galaxio.gatling.utils.phone.{PhoneFormat, TypePhone}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneId}

class ExampleSmokeSpec extends AnyWordSpec with Matchers with CoreDsl {

  override implicit def configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  "Legacy feeders from examples" should {
    "produce UUID values" in {
      val f      = RandomUUIDFeeder("uuid")
      val record = f.next()
      record("uuid").toString should have length 36
    }

    "produce current date" in {
      val f = CurrentDateFeeder("date", DateTimeFormatter.ISO_LOCAL_DATE)
      f.next()("date") should not be empty
    }

    "produce random date" in {
      val f = RandomDateFeeder("randomDate", 3, 1)
      f.next()("randomDate") should not be empty
    }

    "produce date range" in {
      val f      = RandomDateRangeFeeder("from", "to", 2, "yyyy-MM-dd", LocalDateTime.now(), ChronoUnit.DAYS, ZoneId.of("UTC"))
      val record = f.next()
      record("from") should not be empty
      record("to") should not be empty
    }

    "produce random digit" in {
      val f = RandomDigitFeeder("digit")
      f.next()("digit") shouldBe a[java.lang.Integer]
    }

    "produce custom value" in {
      val f = CustomFeeder("custom", "hello")
      f.next()("custom") shouldBe "hello"
    }

    "produce phone numbers" in {
      RandomPhoneFeeder("phone").next()("phone") should not be empty
      RandomPhoneFeeder("toll", TypePhone.TollFreePhoneNumber).next()("toll") should not be empty
    }

    "produce phone with custom format" in {
      val fmt   = PhoneFormat(
        countryCode = "+7",
        length = 10,
        areaCodes = Seq("903", "906"),
        prefixes = Seq("123"),
        format = "+X XXX XXX-XX-XX",
      )
      val phone = RandomPhoneFeeder("p", TypePhone.PhoneNumber, fmt).next()("p").toString
      phone should startWith("+7")
    }

    "produce random strings" in {
      RandomStringFeeder("s", 12).next()("s").toString should have length 12
    }

    "produce range strings" in {
      val s = RandomRangeStringFeeder("rs", 4, 8, "abc").next()("rs").toString
      s.length should (be >= 4 and be <= 8)
    }

    "produce sequential values" in {
      val f = SequentialFeeder("seq", 100, 5)
      f.next()("seq") shouldBe 100L
      f.next()("seq") shouldBe 105L
    }

    "produce regex-based values" in {
      RegexFeeder("rx", "[A-Z]{3}").next()("rx").toString should fullyMatch regex "[A-Z]{3}"
    }

    "produce PAN values" in {
      RandomPANFeeder("pan", "421345").next()("pan").toString should have length 16
    }

    "produce government ID feeders" in {
      RandomNatITNFeeder("itn").next()("itn") should not be empty
      RandomJurITNFeeder("jitn").next()("jitn") should not be empty
      RandomOGRNFeeder("ogrn").next()("ogrn") should not be empty
      RandomPSRNSPFeeder("psrnsp").next()("psrnsp") should not be empty
      RandomKPPFeeder("kpp").next()("kpp") should not be empty
      RandomSNILSFeeder("snils").next()("snils") should not be empty
      RandomRusPassportFeeder("pass").next()("pass") should not be empty
    }

    "support feeder lambda syntax" in {
      val f: Feeder[String] = feeder("v")(java.util.UUID.randomUUID().toString.take(8))
      f.next()("v").length shouldBe 8
    }

    "support feeder zip syntax" in {
      val combined = RandomUUIDFeeder("uuid") ** RandomDigitFeeder("digit")
      val record   = combined.next()
      record should contain key "uuid"
      record should contain key "digit"
    }

    "support collection-to-feeder syntax" in {
      val records = Seq("alpha", "beta", "gamma").toFeeder("letter")
      records should have size 3
      records.head("letter") shouldBe "alpha"
    }
  }

  "Faker-based feeders from examples" should {
    "produce generated user fields" in {
      val f      = GeneratedFeeder(
        "userId" -> Faker.uuid.string,
        "email"  -> Faker.internet.email(),
        "phone"  -> Faker.phone.mobile(Country.RU, PhoneFormatMode.E164),
      )
      val record = f.next()
      record("userId").toString should not be empty
      record("email").toString should include("@")
      record("phone").toString should startWith("+")
    }

    "produce government IDs" in {
      val f      = GeneratedFeeder(
        "inn"   -> Faker.ru.inn.person(),
        "snils" -> Faker.ru.snils(),
        "cpf"   -> Faker.br.cpf(formatted = true),
      )
      val record = f.next()
      record("inn").toString should not be empty
      record("snils").toString should not be empty
      record("cpf").toString should not be empty
    }

    "produce date generators" in {
      val f      = GeneratedFeeder(
        "past"    -> Faker.date.past(days = 30).format("yyyy-MM-dd"),
        "between" -> Faker.date.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)).format("yyyy-MM-dd"),
        "future"  -> Faker.date.future(days = 90).format("yyyy-MM-dd"),
      )
      val record = f.next()
      record("past").toString should fullyMatch regex """\d{4}-\d{2}-\d{2}"""
      record("between").toString should fullyMatch regex """\d{4}-\d{2}-\d{2}"""
      record("future").toString should fullyMatch regex """\d{4}-\d{2}-\d{2}"""
    }

    "produce finance fields" in {
      val f      = GeneratedFeeder(
        "pan"      -> Faker.finance.pan("421345"),
        "currency" -> Faker.finance.currency(),
        "iban"     -> Faker.finance.iban(Country.DE),
        "account"  -> Faker.finance.accountNumber(20),
      )
      val record = f.next()
      record("pan").toString.length should be >= 16
      record("currency").toString should not be empty
      record("iban").toString should startWith("DE")
      record("account").toString should have length 20
    }

    "produce number generators" in {
      val f      = GeneratedFeeder(
        "int"     -> Faker.number.int(1, 1000),
        "long"    -> Faker.number.long(1L, 1000000L),
        "double"  -> Faker.number.double(0.0, 100.0),
        "boolean" -> Faker.number.boolean,
      )
      val record = f.next()
      record("int").asInstanceOf[Int] should (be >= 1 and be <= 1000)
      record("long").asInstanceOf[Long] should (be >= 1L and be <= 1000000L)
    }

    "produce string generators" in {
      val f      = GeneratedFeeder(
        "alpha"    -> Faker.string.alphabetic(10),
        "alphanum" -> Faker.string.alphanumeric(12),
        "hex"      -> Faker.string.hex(16),
        "cyrillic" -> Faker.string.cyrillic(6),
      )
      val record = f.next()
      record("alpha").toString should have length 10
      record("alphanum").toString should have length 12
      record("hex").toString should have length 16
      record("cyrillic").toString should have length 6
    }

    "produce person fields" in {
      val f      = GeneratedFeeder(
        "firstName" -> Faker.person.firstName(),
        "lastName"  -> Faker.person.lastName(),
        "fullName"  -> Faker.person.fullName(),
      )
      val record = f.next()
      record("firstName").toString should not be empty
      record("lastName").toString should not be empty
      record("fullName").toString should not be empty
    }

    "produce internet fields" in {
      val f      = GeneratedFeeder(
        "username" -> Faker.internet.username(),
        "url"      -> Faker.internet.url(),
        "ipv4"     -> Faker.internet.ipv4(),
        "domain"   -> Faker.internet.domain(),
      )
      val record = f.next()
      record("username").toString should not be empty
      record("url").toString should startWith("http")
      record("ipv4").toString should include(".")
    }

    "produce location fields" in {
      val f      = GeneratedFeeder(
        "countryCode" -> Faker.location.countryCode(),
        "latitude"    -> Faker.location.latitude(),
        "longitude"   -> Faker.location.longitude(),
      )
      val record = f.next()
      record("countryCode").toString should have length 2
    }

    "produce commerce fields" in {
      val f      = GeneratedFeeder(
        "product"  -> Faker.commerce.productName(),
        "category" -> Faker.commerce.category(),
        "sku"      -> Faker.commerce.sku("ITEM"),
      )
      val record = f.next()
      record("product").toString should not be empty
      record("sku").toString should startWith("ITEM")
    }

    "produce weather data" in {
      val f = GeneratedFeeder(
        "condition" -> Faker.weather.condition(),
        "temp"      -> Faker.weather.temperatureCelsius(),
      )
      f.next()("condition").toString should not be empty
    }

    "produce lorem text" in {
      val f      = GeneratedFeeder(
        "word"     -> Faker.lorem.word(),
        "sentence" -> Faker.lorem.sentence(8),
      )
      val record = f.next()
      record("word").toString should not be empty
      record("sentence").toString should not be empty
    }

    "support country-specific IDs" in {
      val f      = GeneratedFeeder(
        "usSSN"  -> Faker.us.ssn(),
        "gbNINO" -> Faker.gb.nino(),
        "esNIF"  -> Faker.es.nif(),
      )
      val record = f.next()
      record("usSSN").toString should not be empty
      record("gbNINO").toString should not be empty
      record("esNIF").toString should not be empty
    }

    "support generator combinators" in {
      val f      = GeneratedFeeder.records(
        for {
          constVal <- Generator.const("fixed-value")
          tags     <- Generator.listOf(3, Faker.lorem.word())
          coords   <- Generator.tupleOf(Faker.location.latitude(), Faker.location.longitude())
        } yield Map[String, Any](
          "constVal" -> constVal,
          "tags"     -> tags.mkString(","),
          "lat"      -> coords._1,
          "lon"      -> coords._2,
        ),
      )
      val record = f.next()
      record("constVal") shouldBe "fixed-value"
      record("tags").toString.split(",") should have length 3
    }

    "support single-field feeder" in {
      val f = GeneratedFeeder.single("num", Faker.number.int(1, 999))
      f.next()("num").asInstanceOf[Int] should (be >= 1 and be <= 999)
    }

    "support feeder transformations" in {
      val f = GeneratedFeeder(
        "raw_name" -> Faker.person.fullName(),
        "debug"    -> Generator.const("internal"),
      ).rename("raw_name", "name")
        .dropKeys("debug")
        .withDefaults("role" -> "user")
        .requireKeys("name")
        .mapRecord(r => r + ("upper" -> r("name").toString.toUpperCase))

      val record = f.next()
      record should contain key "name"
      record should contain key "upper"
      record should contain key "role"
      record should not contain key("debug")
      record should not contain key("raw_name")
    }

    "support static enrichment with withGenerated" in {
      val f = Iterator
        .continually(Map[String, Any]("source" -> "demo"))
        .withGenerated("traceId", Faker.uuid.string)

      val record = f.next()
      record("source") shouldBe "demo"
      record("traceId").toString should not be empty
    }

    "support date range tuples" in {
      val f      = GeneratedFeeder.records(
        Faker.date
          .range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 7, 30)
          .map { case (start, end) =>
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            Map[String, Any]("start" -> start.format(fmt), "end" -> end.format(fmt))
          },
      )
      val record = f.next()
      record("start").toString should fullyMatch regex """\d{4}-\d{2}-\d{2}"""
      record("end").toString should fullyMatch regex """\d{4}-\d{2}-\d{2}"""
    }

    "support localization and oneOf" in {
      val f      = GeneratedFeeder.records(
        for {
          c    <- Faker.oneOf(Country.RU, Country.US, Country.DE)
          curr <- Faker.localization.currency(c)
          lang <- Faker.localization.languageCode(c)
        } yield Map[String, Any]("country" -> c.iso2, "currency" -> curr, "language" -> lang),
      )
      val record = f.next()
      record("country").toString should have length 2
      record("currency").toString should not be empty
    }

    "support phone toll-free and builder" in {
      val f      = GeneratedFeeder(
        "tollFree" -> Faker.phone.tollFree(Country.US),
        "builder"  -> Faker.phone.builder.forCountry(Country.DE).withFormat(PhoneFormatMode.National).build,
      )
      val record = f.next()
      record("tollFree").toString should not be empty
      record("builder").toString should not be empty
    }

    "support Map.toSingleRecordFeeder" in {
      val records = Map[String, Any]("key" -> "value", "flag" -> true).toSingleRecordFeeder
      records should have size 1
      records.head("key") shouldBe "value"
      records.head("flag") shouldBe true
    }

    "support renameKeys, suffixKeys, selectKeys" in {
      val f = GeneratedFeeder(
        "alpha" -> Faker.string.alphabetic(4),
        "beta"  -> Faker.number.int(1, 100),
      ).renameKeys(Map("alpha" -> "kAlpha", "beta" -> "kBeta"))
        .suffixKeys("_demo")
        .selectKeys("kAlpha_demo")

      val record = f.next()
      record should contain key "kAlpha_demo"
      record should not contain key("kBeta_demo")
    }
  }

  "JWT from examples" should {
    "produce valid three-part token" in {
      val gen = jwt("HS256", "performance-secret").defaultHeader
        .payload("""{"subject":"picatinny","scope":"smoke-test"}""")

      val fakeEventLoop = new org.galaxio.gatling.transactions.FakeEventLoop
      val session       = io.gatling.core.session.Session("smoke", 1L, fakeEventLoop)
      val result        = session.setJwt(gen, "jwt")
      val token         = result("jwt").as[String]

      token.split("\\.") should have length 3
    }
  }

  "IntensityConverter from examples" should {
    "convert RPM" in {
      60.rpm shouldBe 1.0
    }

    "convert RPH" in {
      3600.rph shouldBe 1.0
    }
  }

  "SeparatedValuesFeeder from examples" should {
    "parse CSV string into records" in {
      val records = SeparatedValuesFeeder("csvValue", "alpha,beta,gamma", ',')
      records should have size 3
      records.head("csvValue") shouldBe "alpha"
    }

    "parse CSV with custom split mapping" in {
      val records = SeparatedValuesFeeder(Some("split"), Seq(Map("HOSTS" -> "host1,host2", "USERS" -> "user1,user2")), ',')
      records should not be empty
    }
  }
}
