package org.galaxio.gatling.feeders

import org.galaxio.gatling.utils.{HttpGetter, HttpResult}
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for [[HttpJsonFeeder]] JSON-extraction logic. The HTTP boundary is a ScalaMock [[HttpGetter]] — no real server
  * (per the test model: HTTP units use ScalaMock; the real wire path is the e2e Gatling simulation). Each test asserts the
  * exact extracted record AND that the feeder issued the expected GET (URL + headers) against the mock.
  */
class HttpJsonFeederSpec extends AnyWordSpec with Matchers with MockFactory {

  private val url = "http://localhost/data"

  /** Mock returning `json` for the expected GET; verifies the feeder called the seam exactly once with `url` + no headers. */
  private def getterReturning(json: String): HttpGetter = {
    val getter = mock[HttpGetter]
    (getter.get _).expects(url, Seq.empty[String]).returning(HttpResult(200, json)).once()
    getter
  }

  "HttpJsonFeeder" when {

    "given valid JSON" should {

      "extract string fields" in {
        val getter = getterReturning("""{"name":"alice","city":"NY"}""")
        HttpJsonFeeder.fetch(getter, url, List("name", "city"), Seq.empty) shouldBe
          IndexedSeq(Map("name" -> "alice", "city" -> "NY"))
      }

      "convert integer fields to strings" in {
        val getter = getterReturning("""{"id":42,"count":0}""")
        HttpJsonFeeder.fetch(getter, url, List("id", "count"), Seq.empty) shouldBe
          IndexedSeq(Map("id" -> "42", "count" -> "0"))
      }

      "convert decimal fields to strings" in {
        val getter = getterReturning("""{"ratio":3.14}""")
        HttpJsonFeeder.fetch(getter, url, List("ratio"), Seq.empty).head("ratio") shouldBe "3.14"
      }

      "convert boolean fields to strings" in {
        val getter = getterReturning("""{"active":true,"admin":false}""")
        HttpJsonFeeder.fetch(getter, url, List("active", "admin"), Seq.empty) shouldBe
          IndexedSeq(Map("active" -> "true", "admin" -> "false"))
      }

      "convert null JSON values to the string 'null'" in {
        val getter = getterReturning("""{"a":null,"b":"ok"}""")
        HttpJsonFeeder.fetch(getter, url, List("a", "b"), Seq.empty) shouldBe
          IndexedSeq(Map("a" -> "null", "b" -> "ok"))
      }

      "filter to only requested keys" in {
        val getter = getterReturning("""{"a":"1","b":"2","c":"3"}""")
        HttpJsonFeeder.fetch(getter, url, List("a", "c"), Seq.empty) shouldBe
          IndexedSeq(Map("a" -> "1", "c" -> "3"))
      }

      "return empty record when no keys match" in {
        val getter = getterReturning("""{"x":"1","y":"2"}""")
        HttpJsonFeeder.fetch(getter, url, List("z"), Seq.empty) shouldBe
          IndexedSeq(Map.empty)
      }

      "handle mixed field types" in {
        val getter = getterReturning("""{"id":7,"name":"alice","active":true,"score":9.5}""")
        HttpJsonFeeder.fetch(getter, url, List("id", "name", "active", "score"), Seq.empty) shouldBe
          IndexedSeq(Map("id" -> "7", "name" -> "alice", "active" -> "true", "score" -> "9.5"))
      }

      "return empty record for empty keys list" in {
        val getter = getterReturning("""{"a":"1"}""")
        HttpJsonFeeder.fetch(getter, url, List.empty, Seq.empty) shouldBe
          IndexedSeq(Map.empty)
      }

      "forward custom headers to the HTTP seam" in {
        val getter = mock[HttpGetter]
        (getter.get _).expects(url, Seq("Authorization", "Bearer t")).returning(HttpResult(200, """{"a":"1"}""")).once()
        HttpJsonFeeder.fetch(getter, url, List("a"), Seq("Authorization", "Bearer t")) shouldBe
          IndexedSeq(Map("a" -> "1"))
      }
    }

    "given invalid input" should {

      "reject empty URL before issuing a request" in {
        val getter = mock[HttpGetter] // no call expected: require fails first
        an[IllegalArgumentException] should be thrownBy {
          HttpJsonFeeder.fetch(getter, "", List("key"), Seq.empty)
        }
      }

      "reject null keys list before issuing a request" in {
        val getter = mock[HttpGetter] // no call expected
        an[IllegalArgumentException] should be thrownBy {
          HttpJsonFeeder.fetch(getter, url, null, Seq.empty)
        }
      }

      "propagate parse error on malformed JSON" in {
        val getter = getterReturning("not-json{{{")
        a[org.json4s.ParserUtil.ParseException] should be thrownBy {
          HttpJsonFeeder.fetch(getter, url, List("key"), Seq.empty)
        }
      }
    }
  }
}
