package org.galaxio.gatling.redis

import com.redis.RedisClient
import com.redis.serialization.Parse

sealed trait RedisCommand {
  def execute(client: RedisClient): Option[Any]
  def commandName: String
}

object RedisCommand {

  object Strings {

    case class Get(key: Any) extends RedisCommand {
      val commandName                               = "GET"
      def execute(client: RedisClient): Option[Any] =
        client.get[String](key)
    }

    case class Set(key: Any, value: Any) extends RedisCommand {
      val commandName                               = "SET"
      def execute(client: RedisClient): Option[Any] =
        Some(client.set(key, value))
    }

    case class GetSet(key: Any, value: Any) extends RedisCommand {
      val commandName                               = "GETSET"
      def execute(client: RedisClient): Option[Any] =
        client.getset[String](key, value)
    }

    case class SetNx(key: Any, value: Any) extends RedisCommand {
      val commandName                               = "SETNX"
      def execute(client: RedisClient): Option[Any] =
        Some(client.setnx(key, value))
    }

    case class SetEx(key: Any, expiry: Long, value: Any) extends RedisCommand {
      val commandName                               = "SETEX"
      def execute(client: RedisClient): Option[Any] =
        Some(client.setex(key, expiry, value))
    }

    case class MGet(key: Any, keys: Seq[Any]) extends RedisCommand {
      val commandName                               = "MGET"
      def execute(client: RedisClient): Option[Any] =
        client.mget[String](key, keys: _*)
    }

    case class MSet(kvs: Seq[(Any, Any)]) extends RedisCommand {
      val commandName                               = "MSET"
      def execute(client: RedisClient): Option[Any] =
        Some(client.mset(kvs: _*))
    }

    case class Incr(key: Any) extends RedisCommand {
      val commandName                               = "INCR"
      def execute(client: RedisClient): Option[Any] =
        client.incr(key)
    }

    case class IncrBy(key: Any, increment: Long) extends RedisCommand {
      val commandName                               = "INCRBY"
      def execute(client: RedisClient): Option[Any] =
        client.incrby(key, increment)
    }

    case class Decr(key: Any) extends RedisCommand {
      val commandName                               = "DECR"
      def execute(client: RedisClient): Option[Any] =
        client.decr(key)
    }

    case class DecrBy(key: Any, decrement: Long) extends RedisCommand {
      val commandName                               = "DECRBY"
      def execute(client: RedisClient): Option[Any] =
        client.decrby(key, decrement)
    }

  }

  object Hashes {

    case class HGet(key: Any, field: Any) extends RedisCommand {
      val commandName                               = "HGET"
      def execute(client: RedisClient): Option[Any] =
        client.hget[String](key, field)
    }

    case class HSet(key: Any, field: Any, value: Any) extends RedisCommand {
      val commandName                               = "HSET"
      def execute(client: RedisClient): Option[Any] =
        Some(client.hset(key, field, value))
    }

    case class HDel(key: Any, field: Any, fields: Seq[Any]) extends RedisCommand {
      val commandName                               = "HDEL"
      def execute(client: RedisClient): Option[Any] =
        client.hdel(key, field, fields: _*)
    }

    case class HGetAll(key: Any) extends RedisCommand {
      val commandName                               = "HGETALL"
      def execute(client: RedisClient): Option[Any] =
        client.hgetall[String, String](key)
    }

    case class HMSet(key: Any, kvs: Iterable[Product2[Any, Any]]) extends RedisCommand {
      val commandName                               = "HMSET"
      def execute(client: RedisClient): Option[Any] =
        Some(client.hmset(key, kvs))
    }

    case class HMGet(key: Any, fields: Seq[Any]) extends RedisCommand {
      val commandName                               = "HMGET"
      def execute(client: RedisClient): Option[Any] =
        client.hmget[Any, String](key, fields: _*)
    }

  }

  object Lists {

    case class LPush(key: Any, value: Any, values: Seq[Any]) extends RedisCommand {
      val commandName                               = "LPUSH"
      def execute(client: RedisClient): Option[Any] =
        client.lpush(key, value, values: _*)
    }

    case class RPush(key: Any, value: Any, values: Seq[Any]) extends RedisCommand {
      val commandName                               = "RPUSH"
      def execute(client: RedisClient): Option[Any] =
        client.rpush(key, value, values: _*)
    }

    case class LPop(key: Any) extends RedisCommand {
      val commandName                               = "LPOP"
      def execute(client: RedisClient): Option[Any] =
        client.lpop[String](key)
    }

    case class RPop(key: Any) extends RedisCommand {
      val commandName                               = "RPOP"
      def execute(client: RedisClient): Option[Any] =
        client.rpop[String](key)
    }

    case class LRange(key: Any, start: Int, end: Int) extends RedisCommand {
      val commandName                               = "LRANGE"
      def execute(client: RedisClient): Option[Any] =
        client.lrange[String](key, start, end)
    }

    case class LLen(key: Any) extends RedisCommand {
      val commandName                               = "LLEN"
      def execute(client: RedisClient): Option[Any] =
        client.llen(key)
    }

  }

  object Keys {

    case class Del(key: Any, keys: Seq[Any]) extends RedisCommand {
      val commandName                               = "DEL"
      def execute(client: RedisClient): Option[Any] =
        client.del(key, keys: _*)
    }

    case class Exists(key: Any) extends RedisCommand {
      val commandName                               = "EXISTS"
      def execute(client: RedisClient): Option[Any] =
        Some(client.exists(key))
    }

    case class Expire(key: Any, ttl: Int) extends RedisCommand {
      val commandName                               = "EXPIRE"
      def execute(client: RedisClient): Option[Any] =
        Some(client.expire(key, ttl))
    }

    case class Ttl(key: Any) extends RedisCommand {
      val commandName                               = "TTL"
      def execute(client: RedisClient): Option[Any] =
        client.ttl(key)
    }

    case class KeysPattern(pattern: Any) extends RedisCommand {
      val commandName                               = "KEYS"
      def execute(client: RedisClient): Option[Any] =
        client.keys[String](pattern)
    }

  }

  object Sets {

    case class SAdd(key: Any, value: Any, values: Seq[Any]) extends RedisCommand {
      val commandName                               = "SADD"
      def execute(client: RedisClient): Option[Any] =
        client.sadd(key, value, values: _*)
    }

    case class SRem(key: Any, value: Any, values: Seq[Any]) extends RedisCommand {
      val commandName                               = "SREM"
      def execute(client: RedisClient): Option[Any] =
        client.srem(key, value, values: _*)
    }

    case class SMembers(key: Any) extends RedisCommand {
      val commandName                               = "SMEMBERS"
      def execute(client: RedisClient): Option[Any] =
        client.smembers[String](key)
    }

    case class SIsMember(key: Any, value: Any) extends RedisCommand {
      val commandName                               = "SISMEMBER"
      def execute(client: RedisClient): Option[Any] =
        Some(client.sismember(key, value))
    }

    case class SCard(key: Any) extends RedisCommand {
      val commandName                               = "SCARD"
      def execute(client: RedisClient): Option[Any] =
        client.scard(key)
    }

  }

}
