package org.galaxio.gatling.transactions

import io.gatling.commons.validation._
import io.gatling.core.Predef._
import io.gatling.core.actor.ActorSystem
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.scenario.SimulationParams
import io.gatling.core.session.Expression
import io.gatling.core.stats.RecordingStatsEngine
import io.gatling.core.structure.{ScenarioBuilder, ScenarioContext}
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.transactions.actions.builders._
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._

object TransactionsSpec {
  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private val now                          = System.currentTimeMillis()
  val transactionScenario: ScenarioBuilder =
    scenario("Test transaction scenario")
      .startTransaction("t1")
      .exec(s => s)
      .endTransaction("t1", now + 500)

  val startBuilder: StartTransactionActionBuilder = StartTransactionActionBuilder("t1")
  val endBuilder: EndTransactionActionBuilder     = EndTransactionActionBuilder("t1", Some(now + 500))

  val transactionScenarioWithDefaultEndTime: ScenarioBuilder =
    scenario("Test transaction scenario").startTransaction("t1").exec(s => s).endTransaction("t1")

  val notOpenedTransactionScenario: ScenarioBuilder =
    scenario("Close not opened transaction").exec(s => s).endTransaction("t1")

  val incorrectEndTimeTransactionScenario: ScenarioBuilder =
    scenario("Incorrect end time transaction")
      .startTransaction("t1")
      .exec(s => s)
      .endTransaction("t1", 1L)

  val incorrectTransactionSequenceScenario: ScenarioBuilder =
    scenario("Incorrect transaction b")
      .startTransaction("t2")
      .exec(s => s)
      .endTransaction("t1")
      .endTransaction("t2")

  val nestedTransactionScenario: ScenarioBuilder =
    scenario("Nested transaction scenario")
      .startTransaction("outer")
      .startTransaction("inner")
      .exec(s => s)
      .endTransaction("inner")
      .endTransaction("outer")

  final class OverrideHookSimulation(hooks: scala.collection.mutable.ArrayBuffer[String]) extends SimulationWithTransactions {
    setUp(scenario("Override hook scenario").exec(s => s).inject(atOnceUsers(1)))

    override def before(): Unit = hooks += "before"

    override def after(): Unit = hooks += "after"
  }

  final class RegisteredHookSimulation(hooks: scala.collection.mutable.ArrayBuffer[String]) extends SimulationWithTransactions {
    before {
      hooks += "before"
    }

    after {
      hooks += "after"
    }

    setUp(scenario("Registered hook scenario").exec(s => s).inject(atOnceUsers(1)))
  }

  def paramsOf(simulation: SimulationWithTransactions): SimulationParams =
    classOf[io.gatling.core.scenario.Simulation]
      .getMethod("params", classOf[GatlingConfiguration])
      .invoke(simulation, configuration)
      .asInstanceOf[SimulationParams]
}

class TransactionsSpec extends AnyWordSpec with Matchers with Mocks {
  import TransactionsSpec._

  private val session = fixtures.emptySession(transactionScenario.name)

  // Deterministic, bounded replacement for the racy Thread.sleep: the latch fires when the terminal `next` is
  // reached. Returns true if the chain completed within the timeout, false on a stall (the #201 hang). See R6.
  private def runScenario(s: ScenarioBuilder, testContext: ScenarioContext): Boolean = {
    val latch    = new CountDownLatch(1)
    val terminal = fixtures.latchAction(latch)
    val actions  = s.actionBuilders.foldLeft(terminal)((next, builder) => builder.build(testContext, next))
    actions ! session
    latch.await(5, TimeUnit.SECONDS)
  }

  private val name     = Symbol("name")
  private val status   = Symbol("status")
  private val errorMsg = Symbol("errorMsg")

  "Transaction scenario DSL" should {
    "contain start transaction action builder with specified name" in {
      transactionScenario.actionBuilders should contain(startBuilder)
    }

    "contain end transaction action builder with specified name" in {
      transactionScenario.actionBuilders should contain(endBuilder)
    }
  }

