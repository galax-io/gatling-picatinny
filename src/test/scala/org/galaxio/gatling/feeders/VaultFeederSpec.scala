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
}
