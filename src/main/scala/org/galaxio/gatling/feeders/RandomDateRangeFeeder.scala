package org.galaxio.gatling.feeders

import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoUnit, TemporalUnit}
import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomDataGenerators


object RandomDateRangeFeeder {

  def apply(
             paramNameFrom: String,
             paramNameTo: String,
             offsetDate: Long,
             datePattern: String = "yyyy-MM-dd",
             dateFrom: LocalDateTime = LocalDateTime.now(),
             unit: TemporalUnit = ChronoUnit.DAYS,
             timezone: ZoneId = ZoneId.systemDefault(),
           ): Feeder[String] = {
    val dateFormatter = DateTimeFormatter.ofPattern(datePattern)

    def addRandomDateToMap(originalMap: Map[String, String]): Map[String, String] =
      originalMap + (paramNameTo -> RandomDataGenerators.randomDate(offsetDate, datePattern, dateFrom, unit, timezone))

    feeder[String](paramNameFrom)(dateFrom.format(dateFormatter)).map(addRandomDateToMap)
  }


}
