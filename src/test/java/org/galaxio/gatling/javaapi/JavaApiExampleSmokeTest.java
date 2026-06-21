package org.galaxio.gatling.javaapi;

import org.galaxio.gatling.javaapi.utils.IntensityConverter;
import org.galaxio.gatling.javaapi.utils.Jwt;
import org.galaxio.gatling.javaapi.utils.phone.PhoneFormatBuilder;
import org.galaxio.gatling.javaapi.utils.phone.TypePhone;
import org.galaxio.gatling.utils.jwt.JwtGeneratorBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.galaxio.gatling.javaapi.FakerApi.*;
import static org.galaxio.gatling.javaapi.Feeders.*;

@DisplayName("Java/Kotlin API smoke tests for examples")
class JavaApiExampleSmokeTest {

    private static Map<String, Object> next(Iterator<Map<String, Object>> feeder) {
        assertThat(feeder.hasNext()).isTrue();
        return feeder.next();
    }

    @Nested
    @DisplayName("Legacy feeders (Java API wrappers)")
    class LegacyFeeders {

        @Test
        void uuidFeederProducesRfc4122Format() {
            var record = next(RandomUUIDFeeder("uuid"));
            assertThat(record.get("uuid").toString())
                    .hasSize(36)
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        void currentDateFeederProducesIsoDate() {
            var record = next(CurrentDateFeeder("date", DateTimeFormatter.ISO_LOCAL_DATE));
            var date = record.get("date").toString();
            assertThat(date).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(LocalDate.parse(date)).isNotNull();
        }

        @Test
        void randomDateFeederProducesValidDate() {
            var record = next(RandomDateFeeder("rd", 3, 1));
            assertThat(record.get("rd").toString()).matches("\\d{4}-\\d{2}-\\d{2}");
        }

        @Test
        void dateRangeFeederProducesOrderedDates() {
            var record = next(RandomDateRangeFeeder("from", "to", 2L, "yyyy-MM-dd",
                    java.time.LocalDateTime.now(), java.time.temporal.ChronoUnit.DAYS, java.time.ZoneId.of("UTC")));
            var from = LocalDate.parse(record.get("from").toString());
            var to = LocalDate.parse(record.get("to").toString());
            assertThat(from).isBeforeOrEqualTo(to);
        }

        @Test
        void digitFeederProducesInteger() {
            var record = next(RandomDigitFeeder("digit"));
            assertThat(record.get("digit")).isInstanceOf(Integer.class);
        }

        @Test
        void customFeederUsesSupplierFunction() {
            var record = next(CustomFeeder("v", () -> "constant-42"));
            assertThat(record.get("v")).isEqualTo("constant-42");
        }

        @Test
        void phoneFeederProducesDigitString() {
            var phone = next(RandomPhoneFeeder("phone")).get("phone").toString();
            assertThat(phone).matches("\\+?\\d{6,15}");
        }

        @Test
        void formattedPhoneMatchesRuFormat() {
            var fmt = PhoneFormatBuilder.apply("+7", 10, List.of("903", "906"), "+X XXX XXX-XX-XX", List.of("123"));
            var phone = next(RandomPhoneFeeder("p", TypePhone.PhoneNumber(), fmt)).get("p").toString();
            assertThat(phone).startsWith("+7").matches("\\+7 \\d{3} \\d{3}-\\d{2}-\\d{2}");
        }

        @Test
        void tollFreePhoneMatchesPattern() {
            var toll = next(RandomPhoneFeeder("toll", TypePhone.TollFreePhoneNumber())).get("toll").toString();
            assertThat(toll).matches("\\(\\d{3}\\) \\d{3}-\\d{4}");
        }

        @Test
        void randomStringHasExactLength() {
            assertThat(next(RandomStringFeeder("s", 12)).get("s").toString()).hasSize(12);
        }

        @Test
        void rangeStringWithinBounds() {
            var s = next(RandomRangeStringFeeder("rs", 4, 8, "abc")).get("rs").toString();
            assertThat(s.length()).isBetween(4, 8);
            assertThat(s).matches("[abc]+");
        }

        @Test
        void sequentialFeederIncrementsByStep() {
            var f = SequentialFeeder("seq", 100, 5);
            assertThat(next(f).get("seq")).isEqualTo(100L);
            assertThat(next(f).get("seq")).isEqualTo(105L);
            assertThat(next(f).get("seq")).isEqualTo(110L);
        }

        @Test
        void regexFeederMatchesPattern() {
            var v = next(RegexFeeder("rx", "[A-Z]{2}[0-9]{4}")).get("rx").toString();
            assertThat(v).hasSize(6).matches("[A-Z]{2}[0-9]{4}");
        }

        @Test
        void panFeederProduces16DigitsWithBin() {
            var pan = next(RandomPANFeeder("pan", "421345", "541673")).get("pan").toString();
            assertThat(pan).hasSize(16).matches("\\d+");
        }

        static Stream<Arguments> govIdFeeders() {
            return Stream.of(
                    Arguments.of("NatITN", RandomNatITNFeeder("v"), 12),
                    Arguments.of("JurITN", RandomJurITNFeeder("v"), 10),
                    Arguments.of("OGRN", RandomOGRNFeeder("v"), 13),
                    Arguments.of("KPP", RandomKPPFeeder("v"), 9),
                    Arguments.of("SNILS", RandomSNILSFeeder("v"), 11),
                    Arguments.of("RusPassport", RandomRusPassportFeeder("v"), 10)
            );
        }

        @ParameterizedTest(name = "{0} has {2} digits")
        @MethodSource("govIdFeeders")
        void governmentIdFeederProducesCorrectLength(String name, Iterator<Map<String, Object>> feeder, int length) {
            var value = next(feeder).get("v").toString();
            assertThat(value).hasSize(length).matches("\\d+");
        }

        @Test
        void psrnspFeederProduces15DigitsStartingWith3() {
            var value = next(RandomPSRNSPFeeder("v")).get("v").toString();
            assertThat(value).hasSize(15).startsWith("3").matches("\\d+");
        }

        @Test
        void separatedValuesFeederParsesCorrectly() {
            var records = Feeders.SeparatedValuesFeeder.csv("csv", "alpha,beta,gamma");
            var first = next(records);
            assertThat(first).containsKey("csv");
        }
    }

