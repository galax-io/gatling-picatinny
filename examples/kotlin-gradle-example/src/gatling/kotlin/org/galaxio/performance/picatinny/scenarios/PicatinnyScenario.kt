package org.galaxio.performance.picatinny.scenarios

import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.ScenarioBuilder
import org.galaxio.performance.picatinny.cases.PicatinnyCases
import org.galaxio.performance.picatinny.feeders.PicatinnyFeeders

object PicatinnyScenario {
    fun apply(name: String, transactionName: String): ScenarioBuilder =
        withFeeders(name).exec(PicatinnyCases.businessOperation(transactionName))

    fun apply(name: String): ScenarioBuilder =
        withFeeders(name).exec(PicatinnyCases.scenarioOperation())

    private fun withFeeders(name: String): ScenarioBuilder {
        var builder = scenario(name)
        PicatinnyFeeders.all().forEach { feeder ->
            builder = builder.feed(feeder)
        }
        return builder
    }
}
