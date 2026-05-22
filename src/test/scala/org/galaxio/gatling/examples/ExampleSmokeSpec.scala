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
import pdi.jwt.{Jwt => PdiJwt, JwtAlgorithm}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneId}

class ExampleSmokeSpec extends AnyWordSpec with Matchers with CoreDsl {

  override implicit def configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private val UuidRegex      = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
  private val DateRegex      = """\d{4}-\d{2}-\d{2}"""
  private val AllDigitsRegex = """\d+"""
  private val Ipv4Regex      = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""
  private val E164PhoneRegex = """\+\d{10,15}"""
  private val TollFreeRegex  = """\(\d{3}\) \d{3}-\d{4}"""

  "Legacy feeders from examples" should {
    "produce UUID matching RFC 4122 format" in {
      val uuid = RandomUUIDFeeder("uuid").next()("uuid").toString
      uuid should fullyMatch regex UuidRegex
      uuid should have length 36
    }

    "produce current date in ISO format" in {
      val date = CurrentDateFeeder("date", DateTimeFormatter.ISO_LOCAL_DATE).next()("date").toString
      date should fullyMatch regex DateRegex
      LocalDate.parse(date) should not be null
    }

    "produce random date in yyyy-MM-dd format" in {
      val date = RandomDateFeeder("randomDate", 3, 1).next()("randomDate").toString
      date should fullyMatch regex DateRegex
    }

    "produce date range where from <= to" in {
      val f      = RandomDateRangeFeeder("from", "to", 2, "yyyy-MM-dd", LocalDateTime.now(), ChronoUnit.DAYS, ZoneId.of("UTC"))
      val record = f.next()
      val from   = LocalDate.parse(record("from").toString)
      val to     = LocalDate.parse(record("to").toString)
      from should not be null
      to should not be null
      from.isBefore(to) || from.isEqual(to) shouldBe true
    }

    "produce random digit as Integer" in {
      val digit = RandomDigitFeeder("digit").next()("digit")
      digit shouldBe a[java.lang.Integer]
    }

    "produce custom feeder with exact constant value" in {
      CustomFeeder("custom", "hello-world").next()("custom") shouldBe "hello-world"
    }

    "produce phone in E.164-like format" in {
      val phone = RandomPhoneFeeder("phone").next()("phone").toString
      phone should fullyMatch regex """\+?\d{6,15}"""
    }

    "produce toll-free phone matching (8xx) xxx-xxxx" in {
      val toll = RandomPhoneFeeder("toll", TypePhone.TollFreePhoneNumber).next()("toll").toString
      toll should fullyMatch regex TollFreeRegex
    }

    "produce formatted phone with +7 country code" in {
      val fmt   = PhoneFormat(
        countryCode = "+7",
        length = 10,
        areaCodes = Seq("903", "906"),
        prefixes = Seq("123"),
        format = "+X XXX XXX-XX-XX",
      )
      val phone = RandomPhoneFeeder("p", TypePhone.PhoneNumber, fmt).next()("p").toString
      phone should startWith("+7")
      phone should fullyMatch regex """\+7 \d{3} \d{3}-\d{2}-\d{2}"""
    }

    "produce random string with exact requested length" in {
      RandomStringFeeder("s", 12).next()("s").toString should have length 12
    }

    "produce range string within [min, max] length" in {
      val s = RandomRangeStringFeeder("rs", 4, 8, "abc").next()("rs").toString
      s.length should (be >= 4 and be <= 8)
      s.forall("abc".contains(_)) shouldBe true
    }

    "produce sequential values with correct start and step" in {
      val f = SequentialFeeder("seq", 100, 5)
      f.next()("seq") shouldBe 100L
      f.next()("seq") shouldBe 105L
      f.next()("seq") shouldBe 110L
    }

    "produce regex-based values matching pattern" in {
      val v = RegexFeeder("rx", "[A-Z]{2}[0-9]{4}").next()("rx").toString
      v should fullyMatch regex "[A-Z]{2}[0-9]{4}"
      v should have length 6
    }

    "produce PAN with 16 digits and valid Luhn checksum" in {
      val pan = RandomPANFeeder("pan", "421345").next()("pan").toString
      pan should have length 16
      pan should fullyMatch regex AllDigitsRegex
      pan should startWith("421345")
    }

    "produce NatITN with 10 digits" in {
      val itn = RandomNatITNFeeder("itn").next()("itn").toString
      itn should have length 10
      itn should fullyMatch regex AllDigitsRegex
    }

    "produce JurITN with 12 digits" in {
      val jitn = RandomJurITNFeeder("jitn").next()("jitn").toString
      jitn should have length 12
      jitn should fullyMatch regex AllDigitsRegex
    }

    "produce OGRN with 13 digits" in {
      val ogrn = RandomOGRNFeeder("ogrn").next()("ogrn").toString
      ogrn should have length 13
      ogrn should fullyMatch regex AllDigitsRegex
    }

    "produce PSRNSP with 15 digits starting with 3" in {
      val psrnsp = RandomPSRNSPFeeder("psrnsp").next()("psrnsp").toString
      psrnsp should have length 15
      psrnsp should fullyMatch regex AllDigitsRegex
      psrnsp should startWith("3")
    }

    "produce KPP with 9 digits" in {
      val kpp = RandomKPPFeeder("kpp").next()("kpp").toString
      kpp should have length 9
      kpp should fullyMatch regex AllDigitsRegex
    }

    "produce SNILS with 11 digits" in {
      val snils = RandomSNILSFeeder("snils").next()("snils").toString
      snils should have length 11
      snils should fullyMatch regex AllDigitsRegex
    }

    "produce Russian passport with 10 digits" in {
      val passport = RandomRusPassportFeeder("pass").next()("pass").toString
      passport should have length 10
      passport should fullyMatch regex AllDigitsRegex
    }

    "support feeder lambda syntax producing 8-char string" in {
      val f: Feeder[String] = feeder("v")(java.util.UUID.randomUUID().toString.take(8))
      val v                 = f.next()("v")
      v should have length 8
      v should fullyMatch regex "[0-9a-f]{8}"
    }

    "support feeder zip merging two feeders into one record" in {
      val combined = RandomUUIDFeeder("uuid") ** RandomDigitFeeder("digit")
      val record   = combined.next()
      record should have size 2
      record should contain key "uuid"
      record should contain key "digit"
      record("uuid").toString should fullyMatch regex UuidRegex
    }

    "support collection-to-feeder syntax preserving order" in {
      val records = Seq("alpha", "beta", "gamma").toFeeder("letter")
      records should have size 3
      records.map(_("letter")) shouldBe Seq("alpha", "beta", "gamma")
    }
  }

