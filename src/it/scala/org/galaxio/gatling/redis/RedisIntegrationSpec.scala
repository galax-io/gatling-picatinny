package org.galaxio.gatling.redis

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import com.redis.RedisClientPool
import org.galaxio.gatling.tags.DockerTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.wait.strategy.Wait

class RedisIntegrationSpec extends AnyWordSpec with Matchers with ForAllTestContainer {

  override val container: GenericContainer = GenericContainer(
    dockerImage = "redis:7-alpine",
    exposedPorts = Seq(6379),
    waitStrategy = Wait.forListeningPort(),
  )

  private lazy val redisPool = new RedisClientPool("localhost", container.mappedPort(6379))

  private def exec(cmd: RedisCommand): Option[Any] =
    redisPool.withClient(client => cmd.execute(client))

  "String commands" should {
    "SET and GET a value" taggedAs DockerTest in {
      exec(RedisCommand.Strings.Set("test:str:1", "hello")) shouldBe Some(true)
      exec(RedisCommand.Strings.Get("test:str:1")) shouldBe Some("hello")
    }

    "SETNX only when key does not exist" taggedAs DockerTest in {
      exec(RedisCommand.Strings.SetNx("test:setnx:1", "first")) shouldBe Some(true)
      exec(RedisCommand.Strings.SetNx("test:setnx:1", "second")) shouldBe Some(false)
      exec(RedisCommand.Strings.Get("test:setnx:1")) shouldBe Some("first")
    }

    "SETEX with expiry" taggedAs DockerTest in {
      exec(RedisCommand.Strings.SetEx("test:setex:1", 10, "expiring")) shouldBe Some(true)
      val ttl = exec(RedisCommand.Keys.Ttl("test:setex:1")).map(_.asInstanceOf[Long])
      ttl.exists(v => v > 0 && v <= 10) shouldBe true
    }

    "GETSET returns old value" taggedAs DockerTest in {
      exec(RedisCommand.Strings.Set("test:getset:1", "old"))
      exec(RedisCommand.Strings.GetSet("test:getset:1", "new")) shouldBe Some("old")
      exec(RedisCommand.Strings.Get("test:getset:1")) shouldBe Some("new")
    }

    "MGET returns multiple values" taggedAs DockerTest in {
      exec(RedisCommand.Strings.Set("test:mget:1", "a"))
      exec(RedisCommand.Strings.Set("test:mget:2", "b"))
      exec(RedisCommand.Strings.MGet("test:mget:1", Seq("test:mget:2"))) shouldBe
        Some(List(Some("a"), Some("b")))
    }

    "MSET sets multiple values" taggedAs DockerTest in {
      exec(RedisCommand.Strings.MSet(Seq("test:mset:1" -> "x", "test:mset:2" -> "y"))) shouldBe Some(true)
      exec(RedisCommand.Strings.Get("test:mset:1")) shouldBe Some("x")
      exec(RedisCommand.Strings.Get("test:mset:2")) shouldBe Some("y")
    }
  }

  "Counter commands" should {
    "INCR and DECR" taggedAs DockerTest in {
      exec(RedisCommand.Strings.Set("test:counter:1", "10"))
      exec(RedisCommand.Strings.Incr("test:counter:1")) shouldBe Some(11L)
      exec(RedisCommand.Strings.Decr("test:counter:1")) shouldBe Some(10L)
    }

    "INCRBY and DECRBY" taggedAs DockerTest in {
      exec(RedisCommand.Strings.Set("test:counter:2", "100"))
      exec(RedisCommand.Strings.IncrBy("test:counter:2", 25)) shouldBe Some(125L)
      exec(RedisCommand.Strings.DecrBy("test:counter:2", 50)) shouldBe Some(75L)
    }
  }

  "Hash commands" should {
    "HSET and HGET" taggedAs DockerTest in {
      exec(RedisCommand.Hashes.HSet("test:hash:1", "field1", "value1")) shouldBe Some(true)
      exec(RedisCommand.Hashes.HGet("test:hash:1", "field1")) shouldBe Some("value1")
    }

    "HDEL removes field" taggedAs DockerTest in {
      exec(RedisCommand.Hashes.HSet("test:hash:2", "f1", "v1"))
      exec(RedisCommand.Hashes.HDel("test:hash:2", "f1", Seq.empty)) shouldBe Some(1L)
      exec(RedisCommand.Hashes.HGet("test:hash:2", "f1")) shouldBe None
    }

    "HGETALL returns all fields" taggedAs DockerTest in {
      exec(RedisCommand.Hashes.HSet("test:hash:3", "a", "1"))
      exec(RedisCommand.Hashes.HSet("test:hash:3", "b", "2"))
      exec(RedisCommand.Hashes.HGetAll("test:hash:3")) shouldBe Some(Map("a" -> "1", "b" -> "2"))
    }

    "HMSET and HMGET" taggedAs DockerTest in {
      exec(RedisCommand.Hashes.HMSet("test:hash:4", Seq("x" -> "10", "y" -> "20"))) shouldBe Some(true)
      exec(RedisCommand.Hashes.HMGet("test:hash:4", Seq("x", "y"))) shouldBe Some(Map("x" -> "10", "y" -> "20"))
    }
  }

