package org.galaxio.performance.picatinny

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.OpenInjectionStep
import org.galaxio.gatling.javaapi.SimulationConfig
import org.galaxio.gatling.javaapi.SimulationWithTransactions
import org.galaxio.gatling.javaapi.Utility
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class MaxPerformance : SimulationWithTransactions() {
    init {
        val injectionProfile = arrayOf<OpenInjectionStep>(
            incrementUsersPerSec(SimulationConfig.intensity() / SimulationConfig.stagesNumber())
                .times(SimulationConfig.stagesNumber())
                .eachLevelLasting(SimulationConfig.stageDuration())
                .separatedByRampsLasting(SimulationConfig.rampDuration())
                .startingFrom(0.0),
        )

        Utility.banner(*injectionProfile)
        Utility.diagnostics()

        setUp(
            PicatinnyScenario.apply("Picatinny Max Performance", "kotlin-max-performance")
                .injectOpen(*injectionProfile),
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(global().failedRequests().count().shouldBe(0L))
    }
}
