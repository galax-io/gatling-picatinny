package org.galaxio.gatling.javaapi;

import static org.galaxio.gatling.javaapi.Feeders.*;

import static io.gatling.javaapi.core.CoreDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.gatling.javaapi.SimulationWithTransactions;


public class SimulationWithTransactionsTest extends SimulationWithTransactions {

    private final ScenarioBuilder scenario =
            scenario("scenario")
                    .exec(session -> {
                        System.out.print(session);
                        return session;
                    });

    {
        setUp(scenario.injectOpen(atOnceUsers(1)));
    }


}