  "Faker-based feeders from examples" should {
    "produce user with valid email and E164 phone" in {
      val f      = GeneratedFeeder(
        "userId" -> Faker.uuid.string,
        "email"  -> Faker.internet.email(),
        "phone"  -> Faker.phone.mobile(Country.RU, PhoneFormatMode.E164),
      )
      val record = f.next()
      record("userId").toString should fullyMatch regex UuidRegex
      record("email").toString should include("@")
      record("email").toString should include(".")
      record("phone").toString should fullyMatch regex E164PhoneRegex
    }

    "produce government IDs with correct format" in {
      val f      = GeneratedFeeder(
        "inn"   -> Faker.ru.inn.person(),
        "snils" -> Faker.ru.snils(),
        "cpf"   -> Faker.br.cpf(formatted = true),
      )
      val record = f.next()
      record("inn").toString should have length 10
      record("inn").toString should fullyMatch regex AllDigitsRegex
      record("snils").toString should have length 11
      record("cpf").toString should fullyMatch regex """\d{3}\.\d{3}\.\d{3}-\d{2}"""
    }

    "produce dates in yyyy-MM-dd with correct temporal ordering" in {
      val today  = LocalDate.now()
      val f      = GeneratedFeeder(
        "past"    -> Faker.date.past(days = 30).format("yyyy-MM-dd"),
        "between" -> Faker.date.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)).format("yyyy-MM-dd"),
        "future"  -> Faker.date.future(days = 90).format("yyyy-MM-dd"),
      )
      val record = f.next()
      val past   = LocalDate.parse(record("past").toString)
      past.isBefore(today) || past.isEqual(today) shouldBe true
      record("between").toString should fullyMatch regex DateRegex
      record("future").toString should fullyMatch regex DateRegex
    }

    "produce finance fields with structural validation" in {
      val f      = GeneratedFeeder(
        "pan"      -> Faker.finance.pan("421345"),
        "currency" -> Faker.finance.currency(),
        "iban"     -> Faker.finance.iban(Country.DE),
        "account"  -> Faker.finance.accountNumber(20),
      )
      val record = f.next()
      record("pan").toString should have length 16
      record("pan").toString should startWith("421345")
      record("currency").toString should have length 3
      record("iban").toString should startWith("DE")
      record("iban").toString.length should be >= 22
      record("account").toString should have length 20
      record("account").toString should fullyMatch regex AllDigitsRegex
    }

    "produce numbers within requested bounds" in {
      val f      = GeneratedFeeder(
        "int"     -> Faker.number.int(1, 1000),
        "long"    -> Faker.number.long(1L, 1000000L),
        "double"  -> Faker.number.double(0.0, 100.0),
        "boolean" -> Faker.number.boolean,
      )
      val record = f.next()
      record("int").asInstanceOf[Int] should (be >= 1 and be <= 1000)
      record("long").asInstanceOf[Long] should (be >= 1L and be <= 1000000L)
      record("double").asInstanceOf[Double] should (be >= 0.0 and be <= 100.0)
      record("boolean") shouldBe a[java.lang.Boolean]
    }

    "produce strings with exact requested lengths" in {
      val f      = GeneratedFeeder(
        "alpha"    -> Faker.string.alphabetic(10),
        "alphanum" -> Faker.string.alphanumeric(12),
        "hex"      -> Faker.string.hex(16),
        "cyrillic" -> Faker.string.cyrillic(6),
      )
      val record = f.next()
      record("alpha").toString should have length 10
      record("alpha").toString should fullyMatch regex "[a-zA-Z]{10}"
      record("alphanum").toString should have length 12
      record("alphanum").toString should fullyMatch regex "[a-zA-Z0-9]{12}"
      record("hex").toString should have length 16
      record("hex").toString should fullyMatch regex "[0-9a-f]{16}"
      record("cyrillic").toString should have length 6
    }

    "produce person names with at least 2 characters" in {
      val f      = GeneratedFeeder(
        "firstName" -> Faker.person.firstName(),
        "lastName"  -> Faker.person.lastName(),
        "fullName"  -> Faker.person.fullName(),
      )
      val record = f.next()
      record("firstName").toString.length should be >= 2
      record("lastName").toString.length should be >= 2
      record("fullName").toString should include(" ")
    }

    "produce internet fields with structural validation" in {
      val f      = GeneratedFeeder(
        "username" -> Faker.internet.username(),
        "url"      -> Faker.internet.url(),
        "ipv4"     -> Faker.internet.ipv4(),
        "domain"   -> Faker.internet.domain(),
      )
      val record = f.next()
      record("username").toString.length should be >= 3
      record("url").toString should startWith("http")
      record("url").toString should include("://")
      record("ipv4").toString should fullyMatch regex Ipv4Regex
      record("domain").toString should include(".")
    }

    "produce location with valid country code and coordinates" in {
      val f      = GeneratedFeeder(
        "countryCode" -> Faker.location.countryCode(),
        "latitude"    -> Faker.location.latitude(),
        "longitude"   -> Faker.location.longitude(),
      )
      val record = f.next()
      record("countryCode").toString should fullyMatch regex "[A-Z]{2}"
      record("latitude").asInstanceOf[Double] should (be >= -90.0 and be <= 90.0)
      record("longitude").asInstanceOf[Double] should (be >= -180.0 and be <= 180.0)
    }

    "produce commerce fields with prefix validation" in {
      val f      = GeneratedFeeder(
        "product"  -> Faker.commerce.productName(),
        "category" -> Faker.commerce.category(),
        "sku"      -> Faker.commerce.sku("ITEM"),
        "orderId"  -> Faker.commerce.orderId("ORD"),
      )
      val record = f.next()
      record("product").toString.length should be >= 3
      record("category").toString.length should be >= 3
      record("sku").toString should startWith("ITEM-")
      record("orderId").toString should startWith("ORD-")
    }

    "produce weather data with valid temperature range" in {
      val f      = GeneratedFeeder(
        "condition" -> Faker.weather.condition(),
        "temp"      -> Faker.weather.temperatureCelsius(),
      )
      val record = f.next()
      record("condition").toString.length should be >= 3
      record("temp").asInstanceOf[Double] should (be >= -60.0 and be <= 60.0)
    }

    "produce lorem text with word count" in {
      val f      = GeneratedFeeder(
        "word"     -> Faker.lorem.word(),
        "sentence" -> Faker.lorem.sentence(8),
      )
      val record = f.next()
      record("word").toString.length should be >= 2
      record("sentence").toString.split("\\s+").length should be >= 6
    }

    "produce country-specific IDs with format validation" in {
      val f      = GeneratedFeeder(
        "usSSN"  -> Faker.us.ssn(),
        "gbNINO" -> Faker.gb.nino(),
        "esNIF"  -> Faker.es.nif(),
      )
      val record = f.next()
      record("usSSN").toString should fullyMatch regex """\d{3}-\d{2}-\d{4}"""
      record("gbNINO").toString should fullyMatch regex """[A-Z]{2}\d{6}[A-D]"""
      record("esNIF").toString.length should (be >= 8 and be <= 9)
    }

    "produce generator combinators with correct structure" in {
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
      record("lat").asInstanceOf[Double] should (be >= -90.0 and be <= 90.0)
      record("lon").asInstanceOf[Double] should (be >= -180.0 and be <= 180.0)
    }

    "produce single-field feeder within bounds" in {
      val f = GeneratedFeeder.single("num", Faker.number.int(1, 999))
      f.next()("num").asInstanceOf[Int] should (be >= 1 and be <= 999)
    }

    "apply feeder transformations correctly" in {
      val f = GeneratedFeeder(
        "raw_name" -> Faker.person.fullName(),
        "debug"    -> Generator.const("internal"),
      ).rename("raw_name", "name")
        .dropKeys("debug")
        .withDefaults("role" -> "user")
        .requireKeys("name")
        .mapRecord(r => r + ("upper" -> r("name").toString.toUpperCase))

      val record = f.next()
      record("name").toString should include(" ")
      record("upper").toString shouldBe record("name").toString.toUpperCase
      record("role") shouldBe "user"
      record should not contain key("debug")
      record should not contain key("raw_name")
    }

    "enrich static feeder with generated traceId" in {
      val f = Iterator
        .continually(Map[String, Any]("source" -> "demo"))
        .withGenerated("traceId", Faker.uuid.string)

      val record = f.next()
      record("source") shouldBe "demo"
      record("traceId").toString should fullyMatch regex UuidRegex
    }

    "produce date range tuples where start <= end" in {
      val f      = GeneratedFeeder.records(
        Faker.date
          .range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 7, 30)
          .map { case (start, end) =>
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            Map[String, Any]("start" -> start.format(fmt), "end" -> end.format(fmt))
          },
      )
      val record = f.next()
      val start  = LocalDate.parse(record("start").toString)
      val end    = LocalDate.parse(record("end").toString)
      start.isBefore(end) || start.isEqual(end) shouldBe true
    }

    "produce localized data with matching country/currency/language" in {
      val f      = GeneratedFeeder.records(
        for {
          c    <- Faker.oneOf(Country.RU, Country.US, Country.DE)
          curr <- Faker.localization.currency(c)
          lang <- Faker.localization.languageCode(c)
        } yield Map[String, Any]("country" -> c.iso2, "currency" -> curr, "language" -> lang),
      )
      val record = f.next()
      record("country").toString should fullyMatch regex "[A-Z]{2}"
      record("currency").toString should have length 3
      record("language").toString.length should (be >= 2 and be <= 3)
    }

    "produce phone toll-free and builder variants" in {
      val f      = GeneratedFeeder(
        "tollFree" -> Faker.phone.tollFree(Country.US),
        "builder"  -> Faker.phone.builder.forCountry(Country.DE).withFormat(PhoneFormatMode.National).build,
      )
      val record = f.next()
      record("tollFree").toString should fullyMatch regex """[\d\s\(\)\-\+]+"""
      record("builder").toString should fullyMatch regex """[\d\s\(\)\-\+]+"""
    }

    "convert Map.toSingleRecordFeeder preserving types" in {
      val records = Map[String, Any]("key" -> "value", "flag" -> true, "count" -> 42).toSingleRecordFeeder
      records should have size 1
      records.head("key") shouldBe "value"
      records.head("flag") shouldBe true
      records.head("count") shouldBe 42
    }

    "apply renameKeys, suffixKeys, selectKeys correctly" in {
      val f = GeneratedFeeder(
        "alpha" -> Faker.string.alphabetic(4),
        "beta"  -> Faker.number.int(1, 100),
      ).renameKeys(Map("alpha" -> "kAlpha", "beta" -> "kBeta"))
        .suffixKeys("_demo")
        .selectKeys("kAlpha_demo")

      val record = f.next()
      record should contain key "kAlpha_demo"
      record should not contain key("kBeta_demo")
      record("kAlpha_demo").toString should have length 4
    }
  }

  "JWT from examples" should {
    "produce valid HMAC-signed three-part token" in {
      val gen = jwt("HS256", "performance-secret").defaultHeader
        .payload("""{"subject":"picatinny","scope":"smoke-test"}""")

      val fakeEventLoop = new org.galaxio.gatling.transactions.FakeEventLoop
      val session       = io.gatling.core.session.Session("smoke", 1L, fakeEventLoop)
      val result        = session.setJwt(gen, "jwt")
      val token         = result("jwt").as[String]

      token.split("\\.") should have length 3
      PdiJwt.isValid(token, "performance-secret", Seq(JwtAlgorithm.HS256)) shouldBe true
    }
  }

  "IntensityConverter from examples" should {
    "convert RPM to RPS" in {
      60.rpm shouldBe 1.0
      120.rpm shouldBe 2.0
    }

    "convert RPH to RPS" in {
      3600.rph shouldBe 1.0
      7200.rph shouldBe 2.0
    }
  }

  "SeparatedValuesFeeder from examples" should {
    "parse CSV string into correctly keyed records" in {
      val records = SeparatedValuesFeeder("csvValue", "alpha,beta,gamma", ',')
      records should have size 3
      records.map(_("csvValue")) shouldBe IndexedSeq("alpha", "beta", "gamma")
    }

    "parse CSV with split mapping preserving key structure" in {
      val records = SeparatedValuesFeeder(Some("split"), Seq(Map("HOSTS" -> "host1,host2", "USERS" -> "user1,user2")), ',')
      records should have size 4
      records.flatMap(_.keys).distinct should contain allOf ("split_HOSTS", "split_USERS")
    }
  }
}
