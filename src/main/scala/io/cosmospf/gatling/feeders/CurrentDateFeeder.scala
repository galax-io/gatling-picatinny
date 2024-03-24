package io.cosmospf.gatling.feeders

import io.gatling.core.feeder.Feeder
import io.cosmospf.gatling.utils.RandomDataGenerators

import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CurrentDateFeeder {

  def apply(paramName: String, datePattern: DateTimeFormatter, timezone: ZoneId = ZoneId.systemDefault()): Feeder[String] =
    feeder[String](paramName)(RandomDataGenerators.currentDate(datePattern, timezone))

}
