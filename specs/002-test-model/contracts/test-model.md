# The Gatling-Picatinny Test Model (authoritative)

> This is the deliverable of FR-001/002/003: the single authoritative description
> of how this project tests. It mirrors how `gatling/gatling` tests itself. The
> implementation phase publishes this content as the project's `TESTING.md` (or an
> AGENTS.md section) and links the constitution to it.

## Principle

Test the **real** thing, **test-first**. Follow `/tdd-workflow`: write the failing
test before the code (red → green → refactor); cover unit + integration + e2e;
assert observable behavior, not internals; keep tests isolated; commit no
skipped/disabled tests; keep coverage above the enforced floor (≥65%/60%). Use real components (real
`ActorSystem`, real HTTP over loopback, real Redis container, real clock or a
controllable `TestClock`); use small fakes **only at the edges** so the test can
observe outputs. Mocking is **plain ScalaMock**, leaf collaborators only — never
the Gatling runtime. Every test asserts **exact real values** and includes at least
one **negative/boundary** case. No empty test bodies. No mock-asserted-against-mock.

## Layers

> A change maps to the layer(s) that fit it — layers are NOT all mandatory per
> change. The DSL-component harness is a shared **test fixture**, not a layer. HTTP code
> is ScalaMock-unit-tested, real external systems use Testcontainers, and the e2e runs
> picatinny in a real Gatling runtime over REAL HTTP against a WireMock server (in the
> example overlay's test scope) — asserting RESPONSES with Gatling `check`, never
> `WireMock.verify` of the request. See "Shared fixtures" below.

### 1. Unit / Functional
- **For**: small/pure functions and utilities (converters, parsers, generators)
  AND HTTP-emitting code (`HttpJsonFeeder`, `THttpClient`, `profile/http`) — the
  HTTP client collaborator is mocked via ScalaMock; the test covers parsing/logic.
- **Harness**: plain ScalaTest `AnyWordSpec`/`AnyFlatSpec` + Matchers; ScalaCheck
  for properties; **plain ScalaMock** to stub leaf collaborators (incl. HTTP client
  interfaces) — no real network, no mock web server.
- **Assert**: exact return values + ≥1 boundary/negative case; for HTTP code,
  assert the value the feeder/client returns after mock-response injection AND that
  the mock expectation was satisfied (ScalaMock verification).
- **Reference**: `utils/IntensityConverterTest.scala` (pure);
  `feeders/HttpJsonFeederSpec.scala`, `utils/THttpClientSpec.scala` (ScalaMock HTTP).
- **Config**: `Test`.

### 2. DSL / Action Component *(conditional — only when a DSL/action component exists)*
- **When it applies**: ONLY when the change introduces or modifies a Gatling DSL
  piece that has runtime behavior (actions, trackers, transactions, stateful
  builders). A pure function, a doc, or a config change does NOT need this layer —
  do not force it. Pick the layer that fits the change.
- **For**: actions, builders, trackers, transactions, templates, profiles,
  assertions — Gatling DSL pieces driven without launching an app.
- **Harness**: real `CoreComponents` + `ScenarioContext` + `RecordingStatsEngine`
  via `transactions/Mocks.scala`; edge fakes `FakeEventLoop`, `TestClock`,
  `noAction`, `latchAction` from `transactions/fixtures.scala`; drive with
  `action ! session`, assert on the recorded stats messages / probed next action.
- **Assert**: recorded `StatsEngine` message fields, session mutations, next-action
  receipt; deterministic timing via `TestClock`.
- **Forbidden**: mocking `StatsEngine`/runtime; `Thread.sleep` races (use latch).
- **Reference**: `transactions/TransactionsSpec.scala`, `examples/ExampleSmokeSpec.scala`.
- **Config**: `Test`.

### 3. External Integration (Testcontainers)
- **For**: container-backed — Redis side effects/session state, Vault feeders, JDBC
  storage. (Non-container external state — JWT, startup diagnostics — also lives in
  `it` but needs no container.)
- **Harness**: `testcontainers-scala-scalatest` (`ForAllTestContainer` +
  `GenericContainer`); start a real container; exercise the real path; read back from
  the container.
- **Assert**: exact stored/read values from the real backend.
- **Forbidden**: embedded fakes / recording proxies (e.g. `RecordingJdbcDriver`) as
  the integration target — that is mock-vs-mock.
- **Reference (EXISTING)**: `src/it/.../redis/RedisIntegrationSpec.scala` (redis:7),
  `src/it/.../feeders/VaultIntegrationSpec.scala` (vault:1.17).
- **Config**: `it` (Docker required; excluded from the unit gate).

### 4. Full Gatling (end-to-end, in examples)
- **For**: proving picatinny's DSL (transactions, feeders, JWT, converters) works inside a
  **real Gatling runtime** over **real HTTP** — exercise picatinny's extensions, not Gatling
  itself. Real consumer usage in the `examples/` overlays, NOT in the library
  (`Provided`/non-runnable).
- **WireMock as a real HTTP target.** The overlay sim drives picatinny features over real HTTP
  against a **WireMock** server that ECHOES request values back into the response via response
  templating; Gatling `check` then validates the RESPONSES. WireMock lives in the example
  overlay's **TEST scope only** (injected by `scripts/test-scala-sbt-template.sh`), never a
  library dependency. The rule that keeps this from being mock-testing-mock: assert the
  **RESPONSES** with `check`, **NEVER** `WireMock.verify` of the request and **NEVER** re-decode
  the request (the JWT's crypto correctness is unit-tested separately in `JwtSpec`, layer 1).
- **Harness**: a real `Simulation`/`SimulationWithTransactions` in an overlay — the decomposed
  `HttpIntegrationCoverage` sim (`scenarios/`→`cases/`→`feeders/`) driving picatinny DSL, each
  feature via its picatinny method: feeders (`CurrentDateFeeder`), JWT (`setJwt`), transactions
  (`startTransaction`/`endTransaction`), converters (`IntensityConverter` `.rpm` for the
  injection rate). Run by the overlay's NATIVE Gatling task — `sbt Gatling/test` (scala-sbt) /
  `mvn gatling:test` (java) / `gradle gatlingRun` (kotlin) — under the `template-tests` CI gate.
  No custom launcher — the build-tool plugin is the runner.
- **Assert**: real picatinny outputs in a real run, validated against the echoed responses with
  Gatling `check` — `jsonPath("$.ts")` == the `CurrentDateFeeder` value, `jsonPath("$.auth")` ==
  `"Bearer #{jwt}"` — plus `.assertions(global.failedRequests.count.is(0), details("api-call")...)`
  / transaction stats. An assertion failure → task non-zero / code 2.
- **Config**: example overlay Gatling task; the library does NOT enable `GatlingPlugin`.

### 5. Compile Guard
- **For**: locking public DSL signatures (compatibility, Constitution II).
- **Harness**: compile-only specs / `*CompileTest` that must compile.
- **Reference**: `javaapi/assertions/JavaAssertionsCompileTest.java`,
  `javaapi/JavaTemplateSyntaxTest.java`.
- **Config**: `Test`.

### 6. Facade Delegation
- **For**: the Java/Kotlin facade.
- **Harness**: JUnit 5 (jupiter-interface) + AssertJ.
- **Assert**: facade output equals Scala-core output for identical inputs
  (delegation/parity); no facade-only logic.
- **Reference**: `javaapi/JavaFeedersTest.java`, `javaapi/JavaUtilsTest.java`.
- **Config**: `Test`.

## Shared fixtures (NOT layers)

- **WireMock HTTP target** (layer-4 e2e only, example overlay TEST scope): a real WireMock
  server that ECHOES request values back via response templating, injected by
  `scripts/test-scala-sbt-template.sh` — never a library dependency. The e2e asserts the
  **RESPONSES** with Gatling `check` (never `WireMock.verify`, never re-decoding the request),
  so it is NOT mock-testing-mock. HTTP-emitting picatinny code is still ScalaMock-unit-tested
  (layer 1); real external systems use Testcontainers (layer 3).
- **DSL-component harness** (`transactions/Mocks.scala` + `fixtures.scala`): real
  `CoreComponents`/`RecordingStatsEngine` + edge fakes, used by layer-2 component
  tests.

## Mock-vs-real boundary

| Allowed real | Allowed fake (edge only) | Mock library (leaf collaborators only) |
|--------------|--------------------------|----------------------------------------|
| ActorSystem, HTTP stack, Redis (container), clock | next-action probe, `FakeEventLoop`, `RecordingStatsEngine`, `TestClock` | **plain ScalaMock** for awkward, non-runtime leaf deps (no Mockito) |

The Gatling runtime/DSL is **never** in the mock column (FR-017).

## CI gates

| Gate | Command | Covers | Docker |
|------|---------|--------|--------|
| Unit/component | `sbt compile test` | layers 1 (ScalaMock for HTTP), 2, 5, 6 | no |
| Integration | `sbt "IntegrationTest / test"` | layer 3 | yes |
| Full Gatling e2e (in `examples/`) | `sbt Gatling/test` in the overlay, under the `template-tests` CI gate | layer 4 (`HttpIntegrationCoverage` over real HTTP vs WireMock; assert RESPONSES with `check`) | no |
| Coverage | `sbt coverage test "IntegrationTest/test" coverageReport` (floor ≥65%/60%) | breadth | yes |
