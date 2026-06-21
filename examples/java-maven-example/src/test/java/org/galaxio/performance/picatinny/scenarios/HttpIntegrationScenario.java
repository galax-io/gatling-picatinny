package org.galaxio.performance.picatinny.scenarios;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.gatling.javaapi.Transactions;
import org.galaxio.gatling.javaapi.utils.Jwt;
import org.galaxio.gatling.utils.jwt.JwtGeneratorBuilder;
import org.galaxio.performance.picatinny.cases.HttpIntegrationCases;
import org.galaxio.performance.picatinny.feeders.HttpIntegrationFeeders;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

public final class HttpIntegrationScenario {
    private HttpIntegrationScenario() {}

    private static final JwtGeneratorBuilder JWT_GEN = Jwt.jwt("HS256", "e2e-secret")
            .defaultHeader()
            .payload("{\"sub\":\"picatinny-e2e\"}");

    public static ScenarioBuilder apply() {
        return scenario("Picatinny HTTP e2e")
                .feed(HttpIntegrationFeeders.ts())
                .exec(Jwt.setJwt(JWT_GEN, "jwt"))
                .exec(Transactions.startTransaction("api-call"))
                .exec(HttpIntegrationCases.echo())
                .exec(Transactions.endTransaction("api-call"));
    }
}
