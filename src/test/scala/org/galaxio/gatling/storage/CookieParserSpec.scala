package org.galaxio.gatling.storage

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CookieParserSpec extends AnyWordSpec with Matchers {

  "CookieParser" should {

    "parse simple name=value cookie" in {
      val result = CookieParser.parse("session_id=abc123", "example.com")
      result should have size 1
      result.head.name shouldBe "session_id"
      result.head.value shouldBe "abc123"
      result.head.domain shouldBe Some("example.com")
    }

    "parse cookie with attributes" in {
      val raw    = "token=xyz; Path=/api; Domain=.example.com; Max-Age=3600; Secure; HttpOnly"
      val result = CookieParser.parse(raw, "fallback.com")
      result should have size 1
      val c      = result.head
      c.name shouldBe "token"
      c.value shouldBe "xyz"
      c.domain shouldBe Some(".example.com")
      c.path shouldBe Some("/api")
      c.maxAge shouldBe Some(3600L)
      c.secure shouldBe true
      c.httpOnly shouldBe true
    }

    "parse multiple cookies separated by newlines" in {
      val raw    = "a=1; Path=/\nb=2; Secure"
      val result = CookieParser.parse(raw, "example.com")
      result should have size 2
      result.map(_.name) shouldBe Seq("a", "b")
    }

    "skip empty lines" in {
      val raw    = "a=1\n\nb=2"
      val result = CookieParser.parse(raw, "example.com")
      result should have size 2
    }

    "skip lines without = in name-value pair" in {
      val raw    = "malformed\na=1"
      val result = CookieParser.parse(raw, "example.com")
      result should have size 1
      result.head.name shouldBe "a"
    }

    "handle value containing =" in {
      val raw    = "token=abc=def=ghi; Path=/"
      val result = CookieParser.parse(raw, "example.com")
      result.head.value shouldBe "abc=def=ghi"
    }

    "use default domain when Domain attribute missing" in {
      val result = CookieParser.parse("x=1", "default.com")
      result.head.domain shouldBe Some("default.com")
    }

  }

}
