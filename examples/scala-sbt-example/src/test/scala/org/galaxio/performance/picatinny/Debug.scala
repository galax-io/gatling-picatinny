package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{WireMock => WM}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.performance.picatinny.cases.{FeederValidationCases, HttpIntegrationCases}
import org.galaxio.performance.picatinny.feeders.{FeederValidationFeeders, HttpIntegrationFeeders}
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

/** Layer-4 e2e debug gate: 1 user, 1 call per picatinny feature, no load.
  *
  * Exercises in sequence:
  *   - all picatinny feeders + JWT + transactions (via PicatinnyScenario)
  *   - every faker feeder echoed + validated over HTTP (WireMock GET /echo)
  *   - CurrentDateFeeder + JWT round-trip over HTTP (WireMock GET /echo/{ts})
  */
class Debug extends SimulationWithTransactions {

  private val mock = new WireMockServer(options().dynamicPort().globalTemplating(true))
  mock.start()

  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/echo"))
      .willReturn(
        WM.aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(
            FeederValidationFeeders.patterns.map { case (field, _) => s""""$field":"{{request.headers.X$field}}"""" }
              .mkString("{", ",", "}"),
          ),
      ),
  )

  mock.stubFor(
    WM.get(WM.urlPathMatching("/echo/.+"))
      .willReturn(
        WM.aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""{"ts":"{{request.pathSegments.[1]}}","auth":"{{request.headers.Authorization}}"}"""),
      ),
  )

  setUp(
    PicatinnyScenario("Picatinny Debug", "scala-debug")
      .feed(FeederValidationFeeders.all)
      .feed(HttpIntegrationFeeders.ts)
      .exec(FeederValidationCases.validateAll)
      .exec(HttpIntegrationCases.echo)
      .inject(atOnceUsers(1)),
  ).protocols(http.baseUrl(s"http://localhost:${mock.port()}"))
    .assertions(global.failedRequests.count.is(0))

  after {
    mock.stop()
  }
}
