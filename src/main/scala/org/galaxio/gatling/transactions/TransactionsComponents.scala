package org.galaxio.gatling.transactions

import io.gatling.core.protocol.ProtocolComponents
import io.gatling.core.session.Session

class TransactionsComponents private[transactions] (private[transactions] val transactionTracker: TransactionTracker)
    extends ProtocolComponents {

  override def onStart: Session => Session = Session.Identity

  override def onExit: Session => Unit = ProtocolComponents.NoopOnExit
}
