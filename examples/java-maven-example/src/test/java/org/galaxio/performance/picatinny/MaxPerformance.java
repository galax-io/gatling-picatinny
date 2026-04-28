package org.galaxio.performance.picatinny;

import org.galaxio.gatling.javaapi.SimulationConfig;
import org.galaxio.gatling.javaapi.Utility;
import org.galaxio.gatling.transactions.Predef;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;
import io.gatling.javaapi.core.OpenInjectionStep;

import static io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec;

public final class MaxPerformance extends Predef.SimulationWithTransactions {
    {
        OpenInjectionStep[] injectionProfile = {
                incrementUsersPerSec(SimulationConfig.intensity() / SimulationConfig.stagesNumber())
                        .times(SimulationConfig.stagesNumber())
                        .eachLevelLasting(SimulationConfig.stageDuration())
                        .separatedByRampsLasting(SimulationConfig.rampDuration())
                        .startingFrom(0.0)
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
