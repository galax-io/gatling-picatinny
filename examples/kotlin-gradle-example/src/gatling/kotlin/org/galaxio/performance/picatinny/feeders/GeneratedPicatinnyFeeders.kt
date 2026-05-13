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

    fun finance() = GeneratedFeeder(
        field("pan", pan()),
        field("amount", amount(100.0, 5000.0)),
        field("currency", currency()),
        field("iban", iban(countryDE())),
        field("transactionId", transactionId()),
    )
}