    @Nested
    @DisplayName("Faker API (Java/Kotlin wrappers)")
    class FakerApiTests {

        @Test
        void generatedUserFieldsHaveCorrectFormat() {
            var record = next(GeneratedFeeder(
                    field("userId", uuidString()),
                    field("email", email()),
                    field("phone", phoneMobile(countryRU(), phoneFormatE164()))
            ));
            assertThat(record.get("userId").toString())
                    .hasSize(36)
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(record.get("email").toString()).contains("@").contains(".");
            assertThat(record.get("phone").toString()).matches("\\+\\d{10,15}");
        }

        static Stream<Arguments> govIdGenerators() {
            return Stream.of(
                    Arguments.of("innPerson", innPerson(), 12, "\\d+"),
                    Arguments.of("innCompany", innCompany(), 10, "\\d+"),
                    Arguments.of("kpp", kpp(), 9, "\\d+"),
                    Arguments.of("ogrn", ogrn(), 13, "\\d+"),
                    Arguments.of("snils", snils(), 11, "\\d+"),
                    Arguments.of("passportRu", passportRu(), 10, "\\d+"),
                    Arguments.of("cpf", cpf(true), -1, "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}"),
                    Arguments.of("ssn", ssn(), -1, "\\d{3}-\\d{2}-\\d{4}"),
                    Arguments.of("nino", nino(), -1, "[A-Z]{2}\\d{6}[A-D]")
            );
        }

        @SuppressWarnings("unchecked")
        @ParameterizedTest(name = "{0} matches expected format")
        @MethodSource("govIdGenerators")
        void governmentIdHasCorrectFormat(String name, org.galaxio.gatling.feeders.faker.Generator<?> generator, int length, String regex) {
            var record = next(GeneratedFeeder(field("v", (org.galaxio.gatling.feeders.faker.Generator<Object>) generator)));
            var value = record.get("v").toString();
            if (length > 0) assertThat(value).hasSize(length);
            assertThat(value).matches(regex);
        }

