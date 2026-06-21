package org.galaxio.performance.picatinny.cases;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import org.galaxio.performance.picatinny.feeders.FeederValidationFeeders;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public final class FeederValidationCases {

    private FeederValidationCases() {}

    /** One request, one header per faker-feeder field. Each echoed value is asserted twice:
     *  exact round-trip (echoed == fed value, before/after) and shape (matches the field pattern). */
    public static ChainBuilder validateAll() {
        HttpRequestActionBuilder req = http("validate-feeders").get("/echo").check(status().is(200));
        for (var e : FeederValidationFeeders.PATTERNS) {
            final String field   = e.getKey();
            final String pattern = e.getValue();
            req = req.header("X" + field, "#{" + field + "}")
                     .check(jsonPath("$." + field).is("#{" + field + "}"))
                     .check(jsonPath("$." + field).<Boolean>transform(v -> v.matches(pattern)).is(Boolean.TRUE));
        }
        return exec(req);
    }
}