  "Transaction scenario execution" should {
    "write request with correct start/stop timestamps and name" in new MockedGatlingCtx {
      runScenario(transactionScenarioWithDefaultEndTime, testContext) shouldBe true

      val requestRecord = getEvents.find(_.evtType == "REQUEST")

      requestRecord shouldBe defined
      requestRecord.value should have(name("t1"), status("OK"), errorMsg(None))
      requestRecord.value.startTimestamp should be <= requestRecord.value.endTimestamp
    }

    "fail with transaction close error when closing a not opened transaction" in new MockedGatlingCtx {
      runScenario(notOpenedTransactionScenario, testContext) shouldBe true

      val errorRecord   = getEvents.find(_.evtType == "ERROR")
      val requestRecord = getEvents.find(_.evtType == "REQUEST")

      requestRecord should not be defined
      errorRecord shouldBe defined
      errorRecord.value should have(
        name("Transaction 't1' close error"),
        status("KO"),
      )
      errorRecord.value.errorMsg.value shouldBe "transaction 't1' wasn't started"
    }

    "fail with illegal state error when a transaction ended before it started" in new MockedGatlingCtx {
      runScenario(incorrectEndTimeTransactionScenario, testContext) shouldBe true

      val errorRecord   = getEvents.find(_.evtType == "ERROR")
      val requestRecord = getEvents.find(_.evtType == "REQUEST")

      requestRecord should not be defined
      errorRecord shouldBe defined
      errorRecord.value should have(
        name("Transaction 't1' illegal state"),
        status("KO"),
      )
      errorRecord.value.errorMsg.value shouldBe "transaction cannot end before it started"
    }

    "write nested transactions with correct timestamps" in new MockedGatlingCtx {
      runScenario(nestedTransactionScenario, testContext) shouldBe true

      val requests = getEvents.filter(_.evtType == "REQUEST")

      requests should have size 2
      requests.map(_.name) should contain allOf ("inner", "outer")

      val inner = requests.find(_.name == "inner").value
      val outer = requests.find(_.name == "outer").value
      inner should have(status("OK"))
      outer should have(status("OK"))
      inner.startTimestamp should be >= outer.startTimestamp
      inner.endTimestamp should be <= outer.endTimestamp
    }

    "fail with transaction close error when transaction sequence is incorrect" in new MockedGatlingCtx {
      runScenario(incorrectTransactionSequenceScenario, testContext) shouldBe true

      val errorRecord    = getEvents.find(_.evtType == "ERROR")
      val requestRecord  = getEvents.find(evt => evt.evtType == "REQUEST" && evt.name == "t1")
      val recoveryRecord = getEvents.find(evt => evt.evtType == "REQUEST" && evt.name == "t2")

      requestRecord should not be defined
      errorRecord shouldBe defined
      errorRecord.value should have(
        name("Transaction 't1' close error"),
        status("KO"),
      )
      errorRecord.value.errorMsg.value shouldBe "has unclosed transaction t2"
      recoveryRecord.value should have(status("KO"))
    }
  }

