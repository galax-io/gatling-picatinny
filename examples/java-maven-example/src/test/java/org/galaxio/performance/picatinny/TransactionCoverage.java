package org.galaxio.performance.picatinny;

import org.galaxio.gatling.transactions.Predef;
import org.galaxio.performance.picatinny.scenarios.TransactionScenario;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;

public final class TransactionCoverage extends Predef.SimulationWithTransactions {
    {
        setUp(TransactionScenario.apply("Picatinny Transaction Coverage", "java-transaction-coverage").injectOpen(atOnceUsers(1)))
                .assertions(PerformanceSupport.noFailedRequests());
    }
}
