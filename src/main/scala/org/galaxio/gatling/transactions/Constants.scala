package org.galaxio.gatling.transactions

import org.galaxio.gatling.config.SimulationConfig

/** Stable, load-bearing identifiers for the transactions module.
  *
  * The crash labels are asserted against in regression tests (FR-010), so they MUST stay stable and MUST NOT use `genName(...)`
  * (which carries a non-deterministic counter suffix). Production code and tests reference these constants directly so the
  * behavioral contract and the assertions cannot drift.
  */
object Constants {

  /** Crash label used when a `startTransaction` name expression fails to resolve. */
  val StartLabel: String = "startTransaction"

  /** Crash label used when an `endTransaction` name or stop-time expression fails to resolve. */
  val EndLabel: String = "endTransaction"

  /** Crash label used for the #70 dropped-events summary recorded at actor termination. */
  val DroppedLabel: String = "transactions dropped"

  /** Config path / JVM system property overriding the in-flight transaction-event bound (#70). */
  val MaxInFlightProperty: String = "galaxio.transactions.maxInFlight"

  /** Default bound on in-flight (sent-but-unprocessed) transaction events (#70). */
  val DefaultMaxInFlight: Int = 100000

  /** Resolves the in-flight bound through the project's config plumbing (JVM `-D` system property over `simulation.conf`),
    * falling back to [[DefaultMaxInFlight]] when unset.
    */
  def maxInFlight: Int = SimulationConfig.getIntParam(MaxInFlightProperty, DefaultMaxInFlight)
}
