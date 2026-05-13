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
            require(!session.getString("formattedPhone").isNullOrBlank()) { "formattedPhone" }
            require((session.getString("pan")?.length ?: 0) >= 16) { "pan" }

            require(!session.getString("randomDate").isNullOrBlank()) { "randomDate" }
            require(!session.getString("rangeFrom").isNullOrBlank()) { "rangeFrom" }
            require(!session.getString("customValue").isNullOrBlank()) { "customValue" }
            require(!session.getString("phone").isNullOrBlank()) { "phone" }
            require(!session.getString("rangeString").isNullOrBlank()) { "rangeString" }
            require(!session.getString("regex").isNullOrBlank()) { "regex" }
            require(!session.getString("natItn").isNullOrBlank()) { "natItn" }
            require(!session.getString("passport").isNullOrBlank()) { "passport" }

            require(!session.getString("alphabeticStr").isNullOrBlank()) { "alphabeticStr" }
            require(!session.getString("firstName").isNullOrBlank()) { "firstName" }
            require(!session.getString("username").isNullOrBlank()) { "username" }
            require(!session.getString("accountNumber").isNullOrBlank()) { "accountNumber" }
            require(!session.getString("productName").isNullOrBlank()) { "productName" }
            require(!session.getString("usSSN").isNullOrBlank()) { "usSSN" }
            require(!session.getString("phoneTollFree").isNullOrBlank()) { "phoneTollFree" }
            require(!session.getString("loremWord").isNullOrBlank()) { "loremWord" }
            require(!session.getString("createdAt").isNullOrBlank()) { "createdAt" }

            session
        }
}
