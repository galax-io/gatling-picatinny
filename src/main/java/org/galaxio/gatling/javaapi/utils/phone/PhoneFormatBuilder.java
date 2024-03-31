package org.galaxio.gatling.javaapi.utils.phone;

import static scala.jdk.javaapi.CollectionConverters.asScala;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class PhoneFormatBuilder {

    public static org.galaxio.gatling.utils.phone.PhoneFormat apply(
            String countryCode,
            Integer length,
            List<String> areaCodes,
            String format,
            List<String> prefixes
    ) {
        return org.galaxio.gatling.utils.phone.PhoneFormat.apply(
                countryCode, length, asScala(areaCodes).toSeq(), format, asScala(prefixes).toSeq()
        );
    }

    public static org.galaxio.gatling.utils.phone.PhoneFormat apply(
            String countryCode,
            Integer length,
            List<String> areaCodes,
            String format
    ) {
        List<String> range = IntStream.rangeClosed(0, 999).mapToObj(Integer::toString).collect(Collectors.toList());
        return org.galaxio.gatling.utils.phone.PhoneFormat.apply(
                countryCode, length, asScala(areaCodes).toSeq(), format, asScala(range).toSeq()
        );
    }
}
