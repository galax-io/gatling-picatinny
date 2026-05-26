package org.galaxio.gatling.storage

import io.gatling.core.feeder.Record
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.writePretty

import java.nio.file.{Files, Paths}
import java.sql.{Connection, DriverManager}
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Using

private[storage] trait RedisClientLike {
  def set(key: String, value: String): Boolean
  def get(key: String): Option[String]
  def del(key: String): Option[Long]
  def disconnect(): Unit
}

private[storage] object RedisClientLike {
  def from(client: com.redis.RedisClient): RedisClientLike = new RedisClientLike {
    override def set(key: String, value: String): Boolean = client.set(key, value)
    override def get(key: String): Option[String]         = client.get(key)
    override def del(key: String): Option[Long]           = client.del(key)
    override def disconnect(): Unit                       = client.disconnect
  }
}

trait StorageBackend {
  def save(records: Seq[Record[Any]]): Unit
  def load(): Seq[Record[Any]]
  def clear(): Unit
}

final class StorageWriteException(message: String) extends RuntimeException(message)

final case class JsonFileBackend(filePath: String) extends StorageBackend {
  private implicit val formats: Formats = DefaultFormats

  override def save(records: Seq[Record[Any]]): Unit =
    Files.writeString(Paths.get(filePath), writePretty(records))

  override def load(): Seq[Record[Any]] = {
    val path = Paths.get(filePath)
    if (Files.exists(path))
      parse(Files.readString(path)).extract[List[Map[String, Any]]]
    else
      Seq.empty
  }

  override def clear(): Unit = Files.deleteIfExists(Paths.get(filePath))
}

final case class RedisBackend(
    host: String,
    port: Int = 6379,
    storageKey: String = "gatling:auth:storage",
) extends StorageBackend {
  import com.redis.RedisClient
  private implicit val formats: Formats = DefaultFormats

  private def withClient[T](f: RedisClientLike => T): T = {
    val client  = new RedisClient(host, port)
    val adapter = RedisClientLike.from(client)
    try f(adapter)
    finally adapter.disconnect()
  }

  override def save(records: Seq[Record[Any]]): Unit = {
    withClient(saveRecords(_, records))
  }

  override def load(): Seq[Record[Any]] = {
    withClient(loadRecords)
  }

  override def clear(): Unit = {
    withClient(clearRecords)
  }

  private[storage] def saveRecords(client: RedisClientLike, records: Seq[Record[Any]]): Unit = {
    if (!client.set(storageKey, writePretty(records)))
      throw new StorageWriteException(s"Failed to persist session storage to Redis key '$storageKey'")
  }

  private[storage] def loadRecords(client: RedisClientLike): Seq[Record[Any]] =
    client.get(storageKey) match {
      case Some(json) => parse(json).extract[List[Map[String, Any]]]
      case None       => Seq.empty
    }

  private[storage] def clearRecords(client: RedisClientLike): Unit = {
    if (client.del(storageKey).isEmpty)
      throw new StorageWriteException(s"Failed to clear session storage from Redis key '$storageKey'")
  }
}

final case class JdbcStorageBackend(
    jdbcUrl: String,
    tableName: String = "gatling_session_storage",
    username: String = "",
    password: String = "",
) extends StorageBackend {
  private implicit val formats: Formats = DefaultFormats
  private val tableInitialized          = new AtomicBoolean(false)
  private val tableInitLock             = new AnyRef

  private def withConnection[T](f: Connection => T): T = {
    val conn =
      if (username.nonEmpty) DriverManager.getConnection(jdbcUrl, username, password)
      else DriverManager.getConnection(jdbcUrl)
    try f(conn)
    finally conn.close()
  }

  private def ensureTable(conn: Connection): Unit = {
    if (!tableInitialized.get)
      tableInitLock.synchronized {
        if (!tableInitialized.get) {
          initTable(conn)
          tableInitialized.set(true)
        }
      }
  }

  private def initTable(conn: Connection): Unit = {
    Using.resource(conn.createStatement()) { stmt =>
      stmt.execute(
        s"""CREATE TABLE IF NOT EXISTS $tableName (
           |  id BIGINT AUTO_INCREMENT PRIMARY KEY,
           |  record_data TEXT NOT NULL,
           |  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
           |)""".stripMargin,
      )
    }
  }

  override def save(records: Seq[Record[Any]]): Unit = withConnection { conn =>
    ensureTable(conn)
    Using.resource(conn.prepareStatement(s"INSERT INTO $tableName (record_data) VALUES (?)")) { ps =>
      records.foreach { record =>
        ps.setString(1, writePretty(record))
        ps.addBatch()
      }
      ps.executeBatch()
    }
  }

  override def load(): Seq[Record[Any]] = withConnection { conn =>
    ensureTable(conn)
    Using.resource(conn.createStatement()) { stmt =>
      Using.resource(stmt.executeQuery(s"SELECT record_data FROM $tableName ORDER BY id")) { rs =>
        val builder = Seq.newBuilder[Record[Any]]
        while (rs.next())
          builder += parse(rs.getString("record_data")).extract[Map[String, Any]]
        builder.result()
      }
    }
  }

  override def clear(): Unit = withConnection { conn =>
    ensureTable(conn)
    Using.resource(conn.createStatement()) { stmt =>
      stmt.execute(s"DELETE FROM $tableName")
    }
  }
}
