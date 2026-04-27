package org.galaxio.performance.picatinny;

import com.sun.net.httpserver.HttpServer;
import io.gatling.javaapi.core.Simulation;
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario;

import java.io.IOException;
import java.net.InetSocketAddress;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public final class ScenarioCoverage extends Simulation {
    private static final HttpServer SERVER = startServer();

    {
        setUp(
                PicatinnyScenario.apply("Picatinny Scenario Coverage")
                        .exec(http("java-scenario-coverage").get("/health").check(status().is(200)))
                        .injectOpen(atOnceUsers(1))
        ).protocols(http.baseUrl("http://127.0.0.1:" + SERVER.getAddress().getPort()))
                .assertions(global().failedRequests().count().is(0L));
    }

    @Override
    public void after() {
        SERVER.stop(0);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/health", exchange -> {
                byte[] body = "ok".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start scenario coverage HTTP server", e);
        }
    }
}
