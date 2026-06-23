package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.feeders.faker.GovIdGenerators

@deprecated("Use org.galaxio.gatling.feeders.faker.Faker.ru.ogrn with GeneratedFeeder instead", "faker-api")
object RandomOGRNFeeder {

  /** Creates a feeder that generates a random OGRN (Primary State Registration Number)
    *
    * OGRN is used only in the Russian Federation (ОГРН in Russian)
    *
    * @param paramName
    *   feeder's name
    * @return
    *   random string OGRN feeder
    */
  def apply(paramName: String): Feeder[String] =
    feeder[String](paramName)(GovIdGenerators.ogrn())

}
