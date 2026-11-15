package org.galaxio.gatling.utils

import org.galaxio.gatling.feeders.faker.Faker
import org.galaxio.gatling.feeders.{GovIdValidators, LuhnValidator}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.DAYS
import java.time.{LocalDateTime, ZoneId}
import scala.annotation.nowarn
import scala.io.Source
import scala.reflect.runtime.{universe => ru}
import scala.util.Using

/** Locks the deprecation of the last `RandomDataGenerators` helpers in favour of the Faker API (BCKLG-498; test-model layers 5
  * and 1).
  *
  * The pins are read by runtime reflection: that is what a consumer's compiler sees, and it survives reformatting. The
  * equivalence checks call both the deprecated method and its Faker analogue, so the replacement table is proven rather than
  * restated. Only the checks that call a deprecated method on purpose carry `@nowarn("cat=deprecation")`.
  */
class RandomDataGeneratorsDeprecationSpec extends AnyWordSpec with Matchers {
  import RandomDataGeneratorsDeprecationSpec._

  private val Samples = 1000

  private def samples[A](draw: => A): Set[A] = Iterator.continually(draw).take(Samples).toSet

  private def read(path: String): String =
    Using(Source.fromFile(path, "UTF-8"))(_.mkString).getOrElse(fail(s"cannot read $path"))

  private val from    = LocalDateTime.of(2026, 5, 7, 12, 0)
  private val utc     = ZoneId.of("UTC")
  private val pattern = "yyyy-MM-dd"

  /** The base date shifted by each of `offsets` days, rendered as `pattern` renders it. */
  private def days(offsets: Int*): Set[String] =
    offsets.map(k => from.plusDays(k.toLong).format(DateTimeFormatter.ofPattern(pattern))).toSet

  private def pinnedTo(name: String, overloads: Int, message: String): Unit = {
    val found = deprecations(RandomDataGeneratorsType, name)
    withClue(s"overloads of RandomDataGenerators.$name: ") {
      found should have size overloads.toLong
    }
    found.foreach { case (method, pins) =>
      withClue(s"RandomDataGenerators.$method: ") {
        pins shouldBe List(Deprecation(message, Since))
      }
    }
  }

  "the deprecated RandomDataGenerators helpers" should {

    "FR-001: pin randomString to Faker.string.fromAlphabet, since faker-api" in {
      pinnedTo("randomString", 1, RandomStringMessage)
    }

    "FR-002: pin randomUUID to Faker.uuid.string, since faker-api" in {
      pinnedTo("randomUUID", 1, RandomUuidMessage)
    }

    "FR-003: pin each of the three randomValue overloads to Faker.number.int / long / float / double, since faker-api" in {
      pinnedTo("randomValue", 3, RandomValueMessage)
    }

    "FR-004: pin both randomDate overloads to Faker.date.offset with Faker.date.formatDateTime, since faker-api" in {
      pinnedTo("randomDate", 2, RandomDateMessage)
    }
  }

  "the helpers that stay" should {

    "FR-008: leave digitString, hexString, cyrillicString, lettersString, alphanumericString and currentDate undeprecated" in {
      val pinned = KeptHelpers.flatMap { name =>
        val found = deprecations(RandomDataGeneratorsType, name)
        withClue(s"RandomDataGenerators.$name must exist: ")(found should not be empty)
        found.collect { case (method, pins) if pins.nonEmpty => s"$method -> $pins" }
      }
      pinned shouldBe empty
    }

    "FR-008: leave Faker.string.fromAlphabet and every analogue a pin names undeprecated" in {
      val pinned = (FakerAnalogues + "Faker.string.fromAlphabet").toList.sorted.flatMap { path =>
        val found = fakerMethod(path)
        withClue(s"$path must exist on the Faker API: ")(found should not be empty)
        found.collect { case (method, pins) if pins.nonEmpty => s"$path ($method) -> $pins" }
      }
      pinned shouldBe empty
    }

    "FR-010: be read by a reader that sees the pin randomOGRN already carries, so a missing pin cannot pass unseen" in {
      deprecations(RandomDataGeneratorsType, "randomOGRN").map(_._2) shouldBe
        List(List(Deprecation("Use Faker.ru.ogrn (with GeneratedFeeder) instead", Since)))
    }
  }

