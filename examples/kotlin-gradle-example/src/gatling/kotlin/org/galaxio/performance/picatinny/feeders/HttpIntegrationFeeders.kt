package org.galaxio.performance.picatinny.feeders

import org.galaxio.gatling.javaapi.Feeders
import java.time.format.DateTimeFormatter

object HttpIntegrationFeeders {
    fun ts() = Feeders.CurrentDateFeeder("ts", DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
}
