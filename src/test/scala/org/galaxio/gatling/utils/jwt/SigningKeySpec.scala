package org.galaxio.gatling.utils.jwt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.security.{KeyPairGenerator, PrivateKey}

/** Unit tests (test-model layer 1) for [[SigningKey]] secret redaction (FR-005). `toString` must never expose the HMAC secret
  * or asymmetric key material, while the `value` accessor still returns the raw material (API intact).
  */
class SigningKeySpec extends AnyWordSpec with Matchers {

  "SigningKey.StringSecret" should {
    "redact the secret in toString while keeping the value accessor intact" in {
      val key = SigningKey.StringSecret("topsecret")
      key.toString shouldBe "StringSecret(******)"
      key.toString should not include "topsecret"
      s"key=$key" should not include "topsecret"
      key.value shouldBe "topsecret"
    }
  }

  "SigningKey.AsymmetricKey" should {
    "redact the key material in toString while keeping the value accessor intact" in {
      val privateKey: PrivateKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate
      val key                    = SigningKey.AsymmetricKey(privateKey)
      key.toString shouldBe "AsymmetricKey(******)"
      key.toString should not include privateKey.toString
      key.value shouldBe privateKey
    }
  }
}
