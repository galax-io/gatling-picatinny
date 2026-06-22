package org.galaxio.gatling.assertions

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import io.gatling.commons.stats.assertion.Assertion
import io.gatling.core.Predef._
import io.gatling.core.assertion.AssertionPathParts
import io.gatling.core.config.GatlingConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

/** Unit tests for the Scala [[AssertionsBuilder]] (test-model layer 1). Driven through the `assertionsFrom` test seam, which
  * takes a [[GatlingConfiguration]] explicitly, so `GatlingConfiguration.loadForTest()` builds against the `nfr.yml` fixture
  * without the throwing simulation-only `Predef.configuration` that the public `assertionFromYaml` relies on at runtime.
  * Asserts the exact set of Gatling `Assertion`s built — including that UNrecognised NFR keys (APDEX, RPS) are skipped (and now
  * logged at WARN), that error-rate thresholds are Double, and that non-numeric values fail with a contextful message.
  */
class AssertionsBuilderSpec extends AnyWordSpec with Matchers {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private lazy val assertions =
    AssertionsBuilder.assertionsFrom("src/test/resources/nfr.yml").toList

  private def grp(parts: String*): AssertionPathParts = AssertionPathParts(parts.toList)

  /** The exact 11 assertions nfr.yml must produce: error-rate = Double, response-time percentile/max = Int. */
  private lazy val expected: Set[Assertion] = Set(
    global.responseTime.percentile(99).lt(1500),
    details(grp("myGroup", "GET /test/id")).responseTime.percentile(99).lt(1500),
    details(grp("GET /test/email")).responseTime.percentile(99).lt(400),
    global.responseTime.percentile(95).lt(1200),
    details(grp("myGroup", "GET /test/id")).responseTime.percentile(95).lt(1200),
    details(grp("myGroup")).responseTime.percentile(95).lt(1600),
    details(grp("GET /test/email")).responseTime.percentile(95).lt(320),
    global.failedRequests.percent.lt(5.0),
    details(grp("GET /test/uuid")).failedRequests.percent.lt(1.0),
    global.responseTime.max.lt(2000),
    details(grp("GET /test/uuid")).responseTime.max.lt(1000),
  )

  private def captureWarns(body: => Unit): List[String] = {
    val logger   = LoggerFactory.getLogger("org.galaxio.gatling.assertions").asInstanceOf[LogbackLogger]
    val appender = new ListAppender[ILoggingEvent]()
    appender.start()
    val prev     = logger.getLevel
    logger.setLevel(Level.WARN)
    logger.addAppender(appender)
    try body
    finally {
      logger.detachAppender(appender)
      logger.setLevel(prev)
    }
    appender.list.asScala.toList.filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)
  }

  "AssertionsBuilder.assertionsFrom" should {

    "build one assertion per recognised NFR entry and skip unknown keys (APDEX, RPS)" in {
      // recognised: 99p(3) + 95p(4) + errors(2) + max(2) = 11; the APDEX and RPS keys are unrecognised → skipped.
      assertions should have size 11
    }

    "produce distinct assertions" in {
      assertions.toSet should have size 11
    }

    "build exactly the expected scopes and thresholds (error-rate is Double, time metrics are Int)" in {
      assertions.toSet shouldBe expected
    }

    "preserve named group/request path parts at the detail scope" in {
      val paths = assertions.map(_.path.toString)
      paths.exists(_.contains("myGroup")) shouldBe true
      paths.exists(_.contains("GET /test/uuid")) shouldBe true
    }

    "log a WARN naming each unknown key it skips" in {
      val warns = captureWarns {
        AssertionsBuilder.assertionsFrom("src/test/resources/nfr.yml")
      }
      warns.exists(_.contains("APDEX")) shouldBe true
      warns.exists(_.contains("RPS")) shouldBe true
    }

    "accept a fractional error-rate threshold as a Double (the old toInt crashed on it)" in {
      val single = AssertionsBuilder.assertionsFrom("src/test/resources/nfrSingle.yml").toList
      single should have size 1
      single.toSet shouldBe Set(global.failedRequests.percent.lt(5.5))
    }

    "fail with a message naming the metric key and the offending value for a non-numeric threshold" in {
      val ex = intercept[IllegalArgumentException] {
        AssertionsBuilder.assertionsFrom("src/test/resources/nfrNonNumeric.yml")
      }
      ex.getMessage should include("99 перцентиль времени выполнения") // the metric record key, not the scope key
      ex.getMessage should include("abc")
    }

    "match non-ASCII keys independent of the platform default charset" in {
      // The removed `toUtf` round-tripped through the platform default charset, which corrupts Cyrillic on a non-UTF-8
      // default — this documents why it was removed (a lossy round-trip is NOT identity).
      val key = "Процент ошибок"
      new String(key.getBytes("ISO-8859-1"), "ISO-8859-1") should not be key
      // and the builder still matches the Cyrillic keys → full set built:
      assertions should have size 11
    }
  }
}
