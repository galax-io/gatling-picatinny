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
      .feed(PicatinnyFeeders.randomDate)
      .feed(PicatinnyFeeders.dateRange)
      .feed(PicatinnyFeeders.digit)
      .feed(PicatinnyFeeders.customValue)
      .feed(PicatinnyFeeders.phone)
      .feed(PicatinnyFeeders.e164Phone)
      .feed(PicatinnyFeeders.formattedPhone)
      .feed(PicatinnyFeeders.tollFreePhone)
      .feed(PicatinnyFeeders.randomString)
      .feed(PicatinnyFeeders.rangeString)
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
      .feed(PicatinnyFeeders.lambdaFeeder)
      .feed(PicatinnyFeeders.zippedFeeder)
      .feed(PicatinnyFeeders.finiteRecords.circular)
      .feed(GeneratedPicatinnyFeeders.generatedUsers)
      .feed(GeneratedPicatinnyFeeders.governmentIds)
      .feed(GeneratedPicatinnyFeeders.dates)
      .feed(GeneratedPicatinnyFeeders.finance)
      .feed(GeneratedPicatinnyFeeders.countries)
      .feed(GeneratedPicatinnyFeeders.enrichedStaticUsers)
      .feed(GeneratedPicatinnyFeeders.numbers)
      .feed(GeneratedPicatinnyFeeders.strings)
      .feed(GeneratedPicatinnyFeeders.persons)
      .feed(GeneratedPicatinnyFeeders.internetData)
      .feed(GeneratedPicatinnyFeeders.locations)
      .feed(GeneratedPicatinnyFeeders.extendedDates)
      .feed(GeneratedPicatinnyFeeders.dateRangeTuple)
      .feed(GeneratedPicatinnyFeeders.extendedFinance)
      .feed(GeneratedPicatinnyFeeders.commerce)
      .feed(GeneratedPicatinnyFeeders.weatherData)
      .feed(GeneratedPicatinnyFeeders.loremText)
      .feed(GeneratedPicatinnyFeeders.localized)
      .feed(GeneratedPicatinnyFeeders.countrySpecificIds)
      .feed(GeneratedPicatinnyFeeders.phones)
      .feed(GeneratedPicatinnyFeeders.combinators)
      .feed(GeneratedPicatinnyFeeders.singleFieldFeeder)
      .feed(GeneratedPicatinnyFeeders.transformedFeeder)
      .feed(GeneratedPicatinnyFeeders.keyOpsDemo)
      .feed(GeneratedPicatinnyFeeders.singleRecordDemo.circular)
}
