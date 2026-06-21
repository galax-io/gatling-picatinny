package org.galaxio.performance.picatinny.cases

import io.gatling.javaapi.core.ChainBuilder
import io.gatling.javaapi.core.CoreDsl.exec

import io.gatling.javaapi.http.HttpDsl.*

object HttpIntegrationCases {
    fun echo(): ChainBuilder = exec(
        http("echo")
            .get("/echo/#{ts}")
            .header("Authorization", "Bearer #{jwt}")
            .check(status().shouldBe(200))
            .check(jsonPath("$.ts").shouldBe("#{ts}"))
            .check(jsonPath("$.ts").transform { it.matches(Regex("\\d{17}")) }.shouldBe(true))
            .check(jsonPath("$.auth").shouldBe("Bearer #{jwt}"))
            .check(jsonPath("$.auth").transform { it.matches(Regex("Bearer [\\w-]+\\.[\\w-]+\\.[\\w-]+")) }.shouldBe(true))
    )
}
