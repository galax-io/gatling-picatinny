package org.galaxio.gatling.redis

import com.redis.RedisClientPool
import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import org.galaxio.gatling.redis.RedisActionBuilder._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RedisActionBuilderSpec extends AnyWordSpec with Matchers {

  private val redisPool = new RedisClientPool("localhost", 6379)

  private val redisKey: Expression[String]         = "key"
  private val redisKeys: Seq[Expression[String]]   = Seq("keys")
  private val redisValue: Expression[String]       = "value"
  private val redisValues: Seq[Expression[String]] = Seq("values")

  "RedisActionBuilder syntax" should {
    "add DEL builders to Gatling chains" in {
      exec(redisPool.DEL(redisKey, redisKeys: _*)).actionBuilders should contain(
        RedisDelActionBuilder(redisPool, redisKey, redisKeys),
      )
    }

    "add SREM builders to Gatling chains" in {
      exec(redisPool.SREM(redisKey, redisValue, redisValues: _*)).actionBuilders should contain(
        RedisSremActionBuilder(redisPool, redisKey, redisValue, redisValues),
      )
    }

    "add SADD builders to Gatling chains" in {
      exec(redisPool.SADD(redisKey, redisValue, redisValues: _*)).actionBuilders should contain(
        RedisSaddActionBuilder(redisPool, redisKey, redisValue, redisValues),
      )
    }
  }
}
