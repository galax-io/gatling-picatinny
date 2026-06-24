package org.galaxio.performance.picatinny

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{WireMock => WM}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.galaxio.gatling.storage.SessionStorage
import org.galaxio.gatling.transactions.Predef._
import org.galaxio.performance.picatinny.cases.{FeederValidationCases, HttpIntegrationCases}
import org.galaxio.performance.picatinny.feeders.{FeederValidationFeeders, HttpIntegrationFeeders}
import org.galaxio.performance.picatinny.scenarios.PicatinnyScenario

/** Layer-4 e2e debug gate: 1 user, 1 call per picatinny feature, no load.
  *
  * Exercises in sequence:
  *   - all picatinny feeders + JWT + transactions (via PicatinnyScenario)
  *   - every faker feeder echoed + validated over HTTP (WireMock GET /echo)
  *   - CurrentDateFeeder + JWT round-trip over HTTP (WireMock GET /echo/{ts})
  *   - picatinny `SessionStorage.restoreCookies` two-role cookie switching (user/admin)
  */
class Debug extends SimulationWithTransactions {

  private val mock = new WireMockServer(options().dynamicPort().globalTemplating(true))
  mock.start()

  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/echo"))
      .willReturn(
        WM.aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(
            FeederValidationFeeders.patterns.map { case (field, _) => s""""$field":"{{request.headers.X$field}}"""" }
              .mkString("{", ",", "}"),
          ),
      ),
  )

  mock.stubFor(
    WM.get(WM.urlPathMatching("/echo/.+"))
      .willReturn(
        WM.aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""{"ts":"{{request.pathSegments.[1]}}","auth":"{{request.headers.Authorization}}"}"""),
      ),
  )

  // --- Cookie role-switch e2e (picatinny SessionStorage.restoreCookies) ---
  // Login stubs return the raw Set-Cookie in the response BODY (not a Set-Cookie header) so Gatling does NOT
  // auto-capture it — the cookie reaches the protected endpoints ONLY via restoreCookies.
  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/login/user"))
      .willReturn(
        WM.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
          .withBody("""{"cookie":"sid=user-secret; Path=/"}"""),
      ),
  )
  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/login/admin"))
      .willReturn(
        WM.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
          .withBody("""{"cookie":"sid=admin-secret; Path=/"}"""),
      ),
  )
  // Protected endpoints: 200 only when the auto-sent cookie value matches the role; otherwise the catch-all 403.
  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/admin/data")).atPriority(1)
      .withCookie("sid", WM.equalTo("admin-secret"))
      .willReturn(WM.aResponse().withStatus(200)),
  )
  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/admin/data")).atPriority(10)
      .willReturn(WM.aResponse().withStatus(403)),
  )
  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/user/data")).atPriority(1)
      .withCookie("sid", WM.equalTo("user-secret"))
      .willReturn(WM.aResponse().withStatus(200)),
  )
  mock.stubFor(
    WM.get(WM.urlPathEqualTo("/user/data")).atPriority(10)
      .willReturn(WM.aResponse().withStatus(403)),
  )

  private val cookieStorage = SessionStorage()

  // 8 steps, 5 role-gated status checks. Each protected call carries NO explicit Cookie header — it relies on
  // the jar populated by restoreCookies. Re-restoring `sid` overwrites the prior value (role switch).
  private val cookieSwitch =
    scenario("Cookie role switch")
      .exec(http("login user").get("/login/user").check(status.is(200)).check(jsonPath("$.cookie").saveAs("setCookie")))
      .exec(cookieStorage.restoreCookies("setCookie", "localhost"))
      .exec(http("admin denied as user").get("/admin/data").check(status.is(403)))
      .exec(http("user ok as user").get("/user/data").check(status.is(200)))
      .exec(http("login admin").get("/login/admin").check(status.is(200)).check(jsonPath("$.cookie").saveAs("setCookie")))
      .exec(cookieStorage.restoreCookies("setCookie", "localhost"))
      .exec(http("admin ok as admin").get("/admin/data").check(status.is(200)))
      .exec(http("user denied as admin").get("/user/data").check(status.is(403)))
      .exec(http("login user again").get("/login/user").check(status.is(200)).check(jsonPath("$.cookie").saveAs("setCookie")))
      .exec(cookieStorage.restoreCookies("setCookie", "localhost"))
      .exec(http("user ok again").get("/user/data").check(status.is(200)))

  setUp(
    PicatinnyScenario("Picatinny Debug", "scala-debug")
      .feed(FeederValidationFeeders.all)
      .feed(HttpIntegrationFeeders.ts)
      .exec(FeederValidationCases.validateAll)
      .exec(HttpIntegrationCases.echo)
      .inject(atOnceUsers(1)),
    cookieSwitch.inject(atOnceUsers(1)),
  ).protocols(http.baseUrl(s"http://localhost:${mock.port()}"))
    .assertions(global.failedRequests.count.is(0))

  after {
    mock.stop()
  }
}
