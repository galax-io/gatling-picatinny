package org.galaxio.gatling.diagnostics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class DiagnosticsSpec extends AnyFlatSpec with Matchers {

  it should "format durations in a compact human readable form" in {
    Formatters.duration(0.seconds) shouldBe "0s"
    Formatters.duration(60.seconds) shouldBe "1m"
    Formatters.duration(61.seconds) shouldBe "1m 1s"
    Formatters.duration(3661.seconds) shouldBe "1h 1m 1s"
  }

  it should "build workload timeline from Picatinny workload settings" in {
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

  it should "render ASCII chart with workload symbols" in {
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

  it should "render smooth chart without gaps for short ramps" in {
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

  it should "build stability timeline as a single ramp and plateau" in {
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

  it should "render vertical ramp only when ramp maps to one chart column" in {
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

  it should "render startup banner with workload preview" in {
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

  it should "enable startup banner by default and keep diagnostics disabled by default" in {
    StartupBanner.isEnabled shouldBe true
    Diagnostics.isEnabled shouldBe false
  }

}
