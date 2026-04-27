package org.galaxio.performance.picatinny.scenarios;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.performance.picatinny.cases.PicatinnyCases;
import org.galaxio.performance.picatinny.feeders.PicatinnyFeeders;

import java.util.Iterator;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.scenario;

public final class TransactionScenario {
    private TransactionScenario() {
    }

    public static ScenarioBuilder apply(String name, String transactionName) {
        ScenarioBuilder builder = scenario(name);
        for (Iterator<Map<String, Object>> feeder : PicatinnyFeeders.all()) {
            builder = builder.feed(feeder);
        }
        return builder.exec(PicatinnyCases.businessOperation(transactionName));
    }
}
