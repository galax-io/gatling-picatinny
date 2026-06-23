package org.galaxio.gatling.profile

import org.galaxio.gatling.profile.ProfileBuilderNew.ProfileBuilderException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProfileBuilderTest extends AnyWordSpec with Matchers {

  private val profilePath = "src/test/resources/profileTemplates/profile1.yml"

  private val expectedProfile = OneProfile(
    "maxPerf",
    "10.05.2022 - 20.05.2022",
    "http",
    List(
      Request(
        "request-1",
        "100 rph",
        Some(List("Group1")),
        Params("POST", "/test/a", Some(List("greetings: Hello world!")), Some("""{"a": "b"}""")),
      ),
    ),
  )

  private val expectedYaml = Yaml(
    "link.ru/v1alpha1",
    "PerformanceTestProfiles",
    Metadata("performance-test-profile", "performance test profile"),
    ProfileSpec(List(expectedProfile)),
  )

  "ProfileBuilderNew.buildFromYaml" should {
    "load profile yaml files" in {
      ProfileBuilderNew.buildFromYaml(profilePath) shouldBe expectedYaml
    }

    "select profiles from parsed yaml" in {
      ProfileBuilderNew.buildFromYaml(profilePath).selectProfile("maxPerf") shouldBe expectedProfile
    }

    "report missing files clearly" in {
      val thrown = the[ProfileBuilderException] thrownBy {
        ProfileBuilderNew.buildFromYaml("notExistsFile")
      }

      thrown.getMessage should include("File not found notExistsFile")
    }

    "report empty file paths clearly" in {
      val thrown = the[ProfileBuilderException] thrownBy {
        ProfileBuilderNew.buildFromYaml("")
      }

      thrown.getMessage should include("File not found")
    }

    "report invalid yaml content clearly" in {
      val path = "src/test/resources/profileTemplates/incorrectProfile.yml"

      val thrown = the[ProfileBuilderException] thrownBy {
        ProfileBuilderNew.buildFromYaml(path)
      }

      thrown.getMessage should include(s"Incorrect file content in $path")
    }

    "reject a path that escapes the working directory via traversal" in {
      val thrown = the[ProfileBuilderException] thrownBy {
        ProfileBuilderNew.buildFromYaml("../../etc/passwd")
      }

      thrown.getMessage.toLowerCase should include("traversal")
    }

    "accept a path that normalizes back within the project" in {
      val path = "src/test/resources/profileTemplates/../profileTemplates/profile1.yml"

      ProfileBuilderNew.buildFromYaml(path) shouldBe expectedYaml
    }

    "reject an absolute path outside the working directory" in {
      val thrown = the[ProfileBuilderException] thrownBy {
        ProfileBuilderNew.buildFromYaml("/etc/passwd")
      }

      thrown.getMessage.toLowerCase should include("traversal")
    }
  }

  "ProfileBuilderNew.buildFromYamlJava" should {

    "reject a path that escapes the working directory via traversal" in {
      val thrown = the[ProfileBuilderException] thrownBy {
        ProfileBuilderNew.buildFromYamlJava("../../etc/passwd")
      }

      thrown.getMessage.toLowerCase should include("traversal")
    }

    "reject an absolute path outside the working directory" in {
      val thrown = the[ProfileBuilderException] thrownBy {
        ProfileBuilderNew.buildFromYamlJava("/etc/passwd")
      }

      thrown.getMessage.toLowerCase should include("traversal")
    }

    "use the same error model as the Scala facade" in {
      Seq(
        "notExistsFile"                                            -> "File not found notExistsFile",
        ""                                                         -> "File not found",
        "src/test/resources/profileTemplates/incorrectProfile.yml" ->
          "Incorrect file content in src/test/resources/profileTemplates/incorrectProfile.yml",
      ).foreach { case (path, expectedMessage) =>
        withClue(s"path=$path") {
          val thrown = the[ProfileBuilderException] thrownBy {
            ProfileBuilderNew.buildFromYamlJava(path)
          }

          thrown.getMessage should include(expectedMessage)
        }
      }
    }
  }

  "Request.parsedHeaders" should {
    "throw ProfileBuilderException on a header with no colon separator" in {
      val request =
        Request("r", "100 rph", None, Params("GET", "/", Some(List("bad-header-no-colon")), None))

      val thrown = the[ProfileBuilderException] thrownBy request.parsedHeaders

      thrown.getMessage should include("bad-header-no-colon")
    }

    "throw ProfileBuilderException on an empty header string" in {
      val request = Request("r", "100 rph", None, Params("GET", "/", Some(List("")), None))

      the[ProfileBuilderException] thrownBy request.parsedHeaders
    }

    "parse a well-formed header into a name to value pair" in {
      val request =
        Request("r", "100 rph", None, Params("GET", "/", Some(List("Content-Type: application/json")), None))

      request.parsedHeaders shouldBe Map("Content-Type" -> "application/json")
    }
  }
}
