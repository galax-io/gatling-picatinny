package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.feeders.faker.Faker

@deprecated("Use org.galaxio.gatling.feeders.faker.Faker.string.matching with GeneratedFeeder instead", "faker-api")
object RegexFeeder {

  def apply(paramName: String, regex: String): Feeder[String] =
    feeder[String](paramName)(Faker.string.matching(regex).sample())

}
