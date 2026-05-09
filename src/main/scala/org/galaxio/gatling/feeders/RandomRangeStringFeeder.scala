package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

@deprecated("Use org.galaxio.gatling.feeders.faker.Faker.string.lengthBetween with GeneratedFeeder instead", "faker-api")
object RandomRangeStringFeeder {

  lazy val alphabet = """abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#%"&*()_-+={}<>?|:[].~"""

  def apply(paramName: String, from: Int = 10, to: Int = 15, alphabet: String = alphabet): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.randomString(alphabet)(RandomDataGenerators.randomValue(from, to)))

}
