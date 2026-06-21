package org.galaxio.performance.picatinny.cases

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.http.HttpDsl.*
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders

object FeederValidationCases {

    /** One request, one header per faker-feeder field. Each echoed value is asserted twice:
     *  exact round-trip (echoed == fed value, before/after) and shape (matches the field pattern). */
    fun validateAll() = run {
        var req = http("validate-feeders").get("/echo").check(status().shouldBe(200))
        for ((field, pattern) in FeederValidationFeeders.PATTERNS) {
            req = req.header("X$field", "#{$field}")
                .check(jsonPath("\$.$field").isEL("#{$field}"))
                .check(jsonPath("\$.$field").transform<Boolean> { it.matches(Regex(pattern)) }.shouldBe(true))
        }
        exec(req)
    }
}
