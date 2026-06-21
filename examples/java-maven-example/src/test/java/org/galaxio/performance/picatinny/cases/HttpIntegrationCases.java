package org.galaxio.performance.picatinny.cases;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.http.HttpDsl.*;

public final class HttpIntegrationCases {
    private HttpIntegrationCases() {}

    public static ChainBuilder echo() {
        return exec(
            http("echo")
                .get("/echo/#{ts}")
                .header("Authorization", "Bearer #{jwt}")
                .check(status().is(200))
                .check(jsonPath("$.ts").is("#{ts}"))
                .check(jsonPath("$.ts").<Boolean>transform(v -> v.matches("\\d{17}")).is(Boolean.TRUE))
                .check(jsonPath("$.auth").is("Bearer #{jwt}"))
                .check(jsonPath("$.auth").<Boolean>transform(
                    v -> v.matches("Bearer [\\w-]+\\.[\\w-]+\\.[\\w-]+")).is(Boolean.TRUE))
        );
    }
}
