package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

@deprecated("Use org.galaxio.gatling.feeders.faker.Faker.uuid with GeneratedFeeder instead", "faker-api")
object RandomUUIDFeeder {

  def apply(paramName: String): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.randomUUID)

}
