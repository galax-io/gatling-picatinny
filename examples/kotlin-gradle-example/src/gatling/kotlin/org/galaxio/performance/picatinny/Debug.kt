package org.galaxio.performance.picatinny

import io.gatling.javaapi.core.CoreDsl.atOnceUsers
import org.galaxio.gatling.transactions.Predef
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class Debug : Predef.SimulationWithTransactions() {
    init {
        setUp(PicatinnyScenario.apply("Picatinny Debug", "kotlin-debug").injectOpen(atOnceUsers(1)))
            .assertions(PerformanceSupport.noFailedRequests())
    }
}
