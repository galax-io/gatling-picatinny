package org.galaxio.gatling.javaapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.galaxio.gatling.javaapi.utils.IntensityConverter.getIntensityFromString;
import static org.galaxio.gatling.javaapi.utils.IntensityConverter.rph;
import static org.galaxio.gatling.javaapi.utils.IntensityConverter.rpm;
import static org.galaxio.gatling.javaapi.utils.IntensityConverter.rps;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.galaxio.gatling.javaapi.utils.RandomDataGenerators;
import org.galaxio.gatling.javaapi.utils.RandomPhoneGenerator;
import org.galaxio.gatling.javaapi.utils.phone.PhoneFormatBuilder;
import org.galaxio.gatling.javaapi.utils.phone.TypePhone;
import org.galaxio.gatling.utils.phone.PhoneFormat;
import org.junit.jupiter.api.Test;

class JavaUtilsTest {
    private static final PhoneFormat RU_MOBILE_FORMAT =
            PhoneFormatBuilder.apply("+7", 10, List.of("945", "946"), "+X XXX XXX-XX-XX", List.of("123", "321"));

    private static final PhoneFormat RU_CITY_FORMAT =
            PhoneFormatBuilder.apply("+7", 10, List.of("945", "946"), "+X XXX XXX-XX-XX");

    @Test
    void shouldConvertIntensityValuesToRequestsPerSecond() {
        assertThat(rph(3600.0)).isEqualTo(1.0);
        assertThat(rpm(60.0)).isEqualTo(1.0);
        assertThat(rps(42.0)).isEqualTo(42.0);
        assertThat(getIntensityFromString("30")).isEqualTo(30.0);
    }

    @Test
    void shouldGenerateRandomPhoneThroughJavaFacade() {
        String phone = RandomPhoneGenerator.randomPhone(List.of(RU_MOBILE_FORMAT, RU_CITY_FORMAT), TypePhone.E164PhoneNumber());

        assertThat(phone).matches("^\\+?\\d{6,15}$");
    }

    @Test
    void shouldGenerateBoundedRandomDataThroughJavaFacade() {
        assertThat(RandomDataGenerators.randomString("abc", 12)).hasSize(12).matches("[abc]+");
        assertThat(RandomDataGenerators.digitString(4)).hasSize(4).containsOnlyDigits();
        assertThat(RandomDataGenerators.hexString(8)).hasSize(8).matches("[a-f0-9]+");
        assertThat(RandomDataGenerators.alphanumericString(8)).hasSize(8).matches("[A-Za-z0-9]+");
        assertThat(RandomDataGenerators.randomOnlyLettersString(8)).hasSize(8).matches("[A-Za-z]+");
        assertThat(RandomDataGenerators.randomCyrillicString(8)).hasSize(8);
        assertThat(RandomDataGenerators.randomDigit(1, 3)).isBetween(1, 3);
        assertThat(RandomDataGenerators.randomDigit(3_000_000_000L, 3_000_000_005L)).isBetween(3_000_000_000L, 3_000_000_005L);
        assertThat(RandomDataGenerators.randomDigit(2.5, 3.6)).isBetween(2.5, 3.6);
        assertThat(RandomDataGenerators.randomDigit(2.5f, 3.6f)).isBetween(2.5f, 3.6f);
    }

    @Test
    void shouldGenerateDomainIdentifiersThroughJavaFacade() {
        assertThat(RandomDataGenerators.randomUUID()).matches("[a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8}");
        assertThat(RandomDataGenerators.randomPAN(List.of("123456"))).startsWith("123456").hasSizeGreaterThanOrEqualTo(16);
        assertThat(RandomDataGenerators.randomOGRN()).hasSize(13).containsOnlyDigits();
        assertThat(RandomDataGenerators.randomPSRNSP()).hasSize(15).containsOnlyDigits();
        assertThat(RandomDataGenerators.randomKPP()).hasSize(9).containsOnlyDigits();
        assertThat(RandomDataGenerators.randomNatITN()).hasSize(12).containsOnlyDigits(); // natural person = 12 digits
        assertThat(RandomDataGenerators.randomJurITN()).hasSize(10).containsOnlyDigits(); // legal entity = 10 digits
        assertThat(RandomDataGenerators.randomSNILS()).hasSize(11).containsOnlyDigits();
        assertThat(RandomDataGenerators.randomRusPassport()).hasSize(10).containsOnlyDigits();
    }

    @Test
    void shouldGenerateDatesThroughJavaFacade() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 7, 12, 0);
        ZoneId zone = ZoneId.of("UTC");

        assertThat(RandomDataGenerators.randomDate(2, 1, "yyyy.MM.dd", now, ChronoUnit.DAYS, zone)).matches("\\d{4}\\.\\d{2}\\.\\d{2}");
        assertThat(RandomDataGenerators.randomDate(1, now, ChronoUnit.DAYS, zone)).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(RandomDataGenerators.randomDate(1, "yyyy-MM-dd", now, ChronoUnit.DAYS, zone)).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(RandomDataGenerators.currentDate(DateTimeFormatter.ofPattern("MM:dd"), zone)).matches("\\d{2}:\\d{2}");
    }
}
