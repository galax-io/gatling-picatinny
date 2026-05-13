package org.galaxio.gatling.diagnostics

import org.galaxio.gatling.utils.Utility
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UtilityIntegrationSpec extends AnyWordSpec with Matchers {

  "Utility.banner" should {
    "print startup banner with a continuous workload chart" in {
      val output = new java.io.ByteArrayOutputStream()

      Console.withOut(output) {
        Utility.banner()
      }

      val text = output.toString("UTF-8")
      text should include("Picatinny Gatling Run")
      text should include("ASCII preview")
      text should include("|")
      text should include("/")
      text should include("_")
      AsciiWorkloadChart.hasContinuousStroke(text) shouldBe true
    }
  }

  "Utility.diagnostics" should {
    "print nothing when disabled in test resources" in {
      val output = new java.io.ByteArrayOutputStream()

      Console.withOut(output) {
        Utility.diagnostics()
      }

      output.toString("UTF-8") shouldBe empty
    }
  }

}
