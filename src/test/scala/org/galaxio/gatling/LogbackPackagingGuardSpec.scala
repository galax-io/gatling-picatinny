package org.galaxio.gatling

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

/** Regression guard for FR-010: the published library MUST NOT ship an auto-discovered logback configuration on its
  * main/compile classpath, which would collide with a consumer's own config. logback config is permitted ONLY in the library's
  * own test scope (`src/test/resources`, excluded from the artifact). Run from the project root.
  */
class LogbackPackagingGuardSpec extends AnyWordSpec with Matchers {

  "the published library" should {
    "ship no logback configuration under src/main/resources (FR-010)" in {
      val mainResources = new File("src/main/resources")
      val offenders     =
        if (!mainResources.isDirectory) Nil
        else
          mainResources
            .listFiles()
            .toList
            .map(_.getName)
            .filter(name => name == "logback.xml" || name == "logback-test.xml")

      offenders shouldBe empty
    }

    "keep logback config in test scope only" in {
      // The library's own test logback config is expected (excluded from the published artifact).
      new File("src/test/resources/logback.xml").isFile shouldBe true
    }
  }
}
