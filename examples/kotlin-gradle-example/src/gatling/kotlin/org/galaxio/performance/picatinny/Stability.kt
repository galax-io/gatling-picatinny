package org.galaxio.performance.picatinny

import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.rampUsersPerSec
import org.galaxio.gatling.javaapi.SimulationConfig
import org.galaxio.gatling.transactions.Predef
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class Stability : Predef.SimulationWithTransactions() {
    init {
        setUp(
            PicatinnyScenario.apply("Picatinny Stability", "kotlin-stability")
                .injectOpen(
                    rampUsersPerSec(0.0).to(SimulationConfig.intensity()).during(SimulationConfig.rampDuration()),
                    constantUsersPerSec(SimulationConfig.intensity()).during(SimulationConfig.stageDuration()),
                ),
        ).maxDuration(PerformanceSupport.toScala(SimulationConfig.testDuration()))
            .assertions(PerformanceSupport.noFailedRequests())
    }
}
