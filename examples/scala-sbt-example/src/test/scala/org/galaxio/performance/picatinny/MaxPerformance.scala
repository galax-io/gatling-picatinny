package org.galaxio.performance.picatinny

import io.gatling.core.Predef._
import org.galaxio.gatling.config.SimulationConfig._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.utils.Utility
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class MaxPerformance extends SimulationWithTransactions {
  private val injectionProfile =
    incrementUsersPerSec(intensity / stagesNumber)
      .times(stagesNumber)
      .eachLevelLasting(stageDuration)
      .separatedByRampsLasting(rampDuration)
      .startingFrom(0)

  Utility.banner(injectionProfile)
  Utility.diagnostics()

  setUp(
    PicatinnyScenario("Picatinny Max Performance", "scala-max-performance")
      .inject(injectionProfile),
  ).maxDuration(testDuration)
    .assertions(global.failedRequests.count.is(0))
}
