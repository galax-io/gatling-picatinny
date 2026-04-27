package org.galaxio.performance.picatinny

import io.gatling.javaapi.core.CoreDsl.atOnceUsers
import org.galaxio.gatling.transactions.Predef
import org.galaxio.performance.picatinny.scenarios.TransactionScenario

class TransactionCoverage : Predef.SimulationWithTransactions() {
    init {
        setUp(TransactionScenario.apply("Picatinny Transaction Coverage", "kotlin-transaction-coverage").injectOpen(atOnceUsers(1)))
            .assertions(PerformanceSupport.noFailedRequests())
    }
}
