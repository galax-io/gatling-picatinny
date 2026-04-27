package org.galaxio.performance.picatinny;

import org.galaxio.gatling.javaapi.SimulationConfig;
import org.galaxio.gatling.transactions.Predef;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;

public final class Stability extends Predef.SimulationWithTransactions {
    {
        setUp(
                PicatinnyScenario.apply("Picatinny Stability", "java-stability")
                        .injectOpen(
                                rampUsersPerSec(0).to(SimulationConfig.intensity()).during(SimulationConfig.rampDuration()),
                                constantUsersPerSec(SimulationConfig.intensity()).during(SimulationConfig.stageDuration())
                        )
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
                .assertions(PerformanceSupport.noFailedRequests());
    }
}
