package org.galaxio.gatling.utils

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class THttpClientSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private var server: HttpServer = _
  private var port: Int          = _

  private var lastRequestHeaders: Map[String, String] = Map.empty
  private var lastRequestBody: String                 = ""

  override def beforeAll(): Unit = {
    server = HttpServer.create(new InetSocketAddress(0), 0)
    port = server.getAddress.getPort

    server.createContext(
      "/get",
      (ex: HttpExchange) => {
        lastRequestHeaders = ex.getRequestHeaders
          .entrySet()
          .toArray
          .collect { case e: java.util.Map.Entry[_, _] => e.getKey.toString -> e.getValue.toString }
          .toMap
        val body = "ok".getBytes(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(200, body.length.toLong)
        val os   = ex.getResponseBody
        os.write(body)
        os.close()
      },
    )

    server.createContext(
      "/post",
      (ex: HttpExchange) => {
        lastRequestHeaders = ex.getRequestHeaders
          .entrySet()
          .toArray
          .collect { case e: java.util.Map.Entry[_, _] => e.getKey.toString -> e.getValue.toString }
          .toMap
        lastRequestBody = new String(ex.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
        val body = "ok".getBytes(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(200, body.length.toLong)
        val os   = ex.getResponseBody
        os.write(body)
        os.close()
      },
    )

    server.createContext(
      "/error",
      (ex: HttpExchange) => {
        val body = """{"errors":["permission denied"]}""".getBytes(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(403, body.length.toLong)
        val os   = ex.getResponseBody
        os.write(body)
        os.close()
      },
    )

    server.createContext(
      "/not-found",
      (ex: HttpExchange) => {
        val body = "not found".getBytes(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(404, body.length.toLong)
        val os   = ex.getResponseBody
        os.write(body)
        os.close()
      },
    )

    server.start()
  }

  override def afterAll(): Unit = server.stop(0)

  "THttpClient" should {

    "return HttpResult with status and body on GET" in {
      val result = THttpClient().get(s"http://localhost:$port/get")
      result.statusCode shouldBe 200
      result.body shouldBe "ok"
      result.isSuccess shouldBe true
    }

    "send custom headers on GET" in {
      THttpClient().get(s"http://localhost:$port/get", Seq("X-Test", "hello"))
      lastRequestHeaders.map { case (k, v) => k.toLowerCase -> v } should contain key "x-test"
    }

    "return HttpResult on POST" in {
      val result = THttpClient().post(s"http://localhost:$port/post", """{"a":1}""")
      result.statusCode shouldBe 200
      result.isSuccess shouldBe true
    }

    "send Content-Type: application/json on POST" in {
      THttpClient().post(s"http://localhost:$port/post", """{"a":1}""")
      lastRequestHeaders.map { case (k, v) => k.toLowerCase -> v } should contain key "content-type"
    }

    "send the JSON body on POST" in {
      THttpClient().post(s"http://localhost:$port/post", """{"x":"y"}""")
      lastRequestBody shouldBe """{"x":"y"}"""
    }

    "respect custom connection timeout parameter" in {
      val client = THttpClient(timeoutInSeconds = 5)
      val result = client.get(s"http://localhost:$port/get")
      result.statusCode shouldBe 200
    }

    "return non-2xx status in HttpResult without throwing" in {
      val result = THttpClient().get(s"http://localhost:$port/error")
      result.statusCode shouldBe 403
      result.body should include("permission denied")
      result.isSuccess shouldBe false
    }

    "throw HttpClientException on getOrThrow with non-2xx" in {
      val ex = the[HttpClientException] thrownBy {
        THttpClient().getOrThrow(s"http://localhost:$port/error")
      }
      ex.statusCode shouldBe 403
      ex.method shouldBe "GET"
      ex.getMessage should include("403")
      ex.getMessage should include("permission denied")
    }

    "throw HttpClientException on postOrThrow with non-2xx" in {
      val ex = the[HttpClientException] thrownBy {
        THttpClient().postOrThrow(s"http://localhost:$port/error", "{}")
      }
      ex.statusCode shouldBe 403
      ex.method shouldBe "POST"
    }

    "return success from getOrThrow on 2xx" in {
      val result = THttpClient().getOrThrow(s"http://localhost:$port/get")
      result.statusCode shouldBe 200
      result.body shouldBe "ok"
    }

    "include URI in HttpClientException message" in {
      val uri = s"http://localhost:$port/not-found"
      val ex  = the[HttpClientException] thrownBy {
        THttpClient().getOrThrow(uri)
      }
      ex.statusCode shouldBe 404
      ex.uri shouldBe uri
      ex.getMessage should include(uri)
    }

    "close without throwing" in {
      val client = THttpClient()
      client.get(s"http://localhost:$port/get")
      noException should be thrownBy client.close()
    }
  }
}
