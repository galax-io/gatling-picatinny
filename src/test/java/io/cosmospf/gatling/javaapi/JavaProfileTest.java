package io.cosmospf.gatling.javaapi;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.cosmospf.gatling.javaapi.profile.ProfileBuilderNew;


public class JavaProfileTest {
    ScenarioBuilder scn = ProfileBuilderNew
            .buildFromYaml("perf/profiles.yml")
            .selectProfile("MaxPerf")
            .toRandomScenario();
}
