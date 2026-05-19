package org.galaxio.gatling.transactions

import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.{NoOpStatsEngine, StatsEngine}

object fixtures {
  val noAction: Action = new ChainableAction {
    override def next: Action = noAction

    override def name: String = "noop"

    override protected def execute(session: Session): Unit = ()

    override def statsEngine: StatsEngine = new NoOpStatsEngine
  }

  val fakeEventLoop = new FakeEventLoop

  def emptySession(scenario: String): Session = Session(scenario, 1, fakeEventLoop)
}
