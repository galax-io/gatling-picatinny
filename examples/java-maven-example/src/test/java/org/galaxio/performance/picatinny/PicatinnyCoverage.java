package org.galaxio.performance.picatinny;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sun.net.httpserver.HttpServer;
import io.gatling.javaapi.core.OpenInjectionStep;
import org.galaxio.gatling.javaapi.SimulationConfig;
import org.galaxio.gatling.javaapi.SimulationWithTransactions;
import org.galaxio.gatling.javaapi.Utility;
import org.galaxio.performance.picatinny.scenarios.HttpIntegrationScenario;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;
import org.galaxio.performance.picatinny.scenarios.TransactionScenario;

import java.io.IOException;
import java.net.InetSocketAddress;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/** Single-run e2e gate (test-model layer 4): all picatinny Java DSL feature checks run as parallel scenarios in ONE
  * Gatling simulation instead of five sequential JVM launches.
  *
  * Scenarios: debug / transaction / stability / maxPerformance / scenarioCoverage (JDK HTTP) / httpIntegration (WireMock).
  */
public final class PicatinnyCoverage extends SimulationWithTransactions {

    private final HttpServer healthServer;
    private final WireMockServer wireMock;

    {
        healthServer = startHealthServer();

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort().globalTemplating(true));
        wireMock.start();
        wireMock.stubFor(get(urlPathMatching("/echo/.*")).willReturn(
            aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"ts\":\"{{request.pathSegments.[1]}}\",\"auth\":\"{{request.headers.Authorization}}\"}")
        ));

        OpenInjectionStep[] stabilityProfile = {
            rampUsersPerSec(0).to(SimulationConfig.intensity()).during(SimulationConfig.rampDuration()),
            constantUsersPerSec(SimulationConfig.intensity()).during(SimulationConfig.stageDuration()),
        };
        OpenInjectionStep maxPerfProfile =
            incrementUsersPerSec(SimulationConfig.intensity() / SimulationConfig.stagesNumber())
                .times(SimulationConfig.stagesNumber())
                .eachLevelLasting(SimulationConfig.stageDuration())
                .separatedByRampsLasting(SimulationConfig.rampDuration())
                .startingFrom(0.0);

        Utility.banner(stabilityProfile);
        Utility.diagnostics();

        setUp(
            PicatinnyScenario.apply("Picatinny Debug", "java-debug")
                .injectOpen(atOnceUsers(1)),
            TransactionScenario.apply("Picatinny Transaction Coverage", "java-transaction-coverage")
                .injectOpen(atOnceUsers(1)),
            PicatinnyScenario.apply("Picatinny Stability", "java-stability")
                .injectOpen(stabilityProfile),
            PicatinnyScenario.apply("Picatinny Max Performance", "java-max-performance")
                .injectOpen(maxPerfProfile),
            PicatinnyScenario.apply("Picatinny Scenario Coverage")
                .exec(http("java-scenario-coverage").get("/health").check(status().is(200)))
                .injectOpen(atOnceUsers(1))
                .protocols(http.baseUrl("http://127.0.0.1:" + healthServer.getAddress().getPort())),
            HttpIntegrationScenario.apply()
                .injectOpen(constantUsersPerSec(2.0).during(2))
                .protocols(http.baseUrl("http://localhost:" + wireMock.port()))
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(global().failedRequests().count().is(0L));
    }

    @Override
    public void after() {
        healthServer.stop(0);
        wireMock.stop();
    }

    private static HttpServer startHealthServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/health", exchange -> {
                byte[] body = "ok".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start health server", e);
        }
    }
}
