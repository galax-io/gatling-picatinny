package org.galaxio.gatling.javaapi;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.gatling.javaapi.profile.ProfileBuilderNew;

// Compile guard for the Java facade. Request header parsing is NOT exercised here because the facade
// `internal.ProfileBuilderNew.toRequest` (like the Scala core `Request.toRequest`) static-initializes the
// Gatling HttpDsl, which needs the Gatling runtime and cannot run in a unit test. The facade delegates header
// parsing to the shared `Request.parsedHeaders`; its malformed-header behavior (ProfileBuilderException, not
// MatchError) is unit-tested once at the core in ProfileBuilderTest ("Request.parsedHeaders"). The full
// toRequest path is exercised end-to-end in the examples/ overlay (WireMock e2e).
public class JavaProfileTest {
    ScenarioBuilder scn = ProfileBuilderNew
            .buildFromYaml("perf/profiles.yml")
            .selectProfile("MaxPerf")
            .toRandomScenario();
}
