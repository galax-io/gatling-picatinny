package org.galaxio.performance.picatinny.feeders

import org.galaxio.gatling.javaapi.FakerApi.*
import org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder

object GeneratedPicatinnyFeeders {

    fun generatedUsers() = GeneratedFeeder(
        field("userId", uuidString()),
        field("email", email()),
        field("phone", phoneMobile(countryRU(), phoneFormatE164())),
        field("city", city(countryRU())),
        field("jobTitle", jobTitle()),
    )

    fun governmentIds() = GeneratedFeeder(
        field("inn", innPerson()),
        field("kpp", kpp()),
        field("ogrn", ogrn()),
        field("snils", snils()),
        field("cpf", cpf(true)),
        field("dni", dni()),
    )

    fun dates() = GeneratedFeeder(
        field("createdAt", formatDate(datePast(30), "yyyy-MM-dd")),
        field("validFrom", formatDate(dateBetween(
            java.time.LocalDate.of(2026, 1, 1),
            java.time.LocalDate.of(2026, 6, 30),
        ), "yyyy-MM-dd")),
        field("validTo", formatDate(dateFuture(90), "yyyy-MM-dd")),
    )

    fun finance() = GeneratedFeeder(
        field("pan", pan()),
        field("amount", amount(100.0, 5000.0)),
        field("currency", currency()),
        field("iban", iban(countryDE())),
        field("transactionId", transactionId()),
    )

    // --- Faker.number ---

    fun numbers() = GeneratedFeeder(
        field("randomInt", intBetween(1, 1000)),
        field("randomLong", longBetween(1L, 1000000L)),
        field("randomDouble", doubleBetween(0.0, 100.0)),
        field("randomBoolean", booleanValue()),
    )

    // --- Faker.string ---

    fun strings() = GeneratedFeeder(
        field("alphabeticStr", alphabetic(10)),
        field("alphanumericStr", alphanumeric(12)),
        field("matchingStr", matching("[A-Z]{2}[0-9]{4}")),
        field("numericStr", numeric(8)),
        field("hexStr", hex(16)),
        field("cyrillicStr", cyrillic(6)),
    )

    // --- Faker.person ---

    fun persons() = GeneratedFeeder(
        field("firstName", firstName()),
        field("lastName", lastName()),
        field("fullName", fullName()),
    )

    // --- Faker.internet ---

    fun internetData() = GeneratedFeeder(
        field("username", username()),
        field("url", url()),
        field("password", password(20)),
        field("ipv4", ipv4()),
        field("ipv6", ipv6()),
    )

    // --- Faker.location ---

    fun locations() = GeneratedFeeder(
        field("postalCode", postalCode(countryUS())),
        field("latitude", latitude()),
        field("longitude", longitude()),
    )

    // --- Faker.finance extended ---

    fun extendedFinance() = GeneratedFeeder(
        field("accountNumber", accountNumber(20)),
        field("bic", bic()),
    )

    // --- Faker.commerce ---

    fun commerce() = GeneratedFeeder(
        field("productName", productName()),
        field("category", category()),
        field("sku", sku()),
        field("orderId", orderId()),
    )

    // --- Country-specific identifiers ---

    fun countrySpecificIds() = GeneratedFeeder(
        field("usSSN", ssn()),
        field("gbNINO", nino()),
        field("frNIR", nir()),
        field("esNIF", nif()),
        field("itCodiceFiscale", codiceFiscale()),
        field("deTIN", steueridentifikationsnummer()),
        field("ruInnCompany", innCompany()),
        field("ruOgrnip", ogrnip()),
        field("passportRU", passportRu()),
        field("passportUS", passportNumber(countryUS())),
    )

    // --- Faker.phone ---

    fun phones() = GeneratedFeeder(
        field("phoneTollFree", phoneTollFree(countryUS())),
    )

    // --- Faker.lorem ---

    fun loremText() = GeneratedFeeder(
        field("loremWord", loremWord()),
        field("loremWords", loremWords(5)),
        field("loremSentence", loremSentence(8)),
    )

    // --- GeneratedFeeder.single ---

    fun singleFieldFeeder() = GeneratedFeeder("singleInt", intBetween(1, 999))
}
