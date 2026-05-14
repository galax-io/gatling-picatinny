package org.galaxio.gatling.storage

import io.gatling.core.feeder.Record
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.writePretty

import java.nio.file.{Files, Paths}
import java.sql.{Connection, DriverManager}

trait StorageBackend {
  def save(records: Seq[Record[Any]]): Unit
  def load(): Seq[Record[Any]]
  def clear(): Unit
}

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

  override def save(records: Seq[Record[Any]]): Unit = {
    val client = new RedisClient(host, port)
    try client.set(storageKey, writePretty(records))
    finally client.disconnect
  }

  override def load(): Seq[Record[Any]] = {
    val client = new RedisClient(host, port)
    try
      client.get(storageKey) match {
        case Some(json) => parse(json).extract[List[Map[String, Any]]]
        case None       => Seq.empty
      }
    finally client.disconnect
  }

  override def clear(): Unit = {
    val client = new RedisClient(host, port)
    try client.del(storageKey)
    finally client.disconnect
  }
}

final case class JdbcStorageBackend(
    jdbcUrl: String,
    tableName: String = "gatling_session_storage",
    username: String = "",
    password: String = "",
) extends StorageBackend {
  private implicit val formats: Formats = DefaultFormats

  private def withConnection[T](f: Connection => T): T = {
    val conn =
      if (username.nonEmpty) DriverManager.getConnection(jdbcUrl, username, password)
      else DriverManager.getConnection(jdbcUrl)
    try f(conn)
    finally conn.close()
  }

  private def ensureTable(conn: Connection): Unit = {
    val stmt = conn.createStatement()
    try
      stmt.execute(
        s"""CREATE TABLE IF NOT EXISTS $tableName (
         |  id BIGINT AUTO_INCREMENT PRIMARY KEY,
         |  record_data TEXT NOT NULL,
         |  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
         |)""".stripMargin,
      )
    finally stmt.close()
  }

  override def save(records: Seq[Record[Any]]): Unit = withConnection { conn =>
    ensureTable(conn)
    val ps = conn.prepareStatement(s"INSERT INTO $tableName (record_data) VALUES (?)")
    try {
      records.foreach { record =>
        ps.setString(1, writePretty(record))
        ps.addBatch()
      }
      ps.executeBatch()
    } finally ps.close()
  }

  override def load(): Seq[Record[Any]] = withConnection { conn =>
    ensureTable(conn)
    val stmt = conn.createStatement()
    try {
      val rs      = stmt.executeQuery(s"SELECT record_data FROM $tableName ORDER BY id")
      val builder = Seq.newBuilder[Record[Any]]
      while (rs.next())
        builder += parse(rs.getString("record_data")).extract[Map[String, Any]]
      builder.result()
    } finally stmt.close()
  }

  override def clear(): Unit = withConnection { conn =>
    val stmt = conn.createStatement()
    try stmt.execute(s"DELETE FROM $tableName")
    finally stmt.close()
  }
}
