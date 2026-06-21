package org.galaxio.gatling.utils

import io.gatling.core.Predef._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

/** Unit tests for the [[Utility]] banner overloads (test-model layer 1). The test `simulation.conf` enables the startup banner,
  * so each overload prints; output is captured via `Console.withOut` and asserted to contain the banner header. This exercises
  * the open/closed/list/tuple injection-step parsing paths (`StartupBanner.printOpen/Closed/SettingsIfEnabled`) that the no-arg
  * `banner()` test does not reach.
  */
class UtilitySpec extends AnyWordSpec with Matchers {

  private val BannerHeader = "Picatinny Gatling Run"

  private def capture(f: => Unit): String = {
    val out = new java.io.ByteArrayOutputStream()
    Console.withOut(out)(f)
    out.toString("UTF-8")
  }

  // The no-arg banner renders the workload from simulation.conf. Each step-parsing overload must render the workload PARSED
  // from its argument, which differs from the conf fallback — so `should not be fallback` catches a silent fall-through to
  // banner() (e.g. the printFromValues default case) that asserting only the static header would miss.
  private val fallback = capture(Utility.banner())

  "Utility.banner" should {

    "parse a single open injection step (not the conf fallback)" in {
      val out = capture(Utility.banner(atOnceUsers(1)))
      out should include(BannerHeader)
      out should not be fallback
    }

    "parse a list of open injection steps (not the conf fallback)" in {
      val out = capture(Utility.banner(List(rampUsers(2).during(1.second), atOnceUsers(1))))
      out should include(BannerHeader)
      out should not be fallback
    }

    "parse a tuple (Product) of open injection steps (not the conf fallback)" in {
      val out = capture(Utility.banner((atOnceUsers(1), rampUsers(2).during(1.second))))
      out should include(BannerHeader)
      out should not be fallback
    }

    "parse a closed injection step (not the conf fallback)" in {
      val out = capture(Utility.banner(constantConcurrentUsers(1).during(1.second)))
      out should include(BannerHeader)
      out should not be fallback
    }
  }
}
