package org.galaxio.gatling.redis

import com.redis.RedisClientPool
import io.gatling.commons.validation.{Failure, Success}
import io.gatling.core.Predef._
import io.gatling.core.session.{Expression, Session}
import org.galaxio.gatling.redis.RedisActionBuilder._
import org.galaxio.gatling.transactions.fixtures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RedisActionBuilderSpec extends AnyWordSpec with Matchers {

  private val redisPool = new RedisClientPool("localhost", 6379)

  private val redisKey: Expression[String]         = "key"
  private val redisKeys: Seq[Expression[String]]   = Seq("keys")
  private val redisValue: Expression[String]       = "value"
  private val redisValues: Seq[Expression[String]] = Seq("values")

  "Legacy RedisActionBuilder syntax" should {
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

  "String operations" should {
    "add GET builder to Gatling chains" in {
      val chain = exec(redisPool.GET(redisKey))
      chain.actionBuilders should have size 1
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add SET builder to Gatling chains" in {
      val chain = exec(redisPool.SET(redisKey, redisValue))
      chain.actionBuilders should have size 1
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add GETSET builder to Gatling chains" in {
      val chain = exec(redisPool.GETSET(redisKey, redisValue))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add SETNX builder to Gatling chains" in {
      val chain = exec(redisPool.SETNX(redisKey, redisValue))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add SETEX builder to Gatling chains" in {
      val expiry: Expression[Any] = "60"
      val chain                   = exec(redisPool.SETEX(redisKey, expiry, redisValue))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add MGET builder to Gatling chains" in {
      val chain = exec(redisPool.MGET(redisKey, redisKeys: _*))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add INCR builder to Gatling chains" in {
      val chain = exec(redisPool.INCR(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add DECR builder to Gatling chains" in {
      val chain = exec(redisPool.DECR(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add INCRBY builder to Gatling chains" in {
      val increment: Expression[Any] = "5"
      val chain                      = exec(redisPool.INCRBY(redisKey, increment))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add DECRBY builder to Gatling chains" in {
      val decrement: Expression[Any] = "3"
      val chain                      = exec(redisPool.DECRBY(redisKey, decrement))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }
  }

  "Hash operations" should {
    "add HGET builder to Gatling chains" in {
      val field: Expression[Any] = "field1"
      val chain                  = exec(redisPool.HGET(redisKey, field))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add HSET builder to Gatling chains" in {
      val field: Expression[Any] = "field1"
      val chain                  = exec(redisPool.HSET(redisKey, field, redisValue))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add HDEL builder to Gatling chains" in {
      val field: Expression[Any] = "field1"
      val chain                  = exec(redisPool.HDEL(redisKey, field))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add HGETALL builder to Gatling chains" in {
      val chain = exec(redisPool.HGETALL(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add HMGET builder to Gatling chains" in {
      val field1: Expression[Any] = "f1"
      val field2: Expression[Any] = "f2"
      val chain                   = exec(redisPool.HMGET(redisKey, field1, field2))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }
  }

  "List operations" should {
    "add LPUSH builder to Gatling chains" in {
      val chain = exec(redisPool.LPUSH(redisKey, redisValue))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add RPUSH builder to Gatling chains" in {
      val chain = exec(redisPool.RPUSH(redisKey, redisValue))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add LPOP builder to Gatling chains" in {
      val chain = exec(redisPool.LPOP(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add RPOP builder to Gatling chains" in {
      val chain = exec(redisPool.RPOP(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add LRANGE builder to Gatling chains" in {
      val start: Expression[Any] = "0"
      val end: Expression[Any]   = "10"
      val chain                  = exec(redisPool.LRANGE(redisKey, start, end))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add LLEN builder to Gatling chains" in {
      val chain = exec(redisPool.LLEN(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }
  }

  "Key operations" should {
    "add EXISTS builder to Gatling chains" in {
      val chain = exec(redisPool.EXISTS(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add EXPIRE builder to Gatling chains" in {
      val ttl: Expression[Any] = "300"
      val chain                = exec(redisPool.EXPIRE(redisKey, ttl))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add TTL builder to Gatling chains" in {
      val chain = exec(redisPool.TTL(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add KEYS builder to Gatling chains" in {
      val pattern: Expression[Any] = "user:*"
      val chain                    = exec(redisPool.KEYS(pattern))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }
  }

  "Set operations" should {
    "add SMEMBERS builder to Gatling chains" in {
      val chain = exec(redisPool.SMEMBERS(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add SISMEMBER builder to Gatling chains" in {
      val chain = exec(redisPool.SISMEMBER(redisKey, redisValue))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add SCARD builder to Gatling chains" in {
      val chain = exec(redisPool.SCARD(redisKey))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }
  }

  "GenericRedisActionBuilder" should {
    "support saveAs chaining" in {
      val builder = redisPool.GET(redisKey).saveAs("result")
      builder shouldBe a[GenericRedisActionBuilder]
      builder.saveAsVar shouldBe Some("result")
    }

    "support requestName chaining" in {
      val builder = redisPool.GET(redisKey).requestName("redis-get")
      builder shouldBe a[GenericRedisActionBuilder]
      builder.reqName shouldBe Some("redis-get")
    }

    "support combined saveAs and requestName chaining" in {
      val builder = redisPool.GET(redisKey).saveAs("result").requestName("redis-get")
      builder.saveAsVar shouldBe Some("result")
      builder.reqName shouldBe Some("redis-get")
    }
  }

  "Numeric argument validation" should {
    def runFactory(builder: GenericRedisActionBuilder, session: Session) =
      builder.commandFactory(session)

    val session = fixtures.emptySession("redis-validation")

    "fail Validation when LRANGE end is non-numeric instead of throwing" in {
      val start: Expression[Any] = "0"
      val end: Expression[Any]   = "not-a-number"
      val builder                = redisPool.LRANGE(redisKey, start, end)
      runFactory(builder, session) shouldBe a[Failure]
    }

    "fail Validation when LRANGE start is non-numeric instead of throwing" in {
      val start: Expression[Any] = "oops"
      val end: Expression[Any]   = "10"
      val builder                = redisPool.LRANGE(redisKey, start, end)
      runFactory(builder, session) shouldBe a[Failure]
    }

    "succeed Validation when LRANGE bounds are numeric strings" in {
      val start: Expression[Any] = "0"
      val end: Expression[Any]   = "10"
      val builder                = redisPool.LRANGE(redisKey, start, end)
      runFactory(builder, session) shouldBe a[Success[_]]
    }

    "fail Validation when EXPIRE ttl is non-numeric instead of throwing" in {
      val ttl: Expression[Any] = "abc"
      val builder              = redisPool.EXPIRE(redisKey, ttl)
      runFactory(builder, session) shouldBe a[Failure]
    }

    "fail Validation when SETEX expiry is non-numeric instead of throwing" in {
      val expiry: Expression[Any] = "not-long"
      val builder                 = redisPool.SETEX(redisKey, expiry, redisValue)
      runFactory(builder, session) shouldBe a[Failure]
    }

    "fail Validation when INCRBY increment is non-numeric instead of throwing" in {
      val inc: Expression[Any] = "x"
      val builder              = redisPool.INCRBY(redisKey, inc)
      runFactory(builder, session) shouldBe a[Failure]
    }

    "fail Validation when DECRBY decrement is non-numeric instead of throwing" in {
      val dec: Expression[Any] = "x"
      val builder              = redisPool.DECRBY(redisKey, dec)
      runFactory(builder, session) shouldBe a[Failure]
    }

    "succeed Validation when SETEX expiry is a typed Long" in {
      val expiry: Expression[Any] = (_: Session) => Success(60L)
      val builder                 = redisPool.SETEX(redisKey, expiry, redisValue)
      runFactory(builder, session) shouldBe a[Success[_]]
    }

    "succeed Validation when EXPIRE ttl is a typed Int" in {
      val ttl: Expression[Any] = (_: Session) => Success(300)
      val builder              = redisPool.EXPIRE(redisKey, ttl)
      runFactory(builder, session) shouldBe a[Success[_]]
    }

    "succeed Validation when LRANGE bounds are typed Ints" in {
      val start: Expression[Any] = (_: Session) => Success(0)
      val end: Expression[Any]   = (_: Session) => Success(10)
      val builder                = redisPool.LRANGE(redisKey, start, end)
      runFactory(builder, session) shouldBe a[Success[_]]
    }
  }

  "Typed numeric variants" should {
    "add setExL builder to Gatling chains" in {
      val ttl: Expression[Long] = 60L
      val chain                 = exec(redisPool.setExL(redisKey, ttl, redisValue))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add incrByL builder to Gatling chains" in {
      val inc: Expression[Long] = 5L
      val chain                 = exec(redisPool.incrByL(redisKey, inc))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add decrByL builder to Gatling chains" in {
      val dec: Expression[Long] = 3L
      val chain                 = exec(redisPool.decrByL(redisKey, dec))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add lRangeI builder to Gatling chains" in {
      val start: Expression[Int] = 0
      val end: Expression[Int]   = 10
      val chain                  = exec(redisPool.lRangeI(redisKey, start, end))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }

    "add expireI builder to Gatling chains" in {
      val ttl: Expression[Int] = 300
      val chain                = exec(redisPool.expireI(redisKey, ttl))
      chain.actionBuilders.head shouldBe a[GenericRedisActionBuilder]
    }
  }
}
