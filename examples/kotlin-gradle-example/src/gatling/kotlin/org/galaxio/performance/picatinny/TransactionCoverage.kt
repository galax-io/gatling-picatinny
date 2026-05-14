package org.galaxio.performance.picatinny

import io.gatling.javaapi.core.CoreDsl.atOnceUsers
import org.galaxio.gatling.javaapi.SimulationWithTransactions
import org.galaxio.performance.picatinny.scenarios.TransactionScenario

class TransactionCoverage : SimulationWithTransactions() {
    init {
        setUp(TransactionScenario.apply("Picatinny Transaction Coverage", "kotlin-transaction-coverage").injectOpen(atOnceUsers(1)))
            .assertions(PerformanceSupport.noFailedRequests())
    }

    override fun before() {
        println("PICATINNY_TRANSACTION_COVERAGE_BEFORE_EXECUTED")
    }

    override fun after() {
        println("PICATINNY_TRANSACTION_COVERAGE_AFTER_EXECUTED")
    }
}
