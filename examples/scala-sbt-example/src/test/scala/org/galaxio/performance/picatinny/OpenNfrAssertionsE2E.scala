package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{WireMock => WM}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.galaxio.gatling.assertions.opennfr.OpenNfrAssertions

import scala.concurrent.duration._

/** Layer-4 e2e: the assertions a real run is judged by come from an OpenNFR document.
  *
  * Everything else about this feature is tested against assertion *values*. This is the only place the values are handed to
  * the Gatling runtime and evaluated against a run that actually happened — which is what proves the document denotes the
  * bar the run is held to, rather than merely producing objects of the right shape.
  *
  * The document asserts a request count of at least one, so a run that issued nothing fails here instead of passing every
  * latency bar vacuously.
  *
  * '''The failing direction is not run here.''' A simulation whose thresholds the run breaches would fail the overlay build,
  * which is the same signal as a broken build. To see it, lower a threshold in `opennfr-e2e.yaml` — the run then fails, and
  * the report names the assertion.
  */
class OpenNfrAssertionsE2E extends Simulation {

  // Built BEFORE the mock starts. `fromYaml` throws on a document that cannot be read or cannot be rendered, and Gatling never
  // runs the `after` hook of a simulation whose constructor failed — so a throw after `mock.start()` would leave a bound port
  // and WireMock's threads behind for the life of the JVM. That is not hypothetical: it is how the missing-resource failure in
  // the template job played out.
  private val nfr = OpenNfrAssertions.fromYaml("src/test/resources/opennfr-e2e.yaml").toSeq

  private val mock = new WireMockServer(options().dynamicPort())
  mock.start()

  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/opennfr"))
      .willReturn(WM.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"ok":true}""")),
  )

  private val httpProtocol = http.baseUrl(s"http://localhost:${mock.port()}")

  // Ungrouped on purpose: `{loadtest.request.name: opennfr echo}` is root-anchored — it denotes the
  // request with no enclosing group — so putting this in a group would make that requirement match nothing.
  private val scn = scenario("OpenNFR assertions")
    .exec(http("opennfr echo").get("/opennfr").check(status.is(200), jsonPath("$.ok").is("true")))

  after(mock.stop())

  setUp(scn.inject(constantUsersPerSec(5).during(3.seconds)))
    .protocols(httpProtocol)
    .assertions(nfr: _*)
}
