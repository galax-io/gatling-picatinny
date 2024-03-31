package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

object RandomRusPassportFeeder {

  /** Creates a feeder that generates a random russian passport series and number
    *
    * @param paramName
    *   feeder's name
    * @return
    *   random string russian passport series and number feeder
    */
  def apply(paramName: String): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.randomRusPassport())

}
