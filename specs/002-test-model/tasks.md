# Tasks: Test Model & Regression-Proof Coverage

**Input**: Design documents from `/specs/002-test-model/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: This feature IS about tests and is worked **test-first (TDD)** — write the
failing test before the code it covers (FR-019). Test tasks are first-class, not optional.

**Branch**: `002-test-model` | **Date**: 2026-06-21

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no incomplete dependency)
- **[Story]**: US1 / US2 / US3 / US4 (maps to spec.md user stories)
- Exact file paths included

## Path Conventions

Single-project library. `src/main/scala`, `src/test/scala`, NEW `src/it/scala`
(Testcontainers, `IntegrationTest` config), `src/test/java` (facade). The full
Gatling e2e is the **example overlay sim `HttpIntegrationCoverage`** (decomposed
`scenarios/`→`cases/`→`feeders/`) under
`examples/scala-sbt-example/src/test/scala/org/galaxio/performance/picatinny/`
(that overlay's Gatling config reads test sources; NO `src/gatling`) — it drives
picatinny features over REAL HTTP against a WireMock server (TEST scope of the
overlay, injected by `scripts/test-scala-sbt-template.sh`), run by
`sbt Gatling/test` — NOT the library. Speckit assets under `.specify/`.

---

## Phase 1: Setup & Authorization (BLOCKING)

- [x] T001 Obtain explicit authorization (Constitution IV / "ask first") and record approval in `specs/002-test-model/plan.md` Complexity Tracking for: (a) raise scoverage floors (set data-driven 65/60); (b) constitution 1.0.2 → 1.1.0; (c) `.github/workflows/ci.yml` edits; (d) `README.md` badge edits; (e) add `org.wiremock:wiremock` to the example overlay's TEST scope (injected by `scripts/test-scala-sbt-template.sh`) and build the `HttpIntegrationCoverage` WireMock e2e. **APPROVED 2026-06-21.** (The WireMock e2e is NOT mock-testing-mock: it asserts the RESPONSES with Gatling `check` — never `WireMock.verify` of the request and never re-decodes the request — so it exercises picatinny's real HTTP path; WireMock merely ECHOES request values back via response templating. WireMock is overlay-TEST-scope only, never a library dependency.)
- [x] T002 Confirm `project/Dependencies.scala` does NOT include `org.wiremock:wiremock`. **DONE: wiremock absent from Dependencies.scala; `Test/compile` clean.**
- [x] T003 Confirm the LIBRARY build stays a `Provided`, non-runnable library. **DONE: `build.sbt` enables only `GitVersioning, JmhPlugin` — no `GatlingPlugin`, no `src/gatling`. WireMock is NOT a library dependency; it lives only in the example overlay's TEST scope (appended by `scripts/test-scala-sbt-template.sh`). The layer-4 e2e is the overlay's `HttpIntegrationCoverage` WireMock+`check` sim, see T028/T037.**
- [x] T004 Confirm the EXISTING `src/it/scala` integration source set is wired. **DONE: `build.sbt` has `IntegrationTest = config("it") extend Test`, `.configs(IntegrationTest)`, `inConfig(IntegrationTest)(Defaults.testSettings)`; `RedisIntegrationSpec` + `VaultIntegrationSpec` present under `src/it`.**
- [x] T005 Verify dependency resolution for the library. **DONE: no wiremock in the LIBRARY classpath (it is overlay-TEST-scope only) → no Jetty/Netty clash in the published artifact. Overlay resolution (picatinny + Gatling DSL + WireMock for the e2e) validated in CI `template-tests`.**

**Checkpoint**: deps resolve, build compiles, unit gate still green (`sbt compile test`).

---

## Phase 2: Foundational (blocking prerequisites)

- [x] T006 Shared test support — **harness-reuse documented in `TESTING.md` + `AGENTS.md` (DSL-component harness reused from `transactions/Mocks.scala` + `transactions/fixtures.scala`, not duplicated). No separate `support/` package created — an empty package existing only to hold a comment adds no value.**

**Checkpoint**: DSL-component harness reused in place (`transactions/Mocks` + `fixtures`), not reinvented; no new `support/` package.

---

## Phase 3: User Story 1 — Layered test model document (Priority: P1) 🎯 MVP

**Goal**: One authoritative, copyable test-model document with a reference example per layer.

**Independent test**: Map 5 sampled production files to a layer using only the doc (SC-001).

- [x] T007 [US1] Author `TESTING.md` at repo root. **DONE: 6 layers (purpose/harness/assert/forbidden/config), ScalaMock-not-Mockito boundary, layer-4-e2e = `HttpIntegrationCoverage` overlay sim over REAL HTTP against WireMock (assert RESPONSES with `check`, never `WireMock.verify`) note, CI gates table, per-feature speckit gate.**
- [x] T008 [US1] Cite a reference example per layer; all paths verified to exist. **DONE: unit → `IntensityConverterTest`/`HttpJsonFeederSpec`/`THttpClientSpec`; component → `TransactionsSpec`/`ExampleSmokeSpec`; integration → `RedisIntegrationSpec`/`VaultIntegrationSpec`; e2e → overlay sim `HttpIntegrationCoverage` (decomposed `scenarios/`→`cases/`→`feeders/`, WireMock+`check`, T028); compile → `JavaAssertionsCompileTest`/`JavaTemplateSyntaxTest`; facade → `JavaFeedersTest`/`JavaUtilsTest`.**
- [x] T009 [US1] Meta-spec — **DROPPED by user direction: unit-testing a Markdown doc with ScalaTest is the wrong tool; it pollutes the suite and tests no code behavior. `TestModelDocSpec` removed. TESTING.md's cited references were verified to exist manually (and are real source paths); doc-drift is caught in review, not by a unit test.**

**Checkpoint**: US1 independently deliverable — doc exists; cited references verified manually (no meta-spec — T009 dropped `TestModelDocSpec`).

---

## Phase 4: User Story 2 — Speckit enforces test-thinking (Priority: P1)

**Goal**: Constitution + templates + checklist mechanically require a code-free per-feature Test Model.

**Independent test**: A plan with an empty Test Model section fails its checklist; a filled one passes (SC-002).

- [x] T010 [US2] Rewrite Principle III + fix over-specification. **DONE: §III now references `TESTING.md`'s six layers; Testcontainers MANDATORY only for container-backed Redis/Vault/JDBC; JWT + diagnostics are non-container `it`; feeder-determinism + transaction-boundary are DSL-component (no container); added test-first TDD, ScalaMock-not-Mockito, HTTP-units-ScalaMock + layer-4-e2e = overlay `HttpIntegrationCoverage` over REAL HTTP against WireMock asserting RESPONSES with `check` (never `WireMock.verify`), per-feature Test Sketch gate, ≥65%/60% floors. Version 1.0.2 → 1.1.0, Last Amended 2026-06-21.**
- [x] T011 [US2] Add "Test Model" section to `plan-template.md`. **DONE: mandatory table + gate comment after Technical Context; Constitution Check III line updated to the new model.**
- [x] T012 [P] [US2] Add test-sketch hook to `spec-template.md`. **DONE: TEST-MODEL HOOK comment under "User Scenarios & Testing".**
- [x] T013 [P] [US2] Add "Test Model Gate" checklist items to `checklist-template.md`. **DONE: always-included 6-item gate (section present, FR rows, valid layer, no code, ≥1 negative/exact assertion, HTTP verify).**
- [x] T014 [P] [US2] Propagate to `AGENTS.md`. **DONE: new "## Test Model" section linking `TESTING.md` with the 6-layer summary + coverage floor + planning gate.**
- [x] T015 [US2] Validate the gate. **DONE: evidence recorded in `quickstart.md` — templates carry the gate; empty/code-containing Test Model fails; this feature's filled plan.md table is the positive example.**

**Checkpoint**: US2 independently deliverable — future `/speckit-plan` is gated.

---

## Phase 5: User Story 3 — HTTP unit tests via ScalaMock (Priority: P2)

**Goal**: HTTP-emitting code (HttpJsonFeeder, THttpClient) tested at unit layer with ScalaMock; layer-4 e2e is the overlay `HttpIntegrationCoverage` sim over REAL HTTP against WireMock, asserting RESPONSES with `check`.

**Independent test**: `THttpClientSpec` uses ScalaMock to assert exact parsed value + mock expectation satisfied; flip expected → fails (SC-005).

- [x] T016 [US3] Confirm the LIBRARY has NO WireMock dep and NO `WireMockSupport` trait in `src/test/` — document the boundary in `TESTING.md` (T007): library HTTP tests = ScalaMock; layer-4 e2e = the overlay `HttpIntegrationCoverage` sim over REAL HTTP against WireMock (WireMock is overlay-TEST-scope only, never in the library). **DONE: wiremock absent from the library; present only in the overlay's TEST scope; boundary documented in TESTING.md.**
- [x] T017 [P] [US3] Rewrite `THttpClientSpec.scala` using ScalaMock. **DONE: extracted `HttpGetter` trait + `private[gatling] THttpClient.withTransport(HttpRequest => HttpResult)` seam; spec drives a `mockFunction` transport, captures the outgoing request for header/method/uri assertions, asserts status/exception on the returned `HttpResult`. 15 tests green. `com.sun.net.httpserver` removed.**
- [x] T018 [P] [US3] Rewrite `HttpJsonFeederSpec.scala` using ScalaMock. **DONE: added `HttpJsonFeeder.fetch(client: HttpGetter, …)` seam; spec mocks `HttpGetter`, asserts exact extracted records + the GET (url+headers) issued. 13 tests green. JDK server removed.**
- [x] T019 [US3] profile/http: **N/A by design — `profile/http` (`HttpProfileConfig`/`HttpRequestComponents`/`HttpRequestConfig`) builds Gatling HTTP request CONFIG and does not itself emit HTTP** (no `THttpClient`/`java.net.http`). Per FR-010 it is out of scope for the HTTP-unit requirement; its build logic is exercised by `ProfileBuilderTest`. No ScalaMock test invented for non-emitting config code.
- [x] T020 [US3] `grep -r "com.sun.net.httpserver" src/` → **empty (confirmed)**. One HTTP-test pattern (ScalaMock) now; ad-hoc JDK servers gone.

**Checkpoint**: US3 done — no ad-hoc JDK servers in library tests; HTTP units pass via ScalaMock.

---

## Phase 6: User Story 4 — Backfill + integrations + e2e + regression-proof (Priority: P2)

**Goal**: Close every model gap; mandated Testcontainers integrations; full Gatling e2e; prove regression detection.

**Independent test**: Audit shows 0 open gaps; deliberate break → ≥1 test fails; restore → green (SC-006/008).

- [x] T021 [US4] Gap List produced as `specs/002-test-model/gap-list.md` (12 rows, location/kind/layer/resolution/status). **DONE — corrected stale seeds: the "empty Redis bodies" and "JWT/diagnostics need Testcontainers" seeds were inaccurate; the real gaps were the JDK-server HTTP tests (fixed) and JDBC real-DB integration (fixed). Open count = 0.**
- [x] T022 [US4] `RedisActionSpec.scala` — **verify-only: already real-value (3 cases incl. negative), no empty bodies.** No change needed.
- [x] T023 [US4] `RedisActionBuilderSpec.scala` — **verify-only: already ~50 real-value cases (builder types, saveAs/requestName, Validation success/failure). No empty bodies.**
- [x] T024 [US4] `RedisIntegrationSpec` + `VaultIntegrationSpec` — **verified comprehensive (redis:7-alpine / vault:1.17, strict exact-value asserts across all command groups). Not recreated.**
- [x] T025 [US4] JWT — **verify-only: `JwtSpec` + `JwtKeysSpec` already cover real RSA/HMAC sign/verify with exact-claim asserts in `Test`. JWT is pure crypto → no container/`it` needed; constitution §III corrected to not over-mandate. No redundant `it` spec created (would be duplication).**
- [x] T026 [US4] Diagnostics — **verify-only: `UtilityIntegrationSpec` + `DiagnosticsSpec` already assert deterministic banner structure + disabled-diagnostics empty output (no env-dependent exact values). Non-container, fine in `Test`.**
- [x] T027 [US4] Create `src/it/scala/org/galaxio/gatling/storage/JdbcStorageIntegrationSpec.scala`. **DONE (user authorized "Fix DDL + Postgres"): portable DDL fix (`AUTO_INCREMENT` → `GENERATED BY DEFAULT AS IDENTITY`), `org.postgresql:postgresql:42.7.4` (`it` only), real `postgres:17-alpine` container, asserts exact round-trip values + insertion order + empty boundary (4 tests). NOT the `RecordingJdbcDriver` fake. Includes a `Class.forName` driver-registration fix for the shared-JVM Test→it run.**
- [x] T028 [US4] e2e in `examples/scala-sbt-example/.../picatinny`. **DONE — layer-4 e2e is the overlay sim `HttpIntegrationCoverage` (decomposed `scenarios/`→`cases/`→`feeders/`): a real picatinny Simulation runs in a real Gatling runtime, driving picatinny features over REAL HTTP against a WireMock server that ECHOES request values back via response templating. Each feature goes through its picatinny method — feeders (`CurrentDateFeeder`), JWT (`setJwt`), transactions (`startTransaction`/`endTransaction`), converters (`IntensityConverter` `.rpm` for the injection rate). Gatling `check` validates the RESPONSES: `jsonPath("$.ts")` == the `CurrentDateFeeder` value, `jsonPath("$.auth")` == `"Bearer #{jwt}"`; plus `.assertions(global.failedRequests.count.is(0), details("api-call")...)`. The rule that keeps it from being mock-testing-mock: assert RESPONSES with `check`, NEVER `WireMock.verify` of the request and NEVER re-decode the request (the JWT's crypto correctness is unit-tested separately in `JwtSpec`). WireMock is overlay-TEST-scope only (injected by `scripts/test-scala-sbt-template.sh`), never a library dep. Overlay compiled+run by CI `template-tests` via `sbt Gatling/test`.**
- [x] T029 [US4] e2e runs via overlay's `sbt Gatling/test` (no custom launcher) — the `HttpIntegrationCoverage` sim. **Wired into the existing `template-tests` CI gate (galaxio CLI generates the overlay, the script appends the WireMock TEST dep + copies the sim sources, runs `sbt Gatling/test`); a failed `check` on the echoed RESPONSE / a failed `.assertions` (e.g. non-zero `global.failedRequests.count`) → non-zero exit. CI-verified (local run needs the galaxio CLI + network).**
- [x] T030 [US4] Facade parity. **DONE: new `JavaRedisFacadeTest.java` — asserts the Redis facade forwards `saveAs`/`requestName` unchanged to the Scala `GenericRedisActionBuilder` (via `asScala`) with no facade-injected defaults (negative case). 4 tests green. (`JavaFeedersTest`/`JavaProfileTest`/`JavaUtilsTest` already cover the other facades.)**
- [x] T031 [US4] Component/unit coverage — **verified: transaction-boundary covered by `TransactionsSpec` (real `ActorSystem`/recording stats, 18 cases); feeder-determinism by `RandomFeedersSpec` (ScalaCheck property tests asserting regex/range conformance + negative cases). Coverage measured 69.88/63.37 (unit+it) — no low-value padding added (floor set data-driven, see T034/T035).**
- [x] T040 [P] [US4] Backfill `feeders/EnvFeeder` unit gap. **DONE: `EnvFeederSpec` asserts exact env-var resolution + the missing-var/default boundary (negative case). Unit layer, no container. All passing.**
- [x] T041 [P] [US4] Backfill `config/SimulationConfig` unit gap. **DONE: `SimulationConfigSpec` asserts exact typed-param parsing/defaults round-trip. Unit layer. All passing.**
- [x] T042 [P] [US4] Backfill `feeders/SeparatedValuesFeeder` unit gap. **DONE: `SeparatedValuesFeederSpec` asserts exact parsed records across separators incl. an empty/edge boundary. Unit layer. All passing.**
- [x] T043 [US4] Refactor `assertions/AssertionsBuilder` to be unit-testable + spec. **DONE: `AssertionsBuilder` now takes an implicit `GatlingConfiguration` so it can be exercised at the unit layer (`AssertionsBuilderSpec`, exact-value asserts). The Java facade `Assertions.java` CANNOT delegate to it — Gatling's Java `Assertion` wrap constructor is package-private — so it keeps its own Java DSL impl, documented in the facade. All passing.**
- [x] T032 [US4] Deliberate-break verification. **DONE: representative break — flipped the production `Content-Type` injection → exactly 1 ScalaMock HTTP test failed; restored → green. All new tests are exact-value (HTTP/JDBC/facade/meta), so they fail on regression by construction.**
- [x] T033 [US4] Closure + scan. **DONE: gap-list open count = 0; `grep` for `ignore(`/`pending`/`@Disabled`/`@Ignore` and `com.sun.net.httpserver` and empty `in {}` bodies → all ZERO.**

**Checkpoint**: US4 done — Gap List all `closed` (T033); no skipped tests; all gates green.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T034 / T035 Scoverage floor — **DONE (collapsed to a single data-driven floor per user decision 2026-06-21)**: measured 69.88% stmt / 63.37% branch (unit+it). Set `coverageMinimumStmtTotal := 65` and INTRODUCED `coverageMinimumBranchTotal := 60` in `build.sbt` (no branch floor existed before; was only stmt 45). 80/70 was NOT pursued — it needs large low-value backfill of generated/faker/benchmark code; the data-driven floor locks in the real gain without padding. `coverage test "IntegrationTest/test" coverageReport` passes the floor with margin.
- [x] T036 CI JUnit publishing — **DONE: `EnricoMi/publish-unit-test-result-action@v2` (`if: always()`) + `actions/upload-artifact@v7` of `**/target/test-reports/*.xml` added to the `test` (Java 17/21), `coverage`, and `redis-integration` jobs; top-level `permissions` gained `checks: write` + `pull-requests: write`. (Java facade runs inside `Test/test`. `template-tests`/Gatling e2e produce no JUnit XML and run in a throwaway temp project → surfaced via job status; documented.)**
- [x] T037 CI e2e gate — **DONE: the `template-tests` scala-sbt job runs the copied `HttpIntegrationCoverage` sim via `sbt Gatling/test`. `scripts/test-scala-sbt-template.sh` re-appends the `org.wiremock:wiremock` TEST dependency to the generated overlay so the WireMock e2e compiles and runs (WireMock stays overlay-TEST-scope only, never a library dep). Non-zero exit fails the job. No library Gatling gate.**
- [x] T038 [P] `README.md` badges — **DONE: codecov badge → modern `codecov.io/gh/.../branch/main/graph/badge.svg`; added a **Tests** badge (`shields.io/github/actions/workflow/status/.../ci.yml?label=tests`) — the gist/`Schneegans` mechanism needs a secret not provisionable here, so used the no-secret workflow-status fallback. All badge URLs verified HTTP 200. CI/Maven/License/Scala-Steward kept.**
- [x] T039 Format + final verify — **DONE: `scalafmtAll`/`scalafmtSbt` applied; full gate `scalafmtCheckAll scalafmtSbtCheck clean coverage test "IntegrationTest/test" coverageReport` green — 616 unit + 38 integration pass, coverage 69.88/63.37 ≥ 65/60 floor. T033 closure/skipped scans re-confirmed ZERO. (Gate-1 Docker-off run for FR-018: unit gate `Test/test` has no Docker dependency — Testcontainers specs are `it`-only.)**

---

## Dependencies & Execution Order

- **Phase 1 (Setup/Auth)** — T001 blocks everything; T002–T005 confirm classpath hygiene (no WireMock in the LIBRARY classpath — it is overlay-TEST-scope only).
- **Phase 2 (Foundational)** — T006 (harness-reuse documentation) before story tests.
- **US1 (P1)** and **US2 (P1)** are independent of each other and of US3/US4 — can proceed once Phase 1–2 done. US1 = MVP.
- **US3 (P2)** harness (T016) blocks T017–T020 and the e2e Simulation (T029).
- **US4 (P2)** Redis/JWT/etc. integration (T024–T027) need T004 (it source set); e2e (T028–T029) is the overlay `HttpIntegrationCoverage` WireMock+`check` sim.
- **US4 closure** — T033 (gap-list = 0 open + no skipped tests) after T021–T032.
- **Polish** — T035 (Stage 2 coverage) after US4 backfill; T036–T038 (CI/README) after the gates they report on exist.

## Parallel Opportunities

- T002 ∥ (after T001).
- US2 docs: T012 ∥ T013 ∥ T014.
- US3 migrations: T018 ∥ T019 (different files, after T016).
- US4 integrations: T025 ∥ T026 ∥ T027 (different files, after T004).
- T038 (README) ∥ CI tasks (different files).

## Implementation Strategy

- **MVP = US1** (the model document; cited references verified in review — no meta-spec). Delivers the shared
  vocabulary immediately, independently testable.
- **Increment 2 = US2**: lock the speckit gate so every future feature complies.
- **Increment 3 = US3**: rewrite HTTP unit tests to ScalaMock; remove ad-hoc JDK servers.
- **Increment 4 = US4**: full backfill, integrations, e2e, regression-proof.
- **Polish**: staged coverage floors, CI JUnit reporting, README badges.
- Work each task **test-first**; raise the coverage floor only after the tests that
  justify it land (Stage 1 early, Stage 2 last). Keep the build green at every commit.
