package io.gatling.core

import io.gatling.core.config.GatlingConfiguration

/** Test-only bridge: `Predef._configuration` is `private[gatling]`, but picatinny specs that drive production code building
  * Gatling bodies (e.g. the `Templates` trait constructing `ElFileBody`) need the implicit configuration initialized. Lives
  * under `io.gatling.core` purely for access — same pattern as the `RecordingStatsEngine` test source.
  */
object GatlingTestBootstrap {
  def init(): Unit = Predef._configuration = GatlingConfiguration.loadForTest()
}
