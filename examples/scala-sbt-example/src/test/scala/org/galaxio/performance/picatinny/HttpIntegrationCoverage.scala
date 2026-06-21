package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.utils.IntensityConverter._
import org.galaxio.performance.picatinny.scenarios.HttpIntegrationScenario

import scala.concurrent.duration._

/** Full HTTP end-to-end (test-model layer 4): picatinny features composed in a real Gatling run against a real in-process
  * WireMock server. Simulation = injection + protocol + setup/teardown only; request/flow/data live in
  * `cases`/`scenarios`/`feeders` (galaxio-gatling-pro boundaries). Every feature is driven by its picatinny method:
  *
  *   - **feeders** — `CurrentDateFeeder` produces `ts`, sent in the request URL;
  *   - **JWT** — `setJwt` signs the token, sent in the `Authorization` header;
  *   - **transactions** — `startTransaction`/`endTransaction` group the call;
  *   - **converters** — `IntensityConverter` (`.rpm`) sets the injection rate.
  *
  * The mock echoes the feeder value and the JWT back; Gatling **`check`** validates the responses (`jsonPath` equals the sent
  * values), proving the picatinny values made the full round-trip through real HTTP and response handling. Run by
  * `sbt Gatling/test` under the `template-tests` CI gate. Break check (FR-015): a wrong echo / 500 → a `check` fails →
  * non-zero exit.
  */
class HttpIntegrationCoverage extends SimulationWithTransactions {

  private val wireMock = new WireMockServer(options().dynamicPort().globalTemplating(true))
  wireMock.start()
  private val baseUrl = s"http://localhost:${wireMock.port()}"

  // The mock echoes the request's path segment (the feeder value) and Authorization header (the JWT) back into the JSON
  // response body via WireMock response templating, so Gatling `check` can confirm the round-trip.
  wireMock.stubFor(
    get(urlPathMatching("/echo/.*")).willReturn(
      aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("""{"ts":"{{request.pathSegments.[1]}}","auth":"{{request.headers.Authorization}}"}"""),
    ),
  )

  private val httpProtocol = http.baseUrl(baseUrl)

  setUp(
    // converters: 120 rpm == 2 req/s via IntensityConverter
    HttpIntegrationScenario().inject(constantUsersPerSec(120.rpm).during(2.seconds)),
  ).protocols(httpProtocol)
    .assertions(
      global.failedRequests.count.is(0),
      details("api-call").failedRequests.count.is(0),
    )

  after {
    wireMock.stop()
  }
}
