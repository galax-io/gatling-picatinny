package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.sun.net.httpserver.HttpServer
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.OpenInjectionStep
import io.gatling.javaapi.http.HttpDsl.*
import org.galaxio.gatling.javaapi.SimulationConfig
import org.galaxio.gatling.javaapi.SimulationWithTransactions
import org.galaxio.gatling.javaapi.Utility
import org.galaxio.performance.picatinny.scenarios.HttpIntegrationScenario
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario
import org.galaxio.performance.picatinny.scenarios.TransactionScenario
import java.net.InetSocketAddress

/** Single-run e2e gate (test-model layer 4): all picatinny Kotlin DSL feature checks run as parallel scenarios in ONE
 * Gatling simulation instead of five sequential JVM launches.
 *
 * Scenarios: debug / transaction / stability / maxPerformance / scenarioCoverage (JDK HTTP) / httpIntegration (WireMock).
 */
class PicatinnyCoverage : SimulationWithTransactions() {
    private val healthServer: HttpServer
    private val wireMock: WireMockServer

    init {
        healthServer = startHealthServer()

        wireMock = WireMockServer(options().dynamicPort().globalTemplating(true))
        wireMock.start()
        wireMock.stubFor(
            get(urlPathMatching("/echo/.*")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody("""{"ts":"{{request.pathSegments.[1]}}","auth":"{{request.headers.Authorization}}"}""")
            )
        )

        val stabilityProfile = arrayOf<OpenInjectionStep>(
            rampUsersPerSec(0.0).to(SimulationConfig.intensity()).during(SimulationConfig.rampDuration()),
            constantUsersPerSec(SimulationConfig.intensity()).during(SimulationConfig.stageDuration()),
        )
        val maxPerfProfile = arrayOf<OpenInjectionStep>(
            incrementUsersPerSec(SimulationConfig.intensity() / SimulationConfig.stagesNumber())
                .times(SimulationConfig.stagesNumber())
                .eachLevelLasting(SimulationConfig.stageDuration())
                .separatedByRampsLasting(SimulationConfig.rampDuration())
                .startingFrom(0.0),
        )

        Utility.banner(*stabilityProfile)
        Utility.diagnostics()

        setUp(
            PicatinnyScenario.apply("Picatinny Debug", "kotlin-debug")
                .injectOpen(atOnceUsers(1)),
            TransactionScenario.apply("Picatinny Transaction Coverage", "kotlin-transaction-coverage")
                .injectOpen(atOnceUsers(1)),
            PicatinnyScenario.apply("Picatinny Stability", "kotlin-stability")
                .injectOpen(*stabilityProfile),
            PicatinnyScenario.apply("Picatinny Max Performance", "kotlin-max-performance")
                .injectOpen(*maxPerfProfile),
            PicatinnyScenario.apply("Picatinny Scenario Coverage")
                .exec(http("kotlin-scenario-coverage").get("/health").check(status().shouldBe(200)))
                .injectOpen(atOnceUsers(1))
                .protocols(http.baseUrl("http://127.0.0.1:${healthServer.address.port}")),
            HttpIntegrationScenario.apply()
                .injectOpen(constantUsersPerSec(2.0).during(2))
                .protocols(http.baseUrl("http://localhost:${wireMock.port()}"))
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(global().failedRequests().count().shouldBe(0L))
    }

    override fun after() {
        healthServer.stop(0)
        wireMock.stop()
    }

    private fun startHealthServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/health") { exchange ->
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }
}
