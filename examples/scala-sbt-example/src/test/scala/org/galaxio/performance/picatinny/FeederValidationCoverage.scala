package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders
import org.galaxio.performance.picatinny.scenarios.FeederValidationScenario

/** Full e2e (test-model layer 4) validating that every picatinny faker feeder generates a contract-shaped value over real HTTP.
  *
  * A single ZIPPED feeder (`GeneratedFeeder`) increments all values at once; one request sends them (a header per field) to a
  * WireMock endpoint that echoes each header into the JSON response, and Gatling `check` validates each echoed value against its
  * EXPECTED pattern. A feeder generating a malformed value fails its `check` → `sbt Gatling/test` non-zero. Run under
  * `template-tests`.
  */
class FeederValidationCoverage extends Simulation {

  private val wireMock = new WireMockServer(options().dynamicPort().globalTemplating(true))
  wireMock.start()

  // Build the echo response body from the same field list that drives the feeder, headers and checks (one source of truth).
  private val echoBody =
    FeederValidationFeeders.patterns
      .map { case (field, _) => s""""$field":"{{request.headers.X$field}}"""" }
      .mkString("{", ",", "}")

  wireMock.stubFor(
    get(urlPathEqualTo("/echo")).willReturn(
      aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(echoBody),
    ),
  )

  private val httpProtocol = http.baseUrl(s"http://localhost:${wireMock.port()}")

  setUp(
    FeederValidationScenario().inject(atOnceUsers(1)),
  ).protocols(httpProtocol)
    .assertions(global.failedRequests.count.is(0))

  after {
    wireMock.stop()
  }
}
