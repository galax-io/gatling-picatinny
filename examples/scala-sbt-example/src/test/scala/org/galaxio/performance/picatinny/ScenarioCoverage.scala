package org.galaxio.performance.picatinny

import com.sun.net.httpserver.HttpServer
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

import java.net.InetSocketAddress

class ScenarioCoverage extends Simulation {
  private val server = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/health",
      exchange => {
        val body = "ok".getBytes
        exchange.sendResponseHeaders(200, body.length)
        exchange.getResponseBody.write(body)
        exchange.close()
      },
    )
    server.start()
    server
  }

  setUp(
    PicatinnyScenario("Picatinny Scenario Coverage")
      .exec(http("scala-scenario-coverage").get("/health").check(status.is(200)))
      .inject(atOnceUsers(1)),
  ).protocols(http.baseUrl(s"http://127.0.0.1:${server.getAddress.getPort}"))
    .assertions(global.failedRequests.count.is(0))

  after {
    server.stop(0)
  }
}
