package org.galaxio.performance.picatinny.feeders;

import org.galaxio.gatling.feeders.faker.Field;

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

    public static Iterator<Map<String, Object>> finance() {
        return GeneratedFeeder(
                field("pan", pan()),
                field("amount", amount(100, 5000)),
                field("currency", currency()),
                field("iban", iban(countryDE())),
                field("transactionId", transactionId())
        );
    }
}
