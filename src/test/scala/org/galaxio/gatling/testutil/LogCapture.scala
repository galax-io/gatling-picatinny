package org.galaxio.gatling.testutil

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

/** Shared log capture for tests — safe under ScalaTest's PARALLEL suite execution.
  *
  * Capturing mutates the GLOBAL logback `LoggerContext` (a logger's level + attached appenders), which is shared across suites.
  * Two independent safeguards make it deterministic without serializing the whole test run:
  *
  *   1. `synchronized` — serializes the level save/set/restore + appender attach/detach so two captures never corrupt each
  *      other's level restore (the part that genuinely needs mutual exclusion).
  *   2. thread-name filter + a thread-safe queue — while a capture window is open, OTHER (non-capturing) suites running in
  *      parallel may still log to the same logger on their own threads. The appender is backed by a `ConcurrentLinkedQueue` (so
  *      concurrent foreign appends can't corrupt it), and the result keeps only events emitted on the capturing thread (the
  *      body logs synchronously on the caller thread), discarding foreign noise.
  *
  * All log-capturing suites must go through this object so the guarantee holds.
  */
object LogCapture {

  /** Capture the events `body` emits under `loggerName` at `level` (inclusive), on the calling thread only. */
  def events(loggerName: String, level: Level)(body: => Unit): List[ILoggingEvent] = synchronized {
    val logger        = logbackLogger(loggerName)
    val captured      = new ConcurrentLinkedQueue[ILoggingEvent]()
    val appender      = new AppenderBase[ILoggingEvent] {
      override def append(event: ILoggingEvent): Unit = captured.add(event)
    }
    appender.start()
    val captureThread = Thread.currentThread().getName
    val previousLevel = logger.getLevel
    logger.setLevel(level)
    logger.addAppender(appender)
    try body
    finally {
      logger.detachAppender(appender)
      logger.setLevel(previousLevel)
    }
    captured.asScala.iterator.filter(_.getThreadName == captureThread).toList
  }

  /** Capture INFO+ events (raw, for asserting logger name / single-event / message structure). */
  def infoEvents(loggerName: String)(body: => Unit): List[ILoggingEvent] =
    events(loggerName, Level.INFO)(body)

  /** Capture WARN messages (formatted) emitted under `loggerName`. */
  def warns(loggerName: String)(body: => Unit): List[String] =
    events(loggerName, Level.WARN)(body).filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)

  /** Resolve the logback `Logger`, retrying past SLF4J's initialization window.
    *
    * Under parallel suite STARTUP, `LoggerFactory.getLogger` returns a temporary `org.slf4j.helpers.SubstituteLogger` until
    * logback finishes binding — casting that to `ch.qos.logback.classic.Logger` throws `ClassCastException`. The first real
    * call triggers binding; we retry briefly until the backend is installed (bounded so a genuine misbind still surfaces a
    * clear cast error rather than hanging).
    */
  @annotation.tailrec
  private def logbackLogger(loggerName: String, attemptsLeft: Int = 2000): LogbackLogger =
    LoggerFactory.getLogger(loggerName) match {
      case logback: LogbackLogger => logback
      case _ if attemptsLeft > 0  => Thread.sleep(1); logbackLogger(loggerName, attemptsLeft - 1)
      case other                  => other.asInstanceOf[LogbackLogger] // exhausted → surface a clear cast error
    }
}
