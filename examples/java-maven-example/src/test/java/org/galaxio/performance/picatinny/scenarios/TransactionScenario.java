package org.galaxio.performance.picatinny.scenarios;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.performance.picatinny.cases.PicatinnyCases;

public final class TransactionScenario {
    private TransactionScenario() {
    }

    public static ScenarioBuilder apply(String name, String transactionName) {
        return PicatinnyScenario.withFeeders(name)
                .exec(PicatinnyCases.businessOperation(transactionName));
    }
}
