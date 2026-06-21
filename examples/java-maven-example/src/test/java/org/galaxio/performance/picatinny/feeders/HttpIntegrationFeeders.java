package org.galaxio.performance.picatinny.feeders;

import org.galaxio.gatling.javaapi.Feeders;

import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

public final class HttpIntegrationFeeders {
    private HttpIntegrationFeeders() {}

    public static Iterator<Map<String, Object>> ts() {
        return Feeders.CurrentDateFeeder("ts", DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}