  "List commands" should {
    "LPUSH, RPUSH and LRANGE" taggedAs DockerTest in {
      exec(RedisCommand.Lists.LPush("test:list:1", "a", Seq("b")))
      exec(RedisCommand.Lists.RPush("test:list:1", "c", Seq.empty))
      val result = exec(RedisCommand.Lists.LRange("test:list:1", 0, -1))
        .map(_.asInstanceOf[List[Option[String]]].flatten)
      result shouldBe Some(List("b", "a", "c"))
    }

    "LPOP and RPOP" taggedAs DockerTest in {
      redisPool.withClient(_.lpush("test:list:2", "a", "b", "c"))
      exec(RedisCommand.Lists.LPop("test:list:2")) shouldBe Some("c")
      exec(RedisCommand.Lists.RPop("test:list:2")) shouldBe Some("a")
    }

    "LLEN returns list length" taggedAs DockerTest in {
      redisPool.withClient(_.lpush("test:list:3", "a", "b", "c"))
      exec(RedisCommand.Lists.LLen("test:list:3")) shouldBe Some(3L)
    }
  }

  "Key commands" should {
    "DEL removes key" taggedAs DockerTest in {
      redisPool.withClient(_.set("test:del:1", "val"))
      exec(RedisCommand.Keys.Del("test:del:1", Seq.empty)) shouldBe Some(1L)
      exec(RedisCommand.Keys.Exists("test:del:1")) shouldBe Some(false)
    }

    "EXISTS checks key existence" taggedAs DockerTest in {
      redisPool.withClient(_.set("test:exists:1", "val"))
      exec(RedisCommand.Keys.Exists("test:exists:1")) shouldBe Some(true)
      exec(RedisCommand.Keys.Exists("test:exists:nonexistent")) shouldBe Some(false)
    }

    "EXPIRE and TTL" taggedAs DockerTest in {
      redisPool.withClient(_.set("test:expire:1", "val"))
      exec(RedisCommand.Keys.Expire("test:expire:1", 60)) shouldBe Some(true)
      val ttl = exec(RedisCommand.Keys.Ttl("test:expire:1")).map(_.asInstanceOf[Long])
      ttl.exists(v => v > 0 && v <= 60) shouldBe true
    }

    "KEYS returns matching keys" taggedAs DockerTest in {
      redisPool.withClient(_.set("test:keys:pattern:a", "1"))
      redisPool.withClient(_.set("test:keys:pattern:b", "2"))
      val result = exec(RedisCommand.Keys.KeysPattern("test:keys:pattern:*"))
        .map(_.asInstanceOf[List[Option[String]]].flatten.sorted)
      result shouldBe Some(List("test:keys:pattern:a", "test:keys:pattern:b"))
    }
  }

  "Set commands" should {
    "SADD, SREM and SMEMBERS" taggedAs DockerTest in {
      exec(RedisCommand.Sets.SAdd("test:set:1", "a", Seq("b", "c"))) shouldBe Some(3L)
      exec(RedisCommand.Sets.SRem("test:set:1", "b", Seq.empty)) shouldBe Some(1L)
      val result = exec(RedisCommand.Sets.SMembers("test:set:1"))
        .map(_.asInstanceOf[Set[Option[String]]].flatten)
      result shouldBe Some(Set("a", "c"))
    }

    "SISMEMBER checks membership" taggedAs DockerTest in {
      redisPool.withClient(_.sadd("test:set:2", "x", "y"))
      exec(RedisCommand.Sets.SIsMember("test:set:2", "x")) shouldBe Some(true)
      exec(RedisCommand.Sets.SIsMember("test:set:2", "z")) shouldBe Some(false)
    }

    "SCARD returns set cardinality" taggedAs DockerTest in {
      redisPool.withClient(_.sadd("test:set:3", "a", "b", "c"))
      exec(RedisCommand.Sets.SCard("test:set:3")) shouldBe Some(3L)
    }
  }
}
