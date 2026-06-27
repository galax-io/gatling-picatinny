package org.galaxio.gatling.diagnostics

import com.typesafe.scalalogging.StrictLogging
import io.gatling.core.controller.inject.closed.ClosedInjectionStep
import io.gatling.core.controller.inject.open.OpenInjectionStep
import org.galaxio.gatling.config.{ConfigManager, ConfigValueMasking}
import org.galaxio.gatling.utils.IntensityConverter.getIntensityFromString

import scala.concurrent.duration.FiniteDuration

object StartupBanner extends StrictLogging {
  private val EnabledPath        = "picatinny.startup.banner.enabled"
  private val InternalStackNames = Set("Utility", "StartupBanner", "Diagnostics")

  def printIfEnabled(): Unit =
    if (isEnabled) logger.info(render())

  def printOpenIfEnabled(openSteps: Iterable[OpenInjectionStep]): Unit =
    printSettingsIfEnabled(InjectionProfileParser.fromOpen(openSteps))

  def printClosedIfEnabled(closedSteps: Iterable[ClosedInjectionStep]): Unit =
    printSettingsIfEnabled(InjectionProfileParser.fromClosed(closedSteps))

  def printIfEnabled(simulationClass: Class[_]): Unit =
    printSimulationIfEnabled(Some(simulationClass))

  private[gatling] def printSettingsIfEnabled(settings: Option[WorkloadSettings]): Unit =
    if (isEnabled) logger.info(settings.map(render).getOrElse(render()))

  private def printSimulationIfEnabled(simulationClass: Option[Class[_]]): Unit =
    if (isEnabled) logger.info(render(simulationClass, None))

  private[gatling] def isEnabled: Boolean =
    ConfigManager.simulationConfig.get(EnabledPath, true)

  private[diagnostics] def render(): String =
    render(None, None)

  private[diagnostics] def render(settings: WorkloadSettings): String =
    render(None, Some(settings))

  private[diagnostics] def render(simulationClass: Class[_]): String =
    render(Some(simulationClass), None)

  private def render(simulationClass: Option[Class[_]], providedSettings: Option[WorkloadSettings]): String = {
    val config = ConfigManager.simulationConfig
    val stages = config.get[Int]("stagesNumber", 1)
    val line   = "=" * 80
    val name   = simulationName(simulationClass)

    s"""$line
       | Picatinny Gatling Run
       |$line
       | Simulation   : $name
       | Base URL     : ${ConfigValueMasking.redactUserInfo(config.get[String]("baseUrl", "<undefined>"))}
       |
       |${providedSettings.map(workloadBlock).getOrElse(configWorkloadBlock(name, stages))}
       |$line
       |""".stripMargin
  }

  private def configWorkloadBlock(simulationName: String, stages: Int): String = {
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
          case WorkloadProfile.Provided(_)     => rampDuration + stageDuration
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

        workloadBlock(settings)

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

  private def workloadBlock(settings: WorkloadSettings): String =
    s""" Workload
       |${settingsBlock(settings)}
       |
       | Timeline
       |${WorkloadTimeline.render(settings)}
       |
       | ASCII preview
       |${AsciiWorkloadChart.render(settings)}""".stripMargin

  private def settingsBlock(settings: WorkloadSettings): String =
    List(
      "intensity"      -> intensityLine(settings),
      "profile"        -> settings.profile.label,
      "stages"         -> settings.stagesNumber.toString,
      "ramp duration"  -> Formatters.duration(settings.rampDuration),
      "stage duration" -> Formatters.duration(settings.stageDuration),
      "total duration" -> Formatters.duration(settings.testDuration),
    ).filterNot { case (name, _) =>
      name == "stages" && (settings.profile == WorkloadProfile.RampAndPlateau || settings.stagesNumber <= 1)
    }.map { case (name, value) =>
      s"   ${name.padTo(15, ' ')}: $value"
    }
      .mkString("\n")

  private def intensityLine(settings: WorkloadSettings): String =
    if (settings.intensityText.endsWith(s" ${settings.unit}")) settings.intensityText
    else s"${settings.intensityText} = ${Formatters.decimal(settings.intensityRps)} ${settings.unit}"

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
