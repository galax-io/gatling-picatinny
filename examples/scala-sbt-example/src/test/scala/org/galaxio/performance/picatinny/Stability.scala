package org.galaxio.performance.picatinny

import io.gatling.core.Predef._
import org.galaxio.gatling.config.SimulationConfig._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.utils.Utility
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class Stability extends SimulationWithTransactions {
  Utility.banner()
  Utility.diagnostics()

  setUp(
    PicatinnyScenario("Picatinny Stability", "scala-stability").inject(
      rampUsersPerSec(0) to intensity during rampDuration,
      constantUsersPerSec(intensity) during stageDuration,
    ),
  ).maxDuration(testDuration)
    .assertions(global.failedRequests.count.is(0))
}
