package org.galaxio.gatling.testutil

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

/** The two sides of `LocaleFixture`'s lock: `withLocale` windows are visible to every thread, so a reader whose expectation is
  * only valid under the ambient locale must hold the same lock (`withStableDefault`) or a window opened by another suite can
  * land between two reads of the default.
  */
class LocaleFixtureSpec extends AnyWordSpec with Matchers {

  private def daemon(body: => Unit): Thread = {
    val thread = new Thread(() => body)
    thread.setDaemon(true)
    thread
  }

  /** Bounded poll until `thread` is queued on a monitor or has finished, so a state assertion cannot race its start. */
  private def awaitBlockedOrDone(thread: Thread): Unit = {
    val deadline = System.nanoTime() + SECONDS.toNanos(5)
    while (thread.isAlive && thread.getState != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.onSpinWait()
  }

  "a withLocale window" should {

    "be visible to a reader on another thread, which is why readers must hold withStableDefault" in {
      val opened  = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val writer  = daemon {
        LocaleFixture.withTurkish {
          opened.countDown()
          release.await()
        }
      }
      writer.start()
      try {
        opened.await(5, SECONDS) shouldBe true
        Locale.getDefault shouldBe LocaleFixture.Turkish
      } finally {
        release.countDown()
        writer.join()
      }
    }
  }

  "withStableDefault" should {

    "hold off a window requested from another thread until the body returns" in {
      val windowRan = new AtomicBoolean(false)
      val intruder  = daemon(LocaleFixture.withTurkish(windowRan.set(true)))

      val (before, during, intruderState, ranDuring) = LocaleFixture.withStableDefault {
        val before = Locale.getDefault
        intruder.start()
        awaitBlockedOrDone(intruder)
        (before, Locale.getDefault, intruder.getState, windowRan.get)
      }

      withClue("the window must be queued behind the body, not already open: ") {
        intruderState shouldBe Thread.State.BLOCKED
        ranDuring shouldBe false
        during shouldBe before
      }
      intruder.join(SECONDS.toMillis(5))
      withClue("the window is deferred, not dropped: ") {
        windowRan.get shouldBe true
      }
    }

    "release the lock when the body throws" in {
      an[IllegalStateException] should be thrownBy LocaleFixture.withStableDefault[Unit](
        throw new IllegalStateException("boom"),
      )

      val window = daemon(LocaleFixture.withTurkish(()))
      window.start()
      window.join(SECONDS.toMillis(5))
      window.isAlive shouldBe false
    }
  }
}
