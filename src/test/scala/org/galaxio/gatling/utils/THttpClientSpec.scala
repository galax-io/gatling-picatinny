package org.galaxio.gatling.utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class THttpClientSpec extends AnyWordSpec with Matchers {

  "THttpClient" should {
    "default to a 3 second connection timeout" in {
      val client = THttpClient().buildClient()

      client.connectTimeout().get().toSeconds shouldBe 3L
    }

    "respect custom connection timeout values" in {
      val client = THttpClient(connectTimeoutInSeconds = 5).buildClient()

      client.connectTimeout().get().toSeconds shouldBe 5L
    }
  }
}
