package org.galaxio.gatling.utils.phone

import io.circe.{CursorOp, DecodingFailure, ParsingFailure}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What `Phone(resourcePath)` reads out of a file of phone formats (test-model layer 1), pinned so that a change of the JSON
  * library underneath it cannot change it unnoticed (BCKLG-233, FR-008).
  *
  * Only the public `Phone` is used: a format's fields are read back out of the numbers it generates. A format, an area code and
  * a prefix are drawn at random; the rarest (country code, area code, prefix) of `ru.json` comes up once in sixteen draws, so
  * 2000 draws miss one of its fourteen with a probability below 1e-54 and need no fixed seed. The order of the codes in the
  * file is not visible from outside and is not pinned. A broken file throws circe's own exception, unwrapped: its class is
  * pinned, its text belongs to circe and is not.
  */
class PhoneSpec extends AnyWordSpec with Matchers {

  private val Draws = 2000

  // ru.json, field by field: country code, length, area codes, prefixes, format.
  private val Plus7Areas    = Set("903", "906")
  private val Plus7Prefixes = Set("123", "321", "132", "231")
  private val EightAreas    = Set("495", "499")
  private val EightPrefixes = Set("81", "82", "83")

  private def afterCountryCode(number: String): String =
    if (number.startsWith("+7")) number.drop(2)
    else if (number.startsWith("8")) number.drop(1)
    else fail(s"$number starts with no country code of ru.json")

  "Phone(resourcePath)" should {

    "FR-008: draw from phoneTemplates/ru.json every (country code, area code, prefix) it lists, and nothing else" in {
      val phone = Phone("phoneTemplates/ru.json")
      val plus7 = """\+7(\d{3})(\d{3})\d{4}""".r
      val eight = """8(\d{3})(\d{2})\d{5}""".r
      val drawn = Seq.fill(Draws)(phone.e164PhoneNumber).map {
        case plus7(area, prefix) => ("+7", area, prefix)
        case eight(area, prefix) => ("8", area, prefix)
        case other               => fail(s"$other is a number of no format of ru.json")
      }

      val listed =
        (for { area <- Plus7Areas; prefix <- Plus7Prefixes } yield ("+7", area, prefix)) ++
          (for { area <- EightAreas; prefix <- EightPrefixes } yield ("8", area, prefix))

      listed should have size 14
      drawn.toSet shouldBe listed
    }

    "FR-008: give every E.164 number of phoneTemplates/ru.json its length, 10 digits after the country code" in {
      val phone = Phone("phoneTemplates/ru.json")
      Seq.fill(Draws)(phone.e164PhoneNumber).map(afterCountryCode(_).length).toSet shouldBe Set(10)
    }

    "FR-008: format every number of phoneTemplates/ru.json whole by the template of its own format" in {
      val phone = Phone("phoneTemplates/ru.json")
      val plus7 = """\+7\((903|906)\)(123|321|132|231)\d{4}""".r
      val eight = """8\((495|499)\)(81|82|83)\d-\d{2}-\d{2}""".r

      Seq
        .fill(Draws)(phone.phoneNumber)
        .map {
          case plus7(_, _) => "+X(XXX)XXXXXXX"
          case eight(_, _) => "X(XXX)XXX-XX-XX"
          case other       => fail(s"$other is formatted by no template of ru.json")
        }
        .toSet shouldBe Set("+X(XXX)XXXXXXX", "X(XXX)XXX-XX-XX")
    }

    "FR-008: give per country code of phoneTemplates/ru.json exactly the area codes it lists" in {
      val phone = Phone("phoneTemplates/ru.json")
      Seq.fill(Draws)(phone.areaCode(Some("+7"))).toSet shouldBe Plus7Areas
      Seq.fill(Draws)(phone.areaCode(Some("8"))).toSet shouldBe EightAreas
    }

    "FR-008: refuse a format record without prefixes with circe's DecodingFailure, unwrapped, at the prefixes" in {
      val failure = intercept[DecodingFailure](Phone("phoneTemplates/no-prefixes.json"))
      CursorOp.opsToPath(failure.history) shouldBe ".formats[0].prefixes"
    }

    "FR-008: refuse a file that is not JSON with circe's ParsingFailure, unwrapped" in {
      intercept[ParsingFailure](Phone("phoneTemplates/not-json.json"))
    }

    "FR-008: refuse JSON without formats with circe's DecodingFailure, unwrapped, at the formats" in {
      val failure = intercept[DecodingFailure](Phone("phoneTemplates/no-formats.json"))
      CursorOp.opsToPath(failure.history) shouldBe ".formats"
    }
  }
}
