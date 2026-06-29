package org.galaxio.gatling.testutil

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/** Shared log capture for tests — safe under ScalaTest's PARALLEL suite execution.
  *
  * Capturing mutates the GLOBAL logback `LoggerContext` (a logger's level), which is shared across suites. Three independent
  * safeguards make it deterministic without serializing the whole test run:
  *
  *   1. `synchronized` — serializes the level save/set/restore + queue swap so two captures never corrupt each other's level
  *      restore (the part that genuinely needs mutual exclusion).
  *   2. attach-once appender — a recording appender is added to the target logger exactly ONCE (lazily, at the logger's resting
  *      level where foreign WARNs are suppressed) and NEVER detached. Detaching/attaching per capture mutates the logger's
  *      appender list, which can race against concurrent foreign dispatch through the same logger (logback's `COWArrayList`
  *      refreshes its cached snapshot non-atomically) and intermittently drop the capturing thread's own event — observed as a
  *      flaky "0 was not equal to 1". Keeping the appender attached means no list mutation ever happens while a foreign suite
  *      is logging through the subtree. Activity is toggled via a `ThreadLocal` instead: events are recorded only while a
  *      capture window is open on the calling thread.
  *   3. `ThreadLocal` capture queue — while a capture window is open, OTHER (non-capturing) suites running in parallel may
  *      still log to the same logger subtree on their own threads. Because each thread reads its OWN `ThreadLocal` slot,
  *      foreign threads see `null` and their events are silently dropped. This replaces the previous thread-name-equality
  *      check, which was fragile: thread names are not guaranteed unique across JVM pools and can be mutated by test runners
  *      at suite boundaries, causing spurious inclusions or exclusions.
  *
  * All log-capturing suites must go through this object so the guarantee holds.
  */
object LogCapture {

  /** A recording appender attached ONCE per logger and never detached. Events are forwarded to the calling thread's
    * [[activeQueue]] slot; foreign threads (those not holding the capture window) see `null` and are ignored. `append`
    * NEVER mutates the logger's appender list, so it can't race with concurrent foreign dispatch through the same logger
    * (see the object doc).
    */
  private final class RecordingAppender extends AppenderBase[ILoggingEvent] {
    override def append(event: ILoggingEvent): Unit = {
      val queue = activeQueue.get()
      if (queue != null) queue.add(event)
    }
  }

  /** Set only on the thread that currently holds the capture window; `null` on all other threads. Using a `ThreadLocal`
    * makes the capturing side-channel invisible to foreign threads by construction, without relying on thread names
    * (which are not guaranteed unique and can be mutated by test runners at suite boundaries).
    */
  private val activeQueue = new ThreadLocal[ConcurrentLinkedQueue[ILoggingEvent]]()

  /** One appender per logger name; all access is under `synchronized`, so a plain map suffices. */
  private val appenders = mutable.Map.empty[String, RecordingAppender]

  /** Capture the events `body` emits under `loggerName` at `level` (inclusive), on the calling thread only. */
  def events(loggerName: String, level: Level)(body: => Unit): List[ILoggingEvent] = synchronized {
    val logger = logbackLogger(loggerName)
    appenders.getOrElseUpdate(
      loggerName, {
        val recorder = new RecordingAppender
        recorder.start()
        logger.addAppender(recorder) // one-time attach at the resting level (foreign WARNs suppressed → no list race)
        recorder
      },
    )

    val captured      = new ConcurrentLinkedQueue[ILoggingEvent]()
    val previousLevel = logger.getLevel
    activeQueue.set(captured)
    logger.setLevel(level)
    try body
    finally {
      logger.setLevel(previousLevel)
      activeQueue.remove()
    }
    captured.asScala.toList
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
