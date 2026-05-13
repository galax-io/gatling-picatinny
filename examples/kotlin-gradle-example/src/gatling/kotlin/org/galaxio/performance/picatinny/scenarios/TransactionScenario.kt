package org.galaxio.performance.picatinny.scenarios

import io.gatling.javaapi.core.ScenarioBuilder
import org.galaxio.performance.picatinny.cases.PicatinnyCases

object TransactionScenario {
    fun apply(name: String, transactionName: String): ScenarioBuilder =
        PicatinnyScenario.withFeeders(name)
            .exec(PicatinnyCases.businessOperation(transactionName))
}
