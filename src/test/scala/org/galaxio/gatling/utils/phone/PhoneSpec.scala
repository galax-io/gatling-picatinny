package org.galaxio.gatling.utils.phone

import io.circe.{DecodingFailure, ParsingFailure}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.matching.Regex

/** What circe reads from a phone formats file, pinned through the public [[Phone]] only (test-model layer 1; BCKLG-233,
  * FR-008). Every outcome here was recorded on circe 0.15.0-M1 and must stay the same on any other circe version.
  *
  * A number is drawn as country code, area code, prefix and a random tail, with exactly `length` digits after the country code,
  * so a number read back by position names the record fields it came from. Each draw picks its format, area code and prefix at
  * random; the rarest triple of `ru.json` comes up once in 16 draws, so 2000 draws miss one of its 14 triples with a
  * probability below 1e-54 and no shared seed is needed.
  */
class PhoneSpec extends AnyWordSpec with Matchers {
  import PhoneSpec.Expected

  // phoneTemplates/ru.json, field by field: length 10 for both records.
  private val ruLength = 10
  private val ru       = List(
    Expected("+7", Set("903", "906"), Set("123", "321", "132", "231"), """\+7\((903|906)\)(123|321|132|231)\d{4}""".r),
    Expected("8", Set("495", "499"), Set("81", "82", "83"), """8\((495|499)\)(81|82|83)\d-\d{2}-\d{2}""".r),
  )

  private val draws = 2000

  // Lazy: a file circe no longer reads fails each test below instead of aborting the suite.
  private lazy val phone = Phone("phoneTemplates/ru.json")
  private lazy val e164  = List.fill(draws)(phone.e164PhoneNumber)

  private def recordOf(number: String): Expected =
    ru.find(r => number.startsWith(r.countryCode)).getOrElse(fail(s"$number starts with no country code of the file"))

  "FR-008: Phone built from phoneTemplates/ru.json" should {

    "draw exactly the file's 14 (country code, area code, prefix) triples, and no other" in {
      val seen = e164.map { n =>
        val r      = recordOf(n)
        val digits = n.drop(r.countryCode.length)
        val area   = digits.take(r.areaCodes.head.length)
        (r.countryCode, area, digits.slice(area.length, area.length + r.prefixes.head.length))
      }.toSet

      seen shouldBe ru.flatMap(_.triples).toSet
      seen should have size 14
    }

    "put exactly the file's length of digits after the country code of every E.164 number" in {
      e164.filterNot(n => n.drop(recordOf(n).countryCode.length).matches(s"\\d{$ruLength}")) shouldBe empty
    }

    "format every number whole by the pattern of its own record, and use both records" in {
      val formatted = List.fill(draws)(phone.phoneNumber)

      formatted.filterNot(n => ru.exists(_.pattern.matches(n))) shouldBe empty
      ru.filterNot(r => formatted.exists(r.pattern.matches)).map(_.countryCode) shouldBe empty
    }

    "give exactly the file's area codes for each country code" in {
      ru.foreach { r =>
        withClue(s"country code ${r.countryCode}: ") {
          List.fill(200)(phone.areaCode(Some(r.countryCode))).toSet shouldBe r.areaCodes
        }
      }
    }
  }

  "FR-008: Phone built from a format record without prefixes" should {

    "refuse it with circe's DecodingFailure, unwrapped, since the derived decoder does not apply the default prefixes" in {
      intercept[DecodingFailure](Phone("phoneTemplates/no-prefixes.json"))
    }
  }

  "FR-008: Phone built from a malformed formats file" should {

    "throw circe's ParsingFailure, unwrapped, for a file that is not JSON" in {
      intercept[ParsingFailure](Phone("phoneTemplates/not-json.json"))
    }

    "throw circe's DecodingFailure, unwrapped, for JSON without the formats field" in {
      intercept[DecodingFailure](Phone("phoneTemplates/no-formats.json"))
    }
  }
}

object PhoneSpec {

  // In the companion, not the class: a case class nested in the class carries an outer reference, which -Werror refuses.
  private final case class Expected(countryCode: String, areaCodes: Set[String], prefixes: Set[String], pattern: Regex) {
    def triples: Set[(String, String, String)] = for (a <- areaCodes; p <- prefixes) yield (countryCode, a, p)
  }
}
