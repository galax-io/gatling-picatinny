package org.galaxio.performance.picatinny;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.galaxio.gatling.javaapi.SimulationConfig;
import org.galaxio.gatling.javaapi.SimulationWithTransactions;
import org.galaxio.gatling.javaapi.Utility;
import org.galaxio.performance.picatinny.scenarios.HttpIntegrationScenario;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;
import org.galaxio.performance.picatinny.scenarios.TransactionScenario;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/** Single-run e2e gate (test-model layer 4): all picatinny Java DSL feature checks as parallel scenarios.
 *
 * One WireMock server stubs /health (scenarioCoverage) and /echo/... (httpIntegration).
 */
public final class PicatinnyCoverage extends SimulationWithTransactions {

    private final WireMockServer mock;

    {
        mock = new WireMockServer(WireMockConfiguration.options().dynamicPort().globalTemplating(true));
        mock.start();
        mock.stubFor(get(urlEqualTo("/health"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));
        mock.stubFor(get(urlPathMatching("/echo/.+"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"ts\":\"{{request.pathSegments.[1]}}\",\"auth\":\"{{request.headers.Authorization}}\"}")));

        HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:" + mock.port());

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
                .protocols(httpProtocol),
            HttpIntegrationScenario.apply()
                .injectOpen(constantUsersPerSec(2.0).during(2))
                .protocols(httpProtocol)
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(global().failedRequests().count().is(0L));
    }

    @Override
    public void after() {
        mock.stop();
    }
}
