package org.galaxio.gatling.diagnostics

import scala.concurrent.duration._

private[diagnostics] final case class WorkloadSettings(
    intensityRps: Double,
    intensityText: String,
    profile: WorkloadProfile,
    stagesNumber: Int,
    rampDuration: FiniteDuration,
    stageDuration: FiniteDuration,
    testDuration: FiniteDuration,
)

private[diagnostics] final case class WorkloadSegment(
    start: FiniteDuration,
    end: FiniteDuration,
    kind: String,
    fromRps: Double,
    toRps: Double,
)

private[diagnostics] sealed trait WorkloadProfile {
  def label: String
}

private[diagnostics] object WorkloadProfile {
  case object RampAndPlateau extends WorkloadProfile {
    override val label: String = "ramp-and-plateau"
  }

  case object StagedIncrement extends WorkloadProfile {
    override val label: String = "staged-increment"
  }

  def fromSimulationName(name: String): WorkloadProfile =
    if (name.toLowerCase.contains("maxperformance")) StagedIncrement
    else RampAndPlateau
}

private[diagnostics] object WorkloadTimeline {

  def segments(settings: WorkloadSettings): List[WorkloadSegment] =
    settings.profile match {
      case WorkloadProfile.RampAndPlateau  => clamp(rampAndPlateau(settings), settings.testDuration)
      case WorkloadProfile.StagedIncrement => clamp(stagedIncrement(settings), settings.testDuration)
    }

  private def rampAndPlateau(settings: WorkloadSettings): List[WorkloadSegment] =
    List(
      WorkloadSegment(0.seconds, settings.rampDuration, "ramp", 0.0, settings.intensityRps),
      WorkloadSegment(
        settings.rampDuration,
        settings.rampDuration + settings.stageDuration,
        "plateau",
        settings.intensityRps,
        settings.intensityRps,
      ),
    ).filter(segment => segment.end > segment.start)

  private def stagedIncrement(settings: WorkloadSettings): List[WorkloadSegment] = {
    val stageCount = math.max(1, settings.stagesNumber)
    val step       = settings.intensityRps / stageCount

    (1 to stageCount).toList.flatMap { stage =>
      val previousLevel = step * (stage - 1)
      val nextLevel     = step * stage
      val offset        = (settings.rampDuration + settings.stageDuration) * (stage - 1).toLong
      val rampEnd       = offset + settings.rampDuration
      val stageEnd      = rampEnd + settings.stageDuration

      List(
        WorkloadSegment(offset, rampEnd, "ramp", previousLevel, nextLevel),
        WorkloadSegment(rampEnd, stageEnd, "stage", nextLevel, nextLevel),
      ).filter(segment => segment.end > segment.start)
    }
  }

  private def clamp(segments: List[WorkloadSegment], testDuration: FiniteDuration): List[WorkloadSegment] =
    segments.lastOption match {
      case Some(last) if testDuration > last.end =>
        segments :+ WorkloadSegment(last.end, testDuration, "hold", last.toRps, last.toRps)
      case _                                     =>
        segments.filter(_.start < testDuration).map { segment =>
          if (segment.end > testDuration) segment.copy(end = testDuration) else segment
        }
    }

  def render(settings: WorkloadSettings): String =
    segments(settings)
      .map(renderSegment)
      .mkString("\n")

  private def renderSegment(segment: WorkloadSegment): String = {
    val range = s"${Formatters.time(segment.start)} - ${Formatters.time(segment.end)}"
    val label = segment.kind.padTo(5, ' ')
    val level = Formatters.decimal(segment.toRps)

    segment.kind match {
      case "ramp" => s"   $range  $label  ${Formatters.decimal(segment.fromRps)} -> $level rps"
      case _      => s"   $range  $label  $level rps"
    }
  }

}
