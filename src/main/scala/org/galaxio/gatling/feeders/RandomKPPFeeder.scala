package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators

@deprecated("Use org.galaxio.gatling.feeders.faker.Faker.ru.kpp with GeneratedFeeder instead", "faker-api")
object RandomKPPFeeder {

  /** Creates a feeder that generates a random KPP (Tax Registration Reason Code)
    *
    * KPP is used only in the Russian Federation (КПП in Russian)
    *
    * @param paramName
    *   feeder's name
    * @return
    *   random string KPP feeder
    */
  def apply(paramName: String): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.randomKPP())

}
