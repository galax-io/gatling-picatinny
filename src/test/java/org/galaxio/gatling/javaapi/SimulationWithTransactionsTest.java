package org.galaxio.gatling.javaapi;

import static org.galaxio.gatling.javaapi.Feeders.*;

import static io.gatling.javaapi.core.CoreDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.galaxio.gatling.transactions.Predef;


public class SimulationWithTransactionsTest extends Predef.SimulationWithTransactions {

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
