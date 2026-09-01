package org.galaxio.gatling.config

import com.typesafe.config.ConfigFactory
import org.galaxio.gatling.testutil.LocaleFixture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Secret-key matching must not depend on the host's default locale (#333).
  *
  * `splitWords` lowercases with the platform default. Under Turkish/Azeri an ASCII capital `I` maps to a dotless `ı`, so a key
  * spelled in capitals no longer matches the secret words that contain `i` — `credential`, `credentials`, `authorization`,
  * `apikey`, `privatekey`, `clientsecret` — and **the value is printed in full** by the feature whose whole purpose is to
  * prevent that.
  *
  * Only an UPPERCASE `I` triggers it: `apiKey` is safe, `API_KEY` is not. Every case below therefore uses a capitalised
  * spelling; a mixed-case test would pass before the fix and prove nothing.
  */
class ConfigValueMaskingTurkishLocaleSpec extends AnyWordSpec with Matchers {

  private val Masked = "******"

  "secret-key matching under a Turkish default locale" should {

    "mask capitalised keys whose secret word contains an ASCII capital I" in {
      // Every one of these lowercases to a dotless form under `tr` and misses the word list.
      val keys = List("AUTHORIZATION", "CREDENTIAL", "CREDENTIALS", "API_KEY", "PRIVATE_KEY", "CLIENT_SECRET")
      LocaleFixture.withTurkish {
        keys.foreach { key =>
          withClue(s"key `$key` under tr-TR: ") {
            ConfigValueMasking.displayValue(key, "s3cr3t") shouldBe Masked
          }
        }
      }
    }

    "mask through the separator-less suffix floor, not only the word splitter" in {
      // No separator to split on, so this reaches `suffixFloor`, which compares the SAME lowered
      // text — fixing only the splitter would leave this arm broken.
      LocaleFixture.withTurkish {
        ConfigValueMasking.displayValue("DBCREDENTIAL", "s3cr3t") shouldBe Masked
        ConfigValueMasking.displayValue("MYAPIKEY", "s3cr3t") shouldBe Masked
      }
    }

    "mask an operator-supplied extra sensitive key spelled with a capital I" in {
      // `normalizedTerm` routes user-configured keys through the same lowering.
      val masking = ConfigValueMasking.fromConfig(
        ConfigFactory.parseString("""picatinny.redaction.additionalSensitiveKeys = ["TENANTPRIVATEKEY"]"""),
      )
      LocaleFixture.withTurkish {
        masking.displayValue("TENANTPRIVATEKEY", "s3cr3t") shouldBe Masked
      }
    }

    "leave a non-secret key visible — the fix widens matching, it does not mask everything" in {
      LocaleFixture.withTurkish {
        ConfigValueMasking.displayValue("BASEURL", "http://localhost") shouldBe "http://localhost"
        ConfigValueMasking.displayValue("TIMEOUT", "30s") shouldBe "30s"
      }
    }

    "behave identically under an unaffected locale" in {
      LocaleFixture.withLocale(java.util.Locale.ENGLISH) {
        ConfigValueMasking.displayValue("AUTHORIZATION", "s3cr3t") shouldBe Masked
        ConfigValueMasking.displayValue("BASEURL", "http://localhost") shouldBe "http://localhost"
      }
    }
  }

  "the fix" should {

    "only ever widen: every key masked under English stays masked under Turkish" in {
      val keys         = List(
        "password",
        "db.password",
        "passwd",
        "pwd",
        "secret",
        "token",
        "credential",
        "credentials",
        "passphrase",
        "authorization",
        "bearer",
        "apiKey",
        "privateKey",
        "clientSecret",
        "accessKey",
        "secretKey",
        "dbpassword",
        "apisecret",
        "accesstoken",
        "AUTHORIZATION",
        "API_KEY",
      )
      val underEnglish = LocaleFixture.withLocale(java.util.Locale.ENGLISH) {
        keys.map(k => k -> ConfigValueMasking.displayValue(k, "v"))
      }
      val underTurkish = LocaleFixture.withTurkish {
        keys.map(k => k -> ConfigValueMasking.displayValue(k, "v"))
      }
      val newlyVisible = underEnglish.zip(underTurkish).collect {
        case ((k, en), (_, tr)) if en == Masked && tr != Masked => k
      }
      withClue(s"keys masked under English but VISIBLE under Turkish: ${newlyVisible.mkString(", ")}\n") {
        newlyVisible shouldBe empty
      }
    }
  }
}
