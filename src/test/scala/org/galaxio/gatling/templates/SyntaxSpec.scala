package org.galaxio.gatling.templates

import org.galaxio.gatling.templates.Syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SyntaxSpec extends AnyWordSpec with Matchers {

  "makeJson" should {

    "render single RawValString field" in {
      makeJson("key" - "value") shouldBe """{"key": "value"}"""
    }

    "render RawValGen Int" in {
      makeJson("n" - 42) shouldBe """{"n": 42}"""
    }

    "render RawValGen Double" in {
      makeJson("d" - 3.14) shouldBe """{"d": 3.14}"""
    }

    "render RawValGen Boolean" in {
      makeJson("b" - true) shouldBe """{"b": true}"""
    }

    "render RawValGen Long" in {
      makeJson("l" - 9999999999L) shouldBe """{"l": 9999999999}"""
    }

    "render InterpolateStrVal with ~ operator" in {
      makeJson("id" ~ "userId") shouldBe """{"id": "#{userId}"}"""
    }

    "render InterpolateGenVal with ~ operator" in {
      makeJson("count" ~ 42) shouldBe """{"count": #{42}}"""
    }

    "render nested ObjectVal one level deep" in {
      makeJson(
        "user" - (
          "name" - "John",
          "age" - 30,
        ),
      ) shouldBe """{"user": {"name": "John","age": 30}}"""
    }

    "render deeply nested objects" in {
      makeJson(
        "a" - obj(
          "b" - obj(
            "c" - "deep",
            "d" - 1,
          ),
        ),
      ) shouldBe """{"a": {"b": {"c": "deep","d": 1}}}"""
    }

    "render ArrayVal with mixed types" in {
      makeJson(
        "items" > ("a", 1, true),
      ) shouldBe """{"items": ["a",1,true]}"""
    }

    "render empty fields as empty object" in {
      makeJson(List.empty[Field]) shouldBe "{}"
    }

    "render multiple fields comma-separated" in {
      makeJson(
        "a" - 1,
        "b" - 2,
        "c" - 3,
      ) shouldBe """{"a": 1,"b": 2,"c": 3}"""
    }

    "escape double quotes in string values" in {
      makeJson("msg" - """say "hello"""") shouldBe """{"msg": "say \"hello\""}"""
    }

    "escape backslash in string values" in {
      makeJson("path" - """C:\Users""") shouldBe """{"path": "C:\\Users"}"""
    }

    "escape newline in string values" in {
      makeJson("text" - "line1\nline2") shouldBe """{"text": "line1\nline2"}"""
    }

    "escape tab in string values" in {
      makeJson("text" - "col1\tcol2") shouldBe """{"text": "col1\tcol2"}"""
    }

    "render NullVal as null" in {
      makeJson("empty" - nullVal) shouldBe """{"empty": null}"""
    }

    "render implicit strToField as session variable reference" in {
      makeJson("userName") shouldBe """{"userName": "#{userName}"}"""
    }

    "render obj() helper for nested objects" in {
      val nested = obj("x" - 1, "y" - 2)
      makeJson("point" - nested) shouldBe """{"point": {"x": 1,"y": 2}}"""
    }
  }

  "makeArrJson" should {

    "render empty array" in {
      makeArrJson(List.empty) shouldBe "[]"
    }

    "render RawValString elements" in {
      makeArrJson(List(RawValString("a"), RawValString("b"))) shouldBe """["a","b"]"""
    }

    "render nested ObjectVal in array" in {
      val fields = List(Field("x", RawValGen(1)))
      makeArrJson(List(ObjectVal(fields))) shouldBe """[{"x": 1}]"""
    }

    "render nested ArrayVal (array of arrays)" in {
      makeArrJson(List(ArrayVal(List(RawValGen(1), RawValGen(2))))) shouldBe "[[1,2]]"
    }

    "render InterpolateStrVal elements" in {
      makeArrJson(List(InterpolateStrVal("x"))) shouldBe """["#{x}"]"""
    }

    "render InterpolateGenVal elements" in {
      makeArrJson(List(InterpolateGenVal("num"))) shouldBe """[#{num}]"""
    }

    "escape special chars in string elements" in {
      makeArrJson(List(RawValString("""he said "hi""""))) shouldBe """["he said \"hi\""]"""
    }
  }

  "makeXml" should {

    "render RawValString field" in {
      makeXml("name" - "foo") shouldBe "<name>foo</name>"
    }

    "render RawValGen numeric field" in {
      makeXml("count" - 42) shouldBe "<count>42</count>"
    }

    "render InterpolateStrVal with ~ operator" in {
      makeXml("ref" ~ "rid") shouldBe "<ref>#{rid}</ref>"
    }

    "render InterpolateGenVal" in {
      makeXml("num" ~ 5) shouldBe "<num>#{5}</num>"
    }

    "render nested ObjectVal" in {
      makeXml(
        "root" - (
          "child" - "val",
          "num" - 1,
        ),
      ) shouldBe "<root><child>val</child><num>1</num></root>"
    }

    "render ArrayVal with items" in {
      makeXml(
        "list" > (1, 2, 3),
      ) shouldBe "<list><item>1</item><item>2</item><item>3</item></list>"
    }

    "render empty fields as empty string" in {
      makeXml(List.empty[Field]) shouldBe ""
    }

    "escape XML special characters in values" in {
      makeXml("data" - "<b>bold & \"cool\"</b>") shouldBe "<data>&lt;b&gt;bold &amp; &quot;cool&quot;&lt;/b&gt;</data>"
    }

    "render NullVal as empty tag" in {
      makeXml("empty" - nullVal) shouldBe "<empty/>"
    }
  }

  "makeXmlArray" should {

    "render empty array" in {
      makeXmlArray(List.empty) shouldBe ""
    }

    "render mixed types" in {
      makeXmlArray(List(RawValString("a"), RawValGen(1), InterpolateStrVal("x"))) shouldBe
        "<item>a</item><item>1</item><item>#{x}</item>"
    }

    "escape special chars in string elements" in {
      makeXmlArray(List(RawValString("a & b"))) shouldBe "<item>a &amp; b</item>"
    }
  }

  "DSL operators" should {

    "- operator with string creates RawValString" in {
      val f = "key" - "val"
      f shouldBe Field("key", RawValString("val"))
    }

    "- operator with Int creates RawValGen" in {
      val f = "key" - 42
      f shouldBe Field("key", RawValGen(42))
    }

    "- operator with nested fields creates ObjectVal" in {
      val f = "obj" - ("a" - 1, "b" - 2)
      f.fieldVal shouldBe a[ObjectVal]
    }

    "~ operator creates InterpolateStrVal for strings" in {
      val f = "key" ~ "sessionVar"
      f shouldBe Field("key", InterpolateStrVal("sessionVar"))
    }

    "~ operator creates InterpolateGenVal for non-strings" in {
      val f = "key" ~ 123
      f shouldBe Field("key", InterpolateGenVal(123))
    }

    "> operator creates ArrayVal" in {
      val f = "items" > (1, 2, 3)
      f.fieldVal shouldBe a[ArrayVal]
    }

    "arr() auto-detects EL string interpolation" in {
      val a = arr("plain", "#{dynamic}", 42)
      a.vs should contain(InterpolateStrVal("dynamic"))
      a.vs should contain(RawValString("plain"))
      a.vs should contain(RawValGen(42))
    }

    "arr() passes through ObjectVal" in {
      val o = obj("x" - 1)
      val a = arr(o)
      a.vs should contain(o)
    }

    "arr() passes through ArrayVal" in {
      val inner = arr(1, 2)
      val a     = arr(inner)
      a.vs should contain(inner)
    }

    "asSessionVar creates InterpolateStrVal" in {
      val f = "key".asSessionVar("var1")
      f shouldBe Field("key", InterpolateStrVal("var1"))
    }

    "array method creates ArrayVal from Seq" in {
      val f = "items".array(Seq(1, 2, 3))
      f.fieldVal shouldBe a[ArrayVal]
    }
  }

  "emptyJson" should {
    "return empty JSON object" in {
      emptyJson shouldBe "{}"
    }
  }

  "emptyArr" should {
    "return empty JSON array" in {
      emptyArr shouldBe "[]"
    }
  }
}
