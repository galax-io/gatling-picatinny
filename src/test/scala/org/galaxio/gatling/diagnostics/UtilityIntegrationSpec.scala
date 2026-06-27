package org.galaxio.gatling.diagnostics

import org.galaxio.gatling.testutil.LogCapture
import org.galaxio.gatling.utils.Utility
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UtilityIntegrationSpec extends AnyWordSpec with Matchers {

  "Utility.banner" should {
    "emit the startup banner through SLF4J (not stdout) as a single event" in {
      val stdout = new java.io.ByteArrayOutputStream()
      val events = LogCapture.infoEvents("org.galaxio.gatling.diagnostics") {
        Console.withOut(stdout) {
          Utility.banner()
        }
      }

      stdout.toString("UTF-8") shouldBe empty // routed through the logging framework, no println
      events should have size 1
      val text = events.head.getFormattedMessage
      text should include("Picatinny Gatling Run")
      text should include("ASCII preview")
      text should include("|")
      text should include("/")
      text should include("_")
      AsciiWorkloadChart.hasContinuousStroke(text) shouldBe true
    }
  }

  "Utility.diagnostics" should {
    "emit nothing when disabled in test resources" in {
      val stdout = new java.io.ByteArrayOutputStream()
      val events = LogCapture.infoEvents("org.galaxio.gatling.diagnostics") {
        Console.withOut(stdout) {
          Utility.diagnostics()
        }
      }

      stdout.toString("UTF-8") shouldBe empty
      events shouldBe empty
    }
  }
}
