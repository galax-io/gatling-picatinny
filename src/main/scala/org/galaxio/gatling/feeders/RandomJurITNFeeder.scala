package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

object RandomJurITNFeeder {

  /** Creates a feeder that generates a random ITN of the juridical person (Individual Taxpayer Number)
    *
    * ITN is used only in the Russian Federation (ИНН in Russian)
    *
    * @param paramName
    *   feeder's name
    * @return
    *   random string ITN of the juridical person feeder
    */
  def apply(paramName: String): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.randomJurITN())

}