  "every pin" should {

    "FR-006: name analogues that exist on the Faker API today" in {
      FakerAnalogues shouldBe Set(
        "Faker.string.fromAlphabet",
        "Faker.uuid.string",
        "Faker.number.int",
        "Faker.number.long",
        "Faker.number.float",
        "Faker.number.double",
        "Faker.date.offset",
        "Faker.date.formatDateTime",
      )
      FakerAnalogues.toList.sorted.foreach { path =>
        withClue(s"$path: ")(fakerMethod(path) should not be empty)
      }
    }
  }

  "the deprecated helpers" should {

    "FR-005: keep the signatures existing callers compile against" in {
      val string: String = RandomDataGenerators.randomString("ab")(4)
      val uuid: String   = RandomDataGenerators.randomUUID
      val any: Int       = RandomDataGenerators.randomValue[Int]()
      val below: Long    = RandomDataGenerators.randomValue(5L)
      val within: Long   = RandomDataGenerators.randomValue(1L, 3L)
      val window: String = RandomDataGenerators.randomDate(1, 1, pattern, from, DAYS, utc)
      val offset: String = RandomDataGenerators.randomDate(offsetDate = 2L, dateFrom = from, unit = DAYS, timezone = utc)

      string should have length 4
      uuid should have length 36
      any should (be >= Int.MinValue and be <= Int.MaxValue)
      below should (be >= 0L and be < 5L)
      within should (be >= 1L and be < 3L)
      window should fullyMatch regex raw"\d{4}-\d{2}-\d{2}"
      offset shouldBe "2026-05-08"
    }: @nowarn("cat=deprecation")

    "FR-005: keep randomValue(1, 11) at exactly 1..10, never 11" in {
      samples(RandomDataGenerators.randomValue(1, 11)) shouldBe (1 to 10).toSet
    }: @nowarn("cat=deprecation")

    "FR-005: keep returning max when min == max and rejecting min > max" in {
      RandomDataGenerators.randomValue(5, 5) shouldBe 5
      the[IllegalArgumentException] thrownBy RandomDataGenerators.randomValue(6, 5) should have message
        "requirement failed: Min value (6) must be less than max value (5)"
    }: @nowarn("cat=deprecation")

    "FR-005: keep rejecting an empty alphabet and a non-positive length in randomString" in {
      the[IllegalArgumentException] thrownBy RandomDataGenerators.randomString("")(3) should have message
        "requirement failed: Alphabet must be non-empty"
      the[IllegalArgumentException] thrownBy RandomDataGenerators.randomString("ab")(0) should have message
        "requirement failed: String length must be > 0, but got 0"
    }: @nowarn("cat=deprecation")

    "FR-005: keep rejecting a negative delta and a zero offset in randomDate" in {
      the[IllegalArgumentException] thrownBy
        RandomDataGenerators.randomDate(-1, 0, pattern, from, DAYS, utc) should have message
        "requirement failed: RandomDateFeeder delta requires values >0. Current values: positiveDelta= -1, negativeDelta= 0"
      the[IllegalArgumentException] thrownBy RandomDataGenerators.randomDate(0L, pattern, from, DAYS, utc) should have message
        "requirement failed: RandomRangeDateFeeder offset cannot be zero. Current value: offsetDate= 0"
    }: @nowarn("cat=deprecation")
  }

