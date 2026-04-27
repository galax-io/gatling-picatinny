package org.galaxio.performance.picatinny

import io.gatling.core.Predef._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class Debug extends SimulationWithTransactions {
  setUp(
    PicatinnyScenario("Picatinny Debug", "scala-debug").inject(atOnceUsers(1)),
  ).assertions(global.failedRequests.count.is(0))
}
