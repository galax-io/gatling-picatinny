package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

object RandomDigitFeeder {

  def apply(paramName: String): Feeder[Int] =
    feeder[Int](paramName)(RandomDataGenerators.randomDigit())

}
