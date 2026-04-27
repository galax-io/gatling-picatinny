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
import org.galaxio.gatling.javaapi.Feeders.SeparatedValuesFeeder
import org.galaxio.gatling.javaapi.Feeders.SequentialFeeder
import org.galaxio.gatling.javaapi.utils.phone.PhoneFormatBuilder
import org.galaxio.gatling.javaapi.utils.phone.TypePhone
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.concurrent.ThreadLocalRandom

object PicatinnyFeeders {
    private val ruMobileFormat = PhoneFormatBuilder.apply(
        "+7",
        10,
        listOf("903", "906"),
        "+X XXX XXX-XX-XX",
        listOf("123", "321"),
    )

    private val splitSource = listOf<Map<String, Any>>(
        mapOf("HOSTS" to "host1,host2", "USERS" to "user1,user2"),
    )

    fun all(): List<Iterator<Map<String, Any>>> = listOf(
        CurrentDateFeeder("currentDate", DateTimeFormatter.ISO_LOCAL_DATE),
        RandomDateFeeder("randomDate", 3, 1),
        RandomDateRangeFeeder("rangeFrom", "rangeTo", 2L, "yyyy-MM-dd", LocalDateTime.now(), ChronoUnit.DAYS, ZoneId.of("UTC")),
        RandomDigitFeeder("digit"),
        CustomFeeder("customValue") { "custom-${ThreadLocalRandom.current().nextInt(1000)}" },
        RandomPhoneFeeder("phone"),
        RandomPhoneFeeder("phoneFromJson", "phoneTemplates/ru.json", TypePhone.E164PhoneNumber()),
        RandomPhoneFeeder("formattedPhone", TypePhone.PhoneNumber(), ruMobileFormat),
        RandomStringFeeder("randomString", 12),
        RandomRangeStringFeeder("rangeString", 4, 8, "abc123"),
        RandomUUIDFeeder("uuid"),
        SequentialFeeder("sequence", 100, 5),
        RegexFeeder("regex", "[A-Z]{2}[0-9]{4}"),
        SeparatedValuesFeeder.csv("csvValue", "alpha,beta,gamma"),
        SeparatedValuesFeeder.csv(Optional.of("split"), splitSource),
        RandomPANFeeder("pan", "421345", "541673"),
        RandomNatITNFeeder("natItn"),
        RandomJurITNFeeder("jurItn"),
        RandomOGRNFeeder("ogrn"),
        RandomPSRNSPFeeder("psrnsp"),
        RandomKPPFeeder("kpp"),
        RandomSNILSFeeder("snils"),
        RandomRusPassportFeeder("passport"),
    )
}
