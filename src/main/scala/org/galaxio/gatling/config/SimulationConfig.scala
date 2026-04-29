package org.galaxio.gatling.config

import org.galaxio.gatling.config.ConfigManager.simulationConfig
import org.galaxio.gatling.utils.IntensityConverter.getIntensityFromString
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.util.{Failure, Try}

/** Entry point for reading Picatinny simulation settings from `simulation.conf`.
  *
  * JVM system properties override values from `simulation.conf`, so CI and environment-specific runs can pass values such as
  * `-DbaseUrl=https://test.example.org` or `-Dintensity="120 rpm"`.
  *
  * Required getters throw [[SimulationConfigException]] when a value is missing or has an invalid type. Optional getters return
  * `None` when the path is not defined.
  */
object SimulationConfig {

  /** Reads a required string parameter.
    *
    * @throws SimulationConfigException
    *   when the path is missing or is not a string
    */
  def getStringParam(path: String): String = simulationConfig.get[String](path)

  /** Reads a required integer parameter.
    *
    * @throws SimulationConfigException
    *   when the path is missing or is not an integer
    */
  def getIntParam(path: String): Int = simulationConfig.get[Int](path)

  /** Reads a required double parameter.
    *
    * @throws SimulationConfigException
    *   when the path is missing or is not numeric
    */
  def getDoubleParam(path: String): Double = simulationConfig.get[Double](path)

  /** Reads a required duration parameter as a finite Scala duration.
    *
    * @throws SimulationConfigException
    *   when the path is missing or is not a duration
    */
  def getDurationParam(path: String): FiniteDuration = simulationConfig.get[FiniteDuration](path)

  /** Reads a required boolean parameter.
    *
    * @throws SimulationConfigException
    *   when the path is missing or is not a boolean
    */
  def getBooleanParam(path: String): Boolean = simulationConfig.get[Boolean](path)

  /** Reads a required list of strings.
    *
    * @throws SimulationConfigException
    *   when the path is missing or is not a string list
    */
  def getStringListParam(path: String): List[String] = simulationConfig.get[List[String]](path)

  /** Reads a required nested Typesafe Config block.
    *
    * @throws SimulationConfigException
    *   when the path is missing or is not an object
    */
  def getConfigParam(path: String): Config = simulationConfig.get[Config](path)

  /** Reads an optional string parameter. */
  def getOptStringParam(path: String): Option[String] = simulationConfig.getOpt[String](path)

  /** Reads an optional integer parameter. */
  def getOptIntParam(path: String): Option[Int] = simulationConfig.getOpt[Int](path)

  /** Reads an optional double parameter. */
  def getOptDoubleParam(path: String): Option[Double] = simulationConfig.getOpt[Double](path)

  /** Reads an optional duration parameter as a finite Scala duration. */
  def getOptDurationParam(path: String): Option[FiniteDuration] = simulationConfig.getOpt[FiniteDuration](path)

  /** Reads an optional boolean parameter. */
  def getOptBooleanParam(path: String): Option[Boolean] = simulationConfig.getOpt[Boolean](path)

  /** Reads an optional list of strings. */
  def getOptStringListParam(path: String): Option[List[String]] = simulationConfig.getOpt[List[String]](path)

  /** Reads an optional nested Typesafe Config block. */
  def getOptConfigParam(path: String): Option[Config] = simulationConfig.getOpt[Config](path)

  /** Reads a string parameter, returning `default` when the path is not defined. */
  def getStringParam(path: String, default: String): String = simulationConfig.get[String](path, default)

  /** Reads an integer parameter, returning `default` when the path is not defined. */
  def getIntParam(path: String, default: Int): Int = simulationConfig.get[Int](path, default)

  /** Reads a double parameter, returning `default` when the path is not defined. */
  def getDoubleParam(path: String, default: Double): Double = simulationConfig.get[Double](path, default)

  /** Reads a duration parameter, returning `default` when the path is not defined. */
  def getDurationParam(path: String, default: FiniteDuration): FiniteDuration =
    simulationConfig.get[FiniteDuration](path, default)

  /** Reads a boolean parameter, returning `default` when the path is not defined. */
  def getBooleanParam(path: String, default: Boolean): Boolean = simulationConfig.get[Boolean](path, default)

  /** Reads a list of strings, returning `default` when the path is not defined. */
  def getStringListParam(path: String, default: List[String]): List[String] =
    simulationConfig.get[List[String]](path, default)

  /** Reads a nested Typesafe Config block, returning `default` when the path is not defined. */
  def getConfigParam(path: String, default: Config): Config = simulationConfig.get[Config](path, default)

  /** Base HTTP URL used by simulations. */
  lazy val baseUrl: String = simulationConfig.get[String]("baseUrl")

  /** Base authentication URL used by simulations. */
  lazy val baseAuthUrl: String = simulationConfig.get[String]("baseAuthUrl")

  /** Base WebSocket URL used by simulations. */
  lazy val wsBaseUrl: String = simulationConfig.get[String]("wsBaseUrl")

  /** Number of workload stages. Must be greater than zero. */
  lazy val stagesNumber: Int = simulationConfig.requirePositive("stagesNumber", simulationConfig.get[Int]("stagesNumber", 1))

  /** Ramp duration. Must be zero or greater. */
  lazy val rampDuration: FiniteDuration =
    simulationConfig.requireNonNegative("rampDuration", simulationConfig.get[FiniteDuration]("rampDuration"))

  /** Stage duration. Must be greater than zero. */
  lazy val stageDuration: FiniteDuration =
    simulationConfig.requirePositive("stageDuration", simulationConfig.get[FiniteDuration]("stageDuration"))

  /** Maximum simulation duration. Defaults to `(rampDuration + stageDuration) * stagesNumber` and must be greater than zero.
    */
  lazy val testDuration: FiniteDuration =
    simulationConfig.requirePositive(
      "testDuration",
      simulationConfig.get[FiniteDuration]("testDuration", (rampDuration + stageDuration) * stagesNumber),
    )

  /** Target request intensity converted to requests per second. Supports `rps`, `rpm`, and `rph` suffixes. */
  lazy val intensity: Double =
    simulationConfig.requirePositive("intensity", parseIntensity(simulationConfig.get[String]("intensity")))

  private def parseIntensity(value: String): Double =
    Try(getIntensityFromString(value)).recoverWith { case e: IllegalArgumentException =>
      Failure(new SimulationConfigException(s"Invalid simulation config value at intensity: ${e.getMessage}", e))
    }.get

}
