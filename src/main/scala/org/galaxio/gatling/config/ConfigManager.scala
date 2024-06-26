package org.galaxio.gatling.config

import com.typesafe.config.{Config, ConfigFactory}
import io.gatling.core.Predef.configuration
import io.gatling.core.config.GatlingConfiguration

private[gatling] object ConfigManager {

  lazy val simulationConfig: SimulationConfigUtils = SimulationConfigUtils(
    ConfigFactory
      .load("simulation.conf"),
  )

  lazy val influxConfig: Config = ConfigFactory
    .load("influx.conf")
    .withFallback(ConfigFactory.load("influx-default.conf"))

  lazy val gatlingConfig: GatlingConfiguration = configuration

}