        @Test
        void dateGeneratorsProduceValidDates() {
            var record = next(GeneratedFeeder(
                    field("past", formatDate(datePast(30), "yyyy-MM-dd")),
                    field("future", formatDate(dateFuture(90), "yyyy-MM-dd")),
                    field("between", formatDate(dateBetween(
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)), "yyyy-MM-dd"))
            ));
            var past = LocalDate.parse(record.get("past").toString());
            assertThat(past).isBeforeOrEqualTo(LocalDate.now());
            assertThat(record.get("future").toString()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(record.get("between").toString()).matches("\\d{4}-\\d{2}-\\d{2}");
        }

        @Test
        void financeFieldsHaveStructuralValidity() {
            var record = next(GeneratedFeeder(
                    field("pan", pan()),
                    field("currency", currency()),
                    field("iban", iban(countryDE())),
                    field("account", accountNumber(20))
            ));
            assertThat(record.get("pan").toString()).matches("\\d+").hasSizeGreaterThanOrEqualTo(16);
            assertThat(record.get("currency").toString()).hasSize(3);
            assertThat(record.get("iban").toString()).startsWith("DE").hasSizeGreaterThanOrEqualTo(22);
            assertThat(record.get("account").toString()).hasSize(20).matches("\\d+");
        }

        @Test
        void numberGeneratorsRespectBounds() {
            var record = next(GeneratedFeeder(
                    field("int", intBetween(1, 1000)),
                    field("long", longBetween(1L, 1000000L)),
                    field("double", doubleBetween(0.0, 100.0)),
                    field("bool", booleanValue())
            ));
            assertThat((Integer) record.get("int")).isBetween(1, 1000);
            assertThat((Long) record.get("long")).isBetween(1L, 1000000L);
            assertThat((Double) record.get("double")).isBetween(0.0, 100.0);
            assertThat(record.get("bool")).isInstanceOf(Boolean.class);
        }

        static Stream<Arguments> stringGenerators() {
            return Stream.of(
                    Arguments.of("alpha", alphabetic(10), 10, "[a-zA-Z]+"),
                    Arguments.of("alphanum", alphanumeric(12), 12, "[a-zA-Z0-9]+"),
                    Arguments.of("matching", matching("[A-Z]{2}[0-9]{4}"), 6, "[A-Z]{2}[0-9]{4}"),
                    Arguments.of("num", numeric(8), 8, "\\d+"),
                    Arguments.of("hex", hex(16), 16, "[0-9a-f]+"),
                    Arguments.of("cyr", cyrillic(6), 6, ".+")
            );
        }

        @SuppressWarnings("unchecked")
        @ParameterizedTest(name = "{0} has length {2}")
        @MethodSource("stringGenerators")
        void stringGeneratorHasExactLength(String name, org.galaxio.gatling.feeders.faker.Generator<?> generator, int length, String regex) {
            var value = next(GeneratedFeeder(field("v", (org.galaxio.gatling.feeders.faker.Generator<Object>) generator))).get("v").toString();
            assertThat(value).hasSize(length).matches(regex);
        }

        @Test
        void regexGeneratorProducesFreshValues() {
            var feeder = GeneratedFeeder(field("rx", matching("[A-Z]{2}[0-9]{4}")));
            var values = new LinkedHashSet<String>();
            for (int i = 0; i < 20; i++) {
                values.add(next(feeder).get("rx").toString());
            }
            assertThat(values).hasSizeGreaterThan(1);
            assertThat(values).allMatch(value -> value.matches("[A-Z]{2}[0-9]{4}"));
        }

