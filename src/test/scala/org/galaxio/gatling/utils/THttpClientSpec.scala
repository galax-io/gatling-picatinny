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

    server.start()
  }

  override def afterAll(): Unit = server.stop(0)

  "THttpClient" should {

    "return a 200 response on GET with default settings" in {
      val response = THttpClient().GET(s"http://localhost:$port/get")
      response.statusCode() shouldBe 200
      response.body() shouldBe "ok"
    }

    "send custom headers on GET" in {
      THttpClient().GET(s"http://localhost:$port/get", Seq("X-Test", "hello"))
      lastRequestHeaders.map { case (k, v) => k.toLowerCase -> v } should contain key "x-test"
    }

    "return a 200 response on POSTJson" in {
      val response = THttpClient().POSTJson(s"http://localhost:$port/post", """{"a":1}""")
      response.statusCode() shouldBe 200
    }

    "send Content-Type: application/json on POSTJson" in {
      THttpClient().POSTJson(s"http://localhost:$port/post", """{"a":1}""")
      lastRequestHeaders.map { case (k, v) => k.toLowerCase -> v } should contain key "content-type"
    }

    "send the JSON body on POSTJson" in {
      THttpClient().POSTJson(s"http://localhost:$port/post", """{"x":"y"}""")
      lastRequestBody shouldBe """{"x":"y"}"""
    }

    "respect custom connection timeout parameter" in {
      val client   = THttpClient(connectTimeoutInSeconds = 5)
      val response = client.GET(s"http://localhost:$port/get")
      response.statusCode() shouldBe 200
    }
  }
}
