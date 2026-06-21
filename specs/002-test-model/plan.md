# Implementation Plan: Test Model & Regression-Proof Coverage

**Branch**: `002-test-model` | **Date**: 2026-06-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-test-model/spec.md`

## Summary

Establish one authoritative, layered test model — mirroring how `gatling/gatling`
tests itself — and make speckit enforce it on every future feature. Then bring the
existing suite up to that model: replace assertion-free/empty tests with real-value
assertions, add the mandated Testcontainers integration tests (Redis et al.), rewrite
HTTP-touching library tests to use ScalaMock (unit level), and exercise a full
end-to-end Gatling layer via an example-overlay Simulation (`HttpIntegrationCoverage`)
that drives picatinny features over REAL HTTP against a WireMock server which ECHOES
request values back, validating the RESPONSES with Gatling `check`. Enforcement
is encoded in the constitution (expanded Test Discipline principle) and in the
speckit plan/spec templates + quality checklist (a mandatory, code-free per-feature
"Test Model" section). The whole model is worked **test-first** per `/tdd-workflow`
(red → green → refactor; data-driven coverage floor 65%/60%, measured 71.69%/66.93%). Regression-detection is proven by
negative-test discipline (deliberate break → at least one test fails), spot-checked
in review. The full end-to-end layer runs in the examples via `sbt Gatling/test`.

## Technical Context

**Language/Version**: Scala 2.13.18 (tests), Java 17 facade tests (JUnit 5)

**Primary Dependencies**: ScalaTest 3.2.19, ScalaCheck 1.19, ScalaMock 7.5,
`gatling-test-framework` 3.13.5 (Test), `testcontainers-scala-scalatest` 0.44.1
(test,it, already present, unused), JUnit Jupiter 6.1 + jupiter-interface 0.19.
WireMock is added to the example overlay's TEST scope ONLY (injected by
`scripts/test-scala-sbt-template.sh`), never as a library dependency. The library's
HTTP-emitting code is unit-tested with ScalaMock; the example overlay's full Gatling
e2e drives REAL HTTP against the WireMock server (which echoes request values back
via response templating) and asserts the RESPONSES with Gatling `check`.

**Storage**: N/A (Redis/JDBC exercised only as integration test targets via
Testcontainers)

**Testing**: sbt, **test-first (TDD)**, ScalaMock (not Mockito) for leaf
collaborators incl. HTTP client interfaces. Gates — library unit/component
(`compile test`, no Docker, ScalaMock for HTTP), library integration
(`IntegrationTest / test`, Docker), and full Gatling e2e in the **examples**
(real Gatling usage via the overlay's `sbt Gatling/test` — the `HttpIntegrationCoverage`
Simulation drives picatinny features over REAL HTTP against a WireMock server that
echoes request values back, asserting the RESPONSES with Gatling `check`, run by the
existing `template-tests` CI gate — NOT in the library build). Coverage gate `sbt-scoverage`,
floor set data-driven to 65/60 stmt/branch (measured 71.69/66.93 unit+it); the earlier
80/70 target was superseded (needs large low-value backfill of generated code). Benchmarks JMH (unchanged).

**Target Platform**: JVM (library test infrastructure)

**Project Type**: Single project (published Scala/Java DSL library)

**Performance Goals**: N/A — test infrastructure. Constraint: unit gate must stay
fast and Docker-free.

**Constraints**: No public API / DSL / serialized-format change (Constitution II).
WireMock lives in the example overlay's TEST scope only (never a library dependency);
the full e2e asserts the echoed RESPONSES with `check`, NEVER `WireMock.verify` of the
request and never re-decodes the request. Any Gatling source set must NOT be added to
the library build. Constitution edits bump version + Last Amended (project rule).

**Scale/Scope**: ~40 existing test files audited; HTTP-touching backfill bounded to
HTTP feeders + `THttpClient` + `profile/http` (per clarification).

## Test Model *(dogfood: this feature's own per-requirement test sketch — no implementation)*

> This is the section FR-005 adds to the templates. It is filled here to prove the
> gate is usable. Each row: real case → layer → prose test sketch (assertions only).

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001/002/003 | The model doc exists and each layer has a copyable in-repo example | Doc (review) | `TESTING.md` lists every layer with a cited in-repo example path; reviewers verify each cited path exists. Doc drift is caught in review — NO ScalaTest meta-spec (unit-testing a Markdown file is the wrong tool; `TestModelDocSpec` was dropped). |
| FR-005/006 | A plan missing the Test Model section is rejected | Speckit gate | Feed a fixture plan with an empty Test Model section to the checklist logic; assert it reports FAIL; feed a filled one; assert PASS. Break: blank the section → must FAIL. |
| FR-004 | Constitution carries the Test Discipline expansion + bumped version | Doc/meta | Assert `constitution.md` contains the layered-model clause and `Version` ≥ 1.1.0 with `Last Amended: 2026-06-21`. |
| FR-007 | A pure util returns exact values incl. a boundary/negative case | Unit | e.g. `IntensityConverter`: assert `60.rpm == 1.0` AND a zero/negative input behaves per contract. Break: off-by-one in converter → fails. |
| FR-008 | A custom action advances the VU and emits the right stats message | DSL component | Drive the action via the `Mocks`/`CoreComponents` harness to a probe; assert the recorded `StatsEngine` message fields and that the next action received the session. Break: drop the stats write → assertion on recorded message fails. |
| FR-009 | A Redis side effect actually stores/reads the expected value | Integration (Testcontainers) | Start a real Redis container; run the Redis action; read the key back from the container; assert the exact stored value. Break: write wrong key → readback mismatch fails. |
| FR-010/011 | An HTTP feeder/`THttpClient`/`profile/http` parses the response and satisfies request expectations | Unit/functional (ScalaMock — no WireMock in library) | ScalaMock the HTTP client interface; inject mock response; assert feeder/client returns exact expected value AND ScalaMock expectation satisfied. Break: change expected parsed value → assertion fails. |
| FR-011/020 (full e2e) | **picatinny DSL exercised in a real Gatling run over REAL HTTP** (feeders `CurrentDateFeeder`, JWT `setJwt`, transactions `startTransaction`/`endTransaction`, converters `IntensityConverter.rpm`), each via its picatinny method, asserting the echoed RESPONSES | Full gatling in `examples/` via `sbt Gatling/test` (template-tests gate) | The `HttpIntegrationCoverage` overlay sim (decomposed scenarios/→cases/→feeders/) drives picatinny features over REAL HTTP against a WireMock server that ECHOES request values back via response templating; Gatling `check` validates the RESPONSES — `jsonPath "$.ts"` == the `CurrentDateFeeder` value, `jsonPath "$.auth"` == `"Bearer #{jwt}"` — plus `.assertions(global.failedRequests.count.is(0), details("api-call")...)`. Rule that keeps it from mock-testing-mock: assert RESPONSES with `check`, NEVER `WireMock.verify` of the request and never re-decode it. JWT crypto correctness is covered separately by unit-level `JwtSpec` (picatinny-generate → `pdi.jwt` verify → claims). Break: lost fed value / bad token shape → `check` fails → non-zero failedRequests. Overlay run+compiled by CI; WireMock injected into the overlay's test scope by `scripts/test-scala-sbt-template.sh`. |
| FR-019 | New work is test-first (red before green) | Process (TDD) | For sampled commits the failing test precedes/accompanies the impl; no `ignore`/`pending` tests committed. Break: a committed skipped test → audit flags it. |
| FR-021 | Coverage floor enforced (data-driven 65/60; branch floor INTRODUCED) | CI (scoverage) | `sbt clean coverage test "IntegrationTest/test" coverageReport`; assert statement ≥65% / branch ≥60% and `coverageMinimumStmtTotal`/`coverageMinimumBranchTotal` set. Break: delete a covering test → coverage gate fails. |
| FR-022 | Every CI gate publishes a JUnit report visible on the PR | CI | After each gate, JUnit XML exists in `target/test-reports`; the publish step shows per-gate pass/fail counts as a check and uploads artifacts (`if: always()`). Break: make a test fail → its count appears in the PR report, not just a red job. |
| FR-023 | README badges resolve and are current | Docs/meta | Each badge URL returns 200 and renders; codecov badge uses the modern form; a test-results badge is present. Break: a legacy/broken URL → badge audit fails. |
| FR-012/013 | No test asserts mock-vs-mock or has an empty body | Audit/meta | Audit enumerates offenders (e.g. empty bodies in `RedisActionSpec`); after fix, assert each named test has ≥1 real-value assertion and no empty `in {}` block remains. |
| FR-016 | A facade call delegates to Scala core (parity, not reimpl) | Facade (JUnit 5) | Call the Java facade and the Scala core with identical inputs; assert identical outputs/shape; assert no facade-only branching. Break: add facade-side transform → parity assertion fails. |
| FR-015 | Each new test fails when its covered behavior breaks | Process | For each new/backfilled test, deliberately break the prod path during dev; confirm ≥1 failure; restore → green. Reviewer spot-checks. |
| FR-018 | Unit gate is green without Docker | Process/CI | Run `compile test` with Docker stopped; assert success; Testcontainers tests run only under `it`. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — Facade tests assert delegation/parity
  (FR-016), no facade logic added.
- [x] **II. Backward Compatibility** — Tests, docs, and speckit templates only. No
  public Scala/Java API, DSL behavior, or serialized config/profile format changes.
  The library does NOT become a runnable Gatling app (no `GatlingPlugin`, no
  `src/gatling`). Real Gatling execution stays in the `examples/` overlays (real
  consumers); WireMock lives in the overlay's test scope only, never as a library
  dependency.
- [x] **III. Test Discipline** — This feature *strengthens* the principle:
  test-first TDD; Testcontainers for Redis/JDBC, real-state `it` tests for
  JWT/diagnostics; runtime never mocked where a real path exists (ScalaMock only for
  leaf collaborators incl. HTTP client interfaces — WireMock only in the overlay's
  test scope, never the library); real picatinny Simulations in the example overlays
  for e2e Gatling scenarios (via `sbt Gatling/test`), driving REAL HTTP against a
  WireMock echo server and asserting the RESPONSES with `check`;
  data-driven 65%/60% coverage floor.
- [⚠] **IV. Small, Focused Changes** — No new library dependency is added (WireMock
  is overlay-test-scope only). The library
  build change is limited to the `it` source set + raised scoverage floors — **no
  GatlingPlugin** is enabled in the library. The full e2e layer adds the
  `HttpIntegrationCoverage` example-overlay Simulation (run by that overlay's
  `sbt Gatling/test`, no custom launcher; WireMock echo server in the overlay test
  scope). Plus **CI workflow edits** (`.github/workflows/ci.yml`
  — JUnit report publishing, FR-022) and **README badge edits** (FR-023). All
  authorization-gated (`.github/workflows` = CI truth).
  Per TDD adoption, the scoverage floor IS raised (45% → data-driven 65/60, FR-021) as the final
  step — an in-scope change here, not opportunistic.
- [x] **V. Release Integrity** *(release PRs only)* — N/A; not a release PR.

**Gate result**: PASS for planning, with a standing **authorization gate** on
Principle IV (scoverage floor raise + CI/README edits) that
MUST be approved before `/speckit-implement`.

## Project Structure

### Documentation (this feature)

```text
specs/002-test-model/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── test-model.md            # The authoritative layered model (FR-001 deliverable)
│   └── plan-test-model-section.md  # Template contract for the speckit gate (FR-005/006)
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (repository root)

