package org.galaxio.gatling.storage

import io.gatling.core.feeder.Record
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

  }

  implicit class TestHelper(storage: SessionStorage) {
    def addRecord(record: Record[Any]): Unit = {
      val field = classOf[SessionStorage].getDeclaredField("records")
      field.setAccessible(true)
      field.get(storage).asInstanceOf[java.util.concurrent.ConcurrentLinkedQueue[Record[Any]]].add(record)
    }
  }

}
