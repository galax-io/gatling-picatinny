package org.galaxio.gatling.templates

import io.gatling.commons.validation.{Failure, Success}
import io.gatling.core.GatlingTestBootstrap
import org.galaxio.gatling.transactions.fixtures
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

class TemplatesSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  // The trait's present path builds ElFileBody, whose implicit ElFileBodies is sourced from
  // io.gatling.core.Predef._configuration. Bootstrapping it with the real test configuration
  // lets these tests drive the REAL production discovery/render path — no local
  // re-implementation of the directory walk (that was the #211 tautology), no mocked runtime.
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    GatlingTestBootstrap.init()
  }

  private class TemplatesProbe extends Templates {
    def names: Set[String]                                             = templates.keySet
    def body(name: String): io.gatling.core.session.Expression[String] = templates(name)
  }

  private def productionTemplateNames: Set[String] = new TemplatesProbe().names

  "Template pipeline end-to-end (#81)" should {

    "render a real resource template against a real session with exact output" in {
      val body    = new TemplatesProbe().body("test_json")
      val session = fixtures.emptySession("tpl").set("userId", "42")
      body(session) shouldBe Success("""{"userId": "42", "action": "test"}""")
    }

    "fail naming the missing variable when the session lacks it (negative)" in {
      val body = new TemplatesProbe().body("test_json")
      body(fixtures.emptySession("tpl")) match {
        case Failure(message) => message should include("userId")
        case Success(value)   => fail(s"expected a validation failure, got: $value")
      }
    }
  }

  "Templates production discovery (#211)" should {

    "discover exactly the test resource templates, extensions stripped" in {
      productionTemplateNames shouldBe Set("test_json", "test_xml")
    }

    "not include file extension in any template name" in {
      productionTemplateNames.foreach { name =>
        name should not include "."
      }
    }
  }
  "Templates registry (FR-005)" should {

    "fail fast with a clear error when the templates resource is missing" in {
      val original = Thread.currentThread.getContextClassLoader
      try {
        Thread.currentThread.setContextClassLoader(new ClassLoader(null) {})
        val ex = intercept[IllegalStateException](new TemplatesProbe().names)
        ex.getMessage should include("Templates directory")
        ex.getMessage should include("resources/templates")
        ex.getMessage.toLowerCase should include("classpath")
      } finally Thread.currentThread.setContextClassLoader(original)
    }

    "yield an empty registry (not an error) when the templates resource exists but is empty" in {
      val original = Thread.currentThread.getContextClassLoader
      val root     = Files.createTempDirectory("tpl-root")
      Files.createDirectory(root.resolve("templates"))
      val cl       = new java.net.URLClassLoader(Array(root.toUri.toURL), null)
      try {
        Thread.currentThread.setContextClassLoader(cl)
        new TemplatesProbe().names shouldBe empty
      } finally {
        Thread.currentThread.setContextClassLoader(original)
        cl.close()
        Files.deleteIfExists(root.resolve("templates"))
        Files.deleteIfExists(root)
      }
    }
  }
}
