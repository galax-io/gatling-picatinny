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

    // --- #127, closed on evidence rather than by a code change ---------------------------------
    //
    // The issue asserts that the varargs entry points allocate a fresh `Seq[Field]` PER REQUEST.
    // They do not. `jsonBody`/`xmlBody` return an `HttpRequestBuilder`, which is an `ActionBuilder`
    // consumed ONCE when the scenario is defined; inside, `StringBody(String)` takes a strict String
    // and `.el[String]` compiles eagerly. So the whole `Field* -> List -> String -> Expression` chain
    // runs once per DSL declaration and is reused for every request — per-request cost is zero.
    //
    // The suggested fix is also not expressible: `makeJson(fs: Field*)` and a `makeJson(fs: Seq[Field])`
    // overload erase to the same signature on Scala 2.13, so they cannot coexist. A differently-named
    // entry point would be new public API, MiMa-relevant, for a path measurement says is not hot.
    //
    // What IS true, and is what these cases pin: the list form the body builders already expose
    // produces byte-identical output to the varargs form, so a caller holding a pre-built list needs
    // nothing new.

    "produce identical JSON from the varargs and pre-built-list forms" in {
      val fields = List("id" - 1, "name" - "test", "nested" - ("a" - 1, "b" - 2))
      makeJson(fields) shouldBe makeJson(fields: _*)
    }

    "produce identical XML from the varargs and pre-built-list forms" in {
      val fields = List("id" - 1, "name" - "test", "nested" - ("a" - 1, "b" - 2))
      makeXml(fields) shouldBe makeXml(fields: _*)
    }

    "agree on the empty field list too — the boundary case" in {
      makeJson(List.empty[Field]) shouldBe makeJson()
      makeXml(List.empty[Field]) shouldBe makeXml()
    }
  }
}
