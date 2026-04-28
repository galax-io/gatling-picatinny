package org.galaxio.performance.picatinny

import io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec
import org.galaxio.gatling.javaapi.SimulationConfig
import org.galaxio.gatling.javaapi.Utility
import org.galaxio.gatling.transactions.Predef
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class MaxPerformance : Predef.SimulationWithTransactions() {
    init {
        Utility.banner()
        Utility.diagnostics()

        setUp(
            PicatinnyScenario.apply("Picatinny Max Performance", "kotlin-max-performance")
                .injectOpen(
                    incrementUsersPerSec(SimulationConfig.intensity() / SimulationConfig.stagesNumber())
                        .times(SimulationConfig.stagesNumber())
                        .eachLevelLasting(SimulationConfig.stageDuration())
                        .separatedByRampsLasting(SimulationConfig.rampDuration())
                        .startingFrom(0.0),
                ),
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(PerformanceSupport.noFailedRequests())
    }
}
