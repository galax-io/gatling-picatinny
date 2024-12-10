package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

object RandomDigitFeeder {

  def apply(paramName: String): Feeder[Int] = {
    require(paramName.nonEmpty, "paramName must not be empty")

    val randomValue = RandomDataGenerators.randomValue[Int]()
    feeder[Int](paramName)(randomValue)
  }

}
