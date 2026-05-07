package org.galaxio.gatling.diagnostics

import io.gatling.core.Predef._
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class DiagnosticsSpec extends AnyWordSpec with Matchers with OptionValues {

  "Diagnostics" should {

    "format durations in a compact human readable form" in {
      Formatters.duration(0.seconds) shouldBe "0s"
      Formatters.duration(60.seconds) shouldBe "1m"
      Formatters.duration(61.seconds) shouldBe "1m 1s"
      Formatters.duration(3661.seconds) shouldBe "1h 1m 1s"
    }

    "build workload timeline from Picatinny workload settings" in {
      val settings = WorkloadSettings(
        intensityRps = 1.0,
        intensityText = "60 rpm",
        profile = WorkloadProfile.StagedIncrement,
        stagesNumber = 2,
        rampDuration = 1.minute,
        stageDuration = 5.minutes,
        testDuration = 12.minutes,
      )

      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 1.minute, "ramp", 0.0, 0.5),
        WorkloadSegment(1.minute, 6.minutes, "stage", 0.5, 0.5),
        WorkloadSegment(6.minutes, 7.minutes, "ramp", 0.5, 1.0),
        WorkloadSegment(7.minutes, 12.minutes, "stage", 1.0, 1.0),
      )
    }

    "render ASCII chart with workload symbols" in {
      val settings = WorkloadSettings(
        intensityRps = 1.0,
        intensityText = "60 rpm",
        profile = WorkloadProfile.StagedIncrement,
        stagesNumber = 2,
        rampDuration = 1.minute,
        stageDuration = 5.minutes,
        testDuration = 12.minutes,
      )

      val chart = AsciiWorkloadChart.render(settings)

      chart should include("|")
      chart should include("/")
      chart should include("_")
      chart should include("1.00")
      chart should include("00:00")
      chart should include("12:00")
      AsciiWorkloadChart.hasContinuousStroke(chart) shouldBe true
    }

    "render smooth chart without gaps for short ramps" in {
      val settings = WorkloadSettings(
        intensityRps = 10.0,
        intensityText = "10 rps",
        profile = WorkloadProfile.StagedIncrement,
        stagesNumber = 4,
        rampDuration = 1.second,
        stageDuration = 3.seconds,
        testDuration = 16.seconds,
      )

      val chart = AsciiWorkloadChart.render(settings)

      AsciiWorkloadChart.hasContinuousStroke(chart) shouldBe true
      chart.linesIterator.exists(line => line.contains("////") || line.contains("/")) shouldBe true
    }

    "build stability timeline as a single ramp and plateau" in {
      val settings = WorkloadSettings(
        intensityRps = 1.0,
        intensityText = "60 rpm",
        profile = WorkloadProfile.RampAndPlateau,
        stagesNumber = 2,
        rampDuration = 1.second,
        stageDuration = 1.second,
        testDuration = 5.seconds,
      )

      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 1.second, "ramp", 0.0, 1.0),
        WorkloadSegment(1.second, 2.seconds, "plateau", 1.0, 1.0),
        WorkloadSegment(2.seconds, 5.seconds, "hold", 1.0, 1.0),
      )
    }

    "render vertical ramp only when ramp maps to one chart column" in {
      val settings = WorkloadSettings(
        intensityRps = 1.0,
        intensityText = "60 rpm",
        profile = WorkloadProfile.RampAndPlateau,
        stagesNumber = 1,
        rampDuration = 1.second,
        stageDuration = 1.second,
        testDuration = 10.minutes,
      )

      val chart = AsciiWorkloadChart.render(settings)

      AsciiWorkloadChart.hasContinuousStroke(chart) shouldBe true
      AsciiWorkloadChart.strokeColumns(chart).exists(_.contains("|")) shouldBe true
    }

    "parse explicit Gatling stability injection as ramp and plateau" in {
      val settings = InjectionProfileParser
        .fromOpen(
          Seq(
            rampUsersPerSec(0).to(1).during(1.second),
            constantUsersPerSec(1).during(4.seconds),
          ),
        )
        .value

      settings.profile.label shouldBe "provided-open-injection"
      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 1.second, "ramp", 0.0, 1.0),
        WorkloadSegment(1.second, 5.seconds, "plateau", 1.0, 1.0),
      )
    }

    "parse explicit Gatling max performance stairs injection" in {
      val settings = InjectionProfileParser
        .fromOpen(
          Seq(
            incrementUsersPerSec(0.5)
              .times(2)
              .eachLevelLasting(1.second)
              .separatedByRampsLasting(1.second)
              .startingFrom(0),
          ),
        )
        .value

      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 1.second, "ramp", 0.0, 0.5),
        WorkloadSegment(1.second, 2.seconds, "plateau", 0.5, 0.5),
        WorkloadSegment(2.seconds, 3.seconds, "ramp", 0.5, 1.0),
        WorkloadSegment(3.seconds, 4.seconds, "plateau", 1.0, 1.0),
      )
    }

    "parse open rate profile with pause, ramp and plateau" in {
      val settings = InjectionProfileParser
        .fromOpen(
          Seq(
            nothingFor(2.seconds),
            rampUsersPerSec(1).to(3).during(4.seconds),
            constantUsersPerSec(3).during(5.seconds),
          ),
        )
        .value

      settings.unit shouldBe "rps"
      settings.intensityRps shouldBe 3.0
      settings.rampDuration shouldBe 4.seconds
      settings.stageDuration shouldBe 5.seconds
      settings.testDuration shouldBe 11.seconds
      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 2.seconds, "pause", 0.0, 0.0),
        WorkloadSegment(2.seconds, 6.seconds, "ramp", 1.0, 3.0),
        WorkloadSegment(6.seconds, 11.seconds, "plateau", 3.0, 3.0),
      )
    }

    "parse open user profile with at once and ramp users" in {
      val settings = InjectionProfileParser
        .fromOpen(
          Seq(
            atOnceUsers(25),
            rampUsers(50).during(10.seconds),
          ),
        )
        .value

      settings.unit shouldBe "rps"
      settings.intensityRps shouldBe 25.0
      settings.testDuration shouldBe 10.seconds
      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 0.seconds, "at-once", 0.0, 25.0),
        WorkloadSegment(0.seconds, 10.seconds, "constant-users", 5.0, 5.0),
      )
    }

    "parse open stress peak profile as a rise and fall" in {
      val settings = InjectionProfileParser
        .fromOpen(
          Seq(
            stressPeakUsers(20).during(10.seconds),
          ),
        )
        .value

      settings.unit shouldBe "rps"
      settings.intensityRps shouldBe 4.0
      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 5.seconds, "stress-peak", 0.0, 4.0),
        WorkloadSegment(5.seconds, 10.seconds, "stress-peak", 4.0, 0.0),
      )
    }

    "parse closed concurrent users profile with plateau and ramp" in {
      val settings = InjectionProfileParser
        .fromClosed(
          Seq(
            constantConcurrentUsers(5).during(3.seconds),
            rampConcurrentUsers(5).to(12).during(7.seconds),
          ),
        )
        .value

      settings.unit shouldBe "users"
      settings.intensityRps shouldBe 12.0
      settings.profile.label shouldBe "provided-closed-injection"
      settings.rampDuration shouldBe 7.seconds
      settings.stageDuration shouldBe 3.seconds
      settings.testDuration shouldBe 10.seconds
      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 3.seconds, "plateau", 5.0, 5.0),
        WorkloadSegment(3.seconds, 10.seconds, "ramp", 5.0, 12.0),
      )
    }

    "parse closed concurrent stairs profile" in {
      val settings = InjectionProfileParser
        .fromClosed(
          Seq(
            incrementConcurrentUsers(3)
              .times(2)
              .eachLevelLasting(4.seconds)
              .separatedByRampsLasting(2.seconds)
              .startingFrom(1),
          ),
        )
        .value

      settings.unit shouldBe "users"
      settings.intensityRps shouldBe 7.0
      settings.stagesNumber shouldBe 2
      settings.rampDuration shouldBe 2.seconds
      settings.stageDuration shouldBe 8.seconds
      settings.testDuration shouldBe 12.seconds
      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 2.seconds, "ramp", 1.0, 4.0),
        WorkloadSegment(2.seconds, 6.seconds, "plateau", 4.0, 4.0),
        WorkloadSegment(6.seconds, 8.seconds, "ramp", 4.0, 7.0),
        WorkloadSegment(8.seconds, 12.seconds, "plateau", 7.0, 7.0),
      )
    }

    "parse Java open injection steps used by Java and Kotlin facade" in {
      val steps = Array(
        io.gatling.javaapi.core.CoreDsl.rampUsersPerSec(0.0).to(2.0).during(java.time.Duration.ofSeconds(2)),
        io.gatling.javaapi.core.CoreDsl.constantUsersPerSec(2.0).during(java.time.Duration.ofSeconds(3)),
      )

      val settings = InjectionProfileParser.javaOpen(steps).value

      settings.unit shouldBe "rps"
      settings.intensityRps shouldBe 2.0
      WorkloadTimeline.segments(settings) shouldBe List(
        WorkloadSegment(0.seconds, 2.seconds, "ramp", 0.0, 2.0),
        WorkloadSegment(2.seconds, 5.seconds, "plateau", 2.0, 2.0),
      )
    }

    "render startup banner with workload preview" in {
      val banner = StartupBanner.render()

      banner should include("Picatinny Gatling Run")
      banner should include("Workload")
      banner should include("Timeline")
      banner should include("ASCII preview")
      banner should include("1.00 rps")
      banner should include("|")
      banner should include("/")
      banner should include("_")
    }

    "enable startup banner and diagnostics in test resources" in {
      StartupBanner.isEnabled shouldBe true
      Diagnostics.isEnabled shouldBe true
    }

  }
}
