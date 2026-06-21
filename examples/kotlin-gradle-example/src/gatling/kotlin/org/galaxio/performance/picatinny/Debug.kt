package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.http.HttpDsl.*
import org.galaxio.gatling.javaapi.SimulationWithTransactions
import org.galaxio.performance.picatinny.cases.FeederValidationCases
import org.galaxio.performance.picatinny.cases.HttpIntegrationCases
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders
import org.galaxio.performance.picatinny.feeders.HttpIntegrationFeeders
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

/** Layer-4 e2e debug gate: 1 user, 1 call per picatinny Kotlin feature, no load.
 *
 * Exercises in sequence:
 *   - all picatinny feeders + JWT + transactions (PicatinnyScenario)
 *   - every faker feeder echoed + validated over HTTP (WireMock GET /echo)
 *   - CurrentDateFeeder + JWT round-trip over HTTP (WireMock GET /echo/{ts})
 */
class Debug : SimulationWithTransactions() {
    private val mock: WireMockServer

    init {
        mock = WireMockServer(options().dynamicPort().globalTemplating(true))
        mock.start()

        val feederEchoBody = FeederValidationFeeders.PATTERNS
            .joinToString(",", "{", "}") { (field, _) ->
                "\"$field\":\"{{request.headers.X$field}}\""
            }
        mock.stubFor(get(urlPathEqualTo("/echo"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(feederEchoBody)))

        mock.stubFor(get(urlPathMatching("/echo/.+"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("""{"ts":"{{request.pathSegments.[1]}}","auth":"{{request.headers.Authorization}}"}""")))

        setUp(
            PicatinnyScenario.apply("Picatinny Debug", "kotlin-debug")
                .feed(FeederValidationFeeders.all())
                .feed(HttpIntegrationFeeders.ts())
                .exec(FeederValidationCases.validateAll())
                .exec(HttpIntegrationCases.echo())
                .injectOpen(atOnceUsers(1))
                .protocols(http.baseUrl("http://localhost:${mock.port()}"))
        ).assertions(global().failedRequests().count().shouldBe(0L))
    }

    override fun after() {
        mock.stop()
    }
}
