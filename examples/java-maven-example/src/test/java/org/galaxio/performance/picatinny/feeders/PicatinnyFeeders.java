package org.galaxio.performance.picatinny.feeders;

import org.galaxio.gatling.javaapi.utils.phone.PhoneFormatBuilder;
import org.galaxio.gatling.javaapi.utils.phone.TypePhone;
import org.galaxio.gatling.utils.phone.PhoneFormat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.galaxio.gatling.javaapi.Feeders.CurrentDateFeeder;
import static org.galaxio.gatling.javaapi.Feeders.CustomFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomDateFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomDateRangeFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomDigitFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomJurITNFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomKPPFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomNatITNFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomOGRNFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomPANFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomPSRNSPFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomPhoneFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomRangeStringFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomRusPassportFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomSNILSFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomStringFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RandomUUIDFeeder;
import static org.galaxio.gatling.javaapi.Feeders.RegexFeeder;
import static org.galaxio.gatling.javaapi.Feeders.SeparatedValuesFeeder;
import static org.galaxio.gatling.javaapi.Feeders.SequentialFeeder;

public final class PicatinnyFeeders {
    private static final PhoneFormat RU_MOBILE_FORMAT =
            PhoneFormatBuilder.apply("+7", 10, List.of("903", "906"), "+X XXX XXX-XX-XX", List.of("123", "321"));

    private PicatinnyFeeders() {
    }

    public static List<Iterator<Map<String, Object>>> all() {
        return List.of(
                CurrentDateFeeder("currentDate", DateTimeFormatter.ISO_LOCAL_DATE),
                RandomDateFeeder("randomDate", 3, 1),
                RandomDateRangeFeeder("rangeFrom", "rangeTo", 2L, "yyyy-MM-dd", LocalDateTime.now(), ChronoUnit.DAYS, ZoneId.of("UTC")),
                RandomDigitFeeder("digit"),
                CustomFeeder("customValue", () -> "custom-" + ThreadLocalRandom.current().nextInt(1000)),
                RandomPhoneFeeder("phone"),
                RandomPhoneFeeder("phoneFromJson", "phoneTemplates/ru.json", TypePhone.E164PhoneNumber()),
                RandomPhoneFeeder("formattedPhone", TypePhone.PhoneNumber(), RU_MOBILE_FORMAT),
                RandomStringFeeder("randomString", 12),
                RandomRangeStringFeeder("rangeString", 4, 8, "abc123"),
                RandomUUIDFeeder("uuid"),
                SequentialFeeder("sequence", 100, 5),
                RegexFeeder("regex", "[A-Z]{2}[0-9]{4}"),
                SeparatedValuesFeeder.csv("csvValue", "alpha,beta,gamma"),
                SeparatedValuesFeeder.csv(Optional.of("split"), List.of(Map.of("HOSTS", "host1,host2", "USERS", "user1,user2"))),
                RandomPANFeeder("pan", "421345", "541673"),
                RandomNatITNFeeder("natItn"),
                RandomJurITNFeeder("jurItn"),
                RandomOGRNFeeder("ogrn"),
                RandomPSRNSPFeeder("psrnsp"),
                RandomKPPFeeder("kpp"),
                RandomSNILSFeeder("snils"),
                RandomRusPassportFeeder("passport")
        );
    }
}
