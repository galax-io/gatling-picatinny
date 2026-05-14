package org.galaxio.performance.picatinny;

import org.galaxio.gatling.javaapi.SimulationWithTransactions;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;

public final class Debug extends SimulationWithTransactions {
    {
        setUp(PicatinnyScenario.apply("Picatinny Debug", "java-debug").injectOpen(atOnceUsers(1)))
                .assertions(PerformanceSupport.noFailedRequests());
    }
}
