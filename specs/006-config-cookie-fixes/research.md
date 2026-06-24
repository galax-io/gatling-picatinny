# Phase 0 Research: Config & Cookie Correctness Fixes (v1.22.0)

Sources: Gatling 3.13.5 sources jar (`gatling-http-3.13.5-sources.jar`), netty 4.1.119.Final cookie sources, repo grep. All findings independently adversarially verified (cookie-API accessibility verdict re-confirmed against the jar).

---

## Decision 1 — Gatling cookie-jar registration route (FR-004)

**Decision**: Use the **public** Gatling DSL `addCookie(Cookie(name, value).withDomain(domain))`, iterating the runtime-parsed cookie list with `foreach` over a session-stored collection. Carry `name`/`value` (runtime) + `domain` (the `restoreCookies` arg) + default path `/`. Keep the existing `session.set(name, value)` (additive).

**Rationale**:
- The full-fidelity jar-store API `io.gatling.http.cookie.CookieSupport.storeCookie/storeCookies` is `private[http] object CookieSupport` (`CookieSupport.scala:27`) — **not referenceable from `org.galaxio.gatling.storage` at Scala compile time** (verified; the bytecode is public but only reachable via reflection). Reflection couples a published library to Gatling internals across `Provided`-scope versions → rejected by user decision (2026-06-23).
- The public `addCookie` route is supported and compile-safe. `AddCookieDsl` (`AddCookieDsl.scala:20-32`) types `name`/`value` as `Expression[String]` (runtime EL OK) but `domain: Option[String]`, `path: Option[String]`, `maxAge: Option[Long]`, `secure: Boolean` are **plain build-time values** — they cannot vary per parsed cookie at runtime. There is **no `withHttpOnly`**.
- `addCookie` returns an `AddCookieBuilder` → a `SessionHook` **action** wired at scenario-build time; it is NOT a `Session => Session` and cannot run inside `exec { session => ... }`. A runtime-variable cookie count is therefore handled with `foreach("#{cookies}", "cookie") { exec(addCookie(...)) }`.

**Alternatives considered**:
- *Reflection into `CookieSupport$.MODULE$.storeCookie`* — full per-cookie fidelity incl. httpOnly, works inside `exec`, but fragile/version-coupled. Rejected.
- *Construct `AddCookieDsl(...)` directly with per-cookie attrs* — the attribute fields are build-time; cannot carry runtime per-cookie domain/path/maxAge/secure. Does not solve the runtime problem.

**Key facts to honor in implementation**:
- `withMaxAge` takes `Int` (`AddCookieDsl.scala:31`) — N/A since we don't propagate maxAge, but note for any future change.
- netty maxAge is **seconds**; a jar cookie is persistent iff `maxAge != UNDEFINED_MAX_AGE` and expired iff `maxAge <= 0` (`CookieJar.scala:53-56`). Restored cookies become session cookies (no maxAge) — correct for a test run.
- `CookieJar.get` filters secure cookies unless the request is a secure context (https/wss/localhost) (`CookieJar.scala:128-159`). Since we don't propagate `secure`, restored cookies attach on plain http too — acceptable.
- The default cookie path constant is `/` (`CookieActionBuilder.scala:25`).
- **httpOnly is irrelevant to an outbound (client-sent) cookie** — it is a server→browser directive governing JS access; a load client transmits only `name=value`. Dropping it has zero functional effect.

**Citations**: `CookieSupport.scala:27,45-51,53-56,80-90`; `AddCookieDsl.scala:20-32`; `AddCookieBuilder.scala:24,63-68,71`; `CookieActionBuilder.scala:25`; `CookieJar.scala:53-56,128-159`; netty `Cookie.java:110,145`, `DefaultCookie.java:37,120-122`.

---

## Decision 2 — IntensityConverter regex (FR-001/002/003)

**Decision**: Replace `"""(\d+\.?\d?)\s?(\w+)?"""` with an **anchored full-match** `"""^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)?$"""`, matched against `intensity.trim` (explicit `.trim` so leading/trailing whitespace like `" 0.25 rps "` is stripped before the anchored `^…$` match; via a `case pattern(value, unit) =>` extractor / `findFirstMatchIn`), defaulting the unit to `rps`, and making the unit `match` exhaustive with an explicit `case _ => throw IllegalArgumentException(...)`.

