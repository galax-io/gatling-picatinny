package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

@deprecated("Use org.galaxio.gatling.feeders.faker.Faker.number with GeneratedFeeder instead", "faker-api")
object RandomDigitFeeder {

  def apply(paramName: String): Feeder[Int] = {
    feeder[Int](paramName)(RandomDataGenerators.randomValue[Int]())
  }

}
