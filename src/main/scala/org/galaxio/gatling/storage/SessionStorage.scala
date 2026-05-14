package org.galaxio.gatling.storage

import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

final class SessionStorage(
    val backend: Option[StorageBackend] = None,
) {

  private val records = new ConcurrentLinkedQueue[Record[Any]]()

  def withBackend(b: StorageBackend): SessionStorage = {
    val storage = new SessionStorage(Some(b))
    records.forEach(r => storage.records.add(r))
    storage
  }

  // --- Write: session → storage ---

  def save(keys: String*): ChainBuilder = exec { session =>
    val record: Record[Any] = keys.flatMap { key =>
      session.attributes.get(key).map(key -> _)
    }.toMap
    if (record.nonEmpty) records.add(record)
    session
  }

  def saveAll: ChainBuilder = exec { session =>
    val record = session.attributes
    if (record.nonEmpty) records.add(record)
    session
  }

  // --- Read: storage → feeder ---

  def toFeeder: IndexedSeq[Record[Any]] = records.asScala.toIndexedSeq

  def size: Int = records.size()

  def clear(): Unit = records.clear()

  // --- Persistence: storage ↔ backend ---

  def persist(): ChainBuilder = exec { session =>
    backend.foreach(_.save(records.asScala.toSeq))
    session
  }

  def load(): SessionStorage = {
    backend.foreach(_.load().foreach(records.add))
    this
  }

  // --- Cookie injection ---

  def restoreCookies(setCookieField: String, domain: String): ChainBuilder = exec { session =>
    session.attributes.get(setCookieField) match {
      case Some(rawCookie: String) =>
        val parsed = CookieParser.parse(rawCookie, domain)
        parsed.foldLeft(session) { (s, cookie) =>
          s.set(cookie.name, cookie.value)
        }
      case _                       => session
    }
  }

}

object SessionStorage {
  def apply(): SessionStorage                        = new SessionStorage()
  def apply(backend: StorageBackend): SessionStorage = new SessionStorage(Some(backend))
}