**Rationale**:
- Current `\d+\.?\d?` captures at most ONE fractional digit, so `"0.25 rps"` → group(1)=`0.2`, group(2)=`5` → `0.2`; `"123.55"` → `123.5`. Verified on the JVM regex engine. This is #93.
- `findAllIn` + `group()` without a match-success check matches a prefix substring and never rejects trailing garbage, so `"1.2.3"`→`1.2`, `".5"`→`5` are silently accepted. Anchoring with `^...$` + `[a-zA-Z]+` unit makes malformed input produce **no match** → clean `IllegalArgumentException` (preserve the existing message `"Simulation param for intensity incorrect"` and type).
- Default unit (`rps`) and case-insensitivity (`toLowerCase`) are preserved.

**Edge-case table** (expected behavior after fix):

| Input | Result |
|-------|--------|
| `"0.25 rps"` | `0.25` (was `0.2`) |
| `"123.55 rph"` | `123.55/3600` (was `123.5/3600`) |
| `"50 rpm"` | `50/60` |
| `"10"` (no unit) | `10` rps |
| `"100 RPS"` / `"Rps"` | `100` rps |
| `"1.5   rps"` (extra ws) | `1.5` rps (was: unit lost → defaulted) |
| `"12rps"` (no sep) | `12` rps |
| `".5"`, `"1."`, `"1.2.3"`, `"abc"`, `""`, `"-5"` | throw `IllegalArgumentException` |
| `"3600.0 jpeg"` (bad unit) | throw (now explicit `case _`, not swallowed `MatchError`) |

**Alternatives considered**: keep `\w+` for the unit (minimal diff) — acceptable once the value group is fixed and the match anchored; `[a-zA-Z]+` chosen as defense-in-depth so a digit can never leak into the unit group.

