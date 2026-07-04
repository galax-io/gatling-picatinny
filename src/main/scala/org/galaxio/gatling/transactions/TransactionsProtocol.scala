package org.galaxio.gatling.transactions

import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolKey}

import java.util.concurrent.atomic.AtomicLong

object TransactionsProtocol {
  val key: ProtocolKey[TransactionsProtocol, TransactionsComponents] =
    new ProtocolKey[TransactionsProtocol, TransactionsComponents] {
      // Justification: Gatling ProtocolKey contract requires Class[Protocol]; same pattern as Gatling built-in protocols
      // scalafix:off DisableSyntax.asInstanceOf
      override def protocolClass: Class[Protocol] = classOf[TransactionsProtocol].asInstanceOf[Class[
        Protocol,
      ]]
      // scalafix:on DisableSyntax.asInstanceOf

      override def defaultProtocolValue(configuration: GatlingConfiguration): TransactionsProtocol = new TransactionsProtocol

      override def newComponents(coreComponents: CoreComponents): TransactionsProtocol => TransactionsComponents =
        _ => {
          // Shared #70 bound state: in-flight (sent − processed) and the observable dropped-event counter.
          val inFlight          = new AtomicLong(0L)
          val droppedEvents     = new AtomicLong(0L)
          val maxInFlight       = Constants.maxInFlight
          val transactionsActor =
            coreComponents.actorSystem.actorOf(
              new TransactionsActor("transactionsActor", coreComponents.statsEngine, inFlight),
            )
          // Make the dropped-event count observable in run output: one summary crash entry at termination.
          TransactionTracker.registerDroppedSummary(
            coreComponents.actorSystem,
            coreComponents.statsEngine,
            droppedEvents,
            maxInFlight,
          )
          new TransactionsComponents(new TransactionTracker(transactionsActor, inFlight, droppedEvents, maxInFlight))
        }
    }
}

final class TransactionsProtocol extends Protocol {
  type Components = TransactionsComponents
}
