package org.galaxio.gatling.assertions

import io.gatling.core.config.GatlingConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for the Scala [[AssertionsBuilder]] (test-model layer 1). Driven through the `assertionsFrom` test seam, which
  * takes a [[GatlingConfiguration]] explicitly, so `GatlingConfiguration.loadForTest()` builds against the `nfr.yml` fixture
  * without the throwing simulation-only `Predef.configuration` that the public `assertionFromYaml` relies on at runtime.
  * Asserts the exact set of Gatling `Assertion`s built — including that UNrecognised NFR keys (APDEX, RPS) are skipped
  * (negative case) and that named group/request entries keep their path parts.
  */
class AssertionsBuilderSpec extends AnyWordSpec with Matchers {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  private lazy val assertions =
    AssertionsBuilder.assertionsFrom("src/test/resources/nfr.yml").toList

  "AssertionsBuilder.assertionsFrom" should {

    "build one assertion per recognised NFR entry and skip unknown keys (APDEX, RPS)" in {
      // recognised: 99p(3) + 95p(4) + errors(2) + max(2) = 11; the APDEX and RPS keys are unrecognised → skipped.
      assertions should have size 11
    }

    "produce distinct assertions" in {
      assertions.toSet should have size 11
    }

    "preserve named group/request path parts at the detail scope" in {
      val paths = assertions.map(_.path.toString)
      paths.exists(_.contains("myGroup")) shouldBe true
      paths.exists(_.contains("GET /test/uuid")) shouldBe true
    }
  }
}