  "Transaction reliability (v1.16.0)" should {

    "fail fast and record a crash when the startTransaction name does not resolve (US1)" in new MockedGatlingCtx {
      val sc = scenario("bad start").startTransaction("#{missing}")
      runScenario(sc, testContext) shouldBe true // latch fired ⇒ VU advanced, no hang (SC-001)
      val crash = getEvents.find(_.evtType == "ERROR")
      crash shouldBe defined
      crash.value should have(name(Constants.StartLabel), status("KO")) // SC-002, FR-010
    }

    "fail fast and record a crash when the endTransaction name does not resolve (US1)" in new MockedGatlingCtx {
      val sc    = scenario("bad end").startTransaction("t1").exec(s => s).endTransaction("#{missing}")
      runScenario(sc, testContext) shouldBe true
      val crash = getEvents.find(evt => evt.evtType == "ERROR" && evt.name == Constants.EndLabel)
      crash shouldBe defined
      crash.value should have(status("KO"))
    }

    "fail fast when the endTransaction stopTime expression does not resolve (US1)" in new MockedGatlingCtx {
      val badStop: Expression[Long] = _ => "unresolvable stopTime".failure
      val sc                        = scenario("bad stoptime").startTransaction("t1").exec(s => s).endTransaction("t1", badStop)
      runScenario(sc, testContext) shouldBe true
      getEvents.exists(evt => evt.evtType == "ERROR" && evt.name == Constants.EndLabel) shouldBe true
    }

    "fast-fail an unresolvable expression structurally before any throttler dispatch (US1 edge)" in new MockedGatlingCtx {
      // NOTE: the harness has no throttler (CoreComponents.throttler = None), so this does not exercise the active-
      // throttling Some-branch. It documents the structural guarantee: resolution failure is matched before the
      // throttler dispatch in execute(), so an unresolvable name fast-fails regardless of throttling.
      val sc = scenario("throttle bad").startTransaction("#{missing}")
      runScenario(sc, testContext) shouldBe true
      getEvents.exists(_.name == Constants.StartLabel) shouldBe true
    }

    "close a default-end transaction OK with no false illegal-state (US2)" in new MockedGatlingCtx {
      testClock.set(1000L)
      val sc  = scenario("default end").startTransaction("t1").exec(s => s).endTransaction("t1")
      runScenario(sc, testContext) shouldBe true
      val req = getEvents.find(_.evtType == "REQUEST")
      req shouldBe defined
      req.value should have(name("t1"), status("OK"))
      req.value.startTimestamp should be <= req.value.endTimestamp
      getEvents.exists(_.evtType == "ERROR") shouldBe false // SC-003: no false "cannot end before it started"
    }

    "report an accurate non-negative duration from the single clock source (US3)" in new MockedGatlingCtx {
      testClock.set(5000L)
      val delta = 500L
      val sc    = scenario("dur").startTransaction("t1").exec { s => testClock.advance(delta); s }.endTransaction("t1")
      runScenario(sc, testContext) shouldBe true
      val req   = getEvents.find(_.evtType == "REQUEST")
      req shouldBe defined
      req.value.startTimestamp shouldBe 5000L
      req.value.endTimestamp shouldBe 5500L
      (req.value.endTimestamp - req.value.startTimestamp) shouldBe delta // SC-004
    }

    "bound in-flight events, count drops, and expose a termination summary (US4)" in {
      val inFlight    = new AtomicLong(2L)                                         // already at the bound
      val dropped     = new AtomicLong(0L)
      val maxInFlight = 2
      val evts        = new ConcurrentLinkedQueue[Evt]()
      val se          = new RecordingStatsEngine(evts)
      val actorSystem = new ActorSystem()
      val actor       = actorSystem.actorOf(new TransactionsActor("t70", se, inFlight))
      TransactionTracker.registerDroppedSummary(actorSystem, se, dropped, maxInFlight)
      val tracker     = new TransactionTracker(actor, inFlight, dropped, maxInFlight)
      try {
        tracker.startTransaction("a", 1L)
        tracker.startTransaction("b", 2L)
        tracker.endTransaction("c", 3L, fixtures.emptySession("s"), fixtures.noAction)
        inFlight.get() should be <= 2L // never grew past the bound (SC-006 bounded)
        dropped.get() shouldBe 3L
      } finally actorSystem.close()
      val summary     = evts.asScala.toList.find(_.name == Constants.DroppedLabel) // SC-006 observable
      summary shouldBe defined
      summary.value should have(status("KO"))
    }

    "advance the virtual user when an End event is dropped at the bound (US4)" in {
      val inFlight    = new AtomicLong(1L)
      val dropped     = new AtomicLong(0L)
      val maxInFlight = 1
      val evts        = new ConcurrentLinkedQueue[Evt]()
      val se          = new RecordingStatsEngine(evts)
      val actorSystem = new ActorSystem()
      val actor       = actorSystem.actorOf(new TransactionsActor("t70b", se, inFlight))
      val tracker     = new TransactionTracker(actor, inFlight, dropped, maxInFlight)
      val latch       = new CountDownLatch(1)
      try {
        tracker.endTransaction("c", 3L, fixtures.emptySession("s"), fixtures.latchAction(latch))
        latch.await(5, TimeUnit.SECONDS) shouldBe true // dropped End still advances the VU (FR-003)
        dropped.get() shouldBe 1L
      } finally actorSystem.close()
    }

    "balance in-flight: admitted events increment on send and are released by the actor (US4)" in {
      // Starts below the bound, so events are ADMITTED — exercises the increment-on-send + actor-decrement balance,
      // not just the saturated drop path. After a full open/close round-trip the counter returns to zero.
      val inFlight    = new AtomicLong(0L)
      val dropped     = new AtomicLong(0L)
      val maxInFlight = 10
      val evts        = new ConcurrentLinkedQueue[Evt]()
      val se          = new RecordingStatsEngine(evts)
      val actorSystem = new ActorSystem()
      val actor       = actorSystem.actorOf(new TransactionsActor("t70c", se, inFlight))
      val tracker     = new TransactionTracker(actor, inFlight, dropped, maxInFlight)
      val latch       = new CountDownLatch(1)
      try {
        tracker.startTransaction("t1", 1L)                                                        // admitted: 0→1
        tracker.endTransaction("t1", 2L, fixtures.emptySession("s"), fixtures.latchAction(latch)) // admitted: 1→2
        latch.await(5, TimeUnit.SECONDS) shouldBe true                                            // actor released both
        dropped.get() shouldBe 0L
        inFlight.get() shouldBe 0L                                                                // every send decremented
      } finally actorSystem.close()
    }
  }

  "SimulationWithTransactions" should {
    "execute override-style hooks for Java and Kotlin simulations" in {
      val hooks            = scala.collection.mutable.ArrayBuffer.empty[String]
      val simulationParams = paramsOf(new OverrideHookSimulation(hooks))

      simulationParams.before()
      simulationParams.after()

      hooks.toSeq shouldBe Seq("before", "after")
    }

    "preserve registered Scala hooks" in {
      val hooks            = scala.collection.mutable.ArrayBuffer.empty[String]
      val simulationParams = paramsOf(new RegisteredHookSimulation(hooks))

      simulationParams.before()
      simulationParams.after()

      hooks.toSeq shouldBe Seq("before", "after")
    }
  }
}
