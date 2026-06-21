package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{WireMock => WM}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.sun.net.httpserver.HttpServer
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.galaxio.gatling.config.SimulationConfig._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.utils.IntensityConverter._
import org.galaxio.gatling.utils.Utility
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders
import org.galaxio.performance.picatinny.scenarios._

import java.net.InetSocketAddress
import scala.concurrent.duration._

/** Single-run e2e gate (test-model layer 4): all picatinny DSL feature checks run as parallel scenarios in ONE Gatling
  * simulation instead of seven sequential JVM launches.
  *
  * Scenarios:
  *   - debug / transaction — feeder + JWT + transactions; no HTTP
  *   - stability / maxPerformance — same features under load-profile injection
  *   - scenarioCoverage — picatinny scenario builder + real HTTP (embedded JDK server)
  *   - feederValidation — every faker feeder echoed + validated over real HTTP (WireMock)
  *   - httpIntegration — JWT + CurrentDateFeeder + transaction over real HTTP (WireMock)
  */
class PicatinnyCoverage extends SimulationWithTransactions {

  private val healthServer = {
    val s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    s.createContext(
      "/health",
      exchange => {
        val body = "ok".getBytes
        exchange.sendResponseHeaders(200, body.length)
        exchange.getResponseBody.write(body)
        exchange.close()
      },
    )
    s.start()
    s
  }

  private val feederMock = new WireMockServer(options().dynamicPort().globalTemplating(true))
  feederMock.start()
  private val feederEchoBody =
    FeederValidationFeeders.patterns
      .map { case (field, _) => s""""$field":"{{request.headers.X$field}}"""" }
      .mkString("{", ",", "}")
  feederMock.stubFor(
    WM.get(WM.urlPathEqualTo("/echo")).willReturn(
      WM.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(feederEchoBody),
    ),
  )

  private val httpMock = new WireMockServer(options().dynamicPort().globalTemplating(true))
  httpMock.start()
  httpMock.stubFor(
    WM.get(WM.urlPathMatching("/echo/.*")).willReturn(
      WM.aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""{"ts":"{{request.pathSegments.[1]}}","auth":"{{request.headers.Authorization}}"}"""),
    ),
  )

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
      .protocols(http.baseUrl(s"http://127.0.0.1:${healthServer.getAddress.getPort}")),
    FeederValidationScenario()
      .inject(atOnceUsers(1))
      .protocols(http.baseUrl(s"http://localhost:${feederMock.port()}")),
    HttpIntegrationScenario()
      .inject(constantUsersPerSec(120.rpm).during(2.seconds))
      .protocols(http.baseUrl(s"http://localhost:${httpMock.port()}")),
  ).maxDuration(testDuration)
    .assertions(
      global.failedRequests.count.is(0),
      details("api-call").failedRequests.count.is(0),
    )

  after {
    healthServer.stop(0)
    feederMock.stop()
    httpMock.stop()
  }
}
