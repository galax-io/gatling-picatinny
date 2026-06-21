package org.galaxio.performance.picatinny.feeders;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.galaxio.gatling.javaapi.FakerApi.*;
import static org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder;

public final class FeederValidationFeeders {

    private FeederValidationFeeders() {}

    /** Faker GeneratedFeeder fields — values produced by {@link #all()}. */
    public static final List<Map.Entry<String, String>> FAKER_PATTERNS = List.of(
        Map.entry("uuid",       "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"),
        Map.entry("str",        "[a-zA-Z0-9]{12}"),
        Map.entry("innPerson",  "\\d{12}"),
        Map.entry("innCompany", "\\d{10}"),
        Map.entry("snils",      "\\d{11}"),
        Map.entry("pan",        "\\d{16}"),
        Map.entry("ogrn",       "\\d{13}"),
        Map.entry("ogrnip",     "\\d{15}"),
        Map.entry("kpp",        "\\d{9}"),
        Map.entry("passport",   "\\d{10}"),
        Map.entry("date",       "\\d{8}")
    );

    /** Non-deprecated picatinny-core feeders — values already in the session, fed by PicatinnyScenario.
     *  Keys that collide with FAKER_PATTERNS (uuid/pan/ogrn/kpp/snils/passport) are round-tripped under those keys;
     *  RegexFeeder is deprecated and split/zipped feeders carry composite keys, so all are omitted. Loose-format
     *  phones assert non-blank round-trip only. */
    public static final List<Map.Entry<String, String>> PICATINNY_PATTERNS = List.of(
        Map.entry("currentDate",    "\\d{4}-\\d{2}-\\d{2}"),
        Map.entry("randomDate",     "\\d{4}-\\d{2}-\\d{2}"),
        Map.entry("rangeFrom",      "\\d{4}-\\d{2}-\\d{2}"),
        Map.entry("rangeTo",        "\\d{4}-\\d{2}-\\d{2}"),
        Map.entry("digit",          "-?\\d+"),
        Map.entry("customValue",    "custom-\\d+"),
        Map.entry("phone",          ".+"),
        Map.entry("e164Phone",      ".+"),
        Map.entry("formattedPhone", ".+"),
        Map.entry("tollFreePhone",  ".+"),
        Map.entry("randomString",   "[a-zA-Z0-9]{12}"),
        Map.entry("rangeString",    "[abc123]{4,8}"),
        Map.entry("sequence",       "\\d+"),
        Map.entry("csvValue",       "alpha|beta|gamma"),
        Map.entry("natItn",         "\\d{12}"),
        Map.entry("jurItn",         "\\d{10}"),
        Map.entry("psrnsp",         "\\d{15}")
    );

    /** Every field round-tripped over HTTP: faker generators plus non-deprecated picatinny-core feeders. */
    public static final List<Map.Entry<String, String>> PATTERNS = patterns();

    private static List<Map.Entry<String, String>> patterns() {
        List<Map.Entry<String, String>> all = new ArrayList<>(FAKER_PATTERNS);
        all.addAll(PICATINNY_PATTERNS);
        return List.copyOf(all);
    }

    /** Single zipped feeder — one `.feed(all())` advances every generator together. */
    public static Iterator<Map<String, Object>> all() {
        return GeneratedFeeder(
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
            field("date",       formatDate(datePast(1), "yyyyMMdd"))
        );
    }
}
