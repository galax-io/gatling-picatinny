package org.galaxio.gatling.templates

import java.nio.file.Files

import io.gatling.core.GatlingTestBootstrap
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
    def names: Set[String] = templates.keySet
  }

  private def productionTemplateNames: Set[String] = new TemplatesProbe().names

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
        val t  = new Templates { def force(): Unit = { templates; () } }
        val ex = intercept[IllegalStateException](t.force())
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
        val t = new Templates { def names: Set[String] = templates.keySet }
        t.names shouldBe empty
      } finally {
        Thread.currentThread.setContextClassLoader(original)
        cl.close()
        Files.deleteIfExists(root.resolve("templates"))
        Files.deleteIfExists(root)
      }
    }
  }
}
