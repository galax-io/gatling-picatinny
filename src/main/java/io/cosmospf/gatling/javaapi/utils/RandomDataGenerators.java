package io.cosmospf.gatling.javaapi.utils;

import static scala.jdk.javaapi.CollectionConverters.asScala;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.List;

public final class RandomDataGenerators {
    public static String randomString(String alphabet, int n) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomString(alphabet, n);
    }

    public static String digitString(int n) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.digitString(n);
    }

    public static String hexString(int n) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.hexString(n);
    }

    public static String alphanumericString(int stringLength) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.alphanumericString(stringLength);
    }

    public static String randomOnlyLettersString(int stringLength) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomOnlyLettersString(stringLength);
    }

    public static String randomCyrillicString(int n) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomCyrillicString(n);
    }

    public static int randomDigit() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit();
    }

    public static int randomDigit(int max) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit(max);
    }

    public static int randomDigit(int min, int max) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit(min, max);
    }

    public static long randomDigit(long max) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit(max);
    }

    public static long randomDigit(long min, long max) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit(min, max);
    }

    public static double randomDigit(double max) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit(max);
    }

    public static double randomDigit(double min, double max) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit(min, max);
    }

    public static float randomDigit(float max) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit(max);
    }

    public static float randomDigit(float min, float max) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDigit(min, max);
    }

    public static String randomUUID() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomUUID();
    }

    public static String randomPAN(List<String> bins) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomPAN(asScala(bins).toSeq());
    }

    public static String randomOGRN() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomOGRN();
    }

    public static String randomPSRNSP() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomPSRNSP();
    }

    public static String randomKPP() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomKPP();
    }

    public static String randomNatITN() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomNatITN();
    }

    public static String randomJurITN() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomJurITN();
    }

    public static String randomSNILS() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomSNILS();
    }

    public static String randomRusPassport() {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomRusPassport();
    }

    public static String randomDate(
            int positiveDelta,
            int negativeDelta,
            String datePattern,
            LocalDateTime dateFrom,
            TemporalUnit unit,
            ZoneId timezone) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDate(positiveDelta, negativeDelta, datePattern, dateFrom, unit, timezone);
    }

    public static String randomDate(
            long offsetDate,
            LocalDateTime dateFrom,
            TemporalUnit unit,
            ZoneId timezone
    ) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDate(offsetDate, "yyyy-MM-dd", dateFrom, unit, timezone);
    }

    public static String randomDate(
            long offsetDate,
            String datePattern,
            LocalDateTime dateFrom,
            TemporalUnit unit,
            ZoneId timezone
    ) {
        return io.cosmospf.gatling.utils.RandomDataGenerators.randomDate(offsetDate, datePattern, dateFrom, unit, timezone);
    }

    public static String currentDate(DateTimeFormatter datePattern, ZoneId timezone){
        return io.cosmospf.gatling.utils.RandomDataGenerators.currentDate(datePattern, timezone);
    }
}
