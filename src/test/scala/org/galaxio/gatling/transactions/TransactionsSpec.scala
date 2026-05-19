package org.galaxio.gatling.transactions

import io.gatling.core.Predef._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.scenario.SimulationParams
import io.gatling.core.session.Expression
import io.gatling.core.structure.{ScenarioBuilder, ScenarioContext}
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.gatling.transactions.actions.builders._
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object TransactionsSpec {
  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private val now                          = System.currentTimeMillis()
  val transactionScenario: ScenarioBuilder =
    scenario("Test transaction scenario")
      .startTransaction("t1")
      .exec(s => s)
      .endTransaction("t1", now + 500)

  val startBuilder: StartTransactionActionBuilder = StartTransactionActionBuilder("t1")
  val endBuilder: EndTransactionActionBuilder     = EndTransactionActionBuilder("t1", now + 500)

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

  private def runScenario(s: ScenarioBuilder, testContext: ScenarioContext) = {
    val actions = s.actionBuilders.foldLeft(fixtures.noAction)((next, builder) => builder.build(testContext, next))
    actions ! session
    Thread.sleep(200)
    actions
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
      runScenario(transactionScenarioWithDefaultEndTime, testContext)

      val requestRecord = getEvents.find(_.evtType == "REQUEST")

      requestRecord shouldBe defined
      requestRecord.value should have(name("t1"), status("OK"), errorMsg(None))
      requestRecord.value.startTimestamp should be <= requestRecord.value.endTimestamp
    }

    "fail with transaction close error when closing a not opened transaction" in new MockedGatlingCtx {
      runScenario(notOpenedTransactionScenario, testContext)

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
      runScenario(incorrectEndTimeTransactionScenario, testContext)

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
      runScenario(nestedTransactionScenario, testContext)

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
      runScenario(incorrectTransactionSequenceScenario, testContext)

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
