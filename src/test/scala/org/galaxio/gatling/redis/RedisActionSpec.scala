package org.galaxio.gatling.redis

import org.galaxio.gatling.transactions.fixtures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RedisActionSpec extends AnyWordSpec with Matchers {

  "RedisAction.updateSessionWithResult" should {
    "store the redis value when a result is present" in {
      val session = fixtures.emptySession("redis-action")

      val updated = RedisAction.updateSessionWithResult(session, Some("redisValue"), Some("cached-token"))

      updated("redisValue").as[String] shouldBe "cached-token"
    }

    "leave the session unchanged when the redis result is missing" in {
      val session = fixtures.emptySession("redis-action")

      val updated = RedisAction.updateSessionWithResult(session, Some("redisValue"), None)

      updated.contains("redisValue") shouldBe false
    }

    "leave the session unchanged when nothing should be saved" in {
      val session = fixtures.emptySession("redis-action")

      val updated = RedisAction.updateSessionWithResult(session, None, Some("cached-token"))

      updated.attributes shouldBe session.attributes
    }
  }
}
