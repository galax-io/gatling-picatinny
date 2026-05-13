package org.galaxio.performance.picatinny.scenarios;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.performance.picatinny.cases.PicatinnyCases;
import org.galaxio.performance.picatinny.feeders.GeneratedPicatinnyFeeders;
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

    static ScenarioBuilder withFeeders(String name) {
        ScenarioBuilder builder = scenario(name);
        for (Iterator<Map<String, Object>> feeder : PicatinnyFeeders.all()) {
            builder = builder.feed(feeder);
        }
        builder = builder
                .feed(GeneratedPicatinnyFeeders.generatedUsers())
                .feed(GeneratedPicatinnyFeeders.governmentIds())
                .feed(GeneratedPicatinnyFeeders.dates())
                .feed(GeneratedPicatinnyFeeders.finance())
                .feed(GeneratedPicatinnyFeeders.numbers())
                .feed(GeneratedPicatinnyFeeders.strings())
                .feed(GeneratedPicatinnyFeeders.persons())
                .feed(GeneratedPicatinnyFeeders.internetData())
                .feed(GeneratedPicatinnyFeeders.locations())
                .feed(GeneratedPicatinnyFeeders.extendedFinance())
                .feed(GeneratedPicatinnyFeeders.commerce())
                .feed(GeneratedPicatinnyFeeders.countrySpecificIds())
                .feed(GeneratedPicatinnyFeeders.phones())
                .feed(GeneratedPicatinnyFeeders.loremText())
                .feed(GeneratedPicatinnyFeeders.singleFieldFeeder());
        return builder;
    }
}
