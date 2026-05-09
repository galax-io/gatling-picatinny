package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

@deprecated("Use org.galaxio.gatling.feeders.faker.Faker.finance.pan with GeneratedFeeder instead", "faker-api")
object RandomPANFeeder {

  /** Creates a feeder that generates a random PAN (Primary Account Number)
    *
    * @param paramName
    *   feeder's name
    * @param bins
    *   list of BINs. Bank Identification Number (BIN) refers to the first six numbers on a payment card. This set of numbers
    *   identifies the financial institution that issues the card
    * @return
    *   random string PAN feeder
    */
  def apply(paramName: String, bins: String*): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.randomPAN(bins: _*))

}