**Citations**: `IntensityConverter.scala:17,20-22,23-27,28-30`; `IntensityConverterTest.scala:34-48` (no multi-digit-decimal case existed — why #93 slipped through).

---

## Decision 3 — Max-Age WARN logging (FR-006/007)

**Decision**: Make `CookieParser` `extends StrictLogging` (`import com.typesafe.scalalogging.StrictLogging`) and emit `logger.warn(...)` naming the offending value only when `Max-Age` is **present but not parseable as a `Long`** (`toLongOption` returns `None` — covers non-numeric `abc`, empty `Max-Age=`, and overflow). Absent → no warn; a value that parses (including negative, e.g. `-1`) → no warn. Never throw.

**Rationale**: Matches the repo's `object ... extends StrictLogging` precedent (`TransactionTracker.scala:5,11,29` — `logger.warn(s"...")`). `CookieParser` is a plain `object` today with no logger. The current `attrs.get("max-age").flatMap(_.toLongOption)` (`CookieParser.scala:37`) silently drops bad values — split it so a present-but-unparseable value triggers the warn while still returning `maxAge = None` (behavior preserved, observability added).

**Test idiom**: assert the returned `ParsedCookie.maxAge` value (`CookieParserSpec` style, `c.maxAge shouldBe ...`), AND capture the WARN with the repo's existing Logback `ListAppender` helper pattern from `AssertionsBuilderSpec.scala:46-59` (`captureWarns`), targeting logger name `org.galaxio.gatling.storage`. No new dependency (logback-classic already on the test classpath); no Mockito.

**Citations**: `CookieParser.scala:13,37`; `TransactionTracker.scala:5,11,29`; `AssertionsBuilderSpec.scala:46-59,82-88`; `CookieParserSpec.scala:6,27`.

---

## Decision 4 — randomValue scaladoc (FR-008)

**Decision**: Fix the Scaladoc on `randomValue(min, max)` (and the single-bound `randomValue(max)`) to state the maximum is **exclusive**. No implementation or test change.

**Rationale**: All `RandomProvider` impls use `ThreadLocalRandom.nextInt/nextLong(min, max)` and `min + nextDouble()*(max-min)` — all **exclusive** of `max`. The Scaladoc at `RandomDataGenerators.scala:143-144` says "inclusive" (wrong); `randomValue(max)` at `:135` says "less than or equal to max" (also wrong). Doc-only — the existing property tests in `RandomDataGeneratorsTest` already assert `[min, max)` and stand as the behavior guard (TESTING.md layer 5 compile/behavior guard). No new test needed.

**Citations**: `RandomDataGenerators.scala:128-137,139-160`; `package.scala:14-44` (provider impls, all exclusive).

---

## Decision 5 — Test layering & e2e (FR-004/005)

**Decision**: pure cookie-string parsing → `CookieParserSpec` (layer 1, exists); `restoreCookies` session-attribute behavior + multi-cookie + no-op → `SessionStorageSpec` extended as a DSL/action component test using the `transactions/Mocks` ActorSystem harness (layer 2); full cookie auto-send round-trip → net-new e2e in `examples/scala-sbt-example` (layer 4).

**e2e sketch (no code) — two-role cookie switching**: Prove that `restoreCookies` injects a cookie into the jar so it auto-sends, AND that re-restoring a different value for the **same** cookie name *swaps the active role* (overwrite revokes the prior one). This is a stronger test than a single echo: the response **status** is gated by the cookie value, asserted via Gatling `check`.

Design rationale: use ONE cookie name `sid` with two distinct values (`user-secret`, `admin-secret`). Same name + same domain (`localhost`) + same path (`/`) ⇒ the jar overwrites on re-restore, so switching roles genuinely *revokes* the previous one. (Two different cookie names would accumulate in the jar — both would stay valid and "the opposite endpoint must fail" could not hold.)

Cookie isolation: the two "login" stubs return the raw Set-Cookie string in the **response body** (NOT in a `Set-Cookie` response header), so Gatling's native cookie auto-capture does NOT fire — the cookie reaches the protected endpoints ONLY via `restoreCookies`. This is what makes the test exercise picatinny, not Gatling's built-in handling.

WireMock stubs (dynamic port, `globalTemplating` already on, `Debug.scala:22`):
- `GET /login/user` → 200, body `{"cookie":"sid=user-secret; Path=/"}` (no Set-Cookie header).
- `GET /login/admin` → 200, body `{"cookie":"sid=admin-secret; Path=/"}`.
- `GET /admin/data`: high-priority stub matching `withCookie("sid", equalTo("admin-secret"))` → 200; low-priority catch-all for the same path → 403.
- `GET /user/data`: high-priority stub matching `withCookie("sid", equalTo("user-secret"))` → 200; catch-all → 403.

Scenario flow — **8 steps, 5 role-gated status checks** (single VU, one chain; each protected call carries NO explicit `Cookie` header — relies purely on auto-send from the jar). Steps 1/4/7 are login+`restoreCookies`; steps 2/3/5/6/8 are the role-gated protected calls:
1. `GET /login/user` (`check(status.is(200))`, save body cookie) → `restoreCookies(<attr>, "localhost")` ⇒ jar `sid=user-secret`.
2. `GET /admin/data` ⇒ **403** `check(status.is(403))` (user cookie, not admin — NOT success).
3. `GET /user/data` ⇒ **200** `check(status.is(200))` (user — success).
4. `GET /login/admin` (200, save body cookie) → `restoreCookies(...)` ⇒ jar `sid=admin-secret` (overwrites user).
5. `GET /admin/data` ⇒ **200** (admin — success).
6. `GET /user/data` ⇒ **403** (user revoked by overwrite — the "наоборот").
7. `GET /login/user` again (200, save) → `restoreCookies(...)` ⇒ jar `sid=user-secret` (back to user).
8. `GET /user/data` ⇒ **200** (returned to user — success).

Each step's expectation is a Gatling `check(status.is(...))` on the RESPONSE; a failed check fails the request and the simulation's `.assertions(global.failedRequests.count.is(0))` gate fails the build. No `WireMock.verify`, no request re-decoding (the stub *matching* on cookie value only drives the response status, which Gatling then asserts — allowed; it is not mock-testing-mock).

Placement (decided 2026-06-23): add this directly to the existing `Debug.scala` simulation — the cookie stubs into its `WireMockServer` setup, the 8-step flow as a scenario wired into its `setUp` (`atOnceUsers(1)`), reusing Debug's existing `after { mock.stop() }` and `.assertions(global.failedRequests.count.is(0))`. No separate `CookieSwitchSimulation` file. Cookie values can be inline constants in the stubs (no separate feeder needed). The `SessionStorageSpec` component unit test is kept for the `session.set`/no-op behavior.

**Run**: from `examples/scala-sbt-example`, `sbt Gatling/test` (or `sbt "Gatling/testOnly ...<Sim>"`); requires the library on the classpath (`sbt publishLocal` or `PICATINNY_VERSION`). No Docker.

**Citations**: `Debug.scala:22,25-46,55-60`; `HttpIntegrationCases.scala:16-28`; `FeederValidationCases.scala:20-25`; `build.sbt:8-19`; `scripts/test-scala-sbt-template.sh:63-69`; TESTING.md layers 1/2/4.
