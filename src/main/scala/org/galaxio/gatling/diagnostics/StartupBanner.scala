package org.galaxio.gatling.diagnostics

import org.galaxio.gatling.config.ConfigManager
import org.galaxio.gatling.utils.IntensityConverter.getIntensityFromString

import scala.concurrent.duration.FiniteDuration

object StartupBanner {
  private val EnabledPath        = "picatinny.startup.banner.enabled"
  private val InternalStackNames = Set("Utility", "StartupBanner", "Diagnostics")

  def printIfEnabled(): Unit =
    printIfEnabled(None)

  def printIfEnabled(simulationClass: Class[_]): Unit =
    printIfEnabled(Some(simulationClass))

  private def printIfEnabled(simulationClass: Option[Class[_]]): Unit =
    if (isEnabled) println(render(simulationClass))

  private[gatling] def isEnabled: Boolean =
    ConfigManager.simulationConfig.get(EnabledPath, true)

  private[diagnostics] def render(): String =
    render(None)

  private[diagnostics] def render(simulationClass: Class[_]): String =
    render(Some(simulationClass))

  private def render(simulationClass: Option[Class[_]]): String = {
    val config = ConfigManager.simulationConfig
    val stages = config.get[Int]("stagesNumber", 1)
    val line   = "=" * 80
    val name   = simulationName(simulationClass)

    s"""$line
       | Picatinny Gatling Run
       |$line
       | Simulation   : $name
       | Base URL     : ${config.get[String]("baseUrl", "<undefined>")}
       |
       |${workloadBlock(name, stages)}
       |$line
       |""".stripMargin
  }

  private def workloadBlock(simulationName: String, stages: Int): String = {
    val config        = ConfigManager.simulationConfig
    val intensityText = config.getOpt[String]("intensity")
    val ramp          = config.getOpt[FiniteDuration]("rampDuration")
    val stage         = config.getOpt[FiniteDuration]("stageDuration")

    (intensityText, ramp, stage) match {
      case (Some(intensity), Some(rampDuration), Some(stageDuration)) =>
        val profile  = WorkloadProfile.fromSimulationName(simulationName)
        val fallback = profile match {
          case WorkloadProfile.RampAndPlateau  => rampDuration + stageDuration
          case WorkloadProfile.StagedIncrement => (rampDuration + stageDuration) * stages
        }
        val total    = config.get[FiniteDuration]("testDuration", fallback)
        val settings = WorkloadSettings(
          intensityRps = getIntensityFromString(intensity),
          intensityText = intensity,
          profile = profile,
          stagesNumber = stages,
          rampDuration = rampDuration,
          stageDuration = stageDuration,
          testDuration = total,
        )

        s""" Workload
           |${settingsBlock(settings)}
           |
           | Timeline
           |${WorkloadTimeline.render(settings)}
           |
           | ASCII preview
           |${AsciiWorkloadChart.render(settings)}""".stripMargin

      case _ =>
        val missing = List(
          "intensity"     -> intensityText,
          "rampDuration"  -> ramp,
          "stageDuration" -> stage,
        ).collect { case (name, None) => name }.mkString(", ")

        s""" Workload
           |   unavailable   : missing $missing""".stripMargin
    }
  }

  private def settingsBlock(settings: WorkloadSettings): String =
    List(
      "intensity"      -> s"${settings.intensityText} = ${Formatters.decimal(settings.intensityRps)} rps",
      "profile"        -> settings.profile.label,
      "stages"         -> settings.stagesNumber.toString,
      "ramp duration"  -> Formatters.duration(settings.rampDuration),
      "stage duration" -> Formatters.duration(settings.stageDuration),
      "total duration" -> Formatters.duration(settings.testDuration),
    ).filterNot { case (name, _) => name == "stages" && settings.profile == WorkloadProfile.RampAndPlateau }.map {
      case (name, value) => s"   ${name.padTo(15, ' ')}: $value"
    }
      .mkString("\n")

  private def simulationName(simulationClass: Option[Class[_]]): String =
    simulationClass
      .map(_.getSimpleName)
      .filter(_.nonEmpty)
      .orElse(
        sys.props
          .get("gatling.core.simulationClass")
          .map(_.split('.').last),
      )
      .orElse(sys.props.get("gatling.simulationClass").map(_.split('.').last))
      .orElse(stackSimulationName)
      .getOrElse("<unknown>")

  private def stackSimulationName: Option[String] =
    Thread
      .currentThread()
      .getStackTrace
      .iterator
      .map(_.getClassName)
      .filterNot(name =>
        name.startsWith("java.") ||
          name.startsWith("jdk.") ||
          name.startsWith("sun.") ||
          name.startsWith("scala.") ||
          name.startsWith("sbt.") ||
          name.startsWith("io.gatling.") ||
          name.startsWith("org.galaxio.gatling."),
      )
      .map(_.split('.').last.replace("$", ""))
      .filterNot(InternalStackNames)
      .find(_.nonEmpty)

}
