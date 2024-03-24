package io.cosmospf.gatling.feeders

import io.gatling.core.feeder.Feeder
import io.cosmospf.gatling.utils.RandomDataGenerators

object RandomStringFeeder {

  def apply(paramName: String, paramLength: Int = 10): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.alphanumericString(paramLength))

}
