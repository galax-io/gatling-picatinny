package org.galaxio.gatling.templates

import org.galaxio.gatling.templates.Syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpBodyExtSpec extends AnyWordSpec with Matchers {

  "HttpBodyExt DSL integration" should {

    "produce valid JSON string for jsonBody fields" in {
      val json = makeJson(
        "id" - 1,
        "name" - "test",
      )
      json shouldBe """{"id": 1,"name": "test"}"""
    }

    "produce valid XML string for xmlBody fields" in {
      val xml = makeXml(
        "id" - 1,
        "name" - "test",
      )
      xml shouldBe "<id>1</id><name>test</name>"
    }

    "handle nested structures for jsonBody" in {
      val json = makeJson(
        "user" - (
          "name" - "John",
          "tags" > (1, 2, 3),
        ),
      )
      json shouldBe """{"user": {"name": "John","tags": [1,2,3]}}"""
    }

    "handle empty fields for jsonBody" in {
      makeJson() shouldBe "{}"
    }

    "handle empty fields for xmlBody" in {
      makeXml() shouldBe ""
    }

    "handle session variable references in json" in {
      val json = makeJson(
        "userId" ~ "uid",
        "fixed" - "value",
      )
      json shouldBe """{"userId": "#{uid}","fixed": "value"}"""
    }

    "handle session variable references in xml" in {
      val xml = makeXml(
        "userId" ~ "uid",
        "fixed" - "value",
      )
      xml shouldBe "<userId>#{uid}</userId><fixed>value</fixed>"
    }
  }
}
