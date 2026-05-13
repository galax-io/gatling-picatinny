package org.galaxio.performance.picatinny.feeders;

import org.galaxio.gatling.javaapi.utils.phone.TypePhone;

import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.galaxio.gatling.javaapi.Feeders.*;

/** Legacy Random*Feeder examples (deprecated — see GeneratedPicatinnyFeeders for the new Faker API). */
public final class PicatinnyFeeders {

    private PicatinnyFeeders() {
    }

    public static List<Iterator<Map<String, Object>>> legacy() {
        return List.of(
                RandomUUIDFeeder("uuid"),
                CurrentDateFeeder("currentDate", DateTimeFormatter.ISO_LOCAL_DATE),
                RandomPhoneFeeder("phoneFromJson", "phoneTemplates/ru.json", TypePhone.E164PhoneNumber()),
                RandomStringFeeder("randomString", 12),
                RandomPANFeeder("pan", "421345", "541673")
        );
    }
}
