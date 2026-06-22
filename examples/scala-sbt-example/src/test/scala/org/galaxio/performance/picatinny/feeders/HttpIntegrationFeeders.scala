package org.galaxio.performance.picatinny.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.feeders.CurrentDateFeeder

import java.time.format.DateTimeFormatter

/** Feeder data for the e2e (test-model layer 4). Data only — no scenario, no injection (galaxio-gatling-pro boundaries).
  *
  * Uses the picatinny `CurrentDateFeeder` to produce a URL-safe `ts` value that the scenario sends and the mock echoes back, so
  * a Gatling `check` can confirm the picatinny feeder value survived the HTTP round-trip.
  */
object HttpIntegrationFeeders {

  val ts: Feeder[String] =
    CurrentDateFeeder("ts", DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
}