  "each deprecated helper and its Faker analogue" should {

    "FR-006: randomString(alphabet)(length) and Faker.string.fromAlphabet draw the same alphabet and length" in {
      val legacy = samples(RandomDataGenerators.randomString("ab")(4))
      val faker  = samples(Faker.string.fromAlphabet("ab", 4).sample())

      List(legacy, faker).foreach { drawn =>
        drawn.map(_.length) shouldBe Set(4)
        drawn.flatMap(_.toSet) shouldBe Set('a', 'b')
      }
    }: @nowarn("cat=deprecation")

    "FR-006: randomString and Faker.string.fromAlphabet fail alike on an empty alphabet and a zero length" in {
      val legacyEmpty = the[IllegalArgumentException] thrownBy RandomDataGenerators.randomString("")(3)
      val fakerEmpty  = the[IllegalArgumentException] thrownBy Faker.string.fromAlphabet("", 3).sample()
      val legacyZero  = the[IllegalArgumentException] thrownBy RandomDataGenerators.randomString("ab")(0)
      val fakerZero   = the[IllegalArgumentException] thrownBy Faker.string.fromAlphabet("ab", 0).sample()

      fakerEmpty.getMessage shouldBe legacyEmpty.getMessage
      fakerZero.getMessage shouldBe legacyZero.getMessage
    }: @nowarn("cat=deprecation")

    "FR-006: randomUUID and Faker.uuid.string give distinct canonical lowercase UUID strings" in {
      val canonical = "[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"
      val legacy    = samples(RandomDataGenerators.randomUUID)
      val faker     = samples(Faker.uuid.string.sample())

      List(legacy, faker).foreach { drawn =>
        drawn should have size Samples.toLong
        all(drawn) should fullyMatch regex canonical
      }
    }: @nowarn("cat=deprecation")

    "FR-006: randomValue(1, 11) and Faker.number.int(1, 10) give exactly 1..10" in {
      samples(RandomDataGenerators.randomValue(1, 11)) shouldBe (1 to 10).toSet
      samples(Faker.number.int(1, 10).sample()) shouldBe (1 to 10).toSet
      samples(RandomDataGenerators.randomValue(1L, 11L)) shouldBe (1L to 10L).toSet
      samples(Faker.number.long(1L, 10L).sample()) shouldBe (1L to 10L).toSet
    }: @nowarn("cat=deprecation")

    "FR-006: randomValue(5) and Faker.number.int(0, 4) give exactly 0..4" in {
      samples(RandomDataGenerators.randomValue(5)) shouldBe (0 to 4).toSet
      samples(Faker.number.int(0, 4).sample()) shouldBe (0 to 4).toSet
    }: @nowarn("cat=deprecation")

    "FR-006: randomValue(5, 5) and Faker.number.int(5, 5) give only 5" in {
      samples(RandomDataGenerators.randomValue(5, 5)) shouldBe Set(5)
      samples(Faker.number.int(5, 5).sample()) shouldBe Set(5)
    }: @nowarn("cat=deprecation")

    "FR-006: randomValue[Double]() and Faker.number.double(0.0, 1.0) stay in [0, 1)" in {
      val legacy = samples(RandomDataGenerators.randomValue[Double]())
      val faker  = samples(Faker.number.double(0.0, 1.0).sample())

      List(legacy, faker).foreach { drawn =>
        drawn.size should be > 1
        all(drawn) should (be >= 0.0 and be < 1.0)
      }
    }: @nowarn("cat=deprecation")

    "FR-006: randomDate(3, 2, …) and Faker.date.offset(from, -2, 2) give the base date -2..+2 days" in {
      samples(RandomDataGenerators.randomDate(3, 2, pattern, from, DAYS, utc)) shouldBe days(-2, -1, 0, 1, 2)
      samples(Faker.date.formatDateTime(Faker.date.offset(from, -2, 2, DAYS), pattern).sample()) shouldBe days(-2, -1, 0, 1, 2)
    }: @nowarn("cat=deprecation")

    "FR-006: randomDate(0, 0, …) and Faker.date.offset(from, 0, 0) give only the base date" in {
      samples(RandomDataGenerators.randomDate(0, 0, pattern, from, DAYS, utc)) shouldBe days(0)
      samples(Faker.date.formatDateTime(Faker.date.offset(from, 0, 0, DAYS), pattern).sample()) shouldBe days(0)
    }: @nowarn("cat=deprecation")

    "FR-006: randomDate(3, …) and Faker.date.offset(from, 1, 2) give +1..+2 days" in {
      samples(RandomDataGenerators.randomDate(3L, pattern, from, DAYS, utc)) shouldBe days(1, 2)
      samples(Faker.date.formatDateTime(Faker.date.offset(from, 1, 2, DAYS), pattern).sample()) shouldBe days(1, 2)
    }: @nowarn("cat=deprecation")

    "FR-006: randomDate(1, …) and Faker.date.offset(from, 1, 1) give exactly +1 day" in {
      samples(RandomDataGenerators.randomDate(1L, pattern, from, DAYS, utc)) shouldBe days(1)
      samples(Faker.date.formatDateTime(Faker.date.offset(from, 1, 1, DAYS), pattern).sample()) shouldBe days(1)
    }: @nowarn("cat=deprecation")

    "FR-006: randomDate(-3, …) and Faker.date.offset(from, -2, -1) give -2..-1 days" in {
      samples(RandomDataGenerators.randomDate(-3L, pattern, from, DAYS, utc)) shouldBe days(-2, -1)
      samples(Faker.date.formatDateTime(Faker.date.offset(from, -2, -1, DAYS), pattern).sample()) shouldBe days(-2, -1)
    }: @nowarn("cat=deprecation")
  }

