package org.galaxio.gatling.assertions

import com.typesafe.scalalogging.LazyLogging
import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.assertion.AssertionPathParts
import io.gatling.core.config.GatlingConfiguration
import pureconfig.generic.auto._
import pureconfig.module.yaml.YamlConfigSource

object AssertionsBuilder extends LazyLogging {

  private case class NFR(nfr: List[Record])

  private case class Record(key: String, value: Map[String, String])

  private def getNfr(path: String): NFR =
    YamlConfigSource.file(path).asObjectSource.loadOrThrow[NFR]

  private def findGroup(key: String): AssertionPathParts =
    AssertionPathParts.apply(key.split(" / ").toList)

  /** Checked integer parse for response-time thresholds (milliseconds). Surfaces the offending NFR metric key and value instead
    * of the bare `NumberFormatException` that `String.toInt` throws.
    */
  private def parseIntThreshold(metricKey: String, value: String): Int =
    value.toIntOption.getOrElse(
      throw new IllegalArgumentException(s"NFR assertion '$metricKey': value '$value' is not a valid number"),
    )

  /** Checked decimal parse for the error-rate threshold. error-rate is a percentage and MUST accept fractional values (e.g.
    * `5.5`); the previous `value.toInt` crashed on them.
    */
  private def parseDoubleThreshold(metricKey: String, value: String): Double =
    value.toDoubleOption.getOrElse(
      throw new IllegalArgumentException(s"NFR assertion '$metricKey': value '$value' is not a valid number"),
    )

  private def buildAssertion(record: Record)(implicit configuration: GatlingConfiguration): Iterable[Assertion] =
    record.key match {
      case "Процент ошибок"                   => buildErrorAssertion(record)
      case "99 перцентиль времени выполнения" => buildPercentileAssertion(record, 99)
      case "95 перцентиль времени выполнения" => buildPercentileAssertion(record, 95)
      case "75 перцентиль времени выполнения" => buildPercentileAssertion(record, 75)
      case "50 перцентиль времени выполнения" => buildPercentileAssertion(record, 50)
      case "Максимальное время выполнения"    => buildMaxResponseTimeAssertion(record)
      case other                              =>
        logger.warn(s"Unknown NFR assertion key '$other' — skipped")
        None
    }

  private def buildErrorAssertion(record: Record)(implicit configuration: GatlingConfiguration): Iterable[Assertion] =
    record.value.map {
      case ("all", v) => global.failedRequests.percent.lt(parseDoubleThreshold(record.key, v))
      case (k, v)     => details(findGroup(k)).failedRequests.percent.lt(parseDoubleThreshold(record.key, v))
    }

  private def buildPercentileAssertion(record: Record, percentile: Int)(implicit
      configuration: GatlingConfiguration,
  ): Iterable[Assertion] =
    record.value.map {
      case ("all", v) => global.responseTime.percentile(percentile).lt(parseIntThreshold(record.key, v))
      case (k, v)     => details(findGroup(k)).responseTime.percentile(percentile).lt(parseIntThreshold(record.key, v))
    }

  private def buildMaxResponseTimeAssertion(record: Record)(implicit
      configuration: GatlingConfiguration,
  ): Iterable[Assertion] =
    record.value.map {
      case ("all", v) => global.responseTime.max.lt(parseIntThreshold(record.key, v))
      case (k, v)     => details(findGroup(k)).responseTime.max.lt(parseIntThreshold(record.key, v))
    }

  /** Builds Gatling assertions from an NFR YAML file.
    *
    * Public, backward-compatible signature (unchanged since v1.16.0): inside a running simulation the implicit
    * `io.gatling.core.Predef.configuration` (brought in by `import io.gatling.core.Predef._`) resolves the configuration, as
    * before. Unit tests cannot use that implicit (it throws outside a running simulation), so they call the `assertionsFrom`
    * seam with `GatlingConfiguration.loadForTest()`.
    *
    * @deprecated
    *   NFR-YAML assertion loading is deprecated and will be replaced by new assertions functionality in a future release. It
    *   still works for now; watch the changelog.
    */
  @deprecated(
    "NFR-YAML assertion loading is deprecated and will be replaced by new assertions functionality in a future release. It still works for now; watch the changelog.",
    "1.18.0",
  )
  def assertionFromYaml(path: String): Iterable[Assertion] =
    assertionsFrom(path)

  /** Test seam: same builder, with the [[GatlingConfiguration]] taken explicitly so unit tests can inject
    * `GatlingConfiguration.loadForTest()` instead of the throwing simulation-only `Predef.configuration`.
    */
  private[assertions] def assertionsFrom(path: String)(implicit configuration: GatlingConfiguration): Iterable[Assertion] =
    getNfr(path).nfr.flatMap(buildAssertion)

}
