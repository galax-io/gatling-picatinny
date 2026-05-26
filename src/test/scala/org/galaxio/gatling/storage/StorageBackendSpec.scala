package org.galaxio.gatling.storage

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

class StorageBackendSpec extends AnyWordSpec with Matchers {

  "JsonFileBackend" should {

    "save and load records" in {
      val tmpFile = java.io.File.createTempFile("backend-test", ".json")
      tmpFile.deleteOnExit()
      val backend = JsonFileBackend(tmpFile.getAbsolutePath)

      val records = Seq(
        Map[String, Any]("user" -> "alice", "token" -> "t1"),
        Map[String, Any]("user" -> "bob", "token"   -> "t2"),
      )
      backend.save(records)

      val loaded = backend.load()
      loaded should have size 2
      loaded(0)("user") shouldBe "alice"
      loaded(1)("token") shouldBe "t2"
    }

    "return empty seq for missing file" in {
      val backend = JsonFileBackend("/tmp/nonexistent-test-file-12345.json")
      backend.load() shouldBe empty
    }

    "clear deletes the file" in {
      val tmpFile = java.io.File.createTempFile("backend-clear", ".json")
      tmpFile.deleteOnExit()
      val backend = JsonFileBackend(tmpFile.getAbsolutePath)
      backend.save(Seq(Map[String, Any]("a" -> "1")))
      Files.exists(tmpFile.toPath) shouldBe true

      backend.clear()
      Files.exists(tmpFile.toPath) shouldBe false
    }

    "overwrite existing file on save" in {
      val tmpFile = java.io.File.createTempFile("backend-overwrite", ".json")
      tmpFile.deleteOnExit()
      val backend = JsonFileBackend(tmpFile.getAbsolutePath)

      backend.save(Seq(Map[String, Any]("v" -> "1")))
      backend.save(Seq(Map[String, Any]("v" -> "2"), Map[String, Any]("v" -> "3")))

      val loaded = backend.load()
      loaded should have size 2
      loaded(0)("v") shouldBe "2"
    }

  }

  "RedisBackend" should {

    "fail when redis rejects a write" in {
      val backend = RedisBackend("127.0.0.1", 6379, "test:storage:backend")
      val client  = new RedisClientLike {
        override def set(key: String, value: String): Boolean = false
        override def get(key: String): Option[String]         = None
        override def del(key: String): Option[Long]           = Some(0L)
        override def disconnect(): Unit                       = ()
      }

      val thrown = intercept[StorageWriteException] {
        backend.saveRecords(client, Seq(Map[String, Any]("user" -> "alice")))
      }

      thrown.getMessage should include("test:storage:backend")
    }

    "fail when redis clear cannot be persisted" in {
      val backend = RedisBackend("127.0.0.1", 6379, "test:storage:backend")
      val client  = new RedisClientLike {
        override def set(key: String, value: String): Boolean = true
        override def get(key: String): Option[String]         = None
        override def del(key: String): Option[Long]           = None
        override def disconnect(): Unit                       = ()
      }

      val thrown = intercept[StorageWriteException] {
        backend.clearRecords(client)
      }

      thrown.getMessage should include("test:storage:backend")
    }
  }

}
