# Phase 1 Data Model: Test Model & Regression-Proof Coverage

**Feature**: 002-test-model | **Date**: 2026-06-21

These are conceptual entities (documents/process artifacts), not runtime data
structures. No serialized format or DB.

## Test Layer

A named category of test with fixed rules.

| Field | Description |
|-------|-------------|
| `name` | One of: Unit/Functional, DSL Component, External Integration, Full Gatling e2e, Compile Guard, Facade Delegation |
| `purpose` | What regression it is meant to catch |
| `harness` | The helper(s) used (e.g. ScalaTest + ScalaMock for leaf deps incl. HTTP; `Mocks`/`CoreComponents` + `RecordingStatsEngine`; Testcontainers; example overlay `sbt Gatling/test` running the `HttpIntegrationCoverage` Simulation over REAL HTTP against a WireMock server that echoes request values back via response templating) |
| `assertion_style` | Required: exact real values + ≥1 negative/boundary case; for e2e Gatling `check` on the echoed RESPONSES (jsonPath `$.ts` == the CurrentDateFeeder value, jsonPath `$.auth` == `"Bearer #{jwt}"`) + `.assertions(global.failedRequests.count.is(0), details("api-call")...)` — never `WireMock.verify` the request, never re-decode it |
| `forbidden` | What is not allowed (empty bodies, mock-vs-mock, mocking the runtime where a real path exists; for e2e: asserting the request via `WireMock.verify` or re-decoding it instead of checking the echoed response) |
| `reference_example` | An existing repo file a contributor can copy (FR-003) |
| `config` | Where it runs: `Test` (no Docker) / `it` (Docker) / `Gatling` |

**Rules**: every production code path maps to ≥1 layer. HTTP-emitting code is
ScalaMock-unit-tested at layer 1; layer 4 (Full Gatling e2e) additionally drives
picatinny features over REAL HTTP against a WireMock server (in the example overlay's
TEST scope only, injected by `scripts/test-scala-sbt-template.sh`, never a library
dependency) that echoes request values back via response templating, and Gatling
`check` validates the RESPONSES. The anti-pattern that stays forbidden is
mock-testing-mock — asserting against the request you configured (`WireMock.verify` /
re-decoding); always assert the echoed response instead.

## Test Sketch (per-feature planning artifact)

Produced by `/speckit-plan` for every functional requirement.

| Field | Description | Constraint |
|-------|-------------|------------|
| `requirement_id` | FR-xxx it covers | required |
| `real_case` | The concrete real-world case under test | required, non-empty, names a real scenario |
| `layer` | Chosen Test Layer name | required, must be a valid layer |
| `sketch` | Prose description of the assertions | required; **MUST NOT contain implementation/code** |

**Validation (checklist gate, FR-006)**: section FAILS if missing, empty, any row
lacks `real_case`/`layer`/`sketch`, or `sketch` contains code fences / language
syntax.

## Gap List Item (audit artifact)

One row per model-vs-current-suite deficiency.

| Field | Description |
|-------|-------------|
| `location` | `file:line` or test name |
| `kind` | empty-body / no-assertion / mock-vs-mock / missing-integration / missing-negative-case / runtime-mocked |
| `target_layer` | Layer the fix belongs to |
| `resolution` | replace-assertions / migrate-to-harness / add-testcontainers-test / delete / add-negative-case |
| `status` | open / closed |

**Known seed items** (from recon): `RedisActionSpec` / `RedisActionBuilderSpec`
empty `in {}` bodies (unit layer); `THttpClientSpec` + `HttpJsonFeederSpec` use
ad-hoc JDK servers → rewrite with ScalaMock at the unit layer. **Already covered (verify, do
NOT recreate)**: `src/it` `RedisIntegrationSpec` (redis:7 Testcontainers) and
`VaultIntegrationSpec` (vault:1.17). **Genuine `it` gaps**: JDBC storage needs a real
DB container (not the `RecordingJdbcDriver` fake); JWT + startup diagnostics need
`it` coverage (`UtilityIntegrationSpec` is under `src/test`, not `it`).

**Completion invariant (SC-008)**: all items `status = closed` at feature end.

## Enforcement Surfaces (deliverables that encode the model)

| Surface | Change | Entity it carries |
|---------|--------|-------------------|
| `contracts/test-model.md` | new authoritative doc | Test Layer × N |
| `.specify/memory/constitution.md` | expand Principle III (layered model + TDD test-first + data-driven 65/60 coverage floor), bump 1.1.x | rule reference to the model + Test Sketch |
| `.specify/templates/plan-template.md` | add "Test Model" section | Test Sketch table schema |
| `.specify/templates/spec-template.md` | add test-sketch hook | Test Sketch prompt |
| spec quality checklist items | add gate items | Test Sketch validation rules |
| `AGENTS.md` | propagate summary | model overview |
