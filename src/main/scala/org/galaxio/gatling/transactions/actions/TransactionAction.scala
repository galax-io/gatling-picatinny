package org.galaxio.gatling.transactions.actions

import io.gatling.core.action.ChainableAction
import io.gatling.core.actor.ActorRef
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import org.galaxio.gatling.transactions.{TransactionsComponents, TransactionsProtocol}

/** Shared structure for the transaction-boundary actions so the start and end paths cannot drift apart.
  *
  * Each action resolves its EL expression(s) and then either records an unresolvable-expression crash and advances the virtual
  * user (never stalls — #201) or dispatches its tracker call, routed through the throttler when one is configured.
  */
private[actions] trait TransactionAction extends ChainableAction with NameGen {

  protected def ctx: ScenarioContext

  /** Stable crash label for this action's unresolvable-expression failures (see
    * [[org.galaxio.gatling.transactions.Constants]]).
    */
  protected def crashLabel: String

  protected lazy val components: TransactionsComponents             =
    ctx.protocolComponentsRegistry.components(TransactionsProtocol.key)
  protected lazy val throttler: Option[ActorRef[Throttler.Command]] = ctx.coreComponents.throttler

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  /** Run `action` now, or hand it to the throttler (rate-limited) when one is configured. */
  protected def throttled(scenario: String)(action: => Unit): Unit =
    throttler match {
      case Some(t) => t ! Throttler.Command.ThrottledRequest(scenario, () => action)
      case None    => action
    }

  /** Record an unresolvable-expression failure under [[crashLabel]] and advance the VU as failed (never stall). */
  protected def crashAndAdvance(session: Session, message: String): Unit = {
    statsEngine.logRequestCrash(session.scenario, session.groups, crashLabel, message)
    next ! session.markAsFailed
  }
}
