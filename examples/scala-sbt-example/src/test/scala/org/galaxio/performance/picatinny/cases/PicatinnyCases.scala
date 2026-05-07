package org.galaxio.performance.picatinny.cases

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import org.galaxio.gatling.config.SimulationConfig._
import org.galaxio.gatling.utils.IntensityConverter._
import org.galaxio.gatling.utils.jwt._

import scala.concurrent.duration.DurationInt

object PicatinnyCases {
  private val jwtGenerator = jwt("HS256", "performance-secret").defaultHeader
    .payload("""{"subject":"picatinny","scope":"scala-sbt-template"}""")

  val businessOperation: ChainBuilder =
    exec(session => session.setJwt(jwtGenerator, "jwt"))
      .pause(1.second)
      .exec { session =>
        require(baseUrl == "http://localhost", "baseUrl")
        require(60.rpm == intensity, "intensity")
        require(session("uuid").as[String].length == 36, "uuid")
        require(session("jwt").as[String].split("\\.").length == 3, "jwt")
        require(session("formattedPhone").as[String].nonEmpty, "formattedPhone")
        require(session("pan").as[String].length >= 16, "pan")
        session
      }
}
