package org.galaxio.gatling.templates

import org.scalatest.funsuite.AnyFunSuite

class SyntaxSpec extends AnyFunSuite {
  import Syntax._

  test("makeJson renders explicit interpolation with ~ as #{var}") {
    val json = makeJson(
      "id" ~ "userId",
      "fixed" - "value"
    )
    assert(json === "{" + "\"id\": \"#{userId}\",\"fixed\": \"value\"" + "}")
  }

  test("makeJson renders array with autodetected '#{var}' string interpolation") {
    val json = makeJson(
      "items" > ("a", "#{x}", 5)
    )
    assert(json === "{" + "\"items\": [\"a\",\"#{x}\",5]" + "}")
  }

  test("makeXml renders fields and arrays with '#{var}' interpolation") {
    val xml = makeXml(
      "name" - "foo",
      "ref" ~ "rid",
      "list" > ("#{v1}", 2)
    )
    assert(xml === "<name>foo</name><ref>#{rid}</ref><list><item>#{v1}</item><item>2</item></list>")
  }
}
