package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Record

import java.util.Objects.requireNonNull

/** Creates a one-record feeder from environment variables.
  *
  * This is useful for CI/CD pipelines where credentials, tenant identifiers, or small static test-data values are already
  * injected as environment variables.
  */
object EnvFeeder {

  /** Reads selected environment variables into a single feeder record.
    *
    * Missing variables are skipped. If no requested variables are present, an empty feeder source is returned.
    *
    * @param keys
    *   environment variable names to read
    * @param prefix
    *   optional prefix to strip from environment variable names when creating feeder keys
    */
  def apply(keys: List[String], prefix: String = ""): IndexedSeq[Record[String]] =
    fromEnv(keys, prefix, sys.env)

  /** Test seam: same logic against an injected environment map (production passes `sys.env`). Lets unit tests use a
    * deterministic environment instead of sampling the live, unspecified-order `sys.env`.
    */
  private[feeders] def fromEnv(keys: List[String], prefix: String, env: Map[String, String]): IndexedSeq[Record[String]] = {
    requireNonNull(keys, "Keys list must not be null")

    val record = keys.flatMap { key =>
      env.get(key).map { value =>
        val feederKey = if (prefix.nonEmpty && key.startsWith(prefix)) key.stripPrefix(prefix) else key
        feederKey -> value
      }
    }.toMap

    if (record.nonEmpty) IndexedSeq(record) else IndexedSeq.empty
  }

  /** Reads all environment variables matching a prefix into a single feeder record.
    *
    * The prefix is stripped from emitted feeder keys, so `PERF_TOKEN` with prefix `PERF_` becomes `TOKEN`.
    */
  def withPrefix(prefix: String): IndexedSeq[Record[String]] =
    withPrefixFrom(prefix, sys.env)

  /** Test seam for [[withPrefix]] against an injected environment map (production passes `sys.env`). */
  private[feeders] def withPrefixFrom(prefix: String, env: Map[String, String]): IndexedSeq[Record[String]] = {
    require(prefix.nonEmpty, "Prefix must be non-empty")

    val record = env.collect {
      case (key, value) if key.startsWith(prefix) => key.stripPrefix(prefix) -> value
    }

    if (record.nonEmpty) IndexedSeq(record) else IndexedSeq.empty
  }
}
