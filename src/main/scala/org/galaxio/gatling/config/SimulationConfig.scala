package org.galaxio.gatling.config

import scala.concurrent.duration._
import org.galaxio.gatling.config.ConfigManager.simulationConfig
import org.galaxio.gatling.utils.IntensityConverter.getIntensityFromString

object SimulationConfig {

  def getStringParam(path: String): String           = simulationConfig.get[String](path)
  def getIntParam(path: String): Int                 = simulationConfig.get[Int](path)
  def getDoubleParam(path: String): Double           = simulationConfig.get[Double](path)
  def getDurationParam(path: String): FiniteDuration = simulationConfig.get[FiniteDuration](path)
  def getBooleanParam(path: String): Boolean         = simulationConfig.get[Boolean](path)

  def getStringParam(path: String, default: String): String                   = simulationConfig.get[String](path, default)
  def getIntParam(path: String, default: Int): Int                            = simulationConfig.get[Int](path, default)
  def getDoubleParam(path: String, default: Double): Double                   = simulationConfig.get[Double](path, default)
  def getDurationParam(path: String, default: FiniteDuration): FiniteDuration =
    simulationConfig.get[FiniteDuration](path, default)
  def getBooleanParam(path: String, default: Boolean): Boolean                = simulationConfig.get[Boolean](path, default)

  lazy val baseUrl: String     = simulationConfig.get[String]("baseUrl")
  lazy val baseAuthUrl: String = simulationConfig.get[String]("baseAuthUrl")
  lazy val wsBaseUrl: String   = simulationConfig.get[String]("wsBaseUrl")

  lazy val stagesNumber: Int = simulationConfig.get[Int]("stagesNumber", 1)

  lazy val rampDuration: FiniteDuration  = simulationConfig.get[FiniteDuration]("rampDuration")
  lazy val stageDuration: FiniteDuration = simulationConfig.get[FiniteDuration]("stageDuration")
  lazy val testDuration: FiniteDuration  =
    simulationConfig.get[FiniteDuration]("testDuration", (rampDuration + stageDuration) * stagesNumber)

  lazy val intensity: Double = getIntensityFromString(simulationConfig.get[String]("intensity"))

}
