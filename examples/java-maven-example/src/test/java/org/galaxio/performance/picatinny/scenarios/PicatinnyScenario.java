package org.galaxio.performance.picatinny.scenarios;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.performance.picatinny.cases.PicatinnyCases;
import org.galaxio.performance.picatinny.feeders.PicatinnyFeeders;

import java.util.Iterator;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.scenario;

public final class PicatinnyScenario {
    private PicatinnyScenario() {
    }

    public static ScenarioBuilder apply(String name, String transactionName) {
        return withFeeders(name).exec(PicatinnyCases.businessOperation(transactionName));
    }

    public static ScenarioBuilder apply(String name) {
        return withFeeders(name).exec(PicatinnyCases.scenarioOperation());
    }

    private static ScenarioBuilder withFeeders(String name) {
        ScenarioBuilder builder = scenario(name);
        for (Iterator<Map<String, Object>> feeder : PicatinnyFeeders.all()) {
            builder = builder.feed(feeder);
        }
        return builder;
    }
}