```text
src/
├── main/scala/org/galaxio/gatling/   # production (UNCHANGED by this feature)
│   ├── feeders/                       #   HttpJsonFeeder → ScalaMock unit target (HTTP interface)
│   ├── utils/THttpClient*             #   THttpClient   → ScalaMock unit target (HTTP interface)
│   ├── profile/http/                  #   profile/http  → DSL component (Mocks harness)
│   └── redis/, storage/, diagnostics/, utils/jwt/  # Testcontainers targets
├── test/scala/org/galaxio/gatling/
│   ├── feeders/HttpJsonFeederSpec     # REWRITE: ScalaMock HTTP client mock (remove JDK server)
│   ├── utils/THttpClientSpec          # REWRITE: ScalaMock HTTP client mock (remove JDK server)
│   ├── redis/RedisActionSpec(+Builder)# FIX empty bodies → real assertions (Test layer)
│   └── ...                            # component/unit backfill across modules
├── it/scala/org/galaxio/gatling/      # EXISTING it source set (Testcontainers)
│   ├── redis/RedisIntegrationSpec     #   EXISTS (redis:7) — verify/strengthen
│   ├── feeders/VaultIntegrationSpec   #   EXISTS (vault:1.17) — verify/strengthen
│   ├── storage/                       #   ADD: JDBC real DB container (Postgres), NOT the RecordingJdbcDriver fake
│   └── utils/jwt/, diagnostics/       #   ADD: JWT + diagnostics it coverage (non-container)
└── test/java/org/galaxio/gatling/javaapi/  # facade delegation/parity (FR-016)

examples/scala-sbt-example/src/test/scala/…/picatinny/  # real Gatling usage (real consumer):
                                            #   HttpIntegrationCoverage sim (decomposed scenarios/→cases/→feeders/)
                                            #   drives picatinny features over REAL HTTP against a WireMock echo
                                            #   server, asserting the RESPONSES with Gatling `check`
                                            #   (overlay Gatling config reads test sources; NO src/gatling)
                                            #   (WireMock in overlay test scope, injected by scripts/test-scala-sbt-template.sh)
                                            #   (layer 4 e2e, run by `sbt Gatling/test` under template-tests)

.specify/
├── templates/plan-template.md         # ADD mandatory "Test Model" section (FR-005)
├── templates/spec-template.md         # ADD per-feature test-sketch hook (FR-005)
├── templates/checklist-template.md    # (reference) checklist items for the gate
└── memory/constitution.md             # EXPAND Principle III; bump 1.0.2 → 1.1.0 (FR-004)

AGENTS.md                              # propagate Test Discipline summary
project/Dependencies.scala             # NO mock-server dep added here (library stays clean; WireMock is overlay-test-scope only)
build.sbt                              # set scoverage floors (stmt raise + branch INTRODUCE; NO GatlingPlugin) (AFTER authorization)
examples/scala-sbt-example/…           # HttpIntegrationCoverage sim covers layer-4 e2e (WireMock echo over real HTTP), run by `sbt Gatling/test`
.github/workflows/ci.yml               # publish JUnit reports (all gates) + artifacts (AFTER authorization)
README.md                              # fix codecov badge + add test-results badge (AFTER authorization)
```

