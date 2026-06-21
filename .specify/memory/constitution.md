# Gatling Picatinny Constitution

## Core Principles

### I. Scala DSL as Source of Truth

The Scala DSL and runtime are the canonical implementation of every feature.
Java/Kotlin facades MUST be thin wrappers that delegate entirely to Scala core —
they MUST NOT reimplement logic that already exists there. Any facade that adds
business logic, conditional branching, or data transformation not present in the
Scala layer is a constitution violation.

### II. Backward Compatibility (NON-NEGOTIABLE)

Gatling Picatinny is a published Maven Central library consumed by external teams.
All of the following are compatibility-sensitive and MUST NOT change without explicit
authorization and a corresponding version bump:

- Public Scala and Java API signatures
- DSL builder/syntax behavior
- Serialized config and profile formats (PureConfig keys, JSON field names)
- Feeder output shapes and session variable names

Version bump rules: PATCH for trivial additive changes (new overload, deprecates nothing);
MINOR for any addition that expands the public surface in a meaningful way; MAJOR for any
removal or behavioral redefinition. Internal `private`/`package-private` refactors are exempt.

### III. Test Discipline

Testing follows the authoritative layered model in `TESTING.md` (mirrors how
`gatling/gatling` tests itself). Work is **test-first** (TDD): write the failing test
before the code (red → green → refactor); assert exact real values with ≥1
negative/boundary case; no empty test bodies; no skipped/disabled tests committed; no
mock asserted against a mock. The Gatling runtime/DSL MUST NOT be mocked where a real
path exists. Leaf-collaborator mocking is **plain ScalaMock only** (never Mockito).

The six layers — apply the one(s) that fit the change; they are NOT all mandatory per
change:

1. **Unit / functional** (`Test`, no Docker): pure functions AND HTTP-emitting code
   (`HttpJsonFeeder`, `THttpClient`) — the HTTP collaborator is mocked with ScalaMock;
   no real server in the library.
2. **DSL / action component** *(conditional)* (`Test`): Gatling DSL pieces with runtime
   behavior (actions, trackers, transactions, stateful builders) driven via a real
   `ActorSystem` + `RecordingStatsEngine` (the `transactions/Mocks` harness), no app run.
   **Feeder determinism and transaction-boundary behavior are covered HERE** (component
   layer), NOT by Testcontainers.
3. **External integration** (`it`, `IntegrationTest` config): two sub-classes —
   - **Container-backed** (Testcontainers MANDATORY): Redis side effects/session state,
     Vault feeders, JDBC storage — assert exact stored/read values against a real
     container; never a recording-proxy fake as the target.
   - **Non-container** (real state, no container): JWT generation/verification (real
     keys/crypto) and startup diagnostics (real JVM/console state) — real tests
     asserting real values (in `Test` or `it`), no Testcontainers required.
4. **Full Gatling e2e** (in the `examples/` overlays, run by `sbt Gatling/test`): a real
   `Simulation` exercises picatinny DSL (feeders, JWT, transactions, converters) over real
   HTTP against a WireMock server that **echoes request values back**; Gatling **`check`**
   validates the RESPONSES (the feeder value + JWT round-trip). Assert on RESPONSE handling
   (`check`), NOT on what the mock received (`verify`) and NOT by re-decoding the request —
   that would be mock-testing-mock (forbidden); the JWT's crypto correctness is unit-tested in
   `JwtSpec`. WireMock lives ONLY in the overlay's test scope (injected by the template-tests
   script), never in the library. The library stays `Provided`/non-runnable (no `GatlingPlugin`).
5. **Compile guard** (`Test`): compile-only specs locking public DSL signatures.
6. **Facade delegation** (`Test`, JUnit 5): facade output == Scala-core output; no
   facade-only logic.

Coverage gate (`sbt-scoverage`): statement ≥65% / branch ≥60% (`coverageFailOnMinimum`);
floor is data-driven (set just under measured) and ratcheted up as real coverage rises —
never padded with low-value tests on generated/benchmark code.

