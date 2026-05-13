package org.galaxio.performance.picatinny.feeders

import org.galaxio.gatling.javaapi.Feeders.CurrentDateFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomPANFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomPhoneFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomStringFeeder
import org.galaxio.gatling.javaapi.Feeders.RandomUUIDFeeder
import org.galaxio.gatling.javaapi.utils.phone.TypePhone
import java.time.format.DateTimeFormatter

/** Legacy Random*Feeder examples (deprecated — see GeneratedPicatinnyFeeders for the new Faker API). */
object PicatinnyFeeders {

    fun legacy(): List<Iterator<Map<String, Any>>> = listOf(
        RandomUUIDFeeder("uuid"),
        CurrentDateFeeder("currentDate", DateTimeFormatter.ISO_LOCAL_DATE),
        RandomPhoneFeeder("phoneFromJson", "phoneTemplates/ru.json", TypePhone.E164PhoneNumber()),
        RandomStringFeeder("randomString", 12),
        RandomPANFeeder("pan", "421345", "541673"),
    )
}