        @Test
        void personFieldsHaveMinimumLength() {
            var record = next(GeneratedFeeder(
                    field("first", firstName()),
                    field("last", lastName()),
                    field("full", fullName())
            ));
            assertThat(record.get("first").toString()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(record.get("last").toString()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(record.get("full").toString()).contains(" ");
        }

        @Test
        void internetFieldsHaveStructuralValidity() {
            var record = next(GeneratedFeeder(
                    field("user", username()),
                    field("url", url()),
                    field("pass", password(20)),
                    field("ip4", ipv4()),
                    field("ip6", ipv6())
            ));
            assertThat(record.get("user").toString()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(record.get("url").toString()).startsWith("http").contains("://");
            assertThat(record.get("pass").toString()).hasSizeGreaterThanOrEqualTo(20);
            assertThat(record.get("ip4").toString()).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
            assertThat(record.get("ip6").toString()).contains(":");
        }

        @Test
        void locationFieldsHaveValidCoordinates() {
            var record = next(GeneratedFeeder(
                    field("lat", latitude()),
                    field("lon", longitude()),
                    field("zip", postalCode(countryUS()))
            ));
            assertThat((Double) record.get("lat")).isBetween(-90.0, 90.0);
            assertThat((Double) record.get("lon")).isBetween(-180.0, 180.0);
            assertThat(record.get("zip").toString()).matches("\\d{5}");
        }

        @Test
        void commerceFieldsHavePrefixes() {
            var record = next(GeneratedFeeder(
                    field("product", productName()),
                    field("cat", category()),
                    field("sku", sku()),
                    field("order", orderId())
            ));
            assertThat(record.get("product").toString()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(record.get("cat").toString()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(record.get("sku").toString()).startsWith("SKU-");
            assertThat(record.get("order").toString()).startsWith("ord-");
        }

        @Test
        void loremTextHasMinimumContent() {
            var record = next(GeneratedFeeder(
                    field("word", loremWord()),
                    field("words", loremWords(5)),
                    field("sentence", loremSentence(8))
            ));
            assertThat(record.get("word").toString()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(record.get("sentence").toString().split("\\s+")).hasSizeGreaterThanOrEqualTo(6);
        }

        @Test
        void singleFieldFeederProducesValueInBounds() {
            var record = next(GeneratedFeeder("singleInt", intBetween(1, 999)));
            assertThat((Integer) record.get("singleInt")).isBetween(1, 999);
        }
    }

    @Nested
    @DisplayName("JWT (Java API)")
    class JwtTests {

        @Test
        void jwtProducesValidThreePartToken() {
            JwtGeneratorBuilder gen = Jwt.jwt("HS256", "performance-secret")
                    .defaultHeader()
                    .payload("{\"subject\":\"picatinny\",\"scope\":\"java-smoke\"}");

            var scalaSession = io.gatling.core.session.Session$.MODULE$.apply(
                    "smoke", 1L, new org.galaxio.gatling.transactions.FakeEventLoop());
            var session = new io.gatling.javaapi.core.Session(scalaSession);
            var result = Jwt.setJwt(gen, "jwt").apply(session);
            var token = result.getString("jwt");

            assertThat(token.split("\\.")).hasSize(3);
        }
    }

    @Nested
    @DisplayName("IntensityConverter (Java API)")
    class IntensityConverterTests {

        @ParameterizedTest(name = "{0} {1} = {2} RPS")
        @CsvSource({
                "60.0, rpm, 1.0",
                "120.0, rpm, 2.0",
                "3600.0, rph, 1.0",
                "7200.0, rph, 2.0",
                "5.0, rps, 5.0"
        })
        void intensityConversion(double input, String unit, double expected) {
            double result = switch (unit) {
                case "rpm" -> IntensityConverter.rpm(input);
                case "rph" -> IntensityConverter.rph(input);
                case "rps" -> IntensityConverter.rps(input);
                default -> throw new IllegalArgumentException(unit);
            };
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest(name = "parse \"{0}\" = {1} RPS")
        @CsvSource({
                "3600 rph, 1.0",
                "60 rpm, 1.0"
        })
        void intensityFromStringParses(String input, double expected) {
            assertThat(IntensityConverter.getIntensityFromString(input)).isEqualTo(expected);
        }
    }
}
