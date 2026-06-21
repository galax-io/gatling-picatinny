package org.galaxio.performance.picatinny.scenarios

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.galaxio.performance.picatinny.cases.FeederValidationCases
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders

/** Business flow for the feeder-validation e2e (test-model layer 4). Flow only (galaxio-gatling-pro boundaries).
  *
  * A SINGLE `.feed` of the zipped picatinny faker feeder increments every value at once; one request then carries them all
  * and validates each echoed value against its expected pattern.
  */
object FeederValidationScenario {

  def apply(): ScenarioBuilder =
    scenario("Picatinny faker feeder validation")
      .feed(FeederValidationFeeders.all)
      .exec(FeederValidationCases.validateAll)
}
