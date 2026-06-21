# Phase 0 Research: Test Model & Regression-Proof Coverage

**Feature**: 002-test-model | **Date**: 2026-06-21

## R1 — Upstream Gatling test model (what to mirror)

**Decision**: Adopt the four-layer upstream model — pure unit; DSL/action
component (real `ActorSystem` + recording probes, no app run); HTTP integration
against a real request-recording server; compile-only DSL signature guards — plus
a facade-delegation layer for the Java/Kotlin surface.

**Rationale**: Verified against `gatling/gatling` (`main`): ScalaTest
`AnyFlatSpec`/`AnyWordSpec` + Matchers, ScalaCheck for properties. Components are
driven for real with small fakes at the edges (`MockActorRef` probe,
`EmptySession`/`FakeEventLoop`, recording `StatsEngine`, `ScenarioTestFixture`).
Upstream tests HTTP via a self-written Netty `HttpServer` that records requests
(`runScenario` + `verifyRequestTo`).

**Our adaptations (do NOT copy upstream verbatim here)**:
- **Mock library**: this project uses **plain ScalaMock** (already a `test,it`
  dependency), NOT Mockito, and only for awkward leaf collaborators (FR-017). The
  Gatling runtime/DSL is never mocked.
- **HTTP server**: the self-written Netty server is NOT adopted at the library level;
  HTTP-emitting library code (`HttpJsonFeeder`, `THttpClient`) is unit-tested with
  **ScalaMock** (R2). The real-HTTP e2e (layer 4) instead uses a **WireMock** server
  in the example overlay's TEST scope only (injected by
  `scripts/test-scala-sbt-template.sh`, never a library dependency) that ECHOES request
  values back via response templating, so Gatling `check` can validate the RESPONSES
  (R3). The self-written-Netty description above is upstream's choice, recorded as the
  philosophy to mirror, not the implementation.

**Already present in this repo (reuse, do not reinvent)**:
- `transactions/Mocks.scala` builds a real `CoreComponents` + `ScenarioContext`
  with `RecordingStatsEngine` (from `gatling-test-framework`) — this is the local
  equivalent of upstream's `ScenarioTestFixture`/`HttpSpec` harness.
- `transactions/fixtures.scala` provides `FakeEventLoop`, `TestClock`,
  `noAction`, `latchAction` — the edge fakes.
- `examples/ExampleSmokeSpec.scala` exercises feeders/JWT/converters via real DSL
  pieces with strict value assertions (component layer, no app run).

**Alternatives considered**: Running full `Simulation`s for every feature —
rejected, see R3. Mocking the runtime — rejected, violates Constitution III and
the upstream philosophy.

## R2 — HTTP unit testing with ScalaMock (library units), WireMock e2e (overlay)

**Decision**: HTTP-emitting library code (`HttpJsonFeeder`, `THttpClient`,
`profile/http`) is tested with **plain ScalaMock** at unit level — mock the HTTP
client interface, assert returned/parsed value + expectation satisfied. The real-HTTP
e2e layer (R3) drives picatinny over REAL HTTP against a **WireMock** server that lives
in the example overlay's TEST scope only (no library dependency, no `Provided`/`Compile`
dependency).

**Rationale**: ScalaMock is lighter and already a `test,it` dependency. Library unit
tests do not need a real HTTP server — mocking the client interface is sufficient and
faster. The WireMock e2e avoids the mock-testing-mock anti-pattern (the project's
founding concern) by ECHOING request values back via response templating and asserting
the **RESPONSES** with Gatling `check` — NEVER `WireMock.verify` of the request and
NEVER re-decoding the request (the JWT's crypto correctness is unit-tested separately
in `JwtSpec`).

**Library HTTP unit boundary**: If `THttpClient`/`HttpJsonFeeder` lack a mockable
HTTP interface, extract one (trait) before writing the ScalaMock test — that's the
TDD "red first" step. The interface extraction may count as a small internal refactor
(public API unchanged; Constitution II OK).

**Alternatives considered**: A self-stubbed mock web server you both configure AND
`verify`/re-decode against — rejected: that tests the mock, not picatinny
(mock-testing-mock). WireMock is used the *right* way in R3 (echo the request back,
assert the RESPONSE with `check`, never `verify`/re-decode). For library HTTP units a
self-written Netty / OkHttp MockWebServer is still unneeded — ScalaMock is sufficient
and faster.

## R3 — Running real Gatling Simulations (in examples, via the build-tool plugin)

**Decision**: Two complementary mechanisms.
1. **Unit / direct-call layer (default, `Test` scope, no Docker)**: use **ScalaMock**
   to mock the HTTP client interface — `HttpJsonFeeder` and `THttpClient` depend on
   an HTTP interface that is mocked; inject a stubbed response; assert the returned
   value + ScalaMock expectation satisfied. For DSL builders (`profile/http`), drive
   via the existing `CoreComponents`/`ScenarioContext` harness (`Mocks.scala` pattern).
   No mock web server at this layer — ScalaMock is sufficient here (the WireMock server
   belongs to the layer-4 e2e overlay only).
