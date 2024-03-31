package org.galaxio.gatling.javaapi;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.gatling.javaapi.profile.ProfileBuilderNew;


public class JavaProfileTest {
    ScenarioBuilder scn = ProfileBuilderNew
            .buildFromYaml("perf/profiles.yml")
            .selectProfile("MaxPerf")
            .toRandomScenario();
}
