package org.galaxio.gatling.feeders

import org.galaxio.gatling.feeders.faker.Faker
import org.scalacheck._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.galaxio.gatling.utils.phone.{PhoneFormat, TypePhone}

import java.time.temporal.{ChronoUnit, TemporalUnit}
import java.time.{LocalDateTime, ZoneId}
import scala.annotation.nowarn

// Regression coverage for the deprecated Random*Feeder family. These tests intentionally
// exercise the deprecated API for as long as it remains in the published surface; remove
// this suite together with the deprecated objects.
@nowarn("cat=deprecation")
class RandomFeedersSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  val positiveInt: Gen[Int] = Gen.posNum[Int]

  val rndString: Gen[String] = Gen.alphaNumStr

  val datePattern: String      = "yyyy-MM-dd"
  val datePatternRegex: String = """\d{4}-\d{2}-\d{2}"""

  val dateFrom: LocalDateTime = LocalDateTime.now()

  val timezone: ZoneId = ZoneId.systemDefault()

  val unit: TemporalUnit = ChronoUnit.DAYS

  val uuidPattern = "([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})"

  val regexPattern = "[a-z0-9]{9}"

  val regexPhonePattern                   = """^\+?(?:[0-9]?){6,14}[0-9]$"""
  val regexFilePhonePattern               = """^\+?\d(\(\d\d\d\)\d\d\d-?\d\d-?\d\d)$"""
  val regexRuMobilePhonePattern           = """^\+\d \d\d\d \d\d\d-\d\d-\d\d"""
  val regexRuMobilePhonePatternWithBraces = """^\+\d \(\d\d\d\) \d\d\d-\d\d-\d\d"""
  val regexTollFreePhonePattern           = """^\(8(00|33|44|55|66|77|88)\) \d{3}-\d{4}$"""

  val phoneFormatsFromFile: String = "phoneTemplates/ru.json"

  val ruMobileFormat: PhoneFormat = PhoneFormat(
    countryCode = "+7",
    length = 10,
    areaCodes = Seq("903", "906", "908"),
    prefixes = Seq("55", "81", "111"),
    format = "+X XXX XXX-XX-XX",
  )

  val ruMobileFormat2: PhoneFormat = PhoneFormat(
    countryCode = "+7",
    length = 10,
    areaCodes = Seq("903", "906", "908"),
    format = "+X (XXX) XXX-XX-XX",
  )

  "Random feeders" should {

    "create RandomDateFeeder with specified date pattern" in {
      forAll(rndString, positiveInt, positiveInt) { (paramName, positive, negative) =>
        whenever(positive > negative) {
          RandomDateFeeder(paramName, positive, negative, datePattern, dateFrom, unit, timezone)
            .take(50)
            .foreach(record =>
              withClue(s"Invalid RandomDateFeeder with specified date pattern: $record, ") {
                record(paramName) should fullyMatch regex datePatternRegex
              },
            )
        }
      }
    }

    "produce IllegalArgumentException when RandomDateFeeder creates with delta dates params <0" in {
      assertThrows[IllegalArgumentException] {
        RandomDateFeeder("paramName", -1, -1, datePattern, dateFrom, unit, timezone).next()
      }
    }

    "create RandomDateRangeFeeder with specified date pattern" in {
      forAll(rndString, rndString, positiveInt) { (paramNameFrom, paramNameTo, offset) =>
        whenever(offset > 1 && paramNameFrom.nonEmpty && paramNameTo.nonEmpty) {
          RandomDateRangeFeeder(paramNameFrom, paramNameTo, offset, datePattern, dateFrom, unit, timezone)
            .take(50)
            .foreach { record =>
              withClue(s"Invalid RandomDigitFeeder: $record, ") {
                record(paramNameFrom) should fullyMatch regex datePatternRegex
                record(paramNameTo) should fullyMatch regex datePatternRegex
              }
            }
        }
      }
    }

    "create RandomDigitFeeder" in {
      forAll(rndString) { paramName =>
        whenever(paramName.nonEmpty) {
          RandomDigitFeeder(paramName)
            .take(50)
            .foreach(record =>
              withClue(s"Invalid RandomDigitFeeder: $record, ") {
                record(paramName) shouldBe a[Int]
              },
            )
        }
      }
    }

    "create RandomStringFeeder with specified param length interval" in {
      forAll(rndString.suchThat(_.nonEmpty), positiveInt.suchThat(_ > 0)) { (paramName, length) =>
        RandomStringFeeder(paramName, length)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid record: ${record.toString}, expected key '$paramName' with length $length, ") {
              record
                .get(paramName)
                .fold(
                  fail(s"Key '$paramName' is missing in the record."),
                )(value => value should have length length)
            }
          }
      }
    }

    "create RandomRangeStringFeeder with param length in the specified interval" in {
      forAll(rndString, positiveInt, positiveInt, rndString) { (paramName, lengthFrom, lengthTo, alphabet) =>
        whenever(lengthFrom > 0 && lengthTo > 0 && lengthFrom < lengthTo && alphabet.nonEmpty) {
          RandomRangeStringFeeder(paramName, lengthFrom, lengthTo, alphabet)
            .take(50)
            .foreach(record =>
              withClue(s"Invalid RandomRangeStringFeeder with param length in the specified interval: $record, ") {
                record(paramName).length should (be >= lengthFrom and be < lengthTo)
              },
            )
        }
      }
    }

    "produce IllegalArgumentException when RandomRangeStringFeeder creates with length params =<0" in {
      assertThrows[IllegalArgumentException] {
        RandomRangeStringFeeder("paramName", 0, 0, "alphabet").next()
      }
    }

    "produce IllegalArgumentException when RandomRangeStringFeeder creates with empty alphabet string" in {
      assertThrows[IllegalArgumentException] {
        RandomRangeStringFeeder("paramName", 1, 2, "").next()
      }
    }

    "create RandomUUIDFeeder" in {
      forAll(rndString) { paramName =>
        RandomUUIDFeeder(paramName)
          .take(50)
          .foreach(record =>
            withClue(s"Invalid RandomUUIDFeeder: $record, ") {
              record(paramName) should fullyMatch regex uuidPattern
            },
          )
      }
    }

    "create RegexFeeder with specified regex pattern" in {
      forAll(rndString) { paramName =>
        RegexFeeder(paramName, regexPattern)
          .take(50)
          .foreach(record =>
            withClue(s"Invalid RegexFeeder with specified regex pattern: $record, ") {
              record(paramName) should fullyMatch regex regexPattern
            },
          )
      }
    }

    "create RegexFeeder with fresh values on each record" in {
      val values = RegexFeeder("rx", "[A-Z]{2}[0-9]{4}").take(20).map(_("rx").toString).toList
      values.distinct.size should be > 1
      values.foreach(_ should fullyMatch regex "[A-Z]{2}[0-9]{4}")
    }

    "preserve surrounding spaces through RegexFeeder" in {
      val value = RegexFeeder("rx", " [A-Z]{2} ").next()("rx").toString

      value should startWith(" ")
      value should endWith(" ")
      value should have length 4
    }

    "fail fast for invalid regex generator patterns" in {
      assertThrows[IllegalArgumentException] {
        Faker.string.matching("[A-Z").sample()
      }
    }

    "preserve surrounding spaces in regex patterns" in {
      val value = Faker.string.matching(" [A-Z]{2} ").sample()

      value should startWith(" ")
      value should endWith(" ")
      value should have length 4
    }

    "create SequentialFeeder" in {
      forAll(rndString, positiveInt, positiveInt) { (paramName, start, step) =>
        val list = SequentialFeeder(paramName, start, step).take(50).toList.flatten
        withClue(s"Invalid SequentialFeeder: ${list.mkString(",")}, ") {
          list shouldBe list.sorted
        }
      }
    }

    "create phoneFeeder with default PhoneNumber format" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid phoneFeeder with custom PhoneNumber format: $record, ") {
              record(paramName) should fullyMatch regex regexPhonePattern
            }
          }
      }
    }

    "create phoneFeeder with custom PhoneNumber format" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, ruMobileFormat)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid phoneFeeder with custom PhoneNumber format: $record, ") {
              record(paramName) should fullyMatch regex regexRuMobilePhonePattern
            }
          }
      }
    }

    "create phoneFeeder with braces custom PhoneNumber format" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, ruMobileFormat2)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid phoneFeeder with braces custom PhoneNumber format: $record, ") {
              record(paramName) should fullyMatch regex regexRuMobilePhonePatternWithBraces
            }
          }
      }
    }

    "create simple Toll Free format ignoring ruMobileFormat" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, TypePhone.TollFreePhoneNumber, ruMobileFormat)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid simple Toll Free format ignoring ruMobileFormat: $record, ") {
              record(paramName) should fullyMatch regex regexTollFreePhonePattern
            }
          }
      }
    }

    "create phoneFeeder with E164 PhoneNumber format ignoring ruMobileFormat" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, TypePhone.E164PhoneNumber, ruMobileFormat)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid phoneFeeder with E164 PhoneNumber format ignoring ruMobileFormat: $record, ") {
              record(paramName) should fullyMatch regex regexPhonePattern
            }
          }
      }
    }

    "create phoneFeeder with E164 PhoneNumber format" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, TypePhone.E164PhoneNumber).take(50).foreach { record =>
          withClue(s"Invalid phoneFeeder with E164 PhoneNumber format: $record, ") {
            record(paramName) should fullyMatch regex regexPhonePattern
          }
        }
      }
    }

    "create phoneFeeder with Toll Free Phone Number format" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, TypePhone.TollFreePhoneNumber)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid phoneFeeder with Toll Free Phone Number format: $record, ") {
              record(paramName) should fullyMatch regex regexTollFreePhonePattern
            }
          }
      }
    }

    "create phoneFeeder with PhoneNumber format fromFile" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, phoneFormatsFromFile)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid phoneFeeder with PhoneNumber format fromFile: $record, ") {
              record(paramName) should fullyMatch regex regexFilePhonePattern
            }
          }
      }
    }

    "create phoneFeeder with Toll Free Phone Number format fromFile" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, phoneFormatsFromFile, TypePhone.TollFreePhoneNumber)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid phoneFeeder with Toll Free Phone Number format fromFile: $record, ") {
              record(paramName) should fullyMatch regex regexTollFreePhonePattern
            }
          }
      }
    }

    "create phoneFeeder with E164 PhoneNumber format fromFile" in {
      forAll(rndString) { paramName =>
        RandomPhoneFeeder(paramName, phoneFormatsFromFile, TypePhone.E164PhoneNumber)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid phoneFeeder with E164 PhoneNumber format fromFile: $record, ") {
              record(paramName) should fullyMatch regex regexPhonePattern
            }
          }
      }
    }

    "create random snilsFeeder" in {
      forAll(rndString) { paramName =>
        RandomSNILSFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random snilsFeeder: $record, ") {
              record(paramName) should fullyMatch regex "\\d{11}"
              GovIdValidators.validSnils(record(paramName)) shouldBe true // honest: control number is correct
            }
          }
      }
    }

    "create random panFeeder without BINs" in {
      forAll(rndString) { paramName =>
        RandomPANFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random panFeeder without BINs: $record, ") {
              LuhnValidator.validate(record(paramName)) shouldBe true
            }
          }
      }
    }

    "create random panFeeder with BINs 6 numbers" in {
      forAll(rndString) { paramName =>
        RandomPANFeeder(paramName, "192837", "293847", "394857", "495867", "596871", "697881", "798192", "891726", "918273")
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random panFeeder with BINs 6 numbers: $record, ") {
              LuhnValidator.validate(record(paramName)) shouldBe true
            }
          }
      }
    }

    "create random panFeeder with BINs 8 numbers" in {
      forAll(rndString) { paramName =>
        RandomPANFeeder(
          paramName,
          "19292837",
          "29392847",
          "39492857",
          "49592867",
          "59692871",
          "69792881",
          "79892192",
          "89192726",
          "91892273",
        )
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random panFeeder with BINs 8 numbers: $record, ") {
              LuhnValidator.validate(record(paramName)) shouldBe true
            }
          }
      }
    }

    "create random rusPassportFeeder" in {
      forAll(rndString) { paramName =>
        RandomRusPassportFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random rusPassportFeeder: $record, ") {
              record(paramName) should fullyMatch regex "\\d{10}"
            }
          }
      }
    }

    "create random PSRNSPFeeder" in {
      forAll(rndString) { paramName =>
        RandomPSRNSPFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random PSRNSPFeeder: $record, ") {
              record(paramName).substring(0, 14).toLong % 13 % 10 shouldBe record(paramName).substring(14, 15).toInt
            }
          }
      }
    }

    "create random OGRNFeeder" in {
      forAll(rndString) { paramName =>
        RandomOGRNFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random OGRNFeeder: $record, ") {
              record(paramName).substring(0, 12).toLong % 11 % 10 shouldBe record(paramName).substring(12, 13).toInt
            }
          }
      }
    }

    "create random NatITNFeeder" in {
      forAll(rndString) { paramName =>
        RandomNatITNFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random NatITNFeeder: $record, ") {
              // natural-person ITN is 12 digits with two valid control digits (honest generation)
              record(paramName) should fullyMatch regex "\\d{12}"
              GovIdValidators.validNatInn(record(paramName)) shouldBe true
            }
          }
      }
    }

    "create random JurITNFeeder" in {
      forAll(rndString) { paramName =>
        RandomJurITNFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random JurITNFeeder: $record, ") {
              // legal-entity ITN is 10 digits with one valid control digit (honest generation)
              record(paramName) should fullyMatch regex "\\d{10}"
              GovIdValidators.validJurInn(record(paramName)) shouldBe true
            }
          }
      }
    }

    "create random KPPFeeder" in {
      forAll(rndString) { paramName =>
        RandomKPPFeeder(paramName)
          .take(50)
          .foreach { record =>
            withClue(s"Invalid random KPPFeeder: $record, ") {
              record(paramName) should fullyMatch regex "\\d{9}"
            }
          }
      }
    }

  }
}
