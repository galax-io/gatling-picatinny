package org.galaxio.performance.picatinny.cases

import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.http.HttpDsl.*
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders

object FeederValidationCases {

    /** One request, one header per faker-feeder field, one pattern-check per echoed value. */
    fun validateAll() = run {
        var req = http("validate-feeders").get("/echo").check(status().shouldBe(200))
        for ((field, pattern) in FeederValidationFeeders.PATTERNS) {
            req = req.header("X$field", "#{$field}")
                .check(jsonPath("\$.$field").transform<Boolean> { it.matches(Regex(pattern)) }.shouldBe(true))
        }
        exec(req)
    }
}
