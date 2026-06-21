package org.galaxio.performance.picatinny.feeders

import org.galaxio.gatling.javaapi.FakerApi.*
import org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder

object FeederValidationFeeders {

    /** (field, regex-pattern) pairs — source of truth for headers, mock body, and response checks. */
    val PATTERNS: List<Pair<String, String>> = listOf(
        "uuid"       to "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
        "str"        to "[a-zA-Z0-9]{12}",
        "innPerson"  to "\\d{12}",
        "innCompany" to "\\d{10}",
        "snils"      to "\\d{11}",
        "pan"        to "\\d{16}",
        "ogrn"       to "\\d{13}",
        "ogrnip"     to "\\d{15}",
        "kpp"        to "\\d{9}",
        "passport"   to "\\d{10}",
        "date"       to "\\d{8}",
    )

    /** Single zipped feeder — one `.feed(all())` advances every generator together. */
    fun all() = GeneratedFeeder(
        field("uuid",       uuidString()),
        field("str",        alphanumeric(12)),
        field("innPerson",  innPerson()),
        field("innCompany", innCompany()),
        field("snils",      snils()),
        field("pan",        pan()),
        field("ogrn",       ogrn()),
        field("ogrnip",     ogrnip()),
        field("kpp",        kpp()),
        field("passport",   passportRu()),
        field("date",       formatDate(datePast(1), "yyyyMMdd")),
    )
}
