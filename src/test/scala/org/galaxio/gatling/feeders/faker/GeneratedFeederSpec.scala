package org.galaxio.gatling.feeders.faker

import org.galaxio.gatling.feeders.LuhnValidator
import org.galaxio.gatling.feeders.faker.Predef._
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.time.LocalDate

class GeneratedFeederSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  private val sampleCount = 50

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
      record("active") shouldBe a[java.lang.Boolean]
    }

    "produce multiple distinct records" in {
      val feeder  = GeneratedFeeder("id" -> Faker.uuid.string)
      val records = (1 to sampleCount).map(_ => feeder.next()("id").toString)

      records.distinct.size should be > 1
    }

    "reject empty field list" in {
      assertThrows[IllegalArgumentException] {
        GeneratedFeeder()
      }
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

    "build single-field typed feeder" in {
      val feeder = GeneratedFeeder.single("n", Faker.number.int(1, 10))
      val record = feeder.next()
      val value  = record("n").asInstanceOf[Int]
      value should (be >= 1 and be <= 10)
    }

    "reject empty field name" in {
      assertThrows[IllegalArgumentException] {
        GeneratedFeeder.single("", Faker.uuid.string)
      }
    }
  }

  "Generator" should {
    "map preserves laziness" in {
      val gen    = Faker.number.int(1, 100).map(_ * 2)
      val values = (1 to sampleCount).map(_ => gen.sample())
      values.distinct.size should be > 1
      all(values) should (be >= 2 and be <= 200)
    }

    "flatMap chains generators correctly" in {
      forAll(Gen.choose(1, 10)) { n =>
        val s = Faker.number.int(1, n).flatMap(i => Faker.string.alphabetic(i)).sample()
        s.length should (be >= 1 and be <= n)
      }
    }

    "zip combines two generators" in {
      val gen    = Faker.number.int(0, 100).zip(Faker.number.boolean)
      val (n, b) = gen.sample()
      n should (be >= 0 and be <= 100)
      b shouldBe a[java.lang.Boolean]
    }

    "const always returns same value" in {
      forAll(Gen.alphaNumStr) { s =>
        Generator.const(s).sample() shouldBe s
      }
    }

    "filter retries until predicate matches" in {
      val even = Faker.number.int(1, 100).filter(_ % 2 == 0)
      (1 to sampleCount).foreach { _ =>
        even.sample() % 2 shouldBe 0
      }
    }

    "toString is readable" in {
      Generator.const(42).toString shouldBe "Generator(<lazy>)"
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

    "convert single map to one-record feeder" in {
      val records = Map("key" -> "value").toSingleRecordFeeder
      records should have size 1
      records.head shouldBe Map("key" -> "value")
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

    "add several generated fields to an existing feeder" in {
      val existing = Iterator.single(Map("id" -> "42"))
      val enriched = existing.withGenerated(
        "active"   -> Generator.const(true),
        "currency" -> Generator.const("USD"),
      )

      enriched.next() shouldBe Map("id" -> "42", "active" -> true, "currency" -> "USD")
    }

    "widen typed feeder records safely in withGenerated" in {
      val stringFeeder: Iterator[Map[String, String]] = Iterator.single(Map("name" -> "alice"))
      val enriched                                    = GeneratedFeeder.withGenerated(stringFeeder, "score", Generator.const(42))

      val record = enriched.next()
      record("name") shouldBe "alice"
      record("score") shouldBe 42
    }

    "rename feeder keys" in {
      val feeder  = Iterator.single(Map("old_name" -> "value"))
      val renamed = feeder.rename("old_name", "new_name")
      val record  = renamed.next()

      record should contain key "new_name"
      record should not contain key("old_name")
      record("new_name") shouldBe "value"
    }

    "rename non-existent key is no-op" in {
      val feeder = Iterator.single(Map("key" -> "value"))
      val result = feeder.rename("missing", "new").next()
      result shouldBe Map("key" -> "value")
    }

    "rename several feeder keys" in {
      val feeder = Iterator.single(Map("first_name" -> "Ada", "last_name" -> "Lovelace", "age" -> 36))
      val result = feeder.renameKeys(Map("first_name" -> "firstName", "last_name" -> "lastName")).next()

      result shouldBe Map("firstName" -> "Ada", "lastName" -> "Lovelace", "age" -> 36)
    }

    "prefix all keys" in {
      val feeder   = Iterator.single(Map("name" -> "Alice", "age" -> 30))
      val prefixed = feeder.prefixKeys("user_")
      val record   = prefixed.next()

      record should contain key "user_name"
      record should contain key "user_age"
      record should not contain key("name")
    }

    "suffix all keys" in {
      val feeder = Iterator.single(Map("name" -> "Alice", "age" -> 30))
      val record = feeder.suffixKeys("_value").next()

      record shouldBe Map("name_value" -> "Alice", "age_value" -> 30)
    }

    "drop selected keys" in {
      val feeder = Iterator.single(Map("id" -> "42", "debug" -> true, "email" -> "a@example.com"))
      feeder.dropKeys("debug").next() shouldBe Map("id" -> "42", "email" -> "a@example.com")
    }

    "select selected keys" in {
      val feeder = Iterator.single(Map("id" -> "42", "debug" -> true, "email" -> "a@example.com"))
      feeder.selectKeys("id", "email").next() shouldBe Map("id" -> "42", "email" -> "a@example.com")
    }

    "add defaults without overriding existing values" in {
      val feeder = Iterator.single(Map("currency" -> "EUR"))
      feeder.withDefaults("currency" -> "USD", "active" -> true).next() shouldBe Map("currency" -> "EUR", "active" -> true)
    }

    "require keys before records are consumed by scenarios" in {
      val valid = Iterator.single(Map("id" -> "42", "email" -> "a@example.com")).requireKeys("id", "email")
      valid.next() shouldBe Map("id" -> "42", "email" -> "a@example.com")

      assertThrows[IllegalArgumentException] {
        Iterator.single(Map("id" -> "42")).requireKeys("id", "email").next()
      }
    }

    "apply record transformation" in {
      val feeder = Iterator.single(Map("x" -> 1, "y" -> 2))
      val mapped = feeder.mapRecord(r => r + ("sum" -> (r("x").asInstanceOf[Int] + r("y").asInstanceOf[Int])))
      val record = mapped.next()

      record("sum") shouldBe 3
    }

    "take finite records" in {
      forAll(Gen.choose(0, 100)) { n =>
        val records = Iterator.continually(Map("n" -> 1)).takeRecords(n)
        records should have size n
      }
    }

    "takeRecords rejects negative" in {
      assertThrows[IllegalArgumentException] {
        Iterator.continually(Map("n" -> 1)).takeRecords(-1)
      }
    }
  }

  "Faker.number" should {
    "generate ints within range" in {
      forAll(Gen.choose(-1000, 1000), Gen.choose(0, 2000)) { (base, span) =>
        whenever(span > 0) {
          val min = base
          val max = base + span
          val v   = Faker.number.int(min, max).sample()
          v should (be >= min and be <= max)
        }
      }
    }

    "handle min == max for int" in {
      forAll(Gen.choose(-100, 100)) { n =>
        Faker.number.int(n, n).sample() shouldBe n
      }
    }

    "handle Int.MaxValue as an inclusive upper bound" in {
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.int(Int.MaxValue - 10, Int.MaxValue).sample()
        v should (be >= Int.MaxValue - 10 and be <= Int.MaxValue)
      }
    }

    "reject min > max for int" in {
      assertThrows[IllegalArgumentException] {
        Faker.number.int(10, 5)
      }
    }

    "generate longs within range" in {
      forAll(Gen.choose(0L, 10000L)) { span =>
        whenever(span > 0) {
          val v = Faker.number.long(0L, span).sample()
          v should (be >= 0L and be <= span)
        }
      }
    }

    "handle Long.MaxValue as an inclusive upper bound" in {
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.long(Long.MaxValue - 10L, Long.MaxValue).sample()
        v should (be >= Long.MaxValue - 10L and be <= Long.MaxValue)
      }
    }

    "generate doubles within range" in {
      forAll(Gen.choose(0.0, 1000.0)) { max =>
        whenever(max > 0.01) {
          val v = Faker.number.double(0.0, max).sample()
          v should (be >= 0.0 and be < max)
        }
      }
    }

    "generate floats within range" in {
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.float(0.0f, 1.0f).sample()
        v should (be >= 0.0f and be < 1.0f)
      }
    }

    "generate booleans with both values" in {
      val values = (1 to 100).map(_ => Faker.number.boolean.sample())
      values should contain(true)
      values should contain(false)
    }

    "generate bytes within range" in {
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.byte(0, 100).sample()
        v should (be >= 0.toByte and be <= 100.toByte)
      }
    }

    "generate shorts within range" in {
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.short(0, 1000).sample()
        v should (be >= 0.toShort and be <= 1000.toShort)
      }
    }

    "generate chars within range" in {
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.char('A', 'Z').sample()
        v should (be >= 'A' and be <= 'Z')
      }
    }

    "generate bigDecimal within range" in {
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.bigDecimal(BigDecimal(0), BigDecimal(100), 3).sample()
        v should (be >= BigDecimal(0) and be <= BigDecimal(100))
        v.scale shouldBe 3
      }
    }

    "generate BigInt beyond Long range" in {
      val min = BigInt(Long.MaxValue) + 1
      val max = min + 100
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.bigInt(min, max).sample()
        v should (be >= min and be <= max)
      }
    }

    "generate positive and negative ints" in {
      Faker.number.positiveInt.sample() should be > 0
      Faker.number.negativeInt.sample() should be < 0
    }

    "generate percentage 0-100" in {
      (1 to sampleCount).foreach { _ =>
        val v = Faker.number.percentage.sample()
        v should (be >= 0 and be <= 100)
      }
    }
  }

  "Faker.string" should {
    "generate alphabetic strings of exact length" in {
      forAll(Gen.choose(1, 50)) { len =>
        val s = Faker.string.alphabetic(len).sample()
        s should have length len
        s should fullyMatch regex s"[a-zA-Z]{$len}"
      }
    }

    "generate alphanumeric strings" in {
      forAll(Gen.choose(1, 50)) { len =>
        val s = Faker.string.alphanumeric(len).sample()
        s should have length len
      }
    }

    "generate numeric strings" in {
      forAll(Gen.choose(1, 20)) { len =>
        val s = Faker.string.numeric(len).sample()
        s should have length len
        s should fullyMatch regex s"\\d{$len}"
      }
    }

    "generate hex strings" in {
      val s = Faker.string.hex(12).sample()
      s should have length 12
      s should fullyMatch regex "[a-fA-F0-9]{12}"
    }

    "generate cyrillic strings" in {
      val s = Faker.string.cyrillic(10).sample()
      s should have length 10
    }

    "generate strings from custom alphabet" in {
      val s = Faker.string.fromAlphabet("abc", 20).sample()
      s should have length 20
      s.toSet.subsetOf(Set('a', 'b', 'c')) shouldBe true
    }

    "generate strings with length between min and max" in {
      forAll(Gen.choose(1, 10), Gen.choose(1, 20)) { (min, extra) =>
        val max = min + extra
        val s   = Faker.string.lengthBetween(min, max, "abcdef").sample()
        s.length should (be >= min and be <= max)
      }
    }
  }

  "Faker.uuid" should {
    "generate valid UUID strings" in {
      forAll(Gen.const(())) { _ =>
        Faker.uuid.string.sample() should fullyMatch regex "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
      }
    }

    "generate distinct UUIDs" in {
      val uuids = (1 to sampleCount).map(_ => Faker.uuid.string.sample())
      uuids.distinct.size shouldBe sampleCount
    }
  }

  "Faker.person" should {
    "generate gender values" in {
      val genders = (1 to 100).map(_ => Faker.person.gender().sample()).toSet
      genders should contain(Gender.Male)
      genders should contain(Gender.Female)
      genders should contain(Gender.Unspecified)
    }

    "generate non-empty first names" in {
      Faker.person.firstName().sample() should not be empty
    }

    "generate gendered first names" in {
      val maleName = Faker.person.firstName(Gender.Male).sample()
      FakerData.maleFirstNames should contain(maleName)

      val femaleName = Faker.person.firstName(Gender.Female).sample()
      FakerData.femaleFirstNames should contain(femaleName)
    }

    "generate non-empty last names" in {
      FakerData.lastNames should contain(Faker.person.lastName().sample())
    }

    "generate full names with space" in {
      val name = Faker.person.fullName().sample()
      name should include(" ")
    }

    "generate job titles from catalog" in {
      FakerData.jobTitles should contain(Faker.person.jobTitle().sample())
    }

    "generate prefixes from catalog" in {
      FakerData.personPrefixes should contain(Faker.person.prefix().sample())
    }
  }

  "Faker.internet" should {
    "generate emails with default domain" in {
      Faker.internet.email().sample() should endWith("@example.com")
    }

    "generate emails with custom domain" in {
      forAll(Gen.identifier.suchThat(_.nonEmpty)) { domain =>
        Faker.internet.email(s"$domain.com").sample() should endWith(s"@$domain.com")
      }
    }

    "generate emails from name" in {
      val email = Faker.internet.email("John Smith", "corp.com").sample()
      email should endWith("@corp.com")
      email should include("john")
    }

    "generate usernames" in {
      val username = Faker.internet.username().sample()
      username should not be empty
      username should not contain " "
    }

    "generate URLs with protocol" in {
      Faker.internet.url().sample() should startWith("https://")
      Faker.internet.url("http").sample() should startWith("http://")
    }

    "generate passwords of specified length" in {
      forAll(Gen.choose(8, 64)) { len =>
        Faker.internet.password(len).sample() should have length len
      }
    }

    "generate user agents from catalog" in {
      FakerData.userAgents should contain(Faker.internet.userAgent().sample())
    }

    "generate valid IPv4 addresses" in {
      (1 to sampleCount).foreach { _ =>
        val ip     = Faker.internet.ipv4().sample()
        val octets = ip.split("\\.").map(_.toInt)
        octets should have length 4
        octets(0) should (be >= 1 and be <= 254)
        octets(1) should (be >= 0 and be <= 255)
        octets(2) should (be >= 0 and be <= 255)
        octets(3) should (be >= 1 and be <= 254)
      }
    }

    "generate valid IPv6 addresses" in {
      val ipv6 = Faker.internet.ipv6().sample()
      ipv6.split(":") should have length 8
    }

    "generate domains from catalog" in {
      FakerData.domains should contain(Faker.internet.domain().sample())
    }
  }

  "Faker.location" should {
    "generate countries" in {
      val country = Faker.location.country().sample()
      country shouldBe a[Country]
    }

    "generate country codes" in {
      val code = Faker.location.countryCode().sample()
      code should have length 2
    }

    "generate cities for country" in {
      val city = Faker.location.city(Country.RU).sample()
      FakerData.citiesByCountry(Country.RU) should contain(city)
    }

    "fallback to US cities for unknown country" in {
      val city = Faker.location.city(Country.custom("XX")).sample()
      FakerData.citiesByCountry(Country.US) should contain(city)
    }

    "generate street addresses" in {
      val addr = Faker.location.streetAddress().sample()
      addr should not be empty
      addr should fullyMatch regex "\\d+ .+"
    }

    "generate postal codes per country" in {
      Faker.location.postalCode(Country.RU).sample() should fullyMatch regex "\\d{6}"
      Faker.location.postalCode(Country.US).sample() should fullyMatch regex "\\d{5}"
      Faker.location.postalCode(Country.BR).sample() should fullyMatch regex "\\d{8}"
      Faker.location.postalCode(Country.AR).sample() should fullyMatch regex "\\d{4}"
      Faker.location.postalCode(Country.JP).sample() should fullyMatch regex "\\d{7}"
      Faker.location.postalCode(Country.AU).sample() should fullyMatch regex "\\d{4}"
      Faker.location.postalCode(Country.MX).sample() should fullyMatch regex "\\d{5}"
    }

    "generate latitude within valid range" in {
      (1 to sampleCount).foreach { _ =>
        val lat = Faker.location.latitude().sample()
        lat should (be >= -90.0 and be < 90.0)
      }
    }

    "generate longitude within valid range" in {
      (1 to sampleCount).foreach { _ =>
        val lon = Faker.location.longitude().sample()
        lon should (be >= -180.0 and be < 180.0)
      }
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

    "generate dates between boundaries" in {
      val from = LocalDate.of(2025, 1, 1)
      val to   = LocalDate.of(2025, 12, 31)
      (1 to sampleCount).foreach { _ =>
        val d = Faker.date.between(from, to).sample()
        d.isBefore(from) shouldBe false
        d.isAfter(to) shouldBe false
      }
    }

    "handle same from and to date" in {
      val d = LocalDate.of(2025, 6, 15)
      Faker.date.between(d, d).sample() shouldBe d
    }

    "reject from after to" in {
      assertThrows[IllegalArgumentException] {
        Faker.date.between(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1))
      }
    }

    "generate past dates within bounds" in {
      forAll(Gen.choose(1L, 365L)) { days =>
        val today = LocalDate.now()
        val d     = Faker.date.past(days, today).sample()
        d.isAfter(today) shouldBe false
        d.isBefore(today.minusDays(days)) shouldBe false
      }
    }

    "generate future dates within bounds" in {
      forAll(Gen.choose(1L, 365L)) { days =>
        val today = LocalDate.now()
        val d     = Faker.date.future(days, today).sample()
        d.isBefore(today) shouldBe false
        d.isAfter(today.plusDays(days)) shouldBe false
      }
    }

    "generate date offset within bounds" in {
      val base = LocalDate.of(2025, 6, 1)
      (1 to sampleCount).foreach { _ =>
        val d = Faker.date.offset(base, -10, 10).sample()
        d.isBefore(base.minusDays(10)) shouldBe false
        d.isAfter(base.plusDays(10)) shouldBe false
      }
    }

    "generate date ranges where start <= end" in {
      val from = LocalDate.of(2025, 1, 1)
      val to   = LocalDate.of(2025, 12, 31)
      (1 to sampleCount).foreach { _ =>
        val (start, end) = Faker.date.range(from, to, 1, 30).sample()
        start.isAfter(end) shouldBe false
        start.isBefore(from) shouldBe false
        end.isAfter(to) shouldBe false
      }
    }

    "generate date ranges respecting minimum length" in {
      val from = LocalDate.of(2025, 1, 1)
      val to   = LocalDate.of(2025, 1, 10)
      (1 to sampleCount).foreach { _ =>
        val (start, end) = Faker.date.range(from, to, minLengthDays = 5, maxLengthDays = 7).sample()
        java.time.temporal.ChronoUnit.DAYS.between(start, end) should (be >= 5L and be <= 7L)
      }
    }

    "reject date ranges where minimum length cannot fit" in {
      assertThrows[IllegalArgumentException] {
        Faker.date.range(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3), minLengthDays = 5)
      }
    }
  }

  "Faker.finance" should {
    "generate PAN numbers passing Luhn validation" in {
      (1 to sampleCount).foreach { _ =>
        val pan = Faker.finance.pan().sample()
        withClue(s"PAN $pan failed Luhn: ") {
          LuhnValidator.validate(pan) shouldBe true
        }
      }
    }

    "generate PAN with specified BINs" in {
      (1 to sampleCount).foreach { _ =>
        val pan = Faker.finance.pan("421345", "541673").sample()
        withClue(s"PAN $pan failed Luhn: ") {
          LuhnValidator.validate(pan) shouldBe true
        }
        pan should (startWith("421345") or startWith("541673"))
      }
    }

    "generate amounts within range" in {
      (1 to sampleCount).foreach { _ =>
        val amt = Faker.finance.amount(BigDecimal(10), BigDecimal(100)).sample()
        amt should (be >= BigDecimal(10) and be <= BigDecimal(100))
        amt.scale shouldBe 2
      }
    }

    "generate money with currency" in {
      val m = Faker.finance.money(BigDecimal(1), BigDecimal(10), "EUR").sample()
      m.currency shouldBe "EUR"
      m.amount should (be >= BigDecimal(1) and be <= BigDecimal(10))
    }

    "generate currencies from catalog" in {
      FakerData.currencies should contain(Faker.finance.currency().sample())
    }

    "generate account numbers of specified length" in {
      forAll(Gen.choose(10, 30)) { len =>
        Faker.finance.accountNumber(len).sample() should fullyMatch regex s"\\d{$len}"
      }
    }

    "generate BIC codes" in {
      val bic = Faker.finance.bic().sample()
      bic.length should (be >= 8 and be <= 11)
    }

    "generate IBAN for multiple countries" in {
      Faker.finance.iban(Country.DE).sample() should startWith("DE89")
      Faker.finance.iban(Country.GB).sample() should startWith("GB82")
      Faker.finance.iban(Country.FR).sample() should startWith("FR14")
      Faker.finance.iban(Country.ES).sample() should startWith("ES91")
      Faker.finance.iban(Country.IT).sample() should startWith("IT60")
      Faker.finance.iban(Country.RU).sample() should startWith("RU33")
      Faker.finance.iban(Country.BR).sample() should startWith("BR18")
    }

    "generate transaction IDs with prefix" in {
      Faker.finance.transactionId("pay").sample() should startWith("pay-")
    }
  }

  "Faker.commerce" should {
    "generate product names from catalog" in {
      FakerData.products should contain(Faker.commerce.productName().sample())
    }

    "generate categories from catalog" in {
      FakerData.categories should contain(Faker.commerce.category().sample())
    }

    "generate SKUs with prefix" in {
      Faker.commerce.sku("ITEM").sample() should startWith("ITEM-")
    }

    "generate order IDs" in {
      Faker.commerce.orderId().sample() should startWith("ord-")
    }
  }

  "Russian government identifiers" should {
    "generate INN (person) with correct format" in {
      (1 to sampleCount).foreach { _ =>
        val inn = Faker.ru.inn.person().sample()
        inn should fullyMatch regex "[0-9][1-9]\\d{8}|[1-9][0-9]\\d{8}"
      }
    }

    "generate INN (company) with correct format" in {
      (1 to sampleCount).foreach { _ =>
        Faker.ru.inn.company().sample() should fullyMatch regex "\\d{12}"
      }
    }

    "generate KPP with correct format" in {
      (1 to sampleCount).foreach { _ =>
        Faker.ru.kpp().sample() should fullyMatch regex "\\d{9}"
      }
    }

    "generate OGRN with valid checksum" in {
      (1 to sampleCount).foreach { _ =>
        val ogrn = Faker.ru.ogrn().sample()
        ogrn should have length 13
        withClue(s"OGRN $ogrn checksum: ") {
          ogrn.substring(0, 12).toLong % 11 % 10 shouldBe ogrn.substring(12, 13).toInt
        }
      }
    }

    "generate OGRNIP with valid checksum" in {
      (1 to sampleCount).foreach { _ =>
        val ogrnip = Faker.ru.ogrnip().sample()
        ogrnip should have length 15
        withClue(s"OGRNIP $ogrnip checksum: ") {
          ogrnip.substring(0, 14).toLong % 13 % 10 shouldBe ogrnip.substring(14, 15).toInt
        }
      }
    }

    "generate SNILS with correct format" in {
      (1 to sampleCount).foreach { _ =>
        Faker.ru.snils().sample() should fullyMatch regex "\\d{11}"
      }
    }

    "generate passport with correct format" in {
      (1 to sampleCount).foreach { _ =>
        Faker.passport.ru().sample() should fullyMatch regex "\\d{10}"
      }
    }
  }

  "Brazilian identifiers" should {
    "generate formatted CPF" in {
      (1 to sampleCount).foreach { _ =>
        Faker.br.cpf(formatted = true).sample() should fullyMatch regex "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}"
      }
    }

    "generate raw CPF with valid checksum" in {
      (1 to sampleCount).foreach { _ =>
        val cpf = Faker.br.cpf().sample()
        cpf should fullyMatch regex "\\d{11}"
        withClue(s"CPF $cpf checksum: ") {
          validateCpf(cpf) shouldBe true
        }
      }
    }
  }

  "Argentinian identifiers" should {
    "generate DNI values" in {
      (1 to sampleCount).foreach { _ =>
        val dni = Faker.ar.dni().sample()
        dni should fullyMatch regex "\\d{8}"
        dni.toInt should (be >= 10000000 and be <= 99999999)
      }
    }

    "generate formatted DNI" in {
      Faker.ar.dni(formatted = true).sample() should fullyMatch regex "\\d{2}\\.\\d{3}\\.\\d{3}"
    }
  }

  "US identifiers" should {
    "generate SSN with valid format" in {
      (1 to sampleCount).foreach { _ =>
        val ssn  = Faker.us.ssn().sample()
        ssn should fullyMatch regex "\\d{3}-\\d{2}-\\d{4}"
        val area = ssn.take(3).toInt
        area should not be 0
        area should not be 666
        area should be < 900
      }
    }

    "generate raw SSN without dashes" in {
      Faker.us.ssn(formatted = false).sample() should fullyMatch regex "\\d{9}"
    }
  }

  "UK identifiers" should {
    "generate NINO with valid format" in {
      (1 to sampleCount).foreach { _ =>
        val nino   = Faker.gb.nino().sample()
        nino should fullyMatch regex "[A-Z]{2}\\d{6}[A-D]"
        val prefix = nino.take(2)
        Set("BG", "GB", "NK", "KN", "TN", "NT", "ZZ") should not contain prefix
        "DFIQUV".toSet should not contain nino.head
      }
    }
  }

  "French identifiers" should {
    "generate NIR with valid key" in {
      (1 to sampleCount).foreach { _ =>
        val nir  = Faker.fr.nir().sample()
        nir should fullyMatch regex "\\d{15}"
        val base = nir.take(13).toLong
        val key  = nir.takeRight(2).toInt
        key shouldBe (97 - (base % 97)).toInt
      }
    }
  }

  "Spanish identifiers" should {
    "generate NIF with valid check letter" in {
      (1 to sampleCount).foreach { _ =>
        val nif     = Faker.es.nif().sample()
        nif should fullyMatch regex "\\d{8}[A-Z]"
        val digits  = nif.take(8).toInt
        val letters = "TRWAGMYFPDXBNJZSQVHLCKE"
        nif.last shouldBe letters.charAt(digits % 23)
      }
    }
  }

  "Italian identifiers" should {
    "generate Codice Fiscale with correct structure" in {
      (1 to sampleCount).foreach { _ =>
        val cf = Faker.it.codiceFiscale().sample()
        cf should have length 16
        cf should fullyMatch regex "[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]"
      }
    }
  }

  "German identifiers" should {
    "generate Steueridentifikationsnummer" in {
      (1 to sampleCount).foreach { _ =>
        val tin = Faker.de.steueridentifikationsnummer().sample()
        tin should fullyMatch regex "[1-9]\\d{10}"
      }
    }
  }

  "Passport for various countries" should {
    "generate correct formats" in {
      Faker.passport.number(Country.US).sample() should fullyMatch regex "\\d{9}"
      Faker.passport.number(Country.AR).sample() should fullyMatch regex "[A-Z0-9]{9}"
      Faker.passport.number(Country.BR).sample() should fullyMatch regex "[A-Z0-9]{8}"
    }
  }

  "Faker.phone" should {
    "generate Russian mobile phones" in {
      (1 to sampleCount).foreach { _ =>
        Faker.phone.mobile(Country.RU).sample() should startWith("+7")
      }
    }

    "generate US mobile phones" in {
      (1 to sampleCount).foreach { _ =>
        Faker.phone.mobile(Country.US).sample() should startWith("+1")
      }
    }

    "generate phones for all 16 supported countries" in {
      val countries = Seq(
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
      countries.foreach { c =>
        noException should be thrownBy Faker.phone.mobile(c).sample()
      }
    }

    "build phones via builder" in {
      val phone = Faker.phone.builder
        .forCountry(Country.RU)
        .withFormat(PhoneFormatMode.E164)
        .build
        .sample()
      phone should startWith("+7")
    }

    "build custom phones with builder" in {
      val phone = Faker.phone.builder
        .withCountryCode("+7")
        .withAreaCodes("999")
        .withLength(10)
        .build
        .sample()
      phone should include("999")
    }

    "fail clearly for unsupported country phone metadata" in {
      val error = intercept[IllegalArgumentException] {
        Faker.phone.mobile(Country.custom("ZZ")).sample()
      }
      error.getMessage should include("No phone formats configured")
    }
  }

  "Faker.weather" should {
    "generate conditions from catalog" in {
      FakerData.weatherConditions should contain(Faker.weather.condition().sample())
    }

    "generate temperature within range" in {
      (1 to sampleCount).foreach { _ =>
        Faker.weather.temperatureCelsius().sample() should (be >= -30.0 and be <= 45.0)
      }
    }

    "generate humidity 0-100" in {
      (1 to sampleCount).foreach { _ =>
        Faker.weather.humidityPercent().sample() should (be >= 0 and be <= 100)
      }
    }

    "generate pressure in realistic range" in {
      (1 to sampleCount).foreach { _ =>
        Faker.weather.pressureHPa().sample() should (be >= 950 and be <= 1050)
      }
    }
  }

  "Faker.lorem" should {
    "generate words from catalog" in {
      FakerData.loremWords should contain(Faker.lorem.word().sample())
    }

    "generate word sequences of exact count" in {
      forAll(Gen.choose(1, 20)) { n =>
        Faker.lorem.words(n).sample().split(" ") should have length n
      }
    }

    "generate sentences with capitalization and period" in {
      val sentence = Faker.lorem.sentence(4).sample()
      sentence.head.isUpper shouldBe true
      sentence should endWith(".")
    }

    "reject zero word count" in {
      assertThrows[IllegalArgumentException] {
        Faker.lorem.words(0)
      }
    }
  }

  "Faker.localization" should {
    "map countries to currencies" in {
      Faker.localization.currency(Country.RU).sample() shouldBe "RUB"
      Faker.localization.currency(Country.US).sample() shouldBe "USD"
      Faker.localization.currency(Country.DE).sample() shouldBe "EUR"
      Faker.localization.currency(Country.GB).sample() shouldBe "GBP"
      Faker.localization.currency(Country.JP).sample() shouldBe "JPY"
      Faker.localization.currency(Country.CN).sample() shouldBe "CNY"
      Faker.localization.currency(Country.IN).sample() shouldBe "INR"
      Faker.localization.currency(Country.CA).sample() shouldBe "CAD"
      Faker.localization.currency(Country.AU).sample() shouldBe "AUD"
      Faker.localization.currency(Country.MX).sample() shouldBe "MXN"
    }

    "map countries to language codes" in {
      Faker.localization.languageCode(Country.RU).sample() shouldBe "ru"
      Faker.localization.languageCode(Country.BR).sample() shouldBe "pt"
      Faker.localization.languageCode(Country.FR).sample() shouldBe "fr"
      Faker.localization.languageCode(Country.JP).sample() shouldBe "ja"
      Faker.localization.languageCode(Country.CN).sample() shouldBe "zh"
    }
  }

  "Faker.oneOf" should {
    "pick from sequence" in {
      val items  = Vector("a", "b", "c")
      val picked = (1 to 100).map(_ => Faker.oneOf(items).sample()).toSet
      picked.subsetOf(items.toSet) shouldBe true
      picked.size should be > 1
    }

    "pick from varargs" in {
      val picked = Faker.oneOf("x", "y", "z").sample()
      Set("x", "y", "z") should contain(picked)
    }

    "reject empty sequence" in {
      assertThrows[IllegalArgumentException] {
        Faker.oneOf(Vector.empty[String])
      }
    }
  }

  "Generator combinators" should {
    "sequence collects results" in {
      val gens   = Seq(Generator.const(1), Generator.const(2), Generator.const(3))
      val result = Generator.sequence(gens).sample()
      result shouldBe Vector(1, 2, 3)
    }

    "listOf generates n values" in {
      forAll(Gen.choose(0, 50)) { n =>
        val result = Generator.listOf(n, Faker.number.int(1, 10)).sample()
        result should have size n
        all(result) should (be >= 1 and be <= 10)
      }
    }

    "mapOf generates key-value pairs" in {
      val result = Generator
        .mapOf(
          (Generator.const("a"), Faker.number.int(1, 10)),
          (Generator.const("b"), Faker.number.int(1, 10)),
        )
        .sample()
      result should contain key "a"
      result should contain key "b"
    }

    "tupleOf combines generators" in {
      val (a, b) = Generator.tupleOf(Generator.const(1), Generator.const("x")).sample()
      a shouldBe 1
      b shouldBe "x"
    }

    "tupleOf3 combines three generators" in {
      val (a, b, c) = Generator.tupleOf(Generator.const(1), Generator.const("x"), Generator.const(true)).sample()
      a shouldBe 1
      b shouldBe "x"
      c shouldBe true
    }
  }

  "Model classes" should {
    "reject empty field name" in {
      assertThrows[IllegalArgumentException] {
        Field("", Generator.const("x"))
      }
    }

    "reject empty currency in Money" in {
      assertThrows[IllegalArgumentException] {
        Money(BigDecimal(1), "")
      }
    }

    "reject empty iso2 in custom Country" in {
      assertThrows[IllegalArgumentException] {
        Country.custom("")
      }
    }

    "provide Java-friendly BigDecimal in Money" in {
      val m = Money(BigDecimal("19.99"), "USD")
      m.javaAmount shouldBe new java.math.BigDecimal("19.99")
    }
  }

  "toFeeder syntax on Generator" should {
    "create single-field feeder" in {
      val feeder = Faker.number.int(1, 10).toFeeder("num")
      val record = feeder.next()
      record should contain key "num"
    }
  }

  private def validateCpf(cpf: String): Boolean = {
    val digits                 = cpf.map(_.asDigit)
    def check(count: Int): Int = {
      val sum = digits.take(count).zip((count + 1) to 2 by -1).map { case (d, w) => d * w }.sum
      val mod = (sum * 10) % 11
      if (mod == 10) 0 else mod
    }
    digits(9) == check(9) && digits(10) == check(10)
  }
}
