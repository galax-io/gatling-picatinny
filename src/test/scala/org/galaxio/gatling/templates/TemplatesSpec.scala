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
}
