package org.galaxio.performance.picatinny.cases

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

/** Atomic HTTP action for the e2e (test-model layer 4). Request only — no scenario, no workload, no injection
  * (galaxio-gatling-pro boundaries).
  *
  * Carries picatinny-generated values into a real request and validates the RESPONSE with Gatling `check`: the mock echoes the
  * feeder value (path segment) and the JWT (Authorization header) back, so the checks prove the values made the full round-trip
  * feeder/jwt → request → server → response → check.
  */
object HttpIntegrationCases {

  val echo: ChainBuilder =
    exec(
      http("echo")
        .get("/echo/#{ts}")
        .header("Authorization", "Bearer #{jwt}")
        .check(status.is(200))
        .check(jsonPath("$.ts").is("#{ts}"))                                   // picatinny feeder value round-tripped
        .check(jsonPath("$.ts").transform(_.matches("\\d{17}")).is(true))      // ...and is a yyyyMMddHHmmssSSS timestamp
        .check(jsonPath("$.auth").is("Bearer #{jwt}"))                         // picatinny JWT round-tripped
        .check(                                                                // ...and is a 3-segment JWT
          jsonPath("$.auth").transform(_.matches("Bearer [\\w-]+\\.[\\w-]+\\.[\\w-]+")).is(true),
        ),
    )
}
