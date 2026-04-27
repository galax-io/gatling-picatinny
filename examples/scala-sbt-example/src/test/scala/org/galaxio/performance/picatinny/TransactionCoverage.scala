package org.galaxio.performance.picatinny

import io.gatling.core.Predef._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.performance.picatinny.scenarios.TransactionScenario

class TransactionCoverage extends SimulationWithTransactions {
  setUp(
    TransactionScenario("Picatinny Transaction Coverage", "scala-transaction-coverage").inject(atOnceUsers(1)),
  ).assertions(global.failedRequests.count.is(0))
}
