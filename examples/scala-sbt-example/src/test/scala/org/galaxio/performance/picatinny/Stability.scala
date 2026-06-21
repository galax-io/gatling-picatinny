package org.galaxio.performance.picatinny

import io.gatling.core.Predef._
import org.galaxio.gatling.config.SimulationConfig._
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.utils.Utility
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

class Stability extends SimulationWithTransactions {
  private val injectionProfile = (
    rampUsersPerSec(0) to intensity during rampDuration,
    constantUsersPerSec(intensity) during stageDuration,
  )

  Utility.banner(injectionProfile)
  Utility.diagnostics()

  setUp(
    PicatinnyScenario("Picatinny Stability", "scala-stability")
      .inject(injectionProfile._1, injectionProfile._2),
  ).maxDuration(testDuration)
    .assertions(global.failedRequests.count.is(0))
}
