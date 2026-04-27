package org.galaxio.performance.picatinny.scenarios

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.performance.picatinny.cases.PicatinnyCases
import org.galaxio.performance.picatinny.feeders.PicatinnyFeeders

object TransactionScenario {
  def apply(name: String, transactionName: String): ScenarioBuilder =
    scenario(name)
      .feed(PicatinnyFeeders.currentDate)
      .feed(PicatinnyFeeders.randomDate)
      .feed(PicatinnyFeeders.dateRange)
      .feed(PicatinnyFeeders.digit)
      .feed(PicatinnyFeeders.customValue)
      .feed(PicatinnyFeeders.phone)
      .feed(PicatinnyFeeders.phoneFromJson)
      .feed(PicatinnyFeeders.formattedPhone)
      .feed(PicatinnyFeeders.randomString)
      .feed(PicatinnyFeeders.rangeString)
      .feed(PicatinnyFeeders.uuid)
      .feed(PicatinnyFeeders.sequence)
      .feed(PicatinnyFeeders.regex)
      .feed(PicatinnyFeeders.csvValue)
      .feed(PicatinnyFeeders.splitValues)
      .feed(PicatinnyFeeders.pan)
      .feed(PicatinnyFeeders.natItn)
      .feed(PicatinnyFeeders.jurItn)
      .feed(PicatinnyFeeders.ogrn)
      .feed(PicatinnyFeeders.psrnsp)
      .feed(PicatinnyFeeders.kpp)
      .feed(PicatinnyFeeders.snils)
      .feed(PicatinnyFeeders.passport)
      .startTransaction(transactionName)
      .exec(PicatinnyCases.businessOperation)
      .endTransaction(transactionName)
}
