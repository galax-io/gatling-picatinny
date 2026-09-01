package org.galaxio.gatling.storage

import org.galaxio.gatling.testutil.LocaleFixture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Cookie attribute-name matching must not depend on the host's default locale (#333).
  *
  * RFC 6265 makes attribute names case-insensitive, so a server may legitimately send `DOMAIN=`. The parser lowercases with the
  * platform default; under Turkish/Azeri an ASCII capital `I` lowers to a dotless `ı`, the `domain` lookup misses, and the
  * cookie SILENTLY takes the fallback domain with no warning — wrong domain, no diagnostic.
  *
  * Of the five recognised attribute names only `DOMAIN` contains an ASCII `I`, so it is the only reachable case of the widening
  * half.
  */
class CookieParserTurkishLocaleSpec extends AnyWordSpec with Matchers {

  private val Fallback = "fallback.example"

  "cookie attribute matching under a Turkish default locale" should {

    "honour a domain attribute spelled in ASCII capitals" in {
      LocaleFixture.withTurkish {
        val c = CookieParser.parse("sid=abc; DOMAIN=sent.example", Fallback).head
        c.domain shouldBe Some("sent.example")
      }
    }

    "recognise every attribute name in every ASCII letter case" in {
      LocaleFixture.withTurkish {
        val raw = "sid=abc; DOMAIN=d.example; PATH=/p; MAX-AGE=60; SECURE; HTTPONLY"
        val c   = CookieParser.parse(raw, Fallback).head
        c.domain shouldBe Some("d.example")
        c.path shouldBe Some("/p")
        c.maxAge shouldBe Some(60L)
        c.secure shouldBe true
        c.httpOnly shouldBe true
      }
    }

    "behave identically under an unaffected locale" in {
      LocaleFixture.withLocale(java.util.Locale.ENGLISH) {
        val c = CookieParser.parse("sid=abc; DOMAIN=sent.example", Fallback).head
        c.domain shouldBe Some("sent.example")
      }
    }

    "keep mixed-case spellings working, which never depended on the locale" in {
      LocaleFixture.withTurkish {
        CookieParser.parse("sid=abc; Domain=d.example", Fallback).head.domain shouldBe Some("d.example")
      }
    }
  }

  "the deliberate narrowing" should {

    /** The one place this fix REMOVES recognition, and it is correct.
      *
      * `"DOMAİN".toLowerCase(tr)` is `"domain"` — the dotted capital `İ` folds onto the ASCII name, so a Turkish host accepts
      * this spelling TODAY. Under `Locale.ROOT` it keeps its combining dot and does not match. That is right: cookie attribute
      * names are ASCII, `DOMAİN` is not a valid spelling, and it was only ever recognised by accident on one family of hosts —
      * accepting it IS the locale dependence being removed.
      *
      * Pinned so it cannot later be mistaken for a regression and "fixed" back.
      */
    "stop honouring a domain attribute spelled with the dotted capital" in {
      LocaleFixture.withTurkish {
        val c = CookieParser.parse("sid=abc; DOMAİN=sent.example", Fallback).head
        withClue("DOMAİN is not an ASCII attribute name; it must fall back, not be honoured: ") {
          c.domain shouldBe Some(Fallback)
        }
      }
    }
  }
}
