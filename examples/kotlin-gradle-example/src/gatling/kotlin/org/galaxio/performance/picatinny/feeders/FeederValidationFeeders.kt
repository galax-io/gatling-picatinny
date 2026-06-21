package org.galaxio.performance.picatinny.feeders

import org.galaxio.gatling.javaapi.FakerApi.*
import org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder

object FeederValidationFeeders {

    /** Faker GeneratedFeeder fields — values produced by [all]. */
    val FAKER_PATTERNS: List<Pair<String, String>> = listOf(
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

    /** Non-deprecated picatinny-core feeders — values already in the session, fed by PicatinnyScenario.
     *  Keys that collide with FAKER_PATTERNS (uuid/pan/ogrn/kpp/snils/passport) are round-tripped under those keys;
     *  RegexFeeder is deprecated and split/zipped feeders carry composite keys, so all are omitted. Loose-format
     *  phones assert non-blank round-trip only. */
    val PICATINNY_PATTERNS: List<Pair<String, String>> = listOf(
        "currentDate"    to "\\d{4}-\\d{2}-\\d{2}",
        "randomDate"     to "\\d{4}-\\d{2}-\\d{2}",
        "rangeFrom"      to "\\d{4}-\\d{2}-\\d{2}",
        "rangeTo"        to "\\d{4}-\\d{2}-\\d{2}",
        "digit"          to "-?\\d+",
        "customValue"    to "custom-\\d+",
        "phone"          to ".+",
        "e164Phone"      to ".+",
        "formattedPhone" to ".+",
        "tollFreePhone"  to ".+",
        "randomString"   to "[a-zA-Z0-9]{12}",
        "rangeString"    to "[abc123]{4,8}",
        "sequence"       to "\\d+",
        "csvValue"       to "alpha|beta|gamma",
        "natItn"         to "\\d{12}",
        "jurItn"         to "\\d{10}",
        "psrnsp"         to "\\d{15}",
    )

    /** Every field round-tripped over HTTP: faker generators plus non-deprecated picatinny-core feeders. */
    val PATTERNS: List<Pair<String, String>> = FAKER_PATTERNS + PICATINNY_PATTERNS

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
