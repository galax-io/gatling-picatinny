package org.galaxio.gatling.transactions
import io.gatling.commons.stats.{KO, OK}
import io.gatling.core.action.Action
import io.gatling.core.actor.{Actor, Behavior}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

object TransactionsActor {
  sealed trait TransactionMessage

  final case class TransactionStarted(name: String, timestamp: Long)                               extends TransactionMessage
  final case class TransactionEnded(name: String, timestamp: Long, session: Session, next: Action) extends TransactionMessage
}

class TransactionsActor(name: String, statsEngine: StatsEngine) extends Actor[TransactionsActor.TransactionMessage](name) {

  private def crash(prefix: String, errorMsg: String, session: Session, next: Action): Unit = {
    statsEngine.logRequestCrash(session.scenario, session.groups, prefix, errorMsg)
    next ! session.markAsFailed
  }

  private def executeNext(name: String, startTimestamp: Long, stopTimestamp: Long, session: Session, next: Action): Unit = {
    statsEngine.logResponse(
      session.scenario,
      session.groups,
      name,
      startTimestamp,
      stopTimestamp,
      if (session.isFailed) KO else OK,
      None,
      if (session.isFailed) Some(s"transaction '$name' failed") else None,
    )
    next ! session.logGroupRequestTimings(startTimestamp, stopTimestamp)
  }

  private def onTransaction(
      transactionsStack: List[TransactionsActor.TransactionStarted],
  ): Behavior[TransactionsActor.TransactionMessage] = {
    case t: TransactionsActor.TransactionStarted                            =>
      become(onTransaction(t :: transactionsStack))
    case TransactionsActor.TransactionEnded(name, timestamp, session, next) =>
      transactionsStack match {
        case Nil =>
          crash(s"Transaction '$name' close error", s"transaction '$name' wasn't started", session, next)
          stay

        case started :: newStack =>
          if (started.timestamp > timestamp) {
            crash(s"Transaction '$name' illegal state", s"transaction cannot end before it started", session, next)
            stay
          } else if (started.name == name) {
            executeNext(name, started.timestamp, timestamp, session, next)
            become(onTransaction(newStack))
          } else {
            crash(s"Transaction '$name' close error", s"has unclosed transaction ${started.name}", session, next)
            stay
          }
      }
  }

  override def init(): Behavior[TransactionsActor.TransactionMessage] = onTransaction(Nil)
}