2. **Full end-to-end (layer 4) — real picatinny Simulation in `examples/`, over REAL
   HTTP against WireMock, via the build-tool Gatling plugin**: real Gatling usage lives
   in the **example overlay projects** (the real consumers), NOT in the library — the
   library is `Provided` and is not a runnable Gatling app. The layer-4 sim is
   `HttpIntegrationCoverage` (decomposed `scenarios/` → `cases/` → `feeders/`), which
   drives picatinny DSL — feeders (`CurrentDateFeeder`), JWT (`setJwt`), transactions
   (`startTransaction`/`endTransaction`), converters (`IntensityConverter` `.rpm` for the
   injection rate) — over REAL HTTP against a **WireMock** server that ECHOES request
   values back via response templating. Gatling `check` validates the **RESPONSES**:
   `jsonPath("$.ts")` == the `CurrentDateFeeder` value, `jsonPath("$.auth")` ==
   `"Bearer #{jwt}"`, plus `.assertions(global.failedRequests.count.is(0),
   details("api-call")…)`. The rule that keeps it from being mock-testing-mock: assert
   RESPONSES with `check`, **NEVER** `WireMock.verify` of the request and **NEVER**
   re-decode the request (the JWT's crypto correctness is unit-tested separately in
   `JwtSpec`). WireMock lives in the overlay's TEST scope only (injected by
   `scripts/test-scala-sbt-template.sh`), never a library dependency. These run by each
   overlay's NATIVE Gatling task — **`sbt Gatling/test`** (scala-sbt), `mvn gatling:test`
   (java-maven), `gradle gatlingRun` (kotlin-gradle) — exactly as the existing
   `template-tests` gate already runs the overlays
   (`scripts/test-scala-sbt-template.sh` → `sbt Gatling/test`). A failing `check` or
   assertion fails the task (non-zero) — the failure signal.

