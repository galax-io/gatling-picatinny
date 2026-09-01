package org.galaxio.gatling.assertions.opennfr

import io.gatling.core.config.GatlingConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

/** The two surfaces build the same assertions (FR-016, test-model layer 6).
  *
  * The Java wrapper cannot be constructed from a core one, so each surface applies the DSL itself. What must hold is that they
  * apply it to the *same decision*: unwrapping the Java assertions must give back exactly the core ones.
  */
class FacadeParitySpec extends AnyWordSpec with Matchers {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private val translated = "src/test/resources/opennfr/nfr.yaml"

  "the Java facade and the Scala core" should {

    "build the same assertion values from the same document" in {
      val viaCore   = OpenNfrAssertions.assertionsFrom(translated).toSet
      val viaFacade = JavaRender.assertions(translated).asScala.map(_.asScala()).toSet

      viaFacade shouldBe viaCore
    }

    // `group-only.yaml` used to be the REFUSAL fixture. Upstream v0.8.0 made it renderable (#328),
    // so the facades are now compared on what they PRODUCE for it rather than on why they refuse it.
    "render the same assertions for a group-only document, on both surfaces" in {
      val doc = "src/test/resources/opennfr/group-only.yaml"
      JavaRender.assertions(doc).size shouldBe OpenNfrAssertions.assertionsFrom(doc).size
    }

    "refuse the same document for the same reasons" in {
      // A document that is still refused: the retired metric name, which v0.8.0 dropped outright.
      val doc  = "src/test/resources/opennfr/retired-metric.yaml"
      val core = intercept[OpenNfrException](OpenNfrAssertions.assertionsFrom(doc))
      val java = intercept[OpenNfrException](JavaRender.assertions(doc))

      java.reasons shouldBe core.reasons
    }
  }
}
