package org.galaxio.gatling.transactions

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.StrictLogging
import io.gatling.core.action.Action
import io.gatling.core.actor.{ActorRef, ActorSystem}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

object TransactionTracker extends StrictLogging {

  /** Makes the #70 dropped-event count observable. Registers a one-shot summary that, at actor-system termination, reports the
    * total dropped (when > 0). It logs a WARN as the **reliable** channel (independent of the stats-engine lifecycle — the
    * engine may already be stopped by the time termination tasks run) and additionally writes a best-effort crash entry under
    * [[Constants.DroppedLabel]] for the run report when the engine still accepts it. Shared by production wiring
    * ([[TransactionsProtocol]]) and tests so the behavior is single-sourced.
    */
  def registerDroppedSummary(
      actorSystem: ActorSystem,
      statsEngine: StatsEngine,
      droppedEvents: AtomicLong,
      maxInFlight: Int,
  ): Unit =
    actorSystem.registerOnTermination {
      val dropped = droppedEvents.get()
      if (dropped > 0L) {
        val message = s"$dropped transaction event(s) dropped (maxInFlight=$maxInFlight)"
        logger.warn(s"transactions: $message")
        statsEngine.logRequestCrash("transactions", Nil, Constants.DroppedLabel, message)
      }
    }
}

/** Picatinny-side bound in front of the framework-owned (unbounded) actor mailbox (#70).
  *
  * `inFlight` approximates queue depth (sent − processed): the tracker reserves a slot on every enqueue and the
  * [[TransactionsActor]] releases it as it processes each message. A single atomic `incrementAndGet` makes the reserve-or-drop
  * decision per producer thread (rolling the slot back on overflow); the common path touches the contended counter once. When
  * the bound is reached the tracker applies a **drop-newest** policy and increments `droppedEvents` instead of enqueuing — the
  * only feasible policy, since the boundary cannot evict the head of a queue it does not own. The bound is a memory safety
  * valve, so a transient overshoot of at most one slot per concurrent producer is acceptable.
  *
  * Invariant: a dropped `TransactionEnded` MUST still advance its virtual user (`next ! session.markAsFailed`), or the bound
  * would re-introduce the #201 hang. A dropped `TransactionStarted` needs no advance — the later close hits the existing
  * "wasn't started" path, which already advances the VU.
  */
class TransactionTracker(
    transactionActor: ActorRef[TransactionsActor.TransactionMessage],
    inFlight: AtomicLong,
    droppedEvents: AtomicLong,
    maxInFlight: Int,
) {

  /** Atomically reserves an in-flight slot, returning false (and rolling back) when the bound is exceeded. */
  private def reserveSlot(): Boolean =
    if (inFlight.incrementAndGet() > maxInFlight) {
      inFlight.decrementAndGet()
      false
    } else true

  def startTransaction(name: String, timestamp: Long): Unit =
    if (reserveSlot()) transactionActor ! TransactionsActor.TransactionStarted(name, timestamp)
    else droppedEvents.incrementAndGet()

  def endTransaction(name: String, timestamp: Long, session: Session, next: Action): Unit =
    if (reserveSlot()) transactionActor ! TransactionsActor.TransactionEnded(name, timestamp, session, next)
    else {
      droppedEvents.incrementAndGet()
      next ! session.markAsFailed
    }
}
