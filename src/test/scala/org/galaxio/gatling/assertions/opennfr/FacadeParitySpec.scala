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

    "refuse the same document for the same reasons" in {
      val doc  = "src/test/resources/opennfr/group-only.yaml"
      val core = intercept[OpenNfrException](OpenNfrAssertions.assertionsFrom(doc))
      val java = intercept[OpenNfrException](JavaRender.assertions(doc))

      java.reasons shouldBe core.reasons
    }
  }
}
