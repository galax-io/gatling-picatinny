package org.galaxio.performance.picatinny.scenarios

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.utils.jwt._
import org.galaxio.performance.picatinny.cases.HttpIntegrationCases
import org.galaxio.performance.picatinny.feeders.HttpIntegrationFeeders

/** Business flow for the e2e (test-model layer 4). Flow only — no injection, no protocol (galaxio-gatling-pro boundaries).
  *
  * Composes the picatinny features each via its picatinny method: `HttpJsonFeeder`-style feeder (`CurrentDateFeeder`), `setJwt`
  * (sign), and the `startTransaction`/`endTransaction` wrapper around the real HTTP call whose response is `check`ed.
  */
object HttpIntegrationScenario {

  private val jwtGen =
    jwt("HS256", "e2e-secret").defaultHeader.payload("""{"sub":"picatinny-e2e"}""")

  def apply(): ScenarioBuilder =
    scenario("Picatinny HTTP e2e")
      .feed(HttpIntegrationFeeders.ts)
      .exec(session => session.setJwt(jwtGen, "jwt"))
      .startTransaction("api-call")
      .exec(HttpIntegrationCases.echo)
      .endTransaction("api-call")
}
