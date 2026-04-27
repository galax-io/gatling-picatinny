package org.galaxio.performance.picatinny.cases

import io.gatling.javaapi.core.ChainBuilder
import io.gatling.javaapi.core.CoreDsl.exec
import org.galaxio.gatling.javaapi.SimulationConfig
import org.galaxio.gatling.javaapi.Transactions
import org.galaxio.gatling.javaapi.utils.IntensityConverter
import org.galaxio.gatling.javaapi.utils.Jwt

object PicatinnyCases {
    private val jwtGenerator = Jwt.jwt("HS256", "performance-secret")
        .defaultHeader()
        .payloadFromResource("jwtTemplates/payload.json")

    fun businessOperation(transactionName: String): ChainBuilder = exec(Transactions.startTransaction(transactionName))
        .exec(scenarioOperation())
        .exec(Transactions.endTransaction(transactionName))

    fun scenarioOperation(): ChainBuilder = exec(Jwt.setJwt(jwtGenerator, "jwt"))
        .pause(1)
        .exec { session ->
            require(SimulationConfig.baseUrl() == "http://localhost") { "baseUrl" }
            require(IntensityConverter.rpm(60.0) == SimulationConfig.intensity()) { "intensity" }
            require(session.getString("uuid")?.length == 36) { "uuid" }
            require(session.getString("jwt")?.split(".")?.size == 3) { "jwt" }
            require(!session.getString("phoneFromJson").isNullOrBlank()) { "phoneFromJson" }
            require((session.getString("pan")?.length ?: 0) >= 16) { "pan" }
            session
        }
}
