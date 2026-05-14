package org.galaxio.performance.picatinny

import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.rampUsersPerSec
import io.gatling.javaapi.core.OpenInjectionStep
import org.galaxio.gatling.javaapi.SimulationConfig
import org.galaxio.gatling.javaapi.Utility
import org.galaxio.gatling.javaapi.SimulationWithTransactions
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class Stability : SimulationWithTransactions() {
    init {
        val injectionProfile = arrayOf<OpenInjectionStep>(
            rampUsersPerSec(0.0).to(SimulationConfig.intensity()).during(SimulationConfig.rampDuration()),
            constantUsersPerSec(SimulationConfig.intensity()).during(SimulationConfig.stageDuration()),
        )

        Utility.banner(*injectionProfile)
        Utility.diagnostics()

        setUp(
            PicatinnyScenario.apply("Picatinny Stability", "kotlin-stability")
                .injectOpen(*injectionProfile),
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(PerformanceSupport.noFailedRequests())
    }
}
