package org.galaxio.performance.picatinny.scenarios

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.performance.picatinny.cases.PicatinnyCases

object TransactionScenario {
  def apply(name: String, transactionName: String): ScenarioBuilder =
    PicatinnyScenario
      .withFeeders(name)
      .startTransaction(transactionName)
      .exec(PicatinnyCases.businessOperation)
      .endTransaction(transactionName)
}
