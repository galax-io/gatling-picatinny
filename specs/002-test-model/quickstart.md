# Quickstart / Validation: Test Model & Regression-Proof Coverage

**Feature**: 002-test-model | **Date**: 2026-06-21

How to prove this feature works end-to-end. No implementation code here — see
`contracts/` and `tasks.md`.

## Prerequisites

- sbt, Java 17, Scala 2.13.18 (project defaults).
- Docker running (only for the integration gate).
- WireMock is authorized in the example overlay's TEST scope (injected by
  `scripts/test-scala-sbt-template.sh`) for the e2e gate (Gate 3) — it is the echo
  HTTP target, never a library dependency. Library itself needs no extra deps for Gate 1.

## Gate 1 — Unit / component (no Docker)

```bash
sbt scalafmtCheckAll scalafmtSbtCheck compile test
```

Expected: green **with Docker stopped** (proves FR-018). Covers pure unit, DSL
component (ScalaMock for leaf deps incl. HTTP), compile-guard, facade layers.
Specifically verify:
- `RedisActionSpec` / `RedisActionBuilderSpec` no longer have empty `in {}` bodies
  and assert real values (FR-013).
- `THttpClientSpec` / `HttpJsonFeederSpec` use ScalaMock (no JDK `com.sun.net.httpserver`
  remains); assert exact parsed/returned values + ScalaMock expectations (FR-010).

## Gate 2 — Integration (Docker / Testcontainers)

```bash
sbt "IntegrationTest / test"
```

Expected: green with Docker running. The EXISTING `RedisIntegrationSpec` (redis:7)
and `VaultIntegrationSpec` (vault:1.17) start real containers and assert exact
stored/read values (verify these). NEW: a JDBC integration test against a real DB
container (Postgres) asserts exact stored/read values; JWT + diagnostics `it` tests
assert real values without a container. The `RecordingJdbcDriver` fake is NOT used
as an integration target.

## Gate 3 — Full Gatling e2e in examples (`sbt Gatling/test`)

Run from the example overlay (real consumer usage), as the `template-tests` CI gate
does — e.g. in `examples/scala-sbt-example`:

```bash
sbt Gatling/test          # or: sbt "Gatling/testOnly org.galaxio.performance.picatinny.HttpIntegrationCoverage"
```

Expected: green. The overlay sim `HttpIntegrationCoverage` (decomposed into
scenarios/ → cases/ → feeders/) runs a real picatinny Simulation in a real Gatling runtime
and drives picatinny features over **REAL HTTP** against a WireMock server that ECHOES the
request values back via response templating. Each feature goes through its picatinny method:
feeders (`CurrentDateFeeder`), JWT (`setJwt`), transactions (`startTransaction` /
`endTransaction`), and the converter (`IntensityConverter` `.rpm` for the injection rate).
Validation is on the RESPONSES via Gatling `check`: `jsonPath("$.ts")` equals the
`CurrentDateFeeder` value and `jsonPath("$.auth")` equals `"Bearer #{jwt}"`, plus
`.assertions(global.failedRequests.count.is(0), details("api-call")...)`. The rule that keeps
this from being mock-testing-mock: assert RESPONSES with `check`, NEVER `WireMock.verify` of
the request and NEVER re-decode the request — the JWT's crypto correctness is unit-tested
separately in `JwtSpec`. Break a `CurrentDateFeeder` value, tamper a `setJwt` payload, or
violate a transaction expectation → a `check` mismatch / the assertion trips → `Gatling/test`
exits non-zero (code 2). WireMock lives in the overlay's TEST scope only (injected by
`scripts/test-scala-sbt-template.sh`), never a library dependency. The library itself is not
runnable (no `GatlingPlugin`); this gate is examples-only and runs under the `template-tests`
CI gate.

## Gate 4 — Coverage floor

```bash
sbt clean coverage test "IntegrationTest/test" coverageOff coverageReport
```

Expected: statement ≥65% / branch ≥60%, with `coverageMinimumStmtTotal := 65` and
`coverageMinimumBranchTotal := 60`; the gate fails if coverage drops below the floor.
(Measured 2026-06-21: 71.69% stmt / 66.93% branch. Floor is data-driven — set just under
measured to lock in the gain + introduce a branch floor; 80/70 was not pursued as it
needs large low-value backfill of generated/benchmark code.)

## Validate the speckit enforcement (dogfood)

1. Open `.specify/templates/plan-template.md` → confirm a mandatory **Test Model**
   section exists (FR-005).
2. Open `specs/002-test-model/plan.md` → its Test Model table is the positive
   example.
3. Negative check: blank the Test Model section in a scratch plan, run the
   requirements checklist evaluation → it reports FAIL (FR-006). Restore → PASS.
   **Evidence (2026-06-21)**: `.specify/templates/plan-template.md` now carries the
   mandatory "Test Model" section + gate comment; `.specify/templates/checklist-template.md`
   carries the always-included "Test Model Gate" (6 items); `.specify/templates/spec-template.md`
   carries the test-model hook. A plan missing the table fails gate item 1; a plan whose
   sketch contains a code fence fails the "no code" item; this feature's own filled
   `plan.md` Test Model table is the positive (PASS) example.
4. Open `.specify/memory/constitution.md` → Principle III names the layered model
   and the per-feature test sketch; `Version` ≥ 1.1.0; `Last Amended: 2026-06-21`
   (FR-004).

## Validate regression-detection discipline (FR-015 / SC-006)

For a sample of new tests, deliberately break the covered production behavior
(e.g. change a stored Redis key, flip an HTTP header, off-by-one in a converter)
and confirm **at least one test fails**; restore and confirm green. Reviewer
spot-checks this in the PR.

## Validate audit closure (SC-008)

Confirm the gap list (see `data-model.md`) has every item `status = closed`: no
empty test bodies, no mock-vs-mock, all mandated integrations present.

## Validate CI reporting + badges (FR-022/023)

- Open a PR → each test gate (unit, `it`, facade, template, e2e) shows a JUnit
  report with per-gate pass/fail counts as a check / job summary; report XML is
  downloadable as an artifact. Force one test to fail → its name/count appears in
  the published report, not just a red job.
- README badges all render (200): CI status, Maven Central, coverage (modern
  codecov URL), test-results, License, Scala Steward.

## Done signals

- All four gates green (Gate 1 also green without Docker); coverage ≥65%/60%.
- New tests written test-first (TDD); no skipped/disabled tests in the suite.
- Speckit template + checklist gate present and demonstrably blocking.
- Constitution bumped (1.1.0, Test Discipline + TDD) and propagated to AGENTS.md.
- Gap list fully closed.
