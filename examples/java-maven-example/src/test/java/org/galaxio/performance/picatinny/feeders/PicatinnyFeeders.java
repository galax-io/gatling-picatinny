package org.galaxio.performance.picatinny.feeders;

import org.galaxio.gatling.javaapi.utils.phone.PhoneFormatBuilder;
import org.galaxio.gatling.javaapi.utils.phone.TypePhone;
import org.galaxio.gatling.utils.phone.PhoneFormat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.galaxio.gatling.javaapi.Feeders.*;

/** Legacy Random*Feeder examples (deprecated — see GeneratedPicatinnyFeeders for the new Faker API). */
public final class PicatinnyFeeders {

    private PicatinnyFeeders() {
    }

    private static final PhoneFormat RU_MOBILE_FORMAT = PhoneFormatBuilder.apply(
            "+7", 10, List.of("903", "906"), "+X XXX XXX-XX-XX", List.of("123", "321")
    );

    public static Iterator<Map<String, Object>> uuid() {
        return RandomUUIDFeeder("uuid");
    }

    public static Iterator<Map<String, Object>> currentDate() {
        return CurrentDateFeeder("currentDate", DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static Iterator<Map<String, Object>> randomDate() {
        return RandomDateFeeder("randomDate", 3, 1);
    }

    public static Iterator<Map<String, Object>> dateRange() {
        return RandomDateRangeFeeder("rangeFrom", "rangeTo", 2L, "yyyy-MM-dd",
                LocalDateTime.now(), ChronoUnit.DAYS, ZoneId.of("UTC"));
    }

    public static Iterator<Map<String, Object>> digit() {
        return RandomDigitFeeder("digit");
    }

    public static Iterator<Map<String, Object>> customValue() {
        return CustomFeeder("customValue", () -> "custom-" + new Random().nextInt(1000));
    }

    public static Iterator<Map<String, Object>> phone() {
        return RandomPhoneFeeder("phone");
    }

    public static Iterator<Map<String, Object>> e164Phone() {
        return RandomPhoneFeeder("e164Phone", TypePhone.E164PhoneNumber(), RU_MOBILE_FORMAT);
    }

    public static Iterator<Map<String, Object>> formattedPhone() {
        return RandomPhoneFeeder("formattedPhone", TypePhone.PhoneNumber(), RU_MOBILE_FORMAT);
    }

    public static Iterator<Map<String, Object>> tollFreePhone() {
        return RandomPhoneFeeder("tollFreePhone", "phoneTemplates/ru.json", TypePhone.TollFreePhoneNumber());
    }

    public static Iterator<Map<String, Object>> randomString() {
        return RandomStringFeeder("randomString", 12);
    }

    public static Iterator<Map<String, Object>> rangeString() {
        return RandomRangeStringFeeder("rangeString", 4, 8, "abc123");
    }

    public static Iterator<Map<String, Object>> sequence() {
        return SequentialFeeder("sequence", 100, 5);
    }

    public static Iterator<Map<String, Object>> regex() {
        return RegexFeeder("regex", "[A-Z]{2}[0-9]{4}");
    }

    public static Iterator<Map<String, Object>> csvValue() {
        return SeparatedValuesFeeder.csv("csvValue", "alpha,beta,gamma");
    }

    public static Iterator<Map<String, Object>> splitValues() {
        return SeparatedValuesFeeder.csv(
                Optional.of("split"),
                List.of(Map.of("HOSTS", "host1,host2", "USERS", "user1,user2"))
        );
    }

    public static Iterator<Map<String, Object>> pan() {
        return RandomPANFeeder("pan", "421345", "541673");
    }

    public static Iterator<Map<String, Object>> natItn() {
        return RandomNatITNFeeder("natItn");
    }

    public static Iterator<Map<String, Object>> jurItn() {
        return RandomJurITNFeeder("jurItn");
    }

    public static Iterator<Map<String, Object>> ogrn() {
        return RandomOGRNFeeder("ogrn");
    }

    public static Iterator<Map<String, Object>> psrnsp() {
        return RandomPSRNSPFeeder("psrnsp");
    }

    public static Iterator<Map<String, Object>> kpp() {
        return RandomKPPFeeder("kpp");
    }

    public static Iterator<Map<String, Object>> snils() {
        return RandomSNILSFeeder("snils");
    }

    public static Iterator<Map<String, Object>> passport() {
        return RandomRusPassportFeeder("passport");
    }

    public static List<Iterator<Map<String, Object>>> all() {
        return List.of(
                uuid(), currentDate(), randomDate(), dateRange(), digit(), customValue(),
                phone(), e164Phone(), formattedPhone(), tollFreePhone(),
                randomString(), rangeString(), sequence(), regex(),
                csvValue(), splitValues(),
                pan(), natItn(), jurItn(), ogrn(), psrnsp(), kpp(), snils(), passport()
        );
    }
}
