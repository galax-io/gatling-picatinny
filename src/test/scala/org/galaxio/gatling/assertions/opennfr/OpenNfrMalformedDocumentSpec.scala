package org.galaxio.gatling.assertions.opennfr

import io.gatling.core.config.GatlingConfiguration
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** What a malformed OpenNFR document is refused with, whole (test-model layer 1; BCKLG-233, FR-008).
  *
  * A reason carries circe's own text — the decoding failure's message, or the YAML parser's — so a circe version that words a
  * failure differently changes what a user reads. Every reason here was recorded on circe 0.15.0-M1 and circe-yaml 1.15.0;
  * should circe word it otherwise, the message is restored in `RequirementSet.decode`, never by editing these values.
  */
class OpenNfrMalformedDocumentSpec extends AnyWordSpec with Matchers with EitherValues {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private def write(yaml: String): String = {
    val f = Files.createTempFile("opennfr", ".yaml")
    f.toFile.deleteOnExit()
    Files.write(f, yaml.getBytes("UTF-8")): Path
    f.toAbsolutePath.toString
  }

  // ModelSpec's smallest accepted document, here without its required `selector`.
  private val missingSelector =
    """apiVersion: opennfr.io/v1
      |kind: RequirementSet
      |metadata: {name: minimal}
      |spec:
      |  requirements:
      |    - name: r
      |      criteria:
      |        - {metric: loadtest.request.duration, aggregation: p95, op: lte, threshold: 500, unit: ms}
      |""".stripMargin

  private val missingSelectorReason =
    "not an OpenNFR document: Attempt to decode value on failed cursor: " +
      "DownField(selector),DownArray,DownField(requirements),DownField(spec) at .spec.requirements[0].selector"

  private val notYaml = "\t: : not: [yaml"

  private val notYamlReason =
    "not an OpenNFR document: while scanning for the next token\n" +
      "found character '\\t(TAB)' that cannot start any token. (Do not use \\t(TAB) for indentation)\n" +
      " in 'reader', line 1, column 1:\n" +
      "    \t: : not: [yaml\n" +
      "    ^\n"

  "FR-008: RequirementSet.decode of a malformed document" should {

    "refuse a document missing a required field with exactly the reasons recorded on the base versions" in {
      RequirementSet.decode(missingSelector).left.value shouldBe List(missingSelectorReason)
    }

    "refuse text that is not YAML with exactly the reason recorded on the base versions" in {
      RequirementSet.decode(notYaml).left.value shouldBe List(notYamlReason)
    }
  }

  "FR-008: OpenNfrAssertions.assertionsFrom of a malformed document" should {

    "throw OpenNfrException with the whole reasons and message for a document missing a required field" in {
      val path = write(missingSelector)
      val e    = intercept[OpenNfrException](OpenNfrAssertions.assertionsFrom(path))
      e.reasons shouldBe List(missingSelectorReason)
      e.getMessage shouldBe s"$path is not a renderable OpenNFR document:\n  - $missingSelectorReason"
    }

    "throw OpenNfrException with the whole reasons and message for text that is not YAML" in {
      val path = write(notYaml)
      val e    = intercept[OpenNfrException](OpenNfrAssertions.assertionsFrom(path))
      e.reasons shouldBe List(notYamlReason)
      e.getMessage shouldBe s"$path is not a renderable OpenNFR document:\n  - $notYamlReason"
    }
  }
}
