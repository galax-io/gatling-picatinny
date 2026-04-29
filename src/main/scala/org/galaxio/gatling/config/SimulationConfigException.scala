package org.galaxio.gatling.config

/** Signals that a value from `simulation.conf` or a JVM system property is missing, has an unsupported type, or violates
  * Picatinny workload validation rules.
  */
class SimulationConfigException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
