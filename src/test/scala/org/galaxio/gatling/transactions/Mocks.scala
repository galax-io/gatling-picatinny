package org.galaxio.gatling.transactions
import io.gatling.commons.util.DefaultClock
import io.gatling.core.CoreComponents
import io.gatling.core.actor.ActorSystem
import io.gatling.core.pause.Disabled
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolComponentsRegistry, ProtocolKey}
import io.gatling.core.stats.{NoOpStatsEngine, StatsEngine}
import io.gatling.core.structure.ScenarioContext
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Suite, TestSuite}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.mutable
import scala.jdk.CollectionConverters._

trait Mocks extends MockFactory with BeforeAndAfterAll {
  this: Suite with TestSuite =>

  trait MockedGatlingCtx {
    private val testActorSystem = new ActorSystem()

    val events: ConcurrentLinkedQueue[Evt] = new ConcurrentLinkedQueue()

    def getEvents: List[Evt] = this.events.asScala.toList

    private val componentsCache           = mutable.Map.empty[ProtocolKey[_, _], ProtocolComponents]
    private val componentsFactoryCache    = mutable.Map.empty[ProtocolKey[_, _], Protocol => ProtocolComponents]
    private val defaultProtocolValueCache = mutable.Map.empty[ProtocolKey[_, _], Protocol]

    val statsEngine: StatsEngine = new NoOpStatsEngine

    val testTransactionsActor = testActorSystem.actorOf(new TransactionsActor("test-transactions-actor", statsEngine))

    private val protoComponents: TransactionsComponents = new TransactionsComponents(
      new TransactionTracker(testTransactionsActor),
    )

    private val testCoreComponents = new CoreComponents(
      testActorSystem,
      fixtures.fakeEventLoop,
      null,
      None,
      statsEngine,
      new DefaultClock,
      fixtures.noAction,
      null,
    )

    val ProtocolComponentsRegistryMock: ProtocolComponentsRegistry = new ProtocolComponentsRegistry(
      testCoreComponents,
      Map.empty,
      componentsFactoryCache,
      defaultProtocolValueCache,
    )

//      new ProtocolComponentsRegistry(testCoreComponents, Map.empty, mutable.Map.empty, mutable.Map.empty) {
//        override def components[P, C](key: ProtocolKey[P, C]): C = protoComponents.asInstanceOf[C]
//      }

    val testContext = new ScenarioContext(
      testCoreComponents,
      ProtocolComponentsRegistryMock,
      Disabled,
      false,
    )

    def stop(): Unit = testActorSystem.close()
  }

  override protected def afterAll(): Unit = {
    fixtures.fakeEventLoop.shutdownGracefully()
    super.afterAll()
  }
}
