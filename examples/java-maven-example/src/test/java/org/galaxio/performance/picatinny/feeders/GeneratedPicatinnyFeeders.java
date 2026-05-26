package org.galaxio.performance.picatinny.feeders;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;

import static org.galaxio.gatling.javaapi.FakerApi.*;
import static org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder;

public final class GeneratedPicatinnyFeeders {

    private GeneratedPicatinnyFeeders() {
    }

    public static Iterator<Map<String, Object>> generatedUsers() {
        return GeneratedFeeder(
                field("userId", uuidString()),
                field("email", email()),
                field("phone", phoneMobile(countryRU(), phoneFormatE164())),
                field("city", city(countryRU())),
                field("jobTitle", jobTitle())
        );
    }

    public static Iterator<Map<String, Object>> governmentIds() {
        return GeneratedFeeder(
                field("inn", innPerson()),
                field("kpp", kpp()),
                field("ogrn", ogrn()),
                field("snils", snils()),
                field("cpf", cpf(true)),
                field("dni", dni())
        );
    }

    public static Iterator<Map<String, Object>> dates() {
        return GeneratedFeeder(
                field("createdAt", formatDate(datePast(30), "yyyy-MM-dd")),
                field("validFrom", formatDate(dateBetween(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)), "yyyy-MM-dd")),
                field("validTo", formatDate(dateFuture(90), "yyyy-MM-dd"))
        );
    }

    public static Iterator<Map<String, Object>> finance() {
        return GeneratedFeeder(
                field("pan", pan()),
                field("amount", amount(100, 5000)),
                field("currency", currency()),
                field("iban", iban(countryDE())),
                field("transactionId", transactionId())
        );
    }

    // --- Faker.number ---

    public static Iterator<Map<String, Object>> numbers() {
        return GeneratedFeeder(
                field("randomInt", intBetween(1, 1000)),
                field("randomLong", longBetween(1L, 1000000L)),
                field("randomDouble", doubleBetween(0.0, 100.0)),
                field("randomBoolean", booleanValue())
        );
    }

    // --- Faker.string ---

    public static Iterator<Map<String, Object>> strings() {
        return GeneratedFeeder(
                field("alphabeticStr", alphabetic(10)),
                field("alphanumericStr", alphanumeric(12)),
                field("matchingStr", matching("[A-Z]{2}[0-9]{4}")),
                field("numericStr", numeric(8)),
                field("hexStr", hex(16)),
                field("cyrillicStr", cyrillic(6))
        );
    }

    // --- Faker.person ---

    public static Iterator<Map<String, Object>> persons() {
        return GeneratedFeeder(
                field("firstName", firstName()),
                field("lastName", lastName()),
                field("fullName", fullName())
        );
    }

    // --- Faker.internet ---

    public static Iterator<Map<String, Object>> internetData() {
        return GeneratedFeeder(
                field("username", username()),
                field("url", url()),
                field("password", password(20)),
                field("ipv4", ipv4()),
                field("ipv6", ipv6())
        );
    }

    // --- Faker.location ---

    public static Iterator<Map<String, Object>> locations() {
        return GeneratedFeeder(
                field("postalCode", postalCode(countryUS())),
                field("latitude", latitude()),
                field("longitude", longitude())
        );
    }

    // --- Faker.finance extended ---

    public static Iterator<Map<String, Object>> extendedFinance() {
        return GeneratedFeeder(
                field("accountNumber", accountNumber(20)),
                field("bic", bic())
        );
    }

    // --- Faker.commerce ---

    public static Iterator<Map<String, Object>> commerce() {
        return GeneratedFeeder(
                field("productName", productName()),
                field("category", category()),
                field("sku", sku()),
                field("orderId", orderId())
        );
    }

    // --- Country-specific identifiers ---

    public static Iterator<Map<String, Object>> countrySpecificIds() {
        return GeneratedFeeder(
                field("usSSN", ssn()),
                field("gbNINO", nino()),
                field("frNIR", nir()),
                field("esNIF", nif()),
                field("itCodiceFiscale", codiceFiscale()),
                field("deTIN", steueridentifikationsnummer()),
                field("ruInnCompany", innCompany()),
                field("ruOgrnip", ogrnip()),
                field("passportRU", passportRu()),
                field("passportUS", passportNumber(countryUS()))
        );
    }

    // --- Faker.phone ---

    public static Iterator<Map<String, Object>> phones() {
        return GeneratedFeeder(
                field("phoneTollFree", phoneTollFree(countryUS()))
        );
    }

    // --- Faker.lorem ---

    public static Iterator<Map<String, Object>> loremText() {
        return GeneratedFeeder(
                field("loremWord", loremWord()),
                field("loremWords", loremWords(5)),
                field("loremSentence", loremSentence(8))
        );
    }

    // --- GeneratedFeeder.single ---

    public static Iterator<Map<String, Object>> singleFieldFeeder() {
        return GeneratedFeeder("singleInt", intBetween(1, 999));
    }
}
