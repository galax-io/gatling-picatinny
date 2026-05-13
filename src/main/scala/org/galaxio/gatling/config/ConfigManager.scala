package org.galaxio.gatling.config

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef.configuration
import io.gatling.core.config.GatlingConfiguration

private[gatling] object ConfigManager {

  lazy val simulationConfig: SimulationConfigUtils = SimulationConfigUtils(
    ConfigFactory.systemProperties().withFallback(ConfigFactory.load("simulation.conf")),
  )

  lazy val gatlingConfig: GatlingConfiguration = configuration

}
