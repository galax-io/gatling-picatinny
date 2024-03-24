package io.cosmospf.gatling.feeders

import io.gatling.core.feeder.Feeder
import io.cosmospf.gatling.utils.RandomDataGenerators

object RandomDigitFeeder {

  def apply(paramName: String): Feeder[Int] =
    feeder[Int](paramName)(RandomDataGenerators.randomDigit())

}
