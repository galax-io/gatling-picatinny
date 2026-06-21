package org.galaxio.gatling.transactions.actions

import io.gatling.commons.validation._
import io.gatling.core.action.Action
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext
import org.galaxio.gatling.transactions.Constants

class EndTransactionAction(
    transactionName: Expression[String],
    stopTime: Expression[Long],
    protected val ctx: ScenarioContext,
    val next: Action,
) extends TransactionAction {

  override def name: String                 = genName("endTransactionAction")
  override protected def crashLabel: String = Constants.EndLabel

  override protected def execute(session: Session): Unit = {
    val resolved =
      for {
        resolvedName  <- transactionName(session)
        stopTimestamp <- stopTime(session)
      } yield (resolvedName, stopTimestamp)

    resolved match {
      case Failure(message)                       => crashAndAdvance(session, message)
      case Success((resolvedName, stopTimestamp)) =>
        throttled(session.scenario) {
          components.transactionTracker.endTransaction(resolvedName, stopTimestamp, session, next)
        }
    }
  }
}
