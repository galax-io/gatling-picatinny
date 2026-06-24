package org.galaxio.gatling.storage

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.AppenderBase
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

class CookieParserSpec extends AnyWordSpec with Matchers {

  /** Capture WARN messages under the `org.galaxio.gatling.storage` logger while `body` runs. Uses a thread-safe queue-backed
    * appender so events propagated from sibling storage classes (exercised by other suites running in parallel) cannot corrupt
    * the capture; results are content-filtered to CookieParser's own Max-Age warnings.
    */
  private def captureWarns(body: => Unit): List[String] = {
    val logger   = LoggerFactory.getLogger("org.galaxio.gatling.storage").asInstanceOf[LogbackLogger]
    val events   = new ConcurrentLinkedQueue[ILoggingEvent]()
    val appender = new AppenderBase[ILoggingEvent] {
      override def append(e: ILoggingEvent): Unit = events.add(e)
    }
    appender.start()
    val prev     = logger.getLevel
    logger.setLevel(Level.WARN)
    logger.addAppender(appender)
    try body
    finally {
      logger.detachAppender(appender)
      logger.setLevel(prev)
    }
    events.asScala.toList.filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)
  }

  "CookieParser" should {

    "parse simple name=value cookie" in {
      val result = CookieParser.parse("session_id=abc123", "example.com")
      result should have size 1
      result.head.name shouldBe "session_id"
      result.head.value shouldBe "abc123"
      result.head.domain shouldBe Some("example.com")
    }

    "parse cookie with attributes" in {
      val raw    = "token=xyz; Path=/api; Domain=.example.com; Max-Age=3600; Secure; HttpOnly"
      val result = CookieParser.parse(raw, "fallback.com")
      result should have size 1
      val c      = result.head
      c.name shouldBe "token"
      c.value shouldBe "xyz"
      c.domain shouldBe Some(".example.com")
      c.path shouldBe Some("/api")
      c.maxAge shouldBe Some(3600L)
      c.secure shouldBe true
      c.httpOnly shouldBe true
    }

    "parse multiple cookies separated by newlines" in {
      val raw    = "a=1; Path=/\nb=2; Secure"
      val result = CookieParser.parse(raw, "example.com")
      result should have size 2
      result.map(_.name) shouldBe Seq("a", "b")
    }

    "skip empty lines" in {
      val raw    = "a=1\n\nb=2"
      val result = CookieParser.parse(raw, "example.com")
      result should have size 2
    }

    "skip lines without = in name-value pair" in {
      val raw    = "malformed\na=1"
      val result = CookieParser.parse(raw, "example.com")
      result should have size 1
      result.head.name shouldBe "a"
    }

    "handle value containing =" in {
      val raw    = "token=abc=def=ghi; Path=/"
      val result = CookieParser.parse(raw, "example.com")
      result.head.value shouldBe "abc=def=ghi"
    }

    "use default domain when Domain attribute missing" in {
      val result = CookieParser.parse("x=1", "default.com")
      result.head.domain shouldBe Some("default.com")
    }

    // Issue #111: a present-but-unparseable Max-Age yields None AND a single WARN naming the value.
    // Assertions filter on the "Max-Age" marker so they stay deterministic even if other suites log
    // under the shared `org.galaxio.gatling.storage` logger during parallel execution.
    "warn and drop a non-numeric Max-Age" in {
      val warns = captureWarns {
        val result = CookieParser.parse("sid=x; Max-Age=abc", "example.com")
        result.head.maxAge shouldBe None
      }
      warns.count(w => w.contains("Max-Age") && w.contains("abc")) shouldBe 1
    }

    "warn and drop an empty Max-Age" in {
      val warns = captureWarns {
        val result = CookieParser.parse("sid=x; Max-Age=", "example.com")
        result.head.maxAge shouldBe None
      }
      warns.count(w => w.contains("Max-Age") && w.contains("value ''")) shouldBe 1
    }

    "warn and drop an overflowing Max-Age" in {
      val warns = captureWarns {
        val result = CookieParser.parse("sid=x; Max-Age=99999999999999999999", "example.com")
        result.head.maxAge shouldBe None
      }
      warns.count(_.contains("99999999999999999999")) shouldBe 1
    }

    "accept a valid Max-Age without warning" in {
      val warns = captureWarns {
        val result = CookieParser.parse("sid=x; Max-Age=3600", "example.com")
        result.head.maxAge shouldBe Some(3600L)
      }
      warns.count(_.contains("Max-Age")) shouldBe 0
    }

    "accept a negative Max-Age without warning" in {
      val warns = captureWarns {
        val result = CookieParser.parse("sid=x; Max-Age=-1", "example.com")
        result.head.maxAge shouldBe Some(-1L)
      }
      warns.count(_.contains("Max-Age")) shouldBe 0
    }

    "not warn when Max-Age is absent" in {
      val warns = captureWarns {
        val result = CookieParser.parse("sid=x; Path=/", "example.com")
        result.head.maxAge shouldBe None
      }
      warns.count(_.contains("Max-Age")) shouldBe 0
    }

  }

}
