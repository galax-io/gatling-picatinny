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

        require(session("randomDate").as[String].nonEmpty, "randomDate")
        require(session("rangeFrom").as[String].nonEmpty, "rangeFrom")
        require(session("digit").as[Int] >= 0, "digit")
        require(session("customValue").as[String].startsWith("custom-"), "customValue")
        require(session("phone").as[String].nonEmpty, "phone")
        require(session("tollFreePhone").as[String].nonEmpty, "tollFreePhone")
        require(session("rangeString").as[String].length >= 4, "rangeString")
        require(session("sequence").as[Long] >= 100, "sequence")
        require(session("regex").as[String].nonEmpty, "regex")
        require(session("natItn").as[String].nonEmpty, "natItn")
        require(session("passport").as[String].nonEmpty, "passport")
        require(session("lambdaValue").as[String].nonEmpty, "lambdaValue")
        require(session("greekLetter").as[String].nonEmpty, "greekLetter")

        require(session("randomInt").as[Int] >= 1, "randomInt")
        require(session("alphabeticStr").as[String].length == 10, "alphabeticStr")
        require(session("firstName").as[String].nonEmpty, "firstName")
        require(session("username").as[String].nonEmpty, "username")
        require(session("countryCode").as[String].length == 2, "countryCode")
        require(session("today").as[String].nonEmpty, "today")
        require(session("rangeStart").as[String].nonEmpty, "rangeStart")
        require(session("accountNumber").as[String].length == 20, "accountNumber")
        require(session("productName").as[String].nonEmpty, "productName")
        require(session("weatherCondition").as[String].nonEmpty, "weatherCondition")
        require(session("loremWord").as[String].nonEmpty, "loremWord")
        require(session("localCurrency").as[String].nonEmpty, "localCurrency")
        require(session("usSSN").as[String].nonEmpty, "usSSN")
        require(session("phoneTollFree").as[String].nonEmpty, "phoneTollFree")
        require(session("constVal").as[String] == "fixed-value", "constVal")
        require(session("singleInt").as[Int] >= 1, "singleInt")
        require(session("tfName").as[String].nonEmpty, "tfName")
        require(session("tfNameUpper").as[String].nonEmpty, "tfNameUpper")
        require(session("kAlpha_demo").as[String].nonEmpty, "kAlpha_demo")
        require(session("configKey").as[String] == "configValue", "configKey")

        session
      }
}
