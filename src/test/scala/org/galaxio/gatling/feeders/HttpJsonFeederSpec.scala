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

  private def serve(path: String, body: String, status: Int = 200): Unit =
    server.createContext(
      path,
      (ex: HttpExchange) => {
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(status, bytes.length.toLong)
        val os    = ex.getResponseBody
        os.write(bytes)
        os.close()
      },
    )

  "HttpJsonFeeder" when {

    "given valid JSON" should {

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
        HttpJsonFeeder(s"http://localhost:$port/dec", List("ratio")).head("ratio") shouldBe "3.14"
      }

      "convert boolean fields to strings" in {
        serve("/bool", """{"active":true,"admin":false}""")
        HttpJsonFeeder(s"http://localhost:$port/bool", List("active", "admin")) shouldBe
          IndexedSeq(Map("active" -> "true", "admin" -> "false"))
      }

      "convert null JSON values to the string 'null'" in {
        serve("/null-val", """{"a":null,"b":"ok"}""")
        HttpJsonFeeder(s"http://localhost:$port/null-val", List("a", "b")) shouldBe
          IndexedSeq(Map("a" -> "null", "b" -> "ok"))
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

      "handle mixed field types" in {
        serve("/mixed", """{"id":7,"name":"alice","active":true,"score":9.5}""")
        HttpJsonFeeder(s"http://localhost:$port/mixed", List("id", "name", "active", "score")) shouldBe
          IndexedSeq(Map("id" -> "7", "name" -> "alice", "active" -> "true", "score" -> "9.5"))
      }

      "return empty record for empty keys list" in {
        serve("/empty-keys", """{"a":"1"}""")
        HttpJsonFeeder(s"http://localhost:$port/empty-keys", List.empty) shouldBe
          IndexedSeq(Map.empty)
      }
    }

    "given invalid input" should {

      "reject empty URL" in {
        an[IllegalArgumentException] should be thrownBy {
          HttpJsonFeeder("", List("key"))
        }
      }

      "reject null keys list" in {
        an[IllegalArgumentException] should be thrownBy {
          HttpJsonFeeder(s"http://localhost:$port/any", null)
        }
      }

      "propagate parse error on malformed JSON" in {
        serve("/bad-json", "not-json{{{")
        a[Exception] should be thrownBy {
          HttpJsonFeeder(s"http://localhost:$port/bad-json", List("key"))
        }
      }
    }
  }
}