**Rationale**: The overlays already run Gatling through the build-tool plugin
(`sbt Gatling/test` / `mvn gatling:test` / `gradle gatlingRun`) — that plugin task
IS the runner: it picks up the Simulation, drives the real picatinny DSL in a real
Gatling runtime, evaluates `.assertions(...)`, and fails (non-zero, sbt-gatling status
code 2 = assertions failed) on failure. No custom programmatic launcher is needed in
the overlay. A hand-written `GatlingRunner` → `Gatling.main(Array("-s", FQCN))`
(forked, because `main` `sys.exit`s) is only relevant when launching WITHOUT the
plugin; since the overlays have the plugin, that approach is **dropped** to avoid
fork/`sys.exit` complexity. (Note for completeness: `Gatling.fromMap`/
`GatlingPropertiesBuilder` were removed in 3.11+; `Gatling.main` with
`-s/--simulation <FQCN>` still exists — but we don't use it here.)

**Build change**: NONE in the library — the library does NOT enable `GatlingPlugin`
and gets NO `src/gatling` source set (keeps it a `Provided`, non-runnable library,
Constitution II). The layer-4 e2e is the `HttpIntegrationCoverage` Simulation in the
**example overlays** (which already have the Gatling plugin), run by `sbt Gatling/test`
under the existing `template-tests` CI gate. The only added dependency is **WireMock**,
in the overlay's TEST scope only (injected by `scripts/test-scala-sbt-template.sh`) —
never a library dependency.

**Alternatives considered**: Custom `GatlingRunner` → `Gatling.main(Array("-s",
FQCN))` forked in the overlay — works, but redundant when the overlay already has the
Gatling plugin; dropped. Library `src/gatling` + `GatlingPlugin` — makes the library
a runnable app, bends Constitution II, rejected. Reflection into `private[gatling]
fromArgs` — unsupported, rejected. A mock-web-server you `verify`/re-decode against —
rejected: mock-testing-mock; instead the WireMock e2e ECHOES the request back and
asserts the RESPONSE with `check` (never `verify`, never re-decode).

## R4 — Testcontainers for external integrations (Redis/JWT/diagnostics/JDBC)

**Decision**: Use `testcontainers-scala-scalatest` (ALREADY a `test,it`
dependency, currently unused) in the `IntegrationTest` (`it`) config. Redis side
effects get a real Redis container; assert on actually-stored/read values.

**Rationale (corrected after deeper recon)**: The `src/it` source set ALREADY EXISTS
and ALREADY has real Testcontainers integration: `RedisIntegrationSpec`
(redis:7-alpine, `ForAllTestContainer`, strict value asserts) and
`VaultIntegrationSpec` (vault:1.17). So Redis and Vault are largely DONE — they are
verified/strengthened, not created. The real remaining `it` gaps are: **JDBC**
storage (no integration test; `JdbcTestSupport` is a `RecordingJdbcDriver` JDK-proxy
fake used by unit tests, NOT a container — a real DB container is needed), and the
**non-container** `it` coverage for JWT and startup diagnostics (`UtilityIntegrationSpec`
currently lives under `src/test`, not `it`). The `testcontainers-scala-scalatest`
dep and the `it` config already exist; no new dependency needed. Docker-dependent
tests live in `it` so the unit gate stays green without Docker (FR-018).

**Alternatives considered**: Hand-rolled embedded Redis / mocks — violates
Constitution III, rejected.

## R5 — Regression-proof verification (negative-test discipline)

**Decision**: Per clarification — manual deliberate-break check during
development, PR-reviewer spot-check. No mutation tooling, no new CI coverage gate.
Each new/backfilled test MUST include at least one negative/boundary assertion and
assert exact values.

**Rationale**: Keeps regression-proof dependency-free (Constitution IV). The
existing `sbt-scoverage` gate (`coverageMinimumStmtTotal := 45`,
`coverageFailOnMinimum`) IS used. **Measured baseline (unit `test` only): statement
64.03% / branch 61.63%.** Floor set **data-driven** (FR-021), just under measured:
- **Enforced (final): statement 65 / branch 60** — branch floor INTRODUCED (none before).
  Measured with `it` included: 69.88% statement / 63.37% branch.
- The earlier two-stage 80/70 target was **superseded**: reaching 80/70 needs large
  low-value backfill of generated/faker/benchmark code; the floor instead ratchets up
  as real coverage rises.
Coverage measured from the instrumented `test`(+`it`) runs; the forked Gatling e2e
JVM does not contribute to scoverage. Negative-test discipline remains the *proof*
tests catch regressions; coverage is the *breadth* gate.

**Alternatives considered**: Stryker4s mutation testing — new dep + CI cost,
rejected by clarification.

## R7 — TDD workflow adoption (`/tdd-workflow`)

**Decision**: Layer the `/tdd-workflow` discipline on top of the six-layer model:
1. Write the failing test FIRST (red), implement minimally (green), then refactor.
2. Cover each behavior at unit + integration + end-to-end levels as applicable
   (our e2e = the Full Gatling layer, R3 — there is no Playwright/browser layer).
3. Assert observable behavior, not implementation internals.
4. Tests are isolated (each sets up its own data); no skipped/disabled tests
   committed; unit tests stay fast.
5. Raise the scoverage floor data-driven (enforced 65/60; FR-021) — ≥80% is an
   aspirational TDD target, not the enforced gate (superseded; see R5).

**Rationale**: User directive. The skill's JS/React examples (Jest/Playwright) are
adapted to this stack: ScalaTest/JUnit for unit+component, Testcontainers for
integration, `sbt Gatling/test` in the examples for e2e. The model document and constitution Principle
III reference TDD so it applies to every future feature via the speckit gate.

**Alternatives considered**: Test-after — rejected, defeats the user's intent and
the point of the speckit test-sketch gate (think of the test before implementing).

## R8 — CI JUnit reporting + README badges

**Decision**:
- **JUnit reports**: both `scalatest` and `sbt-jupiter-interface` already emit
  JUnit XML to `target/test-reports/` (and `target/test-reports` per config). Add a
  CI step per gate to (a) `actions/upload-artifact` the XML, and (b) publish a
  PR-visible test summary via a test-reporter action
  (`EnricoMi/publish-unit-test-result-action` — produces a check run + counts and
  can emit a badge; alternative `dorny/test-reporter`). Run it for: `Test/test`,
  `IntegrationTest/test`, the Java facade suite, `template-tests`, and the Gatling
  e2e run. Use `if: always()` so reports publish even when tests fail (FR-022).
- **README badges**: fix the legacy codecov badge
  `https://codecov.io/github/galax-io/gatling-picatinny/coverage.svg?branch=main`
  → modern `https://codecov.io/gh/galax-io/gatling-picatinny/branch/main/graph/badge.svg`
  (link to the codecov project page). Keep CI + Maven + License + Scala Steward.
  Add a **test-results badge** — either the badge emitted by the publish action, or
  a shields.io endpoint badge fed by the published results (FR-023).

**Rationale**: Reports already exist on disk; the gap is surfacing them. Codecov is
already wired (the `coverage` job uploads via `codecov-action@v7`), so the badge
just needs the modern URL. All edits are in `.github/workflows/ci.yml` + `README.md`.

**⚠️ AUTHORIZATION GATE**: `.github/workflows` is CI truth (AGENTS.md "ask first").
The CI workflow edits MUST be authorized before implementation.

**Alternatives considered**: JUnit-report-only without PR surfacing — rejected,
"обновить все отчёты" implies visible/published, not just stored. Per-gate separate
badges — noise; one aggregate test-results badge preferred.

## R6 — Speckit enforcement mechanism

**Decision**: Encode the model in BOTH (a) the constitution — expand Principle III
(Test Discipline) with the layered model, the **test-first TDD** discipline (R7),
and the per-feature test-sketch requirement (MINOR version bump 1.0.2 → 1.1.0,
update Last Amended 2026-06-21, propagate to AGENTS.md), AND (b) the speckit
templates — add a mandatory "Test Model" section to `plan-template.md` and
`spec-template.md`, plus checklist items that FAIL when it is missing/empty/contains
code.

**Rationale**: Clarification chose the strongest, self-perpetuating enforcement.
Constitution edits must bump version + Last Amended (project rule). Template +
checklist gate makes every future `/speckit-plan` mechanically require the sketch.

**Alternatives considered**: Standalone doc only / template-only — weaker, rejected
by clarification.
