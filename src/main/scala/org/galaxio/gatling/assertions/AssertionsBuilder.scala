package org.galaxio.gatling.assertions

import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.assertion.AssertionPathParts
import io.gatling.core.config.GatlingConfiguration
import pureconfig.generic.auto._
import pureconfig.module.yaml.YamlConfigSource

object AssertionsBuilder {

  private case class NFR(nfr: List[Record])

  private case class Record(key: String, value: Map[String, String])

  private def getNfr(path: String): NFR =
    YamlConfigSource.file(path).asObjectSource.loadOrThrow[NFR]

  private def toUtf(baseString: String): String =
    scala.io.Source.fromBytes(baseString.getBytes(), "UTF-8").mkString

  private def findGroup(key: String): AssertionPathParts =
    AssertionPathParts.apply(key.split(" / ").toList)

  private def buildAssertion(record: Record)(implicit configuration: GatlingConfiguration): Iterable[Assertion] =
    toUtf(record.key) match {
      case "Процент ошибок"                   => buildErrorAssertion(record)
      case "99 перцентиль времени выполнения" => buildPercentileAssertion(record, 99)
      case "95 перцентиль времени выполнения" => buildPercentileAssertion(record, 95)
      case "75 перцентиль времени выполнения" => buildPercentileAssertion(record, 75)
      case "50 перцентиль времени выполнения" => buildPercentileAssertion(record, 50)
      case "Максимальное время выполнения"    => buildMaxResponseTimeAssertion(record)
      case _                                  => None
    }

  private def buildErrorAssertion(record: Record)(implicit configuration: GatlingConfiguration): Iterable[Assertion] =
    record.value.map {
      case ("all", v) => global.failedRequests.percent.lt(v.toInt)
      case (k, v)     => details(findGroup(k)).failedRequests.percent.lt(v.toInt)
    }

  private def buildPercentileAssertion(record: Record, percentile: Int)(implicit
      configuration: GatlingConfiguration,
  ): Iterable[Assertion] =
    record.value.map {
      case ("all", v) => global.responseTime.percentile(percentile).lt(v.toInt)
      case (k, v)     => details(findGroup(k)).responseTime.percentile(percentile).lt(v.toInt)
    }

  private def buildMaxResponseTimeAssertion(record: Record)(implicit
      configuration: GatlingConfiguration,
  ): Iterable[Assertion] =
    record.value.map {
      case ("all", v) => global.responseTime.max.lt(v.toInt)
      case (k, v)     => details(findGroup(k)).responseTime.max.lt(v.toInt)
    }

  /** Builds Gatling assertions from an NFR YAML file.
    *
    * Public, backward-compatible signature (unchanged since v1.16.0): inside a running simulation the implicit
    * `io.gatling.core.Predef.configuration` (brought in by `import io.gatling.core.Predef._`) resolves the configuration, as
    * before. Unit tests cannot use that implicit (it throws outside a running simulation), so they call the [[assertionsFrom]]
    * seam with `GatlingConfiguration.loadForTest()`.
    */
  def assertionFromYaml(path: String): Iterable[Assertion] =
    assertionsFrom(path)

  /** Test seam: same builder, with the [[GatlingConfiguration]] taken explicitly so unit tests can inject
    * `GatlingConfiguration.loadForTest()` instead of the throwing simulation-only `Predef.configuration`.
    */
  private[assertions] def assertionsFrom(path: String)(implicit configuration: GatlingConfiguration): Iterable[Assertion] =
    getNfr(path).nfr.flatMap(buildAssertion)

}
