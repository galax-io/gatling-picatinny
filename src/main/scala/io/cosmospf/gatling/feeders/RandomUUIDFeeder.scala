package io.cosmospf.gatling.feeders

import io.gatling.core.feeder.Feeder
import io.cosmospf.gatling.utils.RandomDataGenerators

object RandomUUIDFeeder {

  def apply(paramName: String): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.randomUUID)

}
