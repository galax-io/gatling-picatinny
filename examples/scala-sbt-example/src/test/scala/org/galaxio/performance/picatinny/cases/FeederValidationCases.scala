package org.galaxio.performance.picatinny.cases

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders

/** Atomic HTTP action for the feeder-validation e2e (test-model layer 4). Request only (galaxio-gatling-pro boundaries).
  *
  * ONE request carries EVERY zipped feeder value (a header per field); the mock echoes them back; each echoed value is
  * `check`ed twice — exact round-trip (echoed == fed value, before/after) AND its EXPECTED pattern — proving every faker feeder
  * produced a contract-shaped value that survives real HTTP, all from a single `.feed()`.
  */
object FeederValidationCases {

  val validateAll: ChainBuilder = {
    val withHeaders = FeederValidationFeeders.patterns.foldLeft(http("validate-feeders").get("/echo")) {
      case (req, (field, _)) => req.header(s"X$field", s"#{$field}")
    }
    val withChecks  = FeederValidationFeeders.patterns.foldLeft(withHeaders.check(status.is(200))) {
      case (req, (field, pattern)) =>
        req
          .check(jsonPath(s"$$.$field").is(s"#{$field}"))
          .check(jsonPath(s"$$.$field").transform(_.matches(pattern)).is(true))
    }
    exec(withChecks)
  }
}
