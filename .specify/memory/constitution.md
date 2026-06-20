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

Unit tests MUST accompany every new or modified code path (ScalaTest, JUnit via
sbt-jupiter-interface). Integration tests using real infrastructure (Testcontainers)
are MANDATORY for:

- Redis side effects and session state
- JWT generation and verification
- Startup diagnostics
- Any behavior that depends on external process state

The Gatling runtime MUST NOT be mocked where a real integration path exists.
Feeder determinism and transaction boundary behavior MUST be covered by Testcontainers-backed
integration tests, not stubs or hand-rolled fakes.

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

**Version**: 1.0.2 | **Ratified**: 2026-06-20 | **Last Amended**: 2026-06-20
