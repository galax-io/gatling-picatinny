package org.galaxio.gatling.transactions

import akka.actor.ActorRef
import io.gatling.commons.stats.Status
import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.core.session.{GroupBlock, Session}
import io.gatling.core.stats.StatsEngine

object fixtures {
  val noAction: Action = new ChainableAction {
    override def next: Action = noAction

    override def name: String = "noop"

    override protected def execute(session: Session): Unit = ()

    override def statsEngine: StatsEngine = new StatsEngine {
      override def start(): Unit = {}

      override def stop(controller: ActorRef, exception: Option[Exception]): Unit = {}

      override def logUserStart(scenario: String): Unit = {}

      override def logUserEnd(scenario: String): Unit = {}

      override def logResponse(
          scenario: String,
          groups: List[String],
          requestName: String,
          startTimestamp: Long,
          endTimestamp: Long,
          status: Status,
          responseCode: Option[String],
          message: Option[String],
      ): Unit = {}

      override def logGroupEnd(scenario: String, groupBlock: GroupBlock, exitTimestamp: Long): Unit = {}

      override def logRequestCrash(scenario: String, groups: List[String], requestName: String, error: String): Unit = {}
    }
  }

  val fakeEventLoop = new FakeEventLoop

  def emptySession(scenario: String): Session = Session(scenario, 1, fakeEventLoop)
}
