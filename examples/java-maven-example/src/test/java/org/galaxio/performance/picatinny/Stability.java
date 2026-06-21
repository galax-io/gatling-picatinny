package org.galaxio.performance.picatinny;

import io.gatling.javaapi.core.OpenInjectionStep;
import org.galaxio.gatling.javaapi.SimulationConfig;
import org.galaxio.gatling.javaapi.SimulationWithTransactions;
import org.galaxio.gatling.javaapi.Utility;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;

import static io.gatling.javaapi.core.CoreDsl.*;

public final class Stability extends SimulationWithTransactions {
    {
        OpenInjectionStep[] injectionProfile = {
            rampUsersPerSec(0).to(SimulationConfig.intensity()).during(SimulationConfig.rampDuration()),
            constantUsersPerSec(SimulationConfig.intensity()).during(SimulationConfig.stageDuration()),
        };

        Utility.banner(injectionProfile);
        Utility.diagnostics();

        setUp(
            PicatinnyScenario.apply("Picatinny Stability", "java-stability")
                .injectOpen(injectionProfile)
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(global().failedRequests().count().is(0L));
    }
}
