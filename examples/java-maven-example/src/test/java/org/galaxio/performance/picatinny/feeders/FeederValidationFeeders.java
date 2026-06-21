package org.galaxio.performance.picatinny.feeders;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.galaxio.gatling.javaapi.FakerApi.*;
import static org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder;

public final class FeederValidationFeeders {

    private FeederValidationFeeders() {}

    /** (field, regex-pattern) pairs — source of truth for headers, mock body, and response checks. */
    public static final List<Map.Entry<String, String>> PATTERNS = List.of(
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
