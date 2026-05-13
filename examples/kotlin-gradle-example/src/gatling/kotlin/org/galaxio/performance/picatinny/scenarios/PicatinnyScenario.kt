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
        PicatinnyFeeders.legacy().forEach { feeder ->
            builder = builder.feed(feeder)
        }
        builder = builder
            .feed(GeneratedPicatinnyFeeders.generatedUsers())
            .feed(GeneratedPicatinnyFeeders.governmentIds())
            .feed(GeneratedPicatinnyFeeders.finance())
        return builder
    }
}
