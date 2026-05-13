package org.galaxio.performance.picatinny.feeders

import org.galaxio.gatling.javaapi.Feeders.CurrentDateFeeder
import org.galaxio.gatling.javaapi.Feeders.CustomFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomDateFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomDateRangeFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomDigitFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomJurITNFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomKPPFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomNatITNFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomOGRNFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomPANFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomPSRNSPFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomPhoneFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomRangeStringFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomRusPassportFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomSNILSFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomStringFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomUUIDFeeder
import org.galaxio.gatling.javaapi.Feeders.RegexFeeder
import org.galaxio.gatling.javaapi.Feeders.SequentialFeeder
import org.galaxio.gatling.javaapi.Feeders.SeparatedValuesFeeder
import org.galaxio.gatling.javaapi.utils.phone.PhoneFormatBuilder
import org.galaxio.gatling.javaapi.utils.phone.TypePhone
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Optional
import kotlin.random.Random

/** Legacy Random*Feeder examples (deprecated — see GeneratedPicatinnyFeeders for the new Faker API). */
object PicatinnyFeeders {

    private val ruMobileFormat = PhoneFormatBuilder.apply(
        "+7", 10, listOf("903", "906"), "+X XXX XXX-XX-XX", listOf("123", "321"),
    )

    val uuid = RandomUUIDFeeder("uuid")
    val currentDate = CurrentDateFeeder("currentDate", DateTimeFormatter.ISO_LOCAL_DATE)
    val randomDate = RandomDateFeeder("randomDate", 3, 1)
    val dateRange = RandomDateRangeFeeder(
        "rangeFrom", "rangeTo", 2L, "yyyy-MM-dd",
        LocalDateTime.now(), ChronoUnit.DAYS, ZoneId.of("UTC"),
    )
    val digit = RandomDigitFeeder("digit")
    val customValue = CustomFeeder("customValue") { "custom-${Random.nextInt(1000)}" }

    val phone = RandomPhoneFeeder("phone")
    val e164Phone = RandomPhoneFeeder("e164Phone", TypePhone.E164PhoneNumber(), ruMobileFormat)
    val formattedPhone = RandomPhoneFeeder("formattedPhone", TypePhone.PhoneNumber(), ruMobileFormat)
    val tollFreePhone = RandomPhoneFeeder("tollFreePhone", "phoneTemplates/ru.json", TypePhone.TollFreePhoneNumber())

    val randomString = RandomStringFeeder("randomString", 12)
    val rangeString = RandomRangeStringFeeder("rangeString", 4, 8, "abc123")
    val sequence = SequentialFeeder("sequence", 100, 5)
    val regex = RegexFeeder("regex", "[A-Z]{2}[0-9]{4}")

    val csvValue = SeparatedValuesFeeder.csv("csvValue", "alpha,beta,gamma")
    val splitValues = SeparatedValuesFeeder.csv(
        Optional.of("split"),
        listOf(mapOf("HOSTS" to "host1,host2", "USERS" to "user1,user2")),
    )

    val pan = RandomPANFeeder("pan", "421345", "541673")
    val natItn = RandomNatITNFeeder("natItn")
    val jurItn = RandomJurITNFeeder("jurItn")
    val ogrn = RandomOGRNFeeder("ogrn")
    val psrnsp = RandomPSRNSPFeeder("psrnsp")
    val kpp = RandomKPPFeeder("kpp")
    val snils = RandomSNILSFeeder("snils")
    val passport = RandomRusPassportFeeder("passport")

    fun all(): List<Iterator<Map<String, Any>>> = listOf(
        uuid, currentDate, randomDate, dateRange, digit, customValue,
        phone, e164Phone, formattedPhone, tollFreePhone,
        randomString, rangeString, sequence, regex,
        csvValue, splitValues,
        pan, natItn, jurItn, ogrn, psrnsp, kpp, snils, passport,
    )
}
