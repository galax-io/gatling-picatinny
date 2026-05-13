package org.galaxio.performance.picatinny.scenarios

import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.ScenarioBuilder
import org.galaxio.performance.picatinny.cases.PicatinnyCases
import org.galaxio.performance.picatinny.feeders.GeneratedPicatinnyFeeders
import org.galaxio.performance.picatinny.feeders.PicatinnyFeeders

object PicatinnyScenario {
    fun apply(name: String, transactionName: String): ScenarioBuilder =
        withFeeders(name).exec(PicatinnyCases.businessOperation(transactionName))

    fun apply(name: String): ScenarioBuilder =
        withFeeders(name).exec(PicatinnyCases.scenarioOperation())

    internal fun withFeeders(name: String): ScenarioBuilder {
        var builder = scenario(name)
        PicatinnyFeeders.all().forEach { feeder ->
            builder = builder.feed(feeder)
        }
        builder = builder
            .feed(GeneratedPicatinnyFeeders.generatedUsers())
            .feed(GeneratedPicatinnyFeeders.governmentIds())
            .feed(GeneratedPicatinnyFeeders.dates())
            .feed(GeneratedPicatinnyFeeders.finance())
            .feed(GeneratedPicatinnyFeeders.numbers())
            .feed(GeneratedPicatinnyFeeders.strings())
            .feed(GeneratedPicatinnyFeeders.persons())
            .feed(GeneratedPicatinnyFeeders.internetData())
            .feed(GeneratedPicatinnyFeeders.locations())
            .feed(GeneratedPicatinnyFeeders.extendedFinance())
            .feed(GeneratedPicatinnyFeeders.commerce())
            .feed(GeneratedPicatinnyFeeders.countrySpecificIds())
            .feed(GeneratedPicatinnyFeeders.phones())
            .feed(GeneratedPicatinnyFeeders.loremText())
            .feed(GeneratedPicatinnyFeeders.singleFieldFeeder())
        return builder
    }
}