  "the Faker API and the helpers that stay" should {

    "FR-007: call none of the deprecated generators and suppress no deprecation" in {
      val callsDeprecated = """(?<!UUID\.)\brandom(String|UUID|Value|Date)\b""".r
      List(
        "src/main/scala/org/galaxio/gatling/feeders/faker/Faker.scala",
        "src/main/scala/org/galaxio/gatling/feeders/faker/GovIdGenerators.scala",
      ).foreach { path =>
        val source = read(path)
        withClue(s"$path: ") {
          callsDeprecated.findAllIn(source).toList shouldBe empty
          source should not include "@nowarn"
        }
      }
      read("src/main/scala/org/galaxio/gatling/utils/RandomDataGenerators.scala") should not include "@nowarn"
    }

    "FR-007: keep the library under -deprecation and -Werror with no category downgraded" in {
      val build = read("build.sbt")
      build should include("\"-deprecation\"")
      build should include("\"-Werror\"")
      build should not include "-Wconf"
      build should not include "-nowarn"
    }

    "FR-007: keep the values of digitString, hexString, cyrillicString, lettersString and alphanumericString" in {
      RandomDataGenerators.digitString(5) should fullyMatch regex "[0-9]{5}"
      RandomDataGenerators.hexString(8) should fullyMatch regex "[0-9a-f]{8}"
      RandomDataGenerators.cyrillicString(6) should fullyMatch regex "[А-Яа-яЁё]{6}"
      RandomDataGenerators.lettersString(7) should fullyMatch regex "[A-Za-z]{7}"
      RandomDataGenerators.alphanumericString(9) should fullyMatch regex "[A-Za-z0-9]{9}"
      the[IllegalArgumentException] thrownBy RandomDataGenerators.digitString(0) should have message
        "requirement failed: String length must be > 0, but got 0"
    }

    "FR-007: keep Faker.ru.*, Faker.finance.pan and Faker.passport.ru checksum-valid and of the same shape" in {
      (1 to 200).foreach { _ =>
        val person  = Faker.ru.inn.person().sample()
        val company = Faker.ru.inn.company().sample()
        val snils   = Faker.ru.snils().sample()
        val pan     = Faker.finance.pan().sample()
        withClue(s"person=$person company=$company snils=$snils pan=$pan: ") {
          GovIdValidators.validNatInn(person) shouldBe true
          GovIdValidators.validJurInn(company) shouldBe true
          GovIdValidators.validSnils(snils) shouldBe true
          LuhnValidator.validate(pan) shouldBe true
        }
        Faker.ru.kpp().sample() should fullyMatch regex "[0-9]{9}"
        Faker.ru.ogrn().sample() should fullyMatch regex "[0-9]{13}"
        Faker.ru.ogrnip().sample() should fullyMatch regex "[0-9]{15}"
        Faker.passport.ru().sample() should fullyMatch regex "[0-9]{10}"
      }
      GovIdValidators.validNatInn("000000000001") shouldBe false
    }
  }
}

