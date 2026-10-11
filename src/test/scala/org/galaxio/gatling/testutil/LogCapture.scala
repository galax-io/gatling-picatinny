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
  * Capturing mutates the GLOBAL logback `LoggerContext` (a logger's level), which is shared across suites. Four independent
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
  *      check, which was fragile: thread names are not guaranteed unique across JVM pools and can be mutated by test runners at
  *      suite boundaries, causing spurious inclusions or exclusions. Nested captures on the same thread (a capture whose `body`
  *      opens another capture) save/restore the slot rather than clearing it, so the outer window keeps recording once the
  *      inner one closes.
  *   4. SLF4J-initialisation barrier — `LoggerFactory.getLogger` hands out a real logback logger as soon as SLF4J's one-time
  *      initialisation has flipped to "successful", but the initialising thread is not done: it then fixes up the
  *      `SubstituteLogger`s handed out earlier and replays what they queued, on its own thread. Production objects keep the
  *      logger they obtained in that stretch for good (`CookieParser` is an `object extends StrictLogging`), so a window opened
  *      inside it queued the capturing thread's event inside SLF4J and got it back after the window had closed, on a thread
  *      whose `ThreadLocal` slot is empty — lost, observed as a flaky "0 was not equal to 1" (#329). The capture resolves its
  *      logger under the `LoggerFactory` class monitor, which the initialiser holds until it is completely done, so a window
  *      never opens on a half-initialised SLF4J. That, not the one-time attach in safeguard 2, was the cause: the capturing
  *      thread attaches the appender itself and so always sees its own appender list.
  *
  * All log-capturing suites must go through this object so the guarantee holds.
  */
object LogCapture {

  /** A recording appender attached ONCE per logger and never detached. Events are forwarded to the calling thread's
    * [[activeQueue]] slot; foreign threads (those not holding the capture window) see `null` and are ignored. `append` NEVER
    * mutates the logger's appender list, so it can't race with concurrent foreign dispatch through the same logger (see the
    * object doc).
    */
  private final class RecordingAppender extends AppenderBase[ILoggingEvent] {
    override def append(event: ILoggingEvent): Unit = {
      val queue = activeQueue.get()
      Option(queue).foreach(_.add(event))
    }
  }

  /** Set only on the thread that currently holds the capture window; `null` on all other threads. Using a `ThreadLocal` makes
    * the capturing side-channel invisible to foreign threads by construction, without relying on thread names (which are not
    * guaranteed unique and can be mutated by test runners at suite boundaries).
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
    val previousQueue = activeQueue.get()
    activeQueue.set(captured)
    logger.setLevel(level)
    try body
    finally {
      logger.setLevel(previousLevel)
      Option(previousQueue).fold(activeQueue.remove())(activeQueue.set)
    }
    captured.asScala.toList
  }

  /** Capture INFO+ events (raw, for asserting logger name / single-event / message structure). */
  def infoEvents(loggerName: String)(body: => Unit): List[ILoggingEvent] =
    events(loggerName, Level.INFO)(body)

  /** Capture WARN messages (formatted) emitted under `loggerName`. */
  def warns(loggerName: String)(body: => Unit): List[String] =
    events(loggerName, Level.WARN)(body).filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)

  /** Resolve the logback `Logger` once SLF4J's one-time initialisation has COMPLETELY finished (safeguard 4 in the object doc).
    *
    * Under parallel suite STARTUP, `LoggerFactory.getLogger` returns a temporary `org.slf4j.helpers.SubstituteLogger` while
    * another thread initialises SLF4J — casting that to `ch.qos.logback.classic.Logger` throws `ClassCastException` — and, once
    * the state has flipped, a real logger while that thread is still fixing up and replaying. Polling for the real logger waits
    * for the flip only; taking the `LoggerFactory` class monitor waits for the end. The initialiser holds it for all of
    * `performInitialization`, so this returns after the fix-up and replay and not before. If nobody has initialised SLF4J yet,
    * `getLogger` does it right here under the same (reentrant) monitor, so there is nothing to poll for either way. A genuine
    * misbind still surfaces as a clear cast error rather than a hang.
    */
  private def logbackLogger(loggerName: String): LogbackLogger =
    classOf[LoggerFactory].synchronized(LoggerFactory.getLogger(loggerName)) match {
      case logback: LogbackLogger => logback
      case other                  =>
        other
          // Justification: deliberate: a non-logback binding surfaces a clear cast error naming the foreign logger binding
          .asInstanceOf[LogbackLogger] // scalafix:ok DisableSyntax.asInstanceOf
    }
}
