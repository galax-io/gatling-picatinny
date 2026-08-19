package org.galaxio.gatling.utils

import com.sun.net.httpserver.HttpServer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.http.HttpTimeoutException
import java.net.{ConnectException, InetSocketAddress, ServerSocket}
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException

/** External-integration test (test-model layer 3, non-container `it`) for [[THttpClient]] TRANSPORT behavior — connection
  * refused, timeout, TLS handshake failure, and both redirect modes (#211, FR-008).
  *
  * These outcomes originate below the mockable [[HttpGetter]] seam: a ScalaMock stub could only *simulate* them by throwing on
  * demand, which would assert the stub rather than the client (mock-testing-mock, forbidden). So real loopback sockets are used
  * instead — no external network, no containers, JDK-built-in server only (plan Complexity Tracking justification).
  */
class THttpClientTransportSpec extends AnyWordSpec with Matchers {

  private def withClient[A](client: THttpClient)(f: THttpClient => A): A =
    try f(client)
    finally client.close()

  /** Ephemeral port bound and immediately released — connecting to it is refused. */
  private def closedPort(): Int = {
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }

  private def withHttpServer[A](routes: (String, com.sun.net.httpserver.HttpExchange => Unit)*)(f: Int => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    routes.foreach { case (path, handler) => server.createContext(path, exchange => handler(exchange)) }
    server.start()
    try f(server.getAddress.getPort)
    finally server.stop(0)
  }

  private def respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, if (bytes.isEmpty) -1 else bytes.length.toLong)
    if (bytes.nonEmpty) exchange.getResponseBody.write(bytes)
    exchange.close()
  }

  "THttpClient transport failures" should {

    "surface an unreachable endpoint as ConnectException (connection refused)" in {
      withClient(THttpClient()) { client =>
        val port = closedPort()
        intercept[ConnectException](client.get(s"http://127.0.0.1:$port/"))
      }
    }

    "surface a connected-but-silent server as HttpTimeoutException at the configured bound" in {
      // Bound socket that is never read from: the TCP handshake succeeds via the kernel backlog,
      // then no HTTP response ever arrives -> request timeout, not connect timeout.
      val silent = new ServerSocket(0, 1)
      try
        withClient(THttpClient(timeoutInSeconds = 1)) { client =>
          val e = intercept[HttpTimeoutException](client.get(s"http://127.0.0.1:${silent.getLocalPort}/"))
          e.getMessage.toLowerCase should include("timed out")
        }
      finally silent.close()
    }

    "surface a plaintext endpoint answered over HTTPS as an SSL handshake failure" in {
      // Raw socket answering the TLS ClientHello with plaintext HTTP: the client's SSL engine
      // rejects it as an unrecognized handshake message (an HttpServer would just hang -> timeout).
      // Serves EVERY incoming connection in a loop: the JDK client may open more than one
      // connection attempt, and an unanswered second attempt would stall into a connect timeout.
      val server    = new ServerSocket(0)
      val responder = new Thread(() =>
        try
          while (!server.isClosed) {
            val socket = server.accept()
            socket.getOutputStream.write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8))
            socket.getOutputStream.flush()
            socket.close()
          }
        catch { case _: Exception => () },
      )
      responder.setDaemon(true)
      responder.start()
      try
        withClient(THttpClient()) { client =>
          intercept[SSLException](client.get(s"https://127.0.0.1:${server.getLocalPort}/"))
        }
      finally server.close()
    }
  }

  "THttpClient redirect semantics (both modes pinned)" should {

    def withRedirectServer[A](f: Int => A): A =
      withHttpServer(
        "/redirect" -> { exchange =>
          exchange.getResponseHeaders.add("Location", "/final")
          respond(exchange, 302, "")
        },
        "/final"    -> (respond(_, 200, "final-body")),
      )(f)

    "return the 302 itself under the default Redirect.NEVER (no follow)" in withRedirectServer { port =>
      withClient(THttpClient()) { client =>
        val result = client.get(s"http://127.0.0.1:$port/redirect")
        result.statusCode shouldBe 302
        result.isSuccess shouldBe false
      }
    }

    "follow to the exact final body when redirects are enabled (NORMAL)" in withRedirectServer { port =>
      withClient(THttpClient(followRedirects = "NORMAL")) { client =>
        client.get(s"http://127.0.0.1:$port/redirect") shouldBe HttpResult(200, "final-body")
      }
    }
  }
}
