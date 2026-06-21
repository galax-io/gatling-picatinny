package org.galaxio.gatling.transactions

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

import io.gatling.commons.util.Clock
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

  /** Controllable time source injected in place of `DefaultClock` (R6). `nowMillis` returns the current value; tests
    * advance/set it explicitly (e.g. inside an `exec` block between start and end) to drive deterministic timestamps.
    */
  final class TestClock(initial: Long) extends Clock {
    private val current                  = new AtomicLong(initial)
    override def nowMillis: Long         = current.get()
    def set(value: Long): Unit           = current.set(value)
    def advance(deltaMillis: Long): Long = current.addAndGet(deltaMillis)
  }

  /** Terminal chainable action that counts down a latch whenever it is reached. Replaces the racy `Thread.sleep` — proving "the
    * virtual user is advanced / never stalls" becomes "the latch fires within a bounded timeout".
    */
  def latchAction(latch: CountDownLatch): Action = new ChainableAction {
    override def next: Action                              = noAction
    override def name: String                              = "latch"
    override protected def execute(session: Session): Unit = latch.countDown()
    override def statsEngine: StatsEngine                  = new NoOpStatsEngine
  }
}
