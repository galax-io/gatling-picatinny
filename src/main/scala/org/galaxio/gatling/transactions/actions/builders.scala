package org.galaxio.gatling.transactions.actions
import io.gatling.commons.validation.SuccessWrapper
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext

object builders {
  final case class StartTransactionActionBuilder(tName: Expression[String]) extends ActionBuilder {
    override def build(ctx: ScenarioContext, next: Action): Action = new StartTransactionAction(tName, ctx, next)
  }

  /** `stopTime = None` → the end timestamp is read from the Gatling clock (the same uniform source as the start); `Some(expr)`
    * → the caller-supplied end timestamp. One builder covers both since the time source is now uniform.
    */
  final case class EndTransactionActionBuilder(tName: Expression[String], stopTime: Option[Expression[Long]] = None)
      extends ActionBuilder {
    override def build(ctx: ScenarioContext, next: Action): Action =
      new EndTransactionAction(tName, stopTime.getOrElse(_ => ctx.coreComponents.clock.nowMillis.success), ctx, next)
  }
}
