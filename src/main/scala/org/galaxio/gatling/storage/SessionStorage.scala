package org.galaxio.gatling.storage

import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.core.session.Session
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

  /** Parse the raw Set-Cookie value from `setCookieField` and (a) set each cookie's name -> value as a session attribute
    * (backward-compatible with the original behavior) and (b) stash the name/value pairs under
    * [[SessionStorage.restoreCookiesAttr]] so the `foreach` in [[restoreCookies]] can register each cookie in the Gatling
    * cookie jar. No-op (only the empty stash is added) when the field is absent or not a String.
    */
  private[storage] def restoreCookiesIntoSession(setCookieField: String, domain: String)(session: Session): Session = {
    val parsed         = session.attributes.get(setCookieField) match {
      case Some(rawCookie: String) => CookieParser.parse(rawCookie, domain)
      case _                       => Seq.empty[ParsedCookie]
    }
    val withAttributes = parsed.foldLeft(session)((s, cookie) => s.set(cookie.name, cookie.value))
    withAttributes.set(
      SessionStorage.restoreCookiesAttr,
      parsed.map(c => Map("name" -> c.name, "value" -> c.value)).toList,
    )
  }

  /** Restore cookies captured in `setCookieField` into the Gatling cookie jar so they are auto-attached to subsequent requests
    * to `domain`. Cookies are registered via the supported public `addCookie` DSL (name/value at runtime, scoped to `domain`,
    * default path `/`); per-cookie path/max-age/secure/httpOnly are not propagated. Re-restoring a cookie of the same
    * name/domain overwrites the prior value (role switching). Each cookie's name -> value is also kept as a session attribute
    * for backward compatibility.
    */
  def restoreCookies(setCookieField: String, domain: String): ChainBuilder =
    exec(session => restoreCookiesIntoSession(setCookieField, domain)(session))
      .foreach(s"#{${SessionStorage.restoreCookiesAttr}}", "cookie") {
        exec(addCookie(Cookie("#{cookie.name}", "#{cookie.value}").withDomain(domain)))
      }
      // Drop the internal stash so it cannot leak into `saveAll`/`persist` records or downstream feeders.
      .exec(_.remove(SessionStorage.restoreCookiesAttr))

}

object SessionStorage {
  // Internal session key holding parsed name/value pairs for the cookie-restore `foreach` (collision-free).
  private[storage] val restoreCookiesAttr = "__picatinny_restore_cookies"

  def apply(): SessionStorage                        = new SessionStorage()
  def apply(backend: StorageBackend): SessionStorage = new SessionStorage(Some(backend))
}