/** The pins this feature sets, and the reflection that reads them; shared with [[MigrationGuideSpec]]. */
object RandomDataGeneratorsDeprecationSpec {

  final case class Deprecation(message: String, since: String)

  val Since: String = "faker-api"

  val RandomStringMessage: String = "Use Faker.string.fromAlphabet (with GeneratedFeeder) instead"
  val RandomUuidMessage: String   = "Use Faker.uuid.string (with GeneratedFeeder) instead"
  val RandomDateMessage: String   = "Use Faker.date.offset with Faker.date.formatDateTime (with GeneratedFeeder) instead"

  val RandomValueMessage: String =
    "Use Faker.number.int / Faker.number.long / Faker.number.float / Faker.number.double (with GeneratedFeeder) instead"

  /** The deprecated helper → the message its pin carries. */
  val Pins: Map[String, String] = Map(
    "randomString" -> RandomStringMessage,
    "randomUUID"   -> RandomUuidMessage,
    "randomValue"  -> RandomValueMessage,
    "randomDate"   -> RandomDateMessage,
  )

  val KeptHelpers: List[String] =
    List("digitString", "hexString", "cyrillicString", "lettersString", "alphanumericString", "currentDate")

  /** Every `Faker.…` path a message names, e.g. `Faker.number.int`. */
  def fakerNames(message: String): List[String] = """Faker(\.\w+)+""".r.findAllIn(message).toList.distinct

  val FakerAnalogues: Set[String] = Pins.values.flatMap(fakerNames).toSet

  val RandomDataGeneratorsType: ru.Type = ru.typeOf[RandomDataGenerators.type]

  /** Every overload of `name` declared on `owner`, each with the `@deprecated` pins it carries. */
  def deprecations(owner: ru.Type, name: String): List[(ru.MethodSymbol, List[Deprecation])] =
    owner.decl(ru.TermName(name)).alternatives.collect {
      case symbol if symbol.isMethod =>
        val method = symbol.asMethod
        // Force the signature first: an unforced symbol may report its annotations as empty.
        method.typeSignature
        method -> method.annotations.filter(_.tree.tpe =:= ru.typeOf[deprecated]).map(deprecation)
    }

  /** The method a `Faker.<group>.<name>` path names, with its pins; empty when the path names nothing. */
  def fakerMethod(path: String): List[(ru.MethodSymbol, List[Deprecation])] =
    path.split('.').toList match {
      case "Faker" :: rest if rest.nonEmpty =>
        val owner = rest.init.foldLeft(Option(ru.typeOf[Faker.type])) { (tpe, group) =>
          tpe.map(_.member(ru.TermName(group))).filter(_.isModule).map(_.asModule.moduleClass.asClass.toType)
        }
        owner.toList.flatMap(deprecations(_, rest.last))
      case _                                => Nil
    }

  /** `deprecated` is a `ConstantAnnotation` in 2.13: its arguments may be stored positionally or by name, so both are read. */
  private def deprecation(annotation: ru.Annotation): Deprecation = {
    import ru._ // the class tags the tree patterns below are checked with
    val positional = List("message", "since")
    val args       = annotation.tree.children.drop(1).zipWithIndex.flatMap { case (arg, index) =>
      arg match {
        case NamedArg(Ident(TermName(key)), Literal(Constant(value: String))) => List(key -> value)
        case Literal(Constant(value: String))                                 => positional.lift(index).map(_ -> value).toList
        case _                                                                => Nil
      }
    }.toMap
    Deprecation(args.getOrElse("message", ""), args.getOrElse("since", ""))
  }
}
