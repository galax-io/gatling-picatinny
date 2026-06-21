package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{WireMock => WM}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.galaxio.gatling.config.SimulationConfig._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.utils.IntensityConverter._
import org.galaxio.gatling.utils.Utility
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders
import org.galaxio.performance.picatinny.scenarios._

import scala.concurrent.duration._

/** Single-run e2e gate (test-model layer 4): all picatinny DSL feature checks run as parallel scenarios in ONE Gatling
  * simulation instead of sequential JVM launches.
  *
  * One WireMock server stubs all HTTP endpoints:
  *   - GET /health           → 200 "ok"  (scenarioCoverage)
  *   - GET /echo             → JSON echo of request headers (feederValidation)
  *   - GET /echo/{ts}        → JSON echo of path + Authorization header (httpIntegration)
  *
  * Scenarios:
  *   - debug / transaction   — feeder + JWT + transactions; no HTTP
  *   - stability / maxPerf   — same features under load-profile injection
  *   - scenarioCoverage      — picatinny scenario builder + real HTTP
  *   - feederValidation      — every faker feeder echoed + validated over real HTTP
  *   - httpIntegration       — JWT + CurrentDateFeeder + transaction over real HTTP
  */
class PicatinnyCoverage extends SimulationWithTransactions {

  private val mock = new WireMockServer(options().dynamicPort().globalTemplating(true))
  mock.start()

  mock.stubFor(WM.get(WM.urlEqualTo("/health"))
    .willReturn(WM.aResponse().withStatus(200).withBody("ok")))

  private val feederEchoBody =
    FeederValidationFeeders.patterns
      .map { case (field, _) => s""""$field":"{{request.headers.X$field}}"""" }
      .mkString("{", ",", "}")
  mock.stubFor(WM.get(WM.urlPathEqualTo("/echo"))
    .willReturn(WM.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(feederEchoBody)))

  mock.stubFor(WM.get(WM.urlPathMatching("/echo/.+"))
    .willReturn(WM.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
      .withBody("""{"ts":"{{request.pathSegments.[1]}}","auth":"{{request.headers.Authorization}}"}""")))

  private val httpProtocol = http.baseUrl(s"http://localhost:${mock.port()}")

  private val stabilityProfile = (
    rampUsersPerSec(0) to intensity during rampDuration,
    constantUsersPerSec(intensity) during stageDuration,
  )
  private val maxPerfProfile =
    incrementUsersPerSec(intensity / stagesNumber)
      .times(stagesNumber)
      .eachLevelLasting(stageDuration)
      .separatedByRampsLasting(rampDuration)
      .startingFrom(0)

  Utility.banner(stabilityProfile)
  Utility.diagnostics()

  setUp(
    PicatinnyScenario("Picatinny Debug", "scala-debug")
      .inject(atOnceUsers(1)),
    TransactionScenario("Picatinny Transaction Coverage", "scala-transaction-coverage")
      .inject(atOnceUsers(1)),
    PicatinnyScenario("Picatinny Stability", "scala-stability")
      .inject(stabilityProfile._1, stabilityProfile._2),
    PicatinnyScenario("Picatinny Max Performance", "scala-max-performance")
      .inject(maxPerfProfile),
    PicatinnyScenario("Picatinny Scenario Coverage")
      .exec(http("scala-scenario-coverage").get("/health").check(status.is(200)))
      .inject(atOnceUsers(1))
      .protocols(httpProtocol),
    FeederValidationScenario()
      .inject(atOnceUsers(1))
      .protocols(httpProtocol),
    HttpIntegrationScenario()
      .inject(constantUsersPerSec(120.rpm).during(2.seconds))
      .protocols(httpProtocol),
  ).maxDuration(testDuration)
    .assertions(
      global.failedRequests.count.is(0),
      details("api-call").failedRequests.count.is(0),
    )

  after {
    mock.stop()
  }
}
