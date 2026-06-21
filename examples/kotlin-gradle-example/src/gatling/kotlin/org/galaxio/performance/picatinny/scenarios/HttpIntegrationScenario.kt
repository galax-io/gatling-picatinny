package org.galaxio.performance.picatinny.scenarios

import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.ScenarioBuilder
import org.galaxio.gatling.javaapi.Transactions
import org.galaxio.gatling.javaapi.utils.Jwt
import org.galaxio.performance.picatinny.cases.HttpIntegrationCases
import org.galaxio.performance.picatinny.feeders.HttpIntegrationFeeders

object HttpIntegrationScenario {
    private val jwtGen = Jwt.jwt("HS256", "e2e-secret")
        .defaultHeader()
        .payload("""{"sub":"picatinny-e2e"}""")

    fun apply(): ScenarioBuilder = scenario("Picatinny HTTP e2e")
        .feed(HttpIntegrationFeeders.ts())
        .exec(Jwt.setJwt(jwtGen, "jwt"))
        .exec(Transactions.startTransaction("api-call"))
        .exec(HttpIntegrationCases.echo())
        .exec(Transactions.endTransaction("api-call"))
}
