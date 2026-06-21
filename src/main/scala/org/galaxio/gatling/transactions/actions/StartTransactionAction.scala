package org.galaxio.gatling.transactions.actions

import io.gatling.commons.validation._
import io.gatling.core.action.Action
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext
import org.galaxio.gatling.transactions.Constants

class StartTransactionAction(transactionName: Expression[String], protected val ctx: ScenarioContext, val next: Action)
    extends TransactionAction {

  override def name: String                 = genName("startTransactionAction")
  override protected def crashLabel: String = Constants.StartLabel

  override protected def execute(session: Session): Unit =
    transactionName(session) match {
      case Failure(message)      => crashAndAdvance(session, message)
      case Success(resolvedName) =>
        val startTimestamp = ctx.coreComponents.clock.nowMillis
        throttled(session.scenario) {
          components.transactionTracker.startTransaction(resolvedName, startTimestamp)
          next ! session
        }
    }
}
