package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.feeders.faker.Faker

@deprecated(
  "Use org.galaxio.gatling.feeders.faker.Faker.string.matching with GeneratedFeeder instead. RegexFeeder now samples random values rather than preserving iterator order.",
  "faker-api",
)
object RegexFeeder {

  def apply(paramName: String, regex: String): Feeder[String] = {
    // Reuse the generator so each feeder iteration does not rebuild its thread-local regex sampler.
    val generator = Faker.string.matching(regex)
    feeder[String](paramName)(generator.sample())
  }

}
