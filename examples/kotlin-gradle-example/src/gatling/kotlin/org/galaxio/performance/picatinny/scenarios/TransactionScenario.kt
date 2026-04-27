package org.galaxio.performance.picatinny.scenarios

import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.ScenarioBuilder
import org.galaxio.performance.picatinny.cases.PicatinnyCases
import org.galaxio.performance.picatinny.feeders.PicatinnyFeeders

object TransactionScenario {
    fun apply(name: String, transactionName: String): ScenarioBuilder {
        var builder = scenario(name)
        PicatinnyFeeders.all().forEach { feeder ->
            builder = builder.feed(feeder)
        }
        return builder.exec(PicatinnyCases.businessOperation(transactionName))
    }
}
