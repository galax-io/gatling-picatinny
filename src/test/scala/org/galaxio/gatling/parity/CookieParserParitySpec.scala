package org.galaxio.gatling.parity

import org.galaxio.gatling.storage.{CookieParser, ParsedCookie}
import org.galaxio.gatling.testutil.LogCapture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.util.Try

/** Live cookie parsing must equal the frozen reference in its FULL observable outcome (FR-011).
  *
  * Three components are compared, and each exists because comparing fewer would let a real change through:
  *
  *   - the parsed cookies — the obvious half;
  *   - the thrown exception, class AND message — a line of only `;` RAISES today, and the obvious defensive rewrite silently
  *     returns an empty result instead (measured at 408 divergences per 20,082 inputs);
  *   - the ordered list of WARN messages — the `Max-Age` warning must fire exactly once, for the LAST occurrence. A fold that
  *     parses inside the loop warns once per occurrence and nothing else would catch it.
  *
  * The oracle is frozen AFTER the `Locale.ROOT` fixes, so live and frozen agree under every host locale. No locale exclusion is
  * needed here — and adding one would suppress exactly the Turkish inputs that must now agree.
  */
class CookieParserParitySpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  import CookieParserParitySpec.Outcome

  private val Domain = "fallback.example"

  private def liveOutcome(raw: String): Outcome = {
    var r: Either[String, Seq[ParsedCookie]] = Right(Nil)
    val w                                    = LogCapture.warns("org.galaxio.gatling.storage") {
      r = Try(CookieParser.parse(raw, Domain)).toEither.left.map(t => s"${t.getClass.getName}: ${t.getMessage}")
    }
    Outcome(r, w)
  }

  /** The oracle logs under `org.galaxio.gatling.parity`, NOT `…storage` — see [[FrozenCookieParserReference]]. If it shared the
    * live logger prefix, this capture would collect both sides' warnings and warning-parity would be vacuously true.
    */
  private def frozenOutcome(raw: String): Outcome = {
    var r: Either[String, Seq[ParsedCookie]] = Right(Nil)
    val w                                    = LogCapture.warns("org.galaxio.gatling.parity") {
      r = Try(FrozenCookieParserReference.parse(raw, Domain)).toEither.left
        .map(t => s"${t.getClass.getName}: ${t.getMessage}")
    }
    Outcome(r, w)
  }

  private def compare(raw: String): Unit =
    withClue(s"input = [${raw.replace("\n", "\\n").replace("\r", "\\r")}]\n") {
      liveOutcome(raw) shouldBe frozenOutcome(raw)
    }

  "cookie parsing" should {

    "match the frozen reference on every enumerated boundary shape" in {
      ParityGenerators.boundaryCookies.foreach(compare)
    }

    "match the frozen reference over generated headers" in {
      forAll(ParityGenerators.rawSetCookie)(compare)
    }
  }

  "the comparison itself" should {

    "actually observe warnings, or warning-parity would be vacuous" in {
      // A positive control: if the capture silently collected nothing, every warning comparison
      // above would trivially pass. This pins that the live side really does emit one.
      val out = liveOutcome("sid=x; Max-Age=nope")
      out.warns.count(w => w.contains("Max-Age") && w.contains("nope")) shouldBe 1
    }

    "observe the frozen side's warnings under its own logger, not the live one" in {
      val out = frozenOutcome("sid=x; Max-Age=nope")
      out.warns.count(w => w.contains("Max-Age") && w.contains("nope")) shouldBe 1
    }

    "observe a raised exception rather than swallowing it" in {
      // The separator-only line raises today. Pinned here so the comparison is known to cover the
      // throw channel, not merely the value channel.
      liveOutcome(";").result.isLeft shouldBe true
    }
  }
}

private object CookieParserParitySpec {

  /** The full observable outcome of one parse: result-or-throw, plus everything it logged.
    *
    * Lives in the companion, not inside the suite: a case class nested in a class carries an outer reference that `-Xlint`
    * cannot type-test at runtime, which is an error under `-Werror`.
    */
  final case class Outcome(result: Either[String, Seq[ParsedCookie]], warns: List[String])
}