**Per-feature Test Sketch (planning gate)**: every `/speckit-plan` MUST include a
code-free "Test Model" section — for each functional requirement: the real case, the
chosen layer, and a prose assertion sketch. The planning checklist FAILS if it is
missing, empty, names no real case, or contains implementation/code.

### IV. Small, Focused Changes

Default to the minimal change that satisfies the task.

- Opportunistic refactors outside task scope are PROHIBITED.
- New dependencies MUST be explicitly authorized before adding to `build.sbt`.
- Public API signature changes MUST be explicitly authorized.
- Changing serialized config/profile formats MUST be explicitly authorized.
- Complexity MUST be justified in the plan's Complexity Tracking table when a
  constitution principle is bent.

### V. Release Integrity

Release process is trunk-based with release branches.

Rules that MUST be followed without exception:

- Every minor version gets its own `release/X.Y.0` branch cut from `main`.
- Version tags (`vX.Y.Z`) are placed ONLY on `release/*` branches or `main`.
- Branch name MUST match the tag version family (`release/1.2.0` hosts `v1.2.0`, `v1.2.1`).
- Once a tag is pushed and Sonatype deployment starts, the tag MUST NOT be deleted.
- Version numbers MUST NOT be reused — Sonatype Central permanently rejects duplicates.
- Patch fixes land on `main` first, then are cherry-picked onto the release branch.

## Stack Constraints

Technology stack is fixed. Exact versions are in `build.sbt` / `project/Dependencies.scala`
(source of truth). Changes require explicit authorization.

| Concern | Value |
|---------|-------|
| Language | Scala 2.13.x |
| Build tool | sbt |
| Compile target | Java 17 (`--release 17`) |
| CI runtime | Temurin 21 |
| Gatling | 3.13.x (`Provided` scope — MUST remain `Provided`) |
| Core deps | PureConfig, Circe, json4s, Jackson, Scala Logging, Generex, JWT, fast-uuid |
| Test infra | ScalaTest, JUnit 5 (sbt-jupiter-interface), Testcontainers |
| Benchmarks | JMH (`sbt Jmh/run`) |
| Code style | scalafmt (enforced; run `sbt scalafmtAll scalafmtSbt` before every commit) |

Gatling MUST stay `Provided` — it is the host runtime, not a bundled dependency.

## Development Workflow

1. Branch from `main` for every change. No direct commits to `main` or `release/*`.
2. Run `sbt scalafmtAll scalafmtSbt` before committing.
3. CI gate: `sbt scalafmtCheckAll scalafmtSbtCheck compile test` MUST pass.
4. Integration gate (when touching Redis, diagnostics, JWT): `sbt "IntegrationTest / test"`.
5. PRs only — force-push and merge commits in PR branches are prohibited (rebase only).
6. Commits MUST be semantic (conventional commits) and leave the build green.
7. New or changed examples in `examples/` MUST compile against the published artifact
   version, not a local snapshot, before release.

## Governance

This constitution supersedes all other agent and developer practices for this project.
Operational guidance (commands, stack details, structure) lives in `AGENTS.md`; this
document governs the non-negotiable rules and their rationale.

**Amendment procedure**: Update this file, bump `CONSTITUTION_VERSION` per semantic
versioning policy below, and propagate changes to affected templates and AGENTS.md.

**Versioning policy**:
- MAJOR: principle removed, redefined, or made incompatible with prior behavior.
- MINOR: new principle or section added; materially expanded guidance.
- PATCH: clarifications, wording, typo fixes, non-semantic refinements.

**Compliance review**: Every PR MUST be evaluated against all five Core Principles.
Violations require explicit justification in the plan's Complexity Tracking table
before merging.

**Version**: 1.1.3 | **Ratified**: 2026-06-20 | **Last Amended**: 2026-06-21
