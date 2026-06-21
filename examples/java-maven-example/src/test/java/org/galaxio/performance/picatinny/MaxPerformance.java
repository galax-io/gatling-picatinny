package org.galaxio.performance.picatinny;

import io.gatling.javaapi.core.OpenInjectionStep;
import org.galaxio.gatling.javaapi.SimulationConfig;
import org.galaxio.gatling.javaapi.SimulationWithTransactions;
import org.galaxio.gatling.javaapi.Utility;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;

import static io.gatling.javaapi.core.CoreDsl.*;

public final class MaxPerformance extends SimulationWithTransactions {
    {
        OpenInjectionStep[] injectionProfile = {
            incrementUsersPerSec(SimulationConfig.intensity() / SimulationConfig.stagesNumber())
                .times(SimulationConfig.stagesNumber())
                .eachLevelLasting(SimulationConfig.stageDuration())
                .separatedByRampsLasting(SimulationConfig.rampDuration())
                .startingFrom(0.0),
        };

        Utility.banner(injectionProfile);
        Utility.diagnostics();

        setUp(
            PicatinnyScenario.apply("Picatinny Max Performance", "java-max-performance")
                .injectOpen(injectionProfile)
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(PerformanceSupport.noFailedRequests());
    }
}
