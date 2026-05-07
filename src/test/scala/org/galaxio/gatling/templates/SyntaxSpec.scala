package org.galaxio.gatling.templates

import org.galaxio.gatling.templates.Syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SyntaxSpec extends AnyWordSpec with Matchers {

  "Syntax.makeJson" should {
    "render explicit interpolation with ~ as a Gatling EL reference" in {
      makeJson(
        "id" ~ "userId",
        "fixed" - "value",
      ) shouldBe """{"id": "#{userId}","fixed": "value"}"""
    }

    "render arrays with autodetected Gatling EL string interpolation" in {
      makeJson(
        "items" > ("a", "#{x}", 5),
      ) shouldBe """{"items": ["a","#{x}",5]}"""
    }
  }

  "Syntax.makeXml" should {
    "render fields and arrays with Gatling EL interpolation" in {
      makeXml(
        "name" - "foo",
        "ref" ~ "rid",
        "list" > ("#{v1}", 2),
      ) shouldBe "<name>foo</name><ref>#{rid}</ref><list><item>#{v1}</item><item>2</item></list>"
    }
  }
}
