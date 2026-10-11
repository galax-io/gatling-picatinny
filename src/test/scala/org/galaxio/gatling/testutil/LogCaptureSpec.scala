package org.galaxio.gatling.testutil

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, TimeUnit}

/** Pins the guarantee that suites capturing a logger which production code obtained early rely on (#329): a capture window must
  * not open while SLF4J's one-time initialisation is still running.
  *
  * Why it matters. `LoggerFactory.getLogger` returns a real logback logger as soon as the initialising thread has flipped its
  * state to "successful", but that thread is not done: it then fixes up the `SubstituteLogger`s handed out earlier and replays
  * the events they queued, on ITS OWN thread. A production object keeps the logger it got during the initialisation for good
  * (`CookieParser` is an `object extends StrictLogging`), so a capture opened in that stretch queued the capturing thread's
  * event inside SLF4J and got it back after the window had closed, on a thread whose `ThreadLocal` slot is empty: the event was
  * lost and the assertion read "0 was not equal to 1".
  *
  * The initialising thread does all of that inside `synchronized (LoggerFactory.class)`, so holding that monitor is what
  * "initialisation in flight" looks like from outside; a stand-in thread holds it here. The unfixed helper never asks for it,
  * so its window opens at once and the test fails.
  *
  * The stretch itself is far too narrow to hit on demand through public API, which is why this pins the lock the fix waits on
  * rather than a timing coincidence.
  */
class LogCaptureSpec extends AnyWordSpec with Matchers {

  private val LoggerName = "org.galaxio.gatling.testutil.LogCaptureSpec"

  "LogCapture" should {

    "not open a capture window while SLF4J's initialisation is still in flight" in {
      // Initialise SLF4J in this JVM first, so the only thing that can hold the capture back is the monitor taken below.
      LoggerFactory.getILoggerFactory

      val initialiserHoldsMonitor = new CountDownLatch(1)
      val initialiserMayFinish    = new CountDownLatch(1)
      val windowOpened            = new CountDownLatch(1)
      val captured                = new AtomicReference(List.empty[String])

      val initialiser = new Thread(
        () =>
          classOf[LoggerFactory].synchronized {
            initialiserHoldsMonitor.countDown()
            initialiserMayFinish.await()
          },
        "slf4j-initialiser-stand-in",
      )
      val capturer    = new Thread(
        () =>
          captured.set(LogCapture.warns(LoggerName) {
            windowOpened.countDown()
            LoggerFactory.getLogger(LoggerName).warn("inside the window")
          }),
        "log-capturer",
      )
      // A stand-in that outlived a failed test would hold the monitor for the rest of the JVM: never let it block exit.
      initialiser.setDaemon(true)
      capturer.setDaemon(true)

      try {
        initialiser.start()
        initialiserHoldsMonitor.await()
        capturer.start()
        // A negative claim needs a bounded wait: the fixed helper cannot open the window while the monitor is held, so this
        // cannot fail on a slow machine; the unfixed one opens it within microseconds, so the wait ends at once.
        withClue("the capture window opened while SLF4J's initialisation was still in flight: ") {
          windowOpened.await(250, TimeUnit.MILLISECONDS) shouldBe false
        }
      } finally initialiserMayFinish.countDown()

      capturer.join(TimeUnit.SECONDS.toMillis(10))
      capturer.isAlive shouldBe false
      captured.get() shouldBe List("inside the window")
      initialiser.join()
    }
  }
}
