package org.galaxio.performance.picatinny

import com.sun.net.httpserver.HttpServer
import io.gatling.javaapi.core.CoreDsl.atOnceUsers
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario
import java.net.InetSocketAddress

class ScenarioCoverage : Simulation() {
    private val server = startServer()

    init {
        setUp(
            PicatinnyScenario.apply("Picatinny Scenario Coverage")
                .exec(http("kotlin-scenario-coverage").get("/health").check(status().shouldBe(200)))
                .injectOpen(atOnceUsers(1)),
        ).protocols(http.baseUrl("http://127.0.0.1:${server.address.port}"))
            .assertions(global().failedRequests().count().shouldBe(0L))
    }

    override fun after() {
        server.stop(0)
    }

    private fun startServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/health") { exchange ->
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }
}
