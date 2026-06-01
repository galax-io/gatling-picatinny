package org.galaxio.gatling.feeders

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class HttpJsonFeederSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private var server: HttpServer = _
  private var port: Int          = _

  override def beforeAll(): Unit = {
    server = HttpServer.create(new InetSocketAddress(0), 0)
    port = server.getAddress.getPort
    server.start()
  }

  override def afterAll(): Unit = server.stop(0)

  private def serve(path: String, body: String): Unit =
    server.createContext(
      path,
      (ex: HttpExchange) => {
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(200, bytes.length.toLong)
        val os = ex.getResponseBody
        os.write(bytes)
        os.close()
      },
    )

  "HttpJsonFeeder" should {

    "extract string fields" in {
      serve("/str", """{"name":"alice","city":"NY"}""")
      HttpJsonFeeder(s"http://localhost:$port/str", List("name", "city")) shouldBe
        IndexedSeq(Map("name" -> "alice", "city" -> "NY"))
    }

    "convert integer fields to strings" in {
      serve("/int", """{"id":42,"count":0}""")
      HttpJsonFeeder(s"http://localhost:$port/int", List("id", "count")) shouldBe
        IndexedSeq(Map("id" -> "42", "count" -> "0"))
    }

    "convert decimal fields to strings" in {
      serve("/dec", """{"ratio":3.14}""")
      val result = HttpJsonFeeder(s"http://localhost:$port/dec", List("ratio"))
      result.head("ratio") shouldBe "3.14"
    }

    "convert boolean fields to strings" in {
      serve("/bool", """{"active":true,"admin":false}""")
      HttpJsonFeeder(s"http://localhost:$port/bool", List("active", "admin")) shouldBe
        IndexedSeq(Map("active" -> "true", "admin" -> "false"))
    }

    "filter to only requested keys" in {
      serve("/keys", """{"a":"1","b":"2","c":"3"}""")
      HttpJsonFeeder(s"http://localhost:$port/keys", List("a", "c")) shouldBe
        IndexedSeq(Map("a" -> "1", "c" -> "3"))
    }

    "return empty record when no keys match" in {
      serve("/nomatch", """{"x":"1","y":"2"}""")
      HttpJsonFeeder(s"http://localhost:$port/nomatch", List("z")) shouldBe
        IndexedSeq(Map.empty)
    }

    "mix field types in a single response" in {
      serve("/mixed", """{"id":7,"name":"alice","active":true,"score":9.5}""")
      HttpJsonFeeder(s"http://localhost:$port/mixed", List("id", "name", "active", "score")) shouldBe
        IndexedSeq(Map("id" -> "7", "name" -> "alice", "active" -> "true", "score" -> "9.5"))
    }
  }
}
