package org.galaxio.gatling.templates

import java.nio.file.{Files, Paths}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class TemplatesSpec extends AnyWordSpec with Matchers {

  private def loadTemplateNames: Set[String] =
    Option(Thread.currentThread.getContextClassLoader.getResource("templates")).map { resource =>
      Files
        .list(Paths.get(resource.toURI))
        .iterator()
        .asScala
        .map(_.toFile)
        .filter(_.isFile)
        .map { f =>
          val name   = f.getName
          val dotIdx = name.lastIndexOf('.')
          if (dotIdx > 0) name.substring(0, dotIdx) else name
        }
        .toSet
    }
      .getOrElse(Set.empty)

  "Templates file discovery" should {

    "find template files in resources/templates directory" in {
      loadTemplateNames should not be empty
    }

    "strip file extension from template name" in {
      loadTemplateNames should contain("test_json")
    }

    "discover both json and xml templates" in {
      loadTemplateNames should contain allOf ("test_json", "test_xml")
    }

    "not include file extension in template name" in {
      loadTemplateNames.foreach { name =>
        name should not include "."
      }
    }
  }

  "Templates error handling" should {

    "handle missing templates directory gracefully" in {
      val resource = Thread.currentThread.getContextClassLoader.getResource("nonexistent_templates")
      resource shouldBe null
    }
  }

  // Present-case discovery is covered above (loadTemplateNames) without constructing
  // ElFileBody; the trait's present path builds ElFileBody, which requires the Gatling
  // runtime and is exercised in the examples/ e2e layer. The unit tests below only hit
  // paths that never construct a body (missing resource → throw; empty dir → empty map).
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
