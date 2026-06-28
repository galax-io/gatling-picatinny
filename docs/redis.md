# Redis

[← Back to README](../README.md)

Use Redis commands as Gatling scenario actions. Not intended for load testing of Redis itself.

## Features

- 33 Redis commands across 6 data types (Strings, Hashes, Lists, Sets, Keys, Counters)
- Save command results to Gatling session with `.saveAs("variable")`
- Custom request names for statistics with `.requestName("name")`
- Gatling EL expressions in keys and values

> Methods are not counted in Gatling statistics by default. Use `.requestName("name")` to track them.

## Import

**Scala:**

```scala
import com.redis.RedisClientPool
import org.galaxio.gatling.redis.RedisActionBuilder._
```

**Java:**

```java
import org.galaxio.gatling.javaapi.redis.RedisClientPoolJava;
```

**Kotlin:**

```kotlin
import org.galaxio.gatling.javaapi.redis.RedisClientPoolJava
```

## Usage

Create `RedisClientPool`:

**Scala:**

```scala
val redisPool = new RedisClientPool("localhost", 6379)
```

**Java:**

```java
RedisClientPoolJava redisPool = new RedisClientPoolJava("localhost", 6379);
```

**Kotlin:**

```kotlin
val redisPool = RedisClientPoolJava("localhost", 6379)
```

Add Redis commands to your scenario chain:

**Scala / Java / Kotlin:**

```scala
.exec(redisPool.SET("key", "value"))
.exec(redisPool.GET("key").saveAs("result"))
.exec(redisPool.DEL("key"))
```

## Available commands

| Category | Commands |
|----------|----------|
| Strings  | `GET`, `SET`, `GETSET`, `SETNX`, `SETEX`, `MGET`, `MSET`* |
| Counters | `INCR`, `INCRBY`, `DECR`, `DECRBY` |
| Hashes   | `HGET`, `HSET`, `HDEL`, `HGETALL`, `HMSET`*, `HMGET` |
| Lists    | `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`, `LLEN` |
| Sets     | `SADD`, `SREM`, `SMEMBERS`, `SISMEMBER`, `SCARD` |
| Keys     | `EXISTS`, `EXPIRE`, `TTL`, `KEYS`, `DEL` |

\* `MSET` and `HMSET` are available only in the Scala API.

> `DEL`, `SADD`, and `SREM` are legacy builders: they do **not** support `.saveAs(...)` / `.requestName(...)` — only the generic commands do.

> **Deprecated:** the standalone action classes `RedisSaddAction`, `RedisSremAction`, and `RedisDelAction` are deprecated (since `picatinny 0.x`) and folded into the generic builder. Prefer `GenericRedisActionBuilder` with `RedisCommand.Sets.SAdd` / `RedisCommand.Sets.SRem` / `RedisCommand.Keys.Del`. See the Deprecations table in [migration.md](migration.md).
