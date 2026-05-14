package org.galaxio.performance.picatinny;

import org.galaxio.gatling.javaapi.SimulationWithTransactions;
import org.galaxio.performance.picatinny.scenarios.TransactionScenario;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;

public final class TransactionCoverage extends SimulationWithTransactions {
    {
        setUp(TransactionScenario.apply("Picatinny Transaction Coverage", "java-transaction-coverage").injectOpen(atOnceUsers(1)))
                .assertions(PerformanceSupport.noFailedRequests());
    }

    @Override
    public void before() {
        System.out.println("PICATINNY_TRANSACTION_COVERAGE_BEFORE_EXECUTED");
    }

    @Override
    public void after() {
        System.out.println("PICATINNY_TRANSACTION_COVERAGE_AFTER_EXECUTED");
    }
}
