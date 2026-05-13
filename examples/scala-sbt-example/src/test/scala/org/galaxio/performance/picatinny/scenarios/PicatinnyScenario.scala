package org.galaxio.performance.picatinny.scenarios

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.performance.picatinny.cases.PicatinnyCases
import org.galaxio.performance.picatinny.feeders.{GeneratedPicatinnyFeeders, PicatinnyFeeders}

object PicatinnyScenario {
  def apply(name: String, transactionName: String): ScenarioBuilder =
    withFeeders(name)
      .startTransaction(transactionName)
      .exec(PicatinnyCases.businessOperation)
      .endTransaction(transactionName)

  def apply(name: String): ScenarioBuilder =
    withFeeders(name).exec(PicatinnyCases.businessOperation)

  private[scenarios] def withFeeders(name: String): ScenarioBuilder =
    scenario(name)
      .feed(PicatinnyFeeders.uuid)
      .feed(PicatinnyFeeders.currentDate)
      .feed(PicatinnyFeeders.formattedPhone)
      .feed(PicatinnyFeeders.randomString)
      .feed(PicatinnyFeeders.e164Phone)
      .feed(GeneratedPicatinnyFeeders.generatedUsers)
      .feed(GeneratedPicatinnyFeeders.governmentIds)
      .feed(GeneratedPicatinnyFeeders.dates)
      .feed(GeneratedPicatinnyFeeders.finance)
}
