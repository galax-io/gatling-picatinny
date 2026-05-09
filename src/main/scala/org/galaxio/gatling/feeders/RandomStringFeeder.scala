package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

@deprecated("Use org.galaxio.gatling.feeders.faker.Faker.string with GeneratedFeeder instead", "faker-api")
object RandomStringFeeder {

  def apply(paramName: String, paramLength: Int = 10): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.alphanumericString(paramLength))

}
