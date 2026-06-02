package org.galaxio.gatling.redis

import com.redis.RedisClientPool
import org.galaxio.gatling.jmh.JmhBenchmark
import org.galaxio.gatling.redis.RedisActionBuilder._
import org.openjdk.jmh.annotations.Benchmark

// Scoped to RedisAction construction / command-factory overhead;
// live Redis I/O is intentionally out of scope (no embedded-redis dependency in JMH classpath).
class RedisActionBenchmark extends JmhBenchmark {

  private val clientPool = new RedisClientPool("localhost", 6379)

  private val key: io.gatling.core.session.Expression[Any]   = (s: io.gatling.core.session.Session) =>
    io.gatling.commons.validation.Success("user:42")
  private val value: io.gatling.core.session.Expression[Any] = (s: io.gatling.core.session.Session) =>
    io.gatling.commons.validation.Success("token")
  private val keyB: io.gatling.core.session.Expression[Any]  = (s: io.gatling.core.session.Session) =>
    io.gatling.commons.validation.Success("user:43")
  private val keyC: io.gatling.core.session.Expression[Any]  = (s: io.gatling.core.session.Session) =>
    io.gatling.commons.validation.Success("user:44")

  private val getBuilder: GenericRedisActionBuilder  = clientPool.GET(key)
  private val setBuilder: GenericRedisActionBuilder  = clientPool.SET(key, value)
  private val incrBuilder: GenericRedisActionBuilder = clientPool.INCR(key)
  private val mgetBuilder: GenericRedisActionBuilder = clientPool.MGET(key, keyB, keyC)

  // A real Session is non-trivial to allocate outside the Gatling runtime;
  // command factories here are designed to ignore the session argument,
  // so passing null exercises the validation/closure path without a live engine.
  private val nullSession: io.gatling.core.session.Session = null

  @Benchmark
  def buildGetCommand(): io.gatling.commons.validation.Validation[RedisCommand] =
    getBuilder.commandFactory(nullSession)

  @Benchmark
  def buildSetCommand(): io.gatling.commons.validation.Validation[RedisCommand] =
    setBuilder.commandFactory(nullSession)

  @Benchmark
  def buildIncrCommand(): io.gatling.commons.validation.Validation[RedisCommand] =
    incrBuilder.commandFactory(nullSession)

  @Benchmark
  def buildMgetCommand(): io.gatling.commons.validation.Validation[RedisCommand] =
    mgetBuilder.commandFactory(nullSession)

  @Benchmark
  def constructGetCase(): RedisCommand = RedisCommand.Strings.Get("user:42")

  @Benchmark
  def constructSetCase(): RedisCommand = RedisCommand.Strings.Set("user:42", "token")

  @Benchmark
  def constructIncrCase(): RedisCommand = RedisCommand.Strings.Incr("user:42")

  @Benchmark
  def constructMgetCase(): RedisCommand =
    RedisCommand.Strings.MGet("user:42", Seq("user:43", "user:44"))

  @Benchmark
  def commandNameGet(): String = RedisCommand.Strings.Get("k").commandName

  @Benchmark
  def commandNameMget(): String = RedisCommand.Strings.MGet("k", Seq("k2")).commandName
}
