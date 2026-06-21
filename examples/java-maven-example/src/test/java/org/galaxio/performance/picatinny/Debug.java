package org.galaxio.performance.picatinny;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.galaxio.gatling.javaapi.SimulationWithTransactions;
import org.galaxio.performance.picatinny.cases.FeederValidationCases;
import org.galaxio.performance.picatinny.cases.HttpIntegrationCases;
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders;
import org.galaxio.performance.picatinny.feeders.HttpIntegrationFeeders;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;

import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/** Layer-4 e2e debug gate: 1 user, 1 call per picatinny Java feature, no load.
 *
 * Exercises in sequence:
 *   - all picatinny feeders + JWT + transactions (PicatinnyScenario)
 *   - every faker feeder echoed + validated over HTTP (WireMock GET /echo)
 *   - CurrentDateFeeder + JWT round-trip over HTTP (WireMock GET /echo/{ts})
 */
public final class Debug extends SimulationWithTransactions {

    private final WireMockServer mock;

    {
        mock = new WireMockServer(WireMockConfiguration.options().dynamicPort().globalTemplating(true));
        mock.start();

        String feederEchoBody = FeederValidationFeeders.PATTERNS.stream()
            .map(e -> "\"" + e.getKey() + "\":\"{{request.headers.X" + e.getKey() + "}}\"")
            .collect(Collectors.joining(",", "{", "}"));
        mock.stubFor(get(urlPathEqualTo("/echo"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(feederEchoBody)));

        mock.stubFor(get(urlPathMatching("/echo/.+"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"ts\":\"{{request.pathSegments.[1]}}\",\"auth\":\"{{request.headers.Authorization}}\"}")));

        setUp(
            PicatinnyScenario.apply("Picatinny Debug", "java-debug")
                .feed(FeederValidationFeeders.all())
                .feed(HttpIntegrationFeeders.ts())
                .exec(FeederValidationCases.validateAll())
                .exec(HttpIntegrationCases.echo())
                .injectOpen(atOnceUsers(1))
                .protocols(http.baseUrl("http://localhost:" + mock.port()))
        ).assertions(global().failedRequests().count().is(0L));
    }

    @Override
    public void after() {
        mock.stop();
    }
}
