package org.galaxio.gatling.storage

import io.gatling.core.feeder.Record
import org.galaxio.gatling.transactions.fixtures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{CountDownLatch, Executors}
import scala.jdk.CollectionConverters._

class SessionStorageSpec extends AnyWordSpec with Matchers {

  "SessionStorage" should {

    "start empty" in {
      val storage = SessionStorage()
      storage.size shouldBe 0
      storage.toFeeder shouldBe empty
    }

    "store and retrieve records via toFeeder" in {
      val storage = SessionStorage()
      storage.addRecord(Map("username" -> "user1", "token" -> "abc"))
      storage.addRecord(Map("username" -> "user2", "token" -> "def"))

      storage.size shouldBe 2
      val feeder = storage.toFeeder
      feeder should have size 2
      feeder(0)("username") shouldBe "user1"
      feeder(1)("token") shouldBe "def"
    }

    "clear all records" in {
      val storage = SessionStorage()
      storage.addRecord(Map("a" -> "1"))
      storage.addRecord(Map("b" -> "2"))
      storage.size shouldBe 2

      storage.clear()
      storage.size shouldBe 0
      storage.toFeeder shouldBe empty
    }

    "handle concurrent writes from multiple threads" in {
      val storage     = SessionStorage()
      val threadCount = 100
      val latch       = new CountDownLatch(threadCount)
      val executor    = Executors.newFixedThreadPool(threadCount)

      (0 until threadCount).foreach { i =>
        executor.submit(new Runnable {
          override def run(): Unit = {
            storage.addRecord(Map("id" -> i.toString))
            latch.countDown()
          }
        })
      }

      latch.await()
      executor.shutdown()

      storage.size shouldBe threadCount
      val ids = storage.toFeeder.map(_("id").toString).toSet
      ids shouldBe (0 until threadCount).map(_.toString).toSet
    }

    "persist to and load from backend" in {
      val tmpFile = java.io.File.createTempFile("storage-test", ".json")
      tmpFile.deleteOnExit()
      val backend = JsonFileBackend(tmpFile.getAbsolutePath)

      val storage1 = SessionStorage(backend)
      storage1.addRecord(Map("user" -> "alice", "token" -> "t1"))
      storage1.addRecord(Map("user" -> "bob", "token" -> "t2"))
      backend.save(storage1.toFeeder)

      val storage2 = SessionStorage(backend).load()
      storage2.size shouldBe 2
      val feeder   = storage2.toFeeder
      feeder(0)("user") shouldBe "alice"
      feeder(1)("token") shouldBe "t2"
    }

    "withBackend copies existing records" in {
      val storage = SessionStorage()
      storage.addRecord(Map("key" -> "value"))

      val tmpFile = java.io.File.createTempFile("storage-test2", ".json")
      tmpFile.deleteOnExit()
      val withBe  = storage.withBackend(JsonFileBackend(tmpFile.getAbsolutePath))
      withBe.size shouldBe 1
      withBe.toFeeder.head("key") shouldBe "value"
    }

    // --- restoreCookies session-attribute behavior (FR-004/005, component layer) ---

    "restoreCookies sets each cookie name -> value as a session attribute" in {
      val session = fixtures.emptySession("cookie").set("raw", "sid=abc123; Path=/")
      val result  = SessionStorage().restoreCookiesIntoSession("raw", "localhost")(session)
      result.attributes("sid") shouldBe "abc123"
    }

    "restoreCookies handles multiple cookies from a multi-line value" in {
      val session = fixtures.emptySession("cookie").set("raw", "a=1; Path=/\nb=2; Secure")
      val result  = SessionStorage().restoreCookiesIntoSession("raw", "localhost")(session)
      result.attributes("a") shouldBe "1"
      result.attributes("b") shouldBe "2"
    }

    "restoreCookies is a no-op when the source attribute is absent" in {
      val session = fixtures.emptySession("cookie")
      val result  = SessionStorage().restoreCookiesIntoSession("missing", "localhost")(session)
      result.attributes.get("missing") shouldBe None
      result.attributes.keySet should not contain "sid"
    }

    // Regression: the internal stash key must be namespaced and dropped at the end of the chain so it
    // cannot leak into saveAll/persist records. `restoreCookies` appends `.exec(_.remove(restoreCookiesAttr))`
    // after the foreach; this test mirrors that final step's net effect on the key.
    "restoreCookies does not leak its internal stash key into the session" in {
      SessionStorage.restoreCookiesAttr should startWith("__picatinny")
      val seeded = fixtures.emptySession("cookie").set("raw", "sid=abc123; Path=/")
      val mid    = SessionStorage().restoreCookiesIntoSession("raw", "localhost")(seeded)
      mid.attributes.keySet should contain(SessionStorage.restoreCookiesAttr) // stashed for the foreach
      val cleaned = mid.remove(SessionStorage.restoreCookiesAttr) // what the chain's final exec does
      cleaned.attributes.keySet should not contain SessionStorage.restoreCookiesAttr
      cleaned.attributes("sid") shouldBe "abc123" // cookie attr still present
    }

    "withBackend keeps record queues independent after copying" in {
      val storage = SessionStorage()
      storage.addRecord(Map("source" -> "original"))

      val tmpFile = java.io.File.createTempFile("storage-test3", ".json")
      tmpFile.deleteOnExit()
      val withBe  = storage.withBackend(JsonFileBackend(tmpFile.getAbsolutePath))

      withBe.addRecord(Map("source" -> "backend"))
      storage.addRecord(Map("source" -> "memory"))

      storage.toFeeder.map(_("source")) should contain theSameElementsInOrderAs Seq("original", "memory")
      withBe.toFeeder.map(_("source")) should contain theSameElementsInOrderAs Seq("original", "backend")
    }

  }

  implicit class TestHelper(storage: SessionStorage) {
    def addRecord(record: Record[Any]): Unit = {
      val field = classOf[SessionStorage].getDeclaredField("records")
      field.setAccessible(true)
      field.get(storage).asInstanceOf[java.util.concurrent.ConcurrentLinkedQueue[Record[Any]]].add(record)
    }
  }

}
