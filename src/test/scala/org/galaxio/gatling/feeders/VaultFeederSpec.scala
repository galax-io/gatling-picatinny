package org.galaxio.gatling.feeders

import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class VaultFeederSpec extends AnyWordSpec with Matchers {

  implicit private val formats: DefaultFormats = DefaultFormats

  "VaultFeeder.approleLoginBody" should {
    "escape credentials without string interpolation artifacts" in {
      val body = VaultFeeder.approleLoginBody("role\"id", "secret\nid")

      (parse(body) \ "role_id").extract[String] shouldBe "role\"id"
      (parse(body) \ "secret_id").extract[String] shouldBe "secret\nid"
    }
  }

  "VaultFeeder.extractClientToken" should {
    "extract the raw token string from the auth payload" in {
      val payload = parse("""{"auth":{"client_token":"vault-token-123"}}""")

      VaultFeeder.extractClientToken(payload) shouldBe "vault-token-123"
    }
  }

  "VaultFeeder.mergeWithStrategy" should {
    "throw IllegalArgumentException naming the duplicate key under FailOnDuplicate" in {
      val thrown = intercept[IllegalArgumentException] {
        VaultFeeder.mergeWithStrategy(List("k" -> "v1", "k" -> "v2"), DuplicateKeyStrategy.FailOnDuplicate)
      }
      thrown.getMessage should include("k")
    }

    "keep the last value under LastWins" in {
      VaultFeeder.mergeWithStrategy(List("k" -> "v1", "k" -> "v2"), DuplicateKeyStrategy.LastWins) shouldBe Map(
        "k" -> "v2",
      )
    }

    "keep the first value under FirstWins" in {
      VaultFeeder.mergeWithStrategy(List("k" -> "v1", "k" -> "v2"), DuplicateKeyStrategy.FirstWins) shouldBe Map(
        "k" -> "v1",
      )
    }
  }

  "VaultFeeder.parseLoginResponse" should {
    "throw RuntimeException on malformed JSON" in {
      val thrown = intercept[RuntimeException] {
        VaultFeeder.parseLoginResponse("not-json", "http://vault/v1/auth/approle/login")
      }
      thrown.getMessage should include("Failed to parse Vault login response")
    }
  }

  "VaultFeeder.parseSecretResponse" should {
    "throw RuntimeException on malformed JSON" in {
      val thrown = intercept[RuntimeException] {
        VaultFeeder.parseSecretResponse("not-json", "secret/mypath")
      }
      thrown.getMessage should include("Failed to parse Vault secret response")
      thrown.getMessage should include("mypath")
    }

    "throw RuntimeException when the data field is not a key-value object" in {
      val thrown = intercept[RuntimeException] {
        VaultFeeder.parseSecretResponse("""{"data":"not-an-object"}""", "secret/mypath")
      }
      thrown.getMessage should include("Failed to extract secret data")
      thrown.getMessage should include("mypath")
    }
  }

  "VaultFeeder.isUnsafeVaultUrl" should {
    "return true for http non-localhost" in {
      VaultFeeder.isUnsafeVaultUrl("http://vault.prod.internal") shouldBe true
    }

    "return false for http localhost" in {
      VaultFeeder.isUnsafeVaultUrl("http://localhost:8200") shouldBe false
    }

    "return false for http 127.0.0.1" in {
      VaultFeeder.isUnsafeVaultUrl("http://127.0.0.1:8200") shouldBe false
    }

    "return false for http IPv6 loopback [::1]" in {
      VaultFeeder.isUnsafeVaultUrl("http://[::1]:8200") shouldBe false
    }

    "return false for http unbracketed IPv6 loopback longhand" in {
      VaultFeeder.isUnsafeVaultUrl("http://0:0:0:0:0:0:0:1") shouldBe false
    }

    "return true for http on a non-loopback IPv6 host" in {
      VaultFeeder.isUnsafeVaultUrl("http://[2001:db8::1]:8200") shouldBe true
    }

    "return false for https" in {
      VaultFeeder.isUnsafeVaultUrl("https://vault.prod.internal") shouldBe false
    }
  }
}
