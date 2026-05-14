package org.galaxio.gatling.redis

import com.redis.RedisClientPool
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext

object RedisActionBuilder {

  implicit class RedisClientPoolOps(clientPool: RedisClientPool) {

    // Legacy methods (backward compatible)
    def DEL(key: Expression[Any], keys: Expression[Any]*): RedisDelActionBuilder =
      RedisDelActionBuilder(clientPool, key, keys)

    def SREM(key: Expression[Any], value: Expression[Any], values: Expression[Any]*): RedisSremActionBuilder =
      RedisSremActionBuilder(clientPool, key, value, values)

    def SADD(key: Expression[Any], value: Expression[Any], values: Expression[Any]*): RedisSaddActionBuilder =
      RedisSaddActionBuilder(clientPool, key, value, values)

    // String operations
    def GET(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Strings.Get(k)),
      )

    def SET(key: Expression[Any], value: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          v <- value(session)
        } yield RedisCommand.Strings.Set(k, v),
      )

    def GETSET(key: Expression[Any], value: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          v <- value(session)
        } yield RedisCommand.Strings.GetSet(k, v),
      )

    def SETNX(key: Expression[Any], value: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          v <- value(session)
        } yield RedisCommand.Strings.SetNx(k, v),
      )

    def SETEX(key: Expression[Any], expiry: Expression[Any], value: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          e <- expiry(session)
          v <- value(session)
        } yield RedisCommand.Strings.SetEx(k, e.toString.toLong, v),
      )

    def MGET(key: Expression[Any], keys: Expression[Any]*): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k  <- key(session)
          ks <- resolveSeq(keys, session)
        } yield RedisCommand.Strings.MGet(k, ks),
      )

    def MSET(kvs: (Expression[Any], Expression[Any])*): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        resolvePairs(kvs, session).map(pairs => RedisCommand.Strings.MSet(pairs)),
      )

    // Counter operations
    def INCR(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Strings.Incr(k)),
      )

    def INCRBY(key: Expression[Any], increment: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          i <- increment(session)
        } yield RedisCommand.Strings.IncrBy(k, i.toString.toLong),
      )

    def DECR(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Strings.Decr(k)),
      )

    def DECRBY(key: Expression[Any], decrement: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          d <- decrement(session)
        } yield RedisCommand.Strings.DecrBy(k, d.toString.toLong),
      )

    // Hash operations
    def HGET(key: Expression[Any], field: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          f <- field(session)
        } yield RedisCommand.Hashes.HGet(k, f),
      )

    def HSET(key: Expression[Any], field: Expression[Any], value: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          f <- field(session)
          v <- value(session)
        } yield RedisCommand.Hashes.HSet(k, f, v),
      )

    def HDEL(key: Expression[Any], field: Expression[Any], fields: Expression[Any]*): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k  <- key(session)
          f  <- field(session)
          fs <- resolveSeq(fields, session)
        } yield RedisCommand.Hashes.HDel(k, f, fs),
      )

    def HGETALL(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Hashes.HGetAll(k)),
      )

    def HMSET(key: Expression[Any], kvs: (Expression[Any], Expression[Any])*): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k     <- key(session)
          pairs <- resolvePairs(kvs, session)
        } yield RedisCommand.Hashes.HMSet(k, pairs.map { case (a, b) => (a, b) }),
      )

    def HMGET(key: Expression[Any], fields: Expression[Any]*): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k  <- key(session)
          fs <- resolveSeq(fields, session)
        } yield RedisCommand.Hashes.HMGet(k, fs),
      )

    // List operations
    def LPUSH(key: Expression[Any], value: Expression[Any], values: Expression[Any]*): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k  <- key(session)
          v  <- value(session)
          vs <- resolveSeq(values, session)
        } yield RedisCommand.Lists.LPush(k, v, vs),
      )

    def RPUSH(key: Expression[Any], value: Expression[Any], values: Expression[Any]*): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k  <- key(session)
          v  <- value(session)
          vs <- resolveSeq(values, session)
        } yield RedisCommand.Lists.RPush(k, v, vs),
      )

    def LPOP(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Lists.LPop(k)),
      )

    def RPOP(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Lists.RPop(k)),
      )

    def LRANGE(key: Expression[Any], start: Expression[Any], end: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          s <- start(session)
          e <- end(session)
        } yield RedisCommand.Lists.LRange(k, s.toString.toInt, e.toString.toInt),
      )

    def LLEN(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Lists.LLen(k)),
      )

    // Key operations (DEL already in legacy methods above)
    def EXISTS(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Keys.Exists(k)),
      )

    def EXPIRE(key: Expression[Any], ttl: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          t <- ttl(session)
        } yield RedisCommand.Keys.Expire(k, t.toString.toInt),
      )

    def TTL(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Keys.Ttl(k)),
      )

    def KEYS(pattern: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        pattern(session).map(p => RedisCommand.Keys.KeysPattern(p)),
      )

    // Set operations (SADD, SREM already in legacy methods above)
    def SMEMBERS(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Sets.SMembers(k)),
      )

    def SISMEMBER(key: Expression[Any], value: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        for {
          k <- key(session)
          v <- value(session)
        } yield RedisCommand.Sets.SIsMember(k, v),
      )

    def SCARD(key: Expression[Any]): GenericRedisActionBuilder =
      GenericRedisActionBuilder(clientPool, session =>
        key(session).map(k => RedisCommand.Sets.SCard(k)),
      )

  }

  // Legacy builders (backward compatible)
  case class RedisDelActionBuilder(clientPool: RedisClientPool, key: Expression[Any], keys: Seq[Expression[Any]])
      extends ActionBuilder {
    override def build(ctx: ScenarioContext, next: Action): Action = RedisDelAction(ctx, next, clientPool, key, keys)
  }

  case class RedisSremActionBuilder(
      clientPool: RedisClientPool,
      key: Expression[Any],
      value: Expression[Any],
      values: Seq[Expression[Any]],
  ) extends ActionBuilder {
    override def build(ctx: ScenarioContext, next: Action): Action = RedisSremAction(ctx, next, clientPool, key, value, values)
  }

  case class RedisSaddActionBuilder(
      clientPool: RedisClientPool,
      key: Expression[Any],
      value: Expression[Any],
      values: Seq[Expression[Any]],
  ) extends ActionBuilder {
    override def build(ctx: ScenarioContext, next: Action): Action = RedisSaddAction(ctx, next, clientPool, key, value, values)
  }

  // New generic builder with saveAs/requestName support
  case class GenericRedisActionBuilder(
      clientPool: RedisClientPool,
      commandFactory: Session => Validation[RedisCommand],
      saveAsVar: Option[String] = None,
      reqName: Option[String] = None,
  ) extends ActionBuilder {

    def saveAs(variable: String): GenericRedisActionBuilder = copy(saveAsVar = Some(variable))

    def requestName(name: String): GenericRedisActionBuilder = copy(reqName = Some(name))

    override def build(ctx: ScenarioContext, next: Action): Action =
      RedisAction(ctx, next, clientPool, commandFactory, saveAsVar, reqName)
  }

  private def resolveSeq(exprs: Seq[Expression[Any]], session: Session): Validation[Seq[Any]] = {
    val results = exprs.map(_(session))
    val failures = results.collect { case Failure(msg) => msg }
    if (failures.nonEmpty)
      Failure(failures.mkString(", "))
    else
      Success(results.collect { case Success(v) => v })
  }

  private def resolvePairs(
      pairs: Seq[(Expression[Any], Expression[Any])],
      session: Session,
  ): Validation[Seq[(Any, Any)]] = {
    val results = pairs.map { case (kExpr, vExpr) =>
      for {
        k <- kExpr(session)
        v <- vExpr(session)
      } yield (k, v)
    }
    val failures = results.collect { case Failure(msg) => msg }
    if (failures.nonEmpty)
      Failure(failures.mkString(", "))
    else
      Success(results.collect { case Success(pair) => pair })
  }

}