**Structure Decision**: Single-project library. The DSL-component harness is reused in
place from `transactions/Mocks.scala` + `transactions/fixtures.scala` — **no new
`support/` package was created** (an empty package holding only a comment adds no value).
HTTP-touching library tests use ScalaMock (unit layer). Use the EXISTING `it` source set (`src/it/scala`, already
wired to `IntegrationTest` config, already holding real Redis + Vault Testcontainers
specs) — verify those, add JDBC (real DB container) + JWT/diagnostics `it` coverage.
The full e2e layer is NOT a library source set — it lives in the `examples/` overlays
(real Gatling usage: the `HttpIntegrationCoverage` Simulation drives picatinny features
over REAL HTTP against a WireMock echo server and asserts the RESPONSES with `check`,
run by `sbt Gatling/test`), under the existing `template-tests` CI gate. WireMock is in
the overlay test scope only. Production
`src/main` and the library's `Provided`/non-runnable nature are untouched.

## Complexity Tracking

> Items where a Constitution principle is bent — require explicit authorization.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| WireMock added to the example overlay's TEST scope (never a library dependency) | Layer-4 e2e drives picatinny features over REAL HTTP and needs an HTTP target that ECHOES request values back (response templating) so Gatling `check` can validate the RESPONSES (`jsonPath "$.ts"` == `CurrentDateFeeder`, `jsonPath "$.auth"` == `"Bearer #{jwt}"`). The keep-it-honest rule: assert RESPONSES with `check`, NEVER `WireMock.verify` of the request and never re-decode it (JWT crypto correctness is unit-tested in `JwtSpec`) | Asserting against the request you configured (`WireMock.verify` / re-decoding) would be mock-testing-mock — rejected; adding WireMock to the LIBRARY was rejected (overlay test scope only, injected by `scripts/test-scala-sbt-template.sh`) |
| Add the `HttpIntegrationCoverage` example-overlay Simulation for layer-4 e2e (run by `sbt Gatling/test`) | Real Gatling usage belongs in the real consumers, not the library; the library stays `Provided`/non-runnable. The overlay's Gatling plugin task is the runner — exactly as `template-tests` already runs the overlays (FR-020). The decomposed sim (scenarios/→cases/→feeders/) exercises picatinny DSL end-to-end over real HTTP, each feature via its picatinny method (CurrentDateFeeder, setJwt, startTransaction/endTransaction, IntensityConverter.rpm) | A library `src/gatling`+`GatlingPlugin` would make the lib a runnable app and bend Constitution II |
| Constitution MINOR bump 1.0.2 → 1.1.0 | FR-004 expands Principle III with the layered model + test-sketch requirement; project rule mandates version + Last Amended bump on any constitution edit | Not bumping violates the project's constitution-versioning rule |
| CI workflow edits (`.github/workflows/ci.yml`) — publish JUnit reports for all gates + artifacts | FR-022 (user ask: "обновить все отчёты junit") — surface per-gate results on the PR; reports already on disk, only publishing missing | Leaving results unpublished hides per-test failures behind a single red job |
| README badge edits | FR-023 (user ask: fix/add badge) — legacy codecov badge + missing test-results badge | Broken/legacy badge misrepresents project health |
