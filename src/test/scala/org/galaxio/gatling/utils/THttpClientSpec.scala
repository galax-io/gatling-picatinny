package org.galaxio.gatling.utils

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.http.HttpRequest

/** Unit tests for [[THttpClient]] using a ScalaMock transport seam — no real server (per the test model: HTTP units use
  * ScalaMock; the real wire path is covered by the e2e Gatling simulation in the example overlay). Request assembly is asserted
  * by capturing the outgoing [[HttpRequest]]; status/exception handling is asserted on the returned [[HttpResult]].
  */
class THttpClientSpec extends AnyWordSpec with Matchers with MockFactory {

  /** Build a client whose transport is a ScalaMock function returning `result`, capturing the request it receives. */
  private def clientReturning(result: HttpResult): (THttpClient, () => HttpRequest) = {
    var captured: HttpRequest = null
    val transport             = mockFunction[HttpRequest, HttpResult]
    transport.expects(*).onCall { (req: HttpRequest) => captured = req; result }.anyNumberOfTimes()
    (THttpClient.withTransport(transport), () => captured)
  }

  "HttpResult.isSuccess" should {
    "be false just below 200 (boundary)" in {
      HttpResult(199, "").isSuccess shouldBe false
    }
    "be true at 200 (boundary)" in {
      HttpResult(200, "").isSuccess shouldBe true
    }
    "be true at 299 (boundary)" in {
      HttpResult(299, "").isSuccess shouldBe true
    }
    "be false at 300 (boundary)" in {
      HttpResult(300, "").isSuccess shouldBe false
    }
  }

  "THttpClient" should {

    "return HttpResult with status and body on GET" in {
      val (client, _) = clientReturning(HttpResult(200, "ok"))
      val result      = client.get("http://localhost/get")
      result.statusCode shouldBe 200
      result.body shouldBe "ok"
      result.isSuccess shouldBe true
    }

    "send method GET and the exact URI" in {
      val (client, captured) = clientReturning(HttpResult(200, "ok"))
      client.get("http://localhost/get")
      captured().method() shouldBe "GET"
      captured().uri().toString shouldBe "http://localhost/get"
    }

    "send custom headers on GET" in {
      val (client, captured) = clientReturning(HttpResult(200, "ok"))
      client.get("http://localhost/get", Seq("X-Test", "hello"))
      captured().headers().firstValue("X-Test").orElse("") shouldBe "hello"
    }

    "send method POST" in {
      val (client, captured) = clientReturning(HttpResult(200, "ok"))
      client.post("http://localhost/post", """{"a":1}""")
      captured().method() shouldBe "POST"
    }

    "inject Content-Type: application/json on POST" in {
      val (client, captured) = clientReturning(HttpResult(200, "ok"))
      client.post("http://localhost/post", """{"a":1}""")
      captured().headers().firstValue("Content-Type").orElse("") shouldBe "application/json"
    }

    "return non-2xx status in HttpResult without throwing" in {
      val (client, _) = clientReturning(HttpResult(403, """{"errors":["permission denied"]}"""))
      val result      = client.get("http://localhost/error")
      result.statusCode shouldBe 403
      result.body should include("permission denied")
      result.isSuccess shouldBe false
    }

    "throw HttpClientException on getOrThrow with non-2xx" in {
      val (client, _) = clientReturning(HttpResult(403, """{"errors":["permission denied"]}"""))
      val ex          = the[HttpClientException] thrownBy client.getOrThrow("http://localhost/error")
      ex.statusCode shouldBe 403
      ex.method shouldBe HttpMethod.Get
      ex.getMessage should include("403")
      ex.getMessage should include("permission denied")
    }

    "throw HttpClientException on postOrThrow with non-2xx" in {
      val (client, _) = clientReturning(HttpResult(403, "denied"))
      val ex          = the[HttpClientException] thrownBy client.postOrThrow("http://localhost/error", "{}")
      ex.statusCode shouldBe 403
      ex.method shouldBe HttpMethod.Post
    }

    "return success from getOrThrow on 2xx" in {
      val (client, _) = clientReturning(HttpResult(200, "ok"))
      val result      = client.getOrThrow("http://localhost/get")
      result.statusCode shouldBe 200
      result.body shouldBe "ok"
    }

    "include URI in HttpClientException message" in {
      val uri         = "http://localhost/not-found"
      val (client, _) = clientReturning(HttpResult(404, "not found"))
      val ex          = the[HttpClientException] thrownBy client.getOrThrow(uri)
      ex.statusCode shouldBe 404
      ex.uri shouldBe uri
      ex.getMessage should include(uri)
    }

    "close without throwing" in {
      val (client, _) = clientReturning(HttpResult(200, "ok"))
      client.get("http://localhost/get")
      noException should be thrownBy client.close()
    }
  }
}
