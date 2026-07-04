package org.galaxio.gatling.redis

import com.redis.RedisClientPool
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.{NoOpStatsEngine, StatsEngine}
import org.galaxio.gatling.transactions.{Mocks, fixtures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.ServerSocket
import scala.collection.mutable

class RedisActionSpec extends AnyWordSpec with Matchers with Mocks {

  "RedisAction.updateSessionWithResult" should {
    "store the redis value when a result is present" in {
      val session = fixtures.emptySession("redis-action")

      val updated = RedisAction.updateSessionWithResult(session, Some("redisValue"), Some("cached-token"))

      updated("redisValue").as[String] shouldBe "cached-token"
    }

    "leave the session unchanged when the redis result is missing" in {
      val session = fixtures.emptySession("redis-action")

      val updated = RedisAction.updateSessionWithResult(session, Some("redisValue"), None)

      updated.contains("redisValue") shouldBe false
    }

    "leave the session unchanged when nothing should be saved" in {
      val session = fixtures.emptySession("redis-action")

      val updated = RedisAction.updateSessionWithResult(session, None, Some("cached-token"))

      updated.attributes shouldBe session.attributes
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Failure branches of RedisAction.execute (#211) — DSL/action-component layer via the Mocks
  // harness. The happy path against a real Redis is covered by RedisIntegrationSpec (layer 3);
  // updateSessionWithResult success cases are unit-covered above.
  // ---------------------------------------------------------------------------------------------

  /** Ephemeral port that is bound, released, and then double-checked to actually refuse connections — this drives the real
    * crash branch of `execute` without stubbing the Redis client (a stub throwing on demand would assert the stub, not the
    * action). The post-release probe closes the port-reuse race: the OS may hand the just-released port to another process, in
    * which case we retry with a fresh one (review finding; suspected source of a rare suite flake).
    */
  private def closedPort(): Int = {
    def candidate(): Int                       = {
      val socket = new ServerSocket(0)
      try socket.getLocalPort
      finally socket.close()
    }
    def refusesConnections(port: Int): Boolean =
      try {
        new java.net.Socket("127.0.0.1", port).close()
        false // something answered — the port was re-bound, not refused
      } catch { case _: java.io.IOException => true }

    Iterator
      .continually(candidate())
      .take(5)
      .find(refusesConnections)
      .getOrElse(fail("could not obtain a connection-refused port after 5 attempts"))
  }

  /** Terminal action recording every session it receives — proves `next` still fires after a failure. */
  private final class ProbeAction extends ChainableAction {
    val received: mutable.Buffer[Session]                  = mutable.Buffer.empty
    override def next: Action                              = fixtures.noAction
    override def name: String                              = "probe"
    override protected def execute(session: Session): Unit = received += session
    override def statsEngine: StatsEngine                  = new NoOpStatsEngine
  }

  private def failingResolution: Session => Validation[RedisCommand] = _ => Failure("boom: no such attribute")

  private def refusedConnectionCommand: Session => Validation[RedisCommand] = _ => Success(RedisCommand.Strings.Get("k"))

  "RedisAction.execute on command-expression resolution failure" should {

    "record an exact KO response, mark the session failed and still advance the chain (stats enabled)" in new MockedGatlingCtx {
      val probe  = new ProbeAction
      val action = RedisAction(
        testContext,
        probe,
        new RedisClientPool("127.0.0.1", closedPort()),
        failingResolution,
        None,
        Some("redis-req"),
      )

      action.execute(fixtures.emptySession("redis"))

      probe.received should have size 1
      probe.received.head.isFailed shouldBe true
      val evts = getEvents
      evts should have size 1
      evts.head.evtType shouldBe "REQUEST"
      evts.head.name shouldBe "redis-req"
      evts.head.status shouldBe "KO"
      evts.head.errorMsg shouldBe Some("boom: no such attribute")
      stop()
    }

    "skip response stats but still fail the session and advance the chain (stats disabled)" in new MockedGatlingCtx {
      val probe  = new ProbeAction
      val action =
        RedisAction(testContext, probe, new RedisClientPool("127.0.0.1", closedPort()), failingResolution, None, None)

      action.execute(fixtures.emptySession("redis"))

      probe.received should have size 1
      probe.received.head.isFailed shouldBe true
      getEvents shouldBe empty // no reqName -> no logResponse and no crash (resolution failure is not a crash)
      stop()
    }
  }

  "RedisAction.execute on client crash (connection refused)" should {

    "record the KO response AND the crash, mark the session failed and advance (stats enabled)" in new MockedGatlingCtx {
      val probe  = new ProbeAction
      val action =
        RedisAction(
          testContext,
          probe,
          new RedisClientPool("127.0.0.1", closedPort()),
          refusedConnectionCommand,
          None,
          Some("redis-req"),
        )

      action.execute(fixtures.emptySession("redis"))

      probe.received should have size 1
      probe.received.head.isFailed shouldBe true
      val evts     = getEvents
      evts.map(_.evtType) shouldBe List("REQUEST", "ERROR")
      val response = evts.head
      response.name shouldBe "redis-req"
      response.status shouldBe "KO"
      val crash    = evts.last
      crash.name should startWith("redisAction")
      crash.errorMsg shouldBe defined
      stop()
    }

    "still record the crash (but no response stats) when stats are disabled" in new MockedGatlingCtx {
      val probe  = new ProbeAction
      val action =
        RedisAction(testContext, probe, new RedisClientPool("127.0.0.1", closedPort()), refusedConnectionCommand, None, None)

      action.execute(fixtures.emptySession("redis"))

      probe.received should have size 1
      probe.received.head.isFailed shouldBe true
      val evts = getEvents
      evts.map(_.evtType) shouldBe List("ERROR") // logRequestCrash fires regardless of reqName
      evts.head.name should startWith("redisAction")
      evts.head.errorMsg shouldBe defined
      stop()
    }
  }
}
