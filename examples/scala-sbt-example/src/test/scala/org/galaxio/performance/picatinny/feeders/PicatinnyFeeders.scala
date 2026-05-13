package org.galaxio.performance.picatinny.feeders

import io.gatling.core.Predef._
import io.gatling.core.feeder.{Feeder, FeederBuilderBase}
import org.galaxio.gatling.feeders._
import org.galaxio.gatling.utils.phone.{PhoneFormat, TypePhone}

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneId}
import scala.annotation.nowarn
import scala.util.Random

@nowarn("cat=deprecation")
object PicatinnyFeeders {
  private val ruMobileFormat = PhoneFormat(
    countryCode = "+7",
    length = 10,
    areaCodes = Seq("903", "906"),
    prefixes = Seq("123", "321"),
    format = "+X XXX XXX-XX-XX",
  )

  val currentDate: Feeder[String] = CurrentDateFeeder("currentDate", DateTimeFormatter.ISO_LOCAL_DATE)
  val randomDate: Feeder[String]  = RandomDateFeeder("randomDate", 3, 1)
  val dateRange: Feeder[String]   =
    RandomDateRangeFeeder("rangeFrom", "rangeTo", 2, "yyyy-MM-dd", LocalDateTime.now(), ChronoUnit.DAYS, ZoneId.of("UTC"))
  val digit: Feeder[Int]          = RandomDigitFeeder("digit")
  val customValue: Feeder[String] = CustomFeeder("customValue", s"custom-${Random.nextInt(1000)}")

  val phone: Feeder[String]          = RandomPhoneFeeder("phone")
  val e164Phone: Feeder[String]      = RandomPhoneFeeder("e164Phone", TypePhone.E164PhoneNumber, ruMobileFormat)
  val formattedPhone: Feeder[String] = RandomPhoneFeeder("formattedPhone", TypePhone.PhoneNumber, ruMobileFormat)
  val tollFreePhone: Feeder[String]  = RandomPhoneFeeder("tollFreePhone", TypePhone.TollFreePhoneNumber)

  val randomString: Feeder[String] = RandomStringFeeder("randomString", 12)
  val rangeString: Feeder[String]  = RandomRangeStringFeeder("rangeString", 4, 8, "abc123")
  val uuid: Feeder[String]         = RandomUUIDFeeder("uuid")
  val sequence: Feeder[Long]       = SequentialFeeder("sequence", 100, 5)
  val regex: Feeder[String]        = RegexFeeder("regex", "[A-Z]{2}[0-9]{4}")

  val csvValue: FeederBuilderBase[String]    = SeparatedValuesFeeder.csv("csvValue", "alpha,beta,gamma").circular
  val splitValues: FeederBuilderBase[String] =
    SeparatedValuesFeeder.csv(Some("split"), Seq(Map("HOSTS" -> "host1,host2", "USERS" -> "user1,user2"))).circular

  val pan: Feeder[String]      = RandomPANFeeder("pan", "421345", "541673")
  val natItn: Feeder[String]   = RandomNatITNFeeder("natItn")
  val jurItn: Feeder[String]   = RandomJurITNFeeder("jurItn")
  val ogrn: Feeder[String]     = RandomOGRNFeeder("ogrn")
  val psrnsp: Feeder[String]   = RandomPSRNSPFeeder("psrnsp")
  val kpp: Feeder[String]      = RandomKPPFeeder("kpp")
  val snils: Feeder[String]    = RandomSNILSFeeder("snils")
  val passport: Feeder[String] = RandomRusPassportFeeder("passport")

  // --- Package-level utilities ---

  val lambdaFeeder: Feeder[String]                   = feeder("lambdaValue")(java.util.UUID.randomUUID().toString.take(8))
  val zippedFeeder: Feeder[Any]                      = uuid ** digit
  val finiteRecords: IndexedSeq[Map[String, String]] = Seq("alpha", "beta", "gamma").toFeeder("greekLetter")
}
