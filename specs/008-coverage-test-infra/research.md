# Research: Coverage & Test Infra Hardening (008)

**Date**: 2026-07-03 | **Spec**: [spec.md](spec.md)

All plan-deferred unknowns resolved below. Versions verified against Maven Central metadata on 2026-07-03.

## R1. Primary linter — Scalafix

- **Decision**: `sbt-scalafix` **0.14.7** (ch.epfl.scala), SemanticDB enabled on all compile scopes. Rules: built-in `DisableSyntax` (noNulls, noReturns, noAsInstanceOf, noIsInstanceOf, noFinalize, noProcedureSyntax-equivalent keywords), `RemoveUnused`, `OrganizeImports` (bundled built-in since scalafix 0.11). CI runs `scalafixAll --check`; local fix command is `sbt scalafixAll scalafmtAll` (fix → format, in that order).
- **Rationale**: Semantic (not just syntactic) analysis; auto-fix rewrites satisfy FR-017; `OrganizeImports` gives deterministic import layout; rule set maps 1:1 onto the AGENTS.md idiom rules (`== null`, `asInstanceOf`, unused code). One primary linter avoids duplicate noise (clarification session).
- **Alternatives considered**: WartRemover (compile-time, overlaps DisableSyntax, no auto-fix for most warts); Scapegoat (inspection breadth but no auto-fix, reporting-oriented); both rejected as second-linter noise.
- **Notes**: `RemoveUnused` requires `-Wunused` scalac flags (synergy with R3). SemanticDB adds compile overhead (~10–20% on 2.13) — accepted within the CI budget (R8). Per-site escape hatch: `// scalafix:ok <Rule>` with a justification comment (FR-021). Benchmark sources excluded via the shared exclusion definition (R6, FR-022) using scalafix `unmanagedSources` filtering.
- **Existing `asInstanceOf`/null usage in production code**: gate lands only after remediation to zero findings (spec edge case); genuinely unavoidable sites (type-erased Redis decode, etc.) get per-site suppression with justification.

## R2. Binary-compatibility check — MiMa (advisory / warning-only)

- **Decision**: `sbt-mima-plugin` **1.1.6** (com.typesafe). `mimaPreviousArtifacts := Set("org.galaxio" %% "gatling-picatinny" % "1.23.0")` (latest published tag at plan time; bumped by the release checklist each release). **Advisory mode** (maintainer decision 2026-07-04): the check NEVER fails the build. Local command: `mimaFindBinaryIssues` (lists problems, exits green). CI: dedicated step running the check with `continue-on-error: true` and emitting GitHub `::warning::` annotations per finding, so incompatibilities are visible on the PR without blocking it. Intentional breaks acknowledged via `mimaBinaryIssueFilters` entries (justification comment + constitution-II version bump) to keep the warning stream clean; reviewing outstanding warnings is a mandatory release-checklist step.
- **Rationale**: Gives constitution principle II tooling *visibility* without letting tooling block intentional evolution — enforcement stays with review + release process (maintainer preference). Backward-direction check (consumer protection) is MiMa's default.
- **Alternatives considered**: Blocking `mimaReportBinaryIssues` gate — rejected by maintainer (warnings preferred); sbt-version-policy — heavier, opinionated about version numbers already governed by the release process; manual review only — the status quo, zero visibility.
- **Notes**: New-in-this-release API has no baseline entry and reports clean automatically (spec edge case). Baseline artifact resolves from Maven Central at build time — CI already has network. `organization` confirmed in `publish.sbt:2` = `org.galaxio`.

## R3. Compiler diagnostics — curated flags, no new plugin

- **Decision**: Extend `scalacOptions` manually: add `-Xlint:_` (with targeted `-Xlint:-<noisy>` exclusions found during remediation), `-Wunused:imports,privates,locals,patvars`, `-Wdead-code`, and escalate with `-Werror` — always on (local == CI, no drift). Per-site tolerance via Scala 2.13 `@nowarn("cat=...")` annotations with justification. Applied to all scopes (Compile, Test, IntegrationTest) per clarification Q3.
- **Rationale**: Zero new dependencies (constitution IV); a curated hand-set is a smaller, reviewable diff; `-Wunused` is required by scalafix `RemoveUnused` anyway.
- **Alternatives considered**: `sbt-tpolecat` 0.5.7 — wholesale flag management incl. mode switching; rejected because it replaces the existing flag set (large uncontrolled diff) and adds a dep where a few literal flags suffice. Revisit if flag drift becomes a maintenance burden.
- **Notes**: `-Wvalue-discard` deliberately NOT enabled — Gatling DSL builder style discards values pervasively in tests; noise > signal. Deprecation warnings from the Provided Gatling host become errors — suppressible per-site (`@nowarn("cat=deprecation")`) with justification (spec edge case).

## R4. Dependency-update automation — Scala Steward (self-hosted action)

- **Decision**: `.github/workflows/scala-steward.yml` ALREADY EXISTS (commit `3f44335`, weekly cron + `workflow_dispatch`, `scala-steward-org/scala-steward-action@v2`) — analysis finding I2. Remaining delta only: add repo config `.scala-steward.conf` and a post-step in the existing workflow assigning every open Steward PR to the standing **"maintenance"** milestone (via `gh pr edit --milestone`), satisfying the every-PR-needs-a-milestone rule (clarification Q2). Milestone "maintenance" (no due date) created once as an admin task. FR-020's automation half is pre-satisfied.
- **Rationale**: Dependabot does not support sbt; Scala Steward is the ecosystem standard. Self-hosted action (vs. the public instance) keeps scheduling and the milestone post-step under repo control in one workflow.
- **Alternatives considered**: Public Scala Steward instance (PR into their repos list) — no control over milestone assignment step; digest-issue-only mode — rejected in clarification Q2 (option C not chosen).
- **Notes**: Exact action version pinned at implementation. Not a release-workflow change (no `release.yml` edit) — new standalone workflow, authorized by the spec.

## R5. Dependency-hygiene report — sbt-explicit-dependencies

- **Decision**: `sbt-explicit-dependencies` **0.3.1** (com.github.cb372; latest and only Maven Central version, published 2023 — compatibility with sbt 1.12.13 verified at implementation as the first task of that issue). **Report-only** (clarification Q4): `undeclaredCompileDependencies` and `unusedCompileDependencies` documented as manual pre-release tasks in the release checklist; NOT wired into CI. Zero findings required once at feature completion.
- **Rationale**: Undeclared/unused detection with zero CI cost; report-only avoids fighting known false positives on `Provided`/macro deps.
- **Alternatives considered**: CI-gating undeclared deps (my recommendation) — rejected by maintainer (Q4 = report only); custom classpath diff script — reinvention.
- **Fallback**: if 0.3.1 breaks on sbt 1.12.x, drop the plugin and document `sbt dependencyTree`-based manual review instead; the FR-020 report obligation is tool-agnostic.

## R6. Coverage exclusions + floor ratchet protocol

- **Decision**: Exclude benchmarks with `coverageExcludedFiles := ".*Benchmark.*"` plus `coverageExcludedPackages := "org\\.galaxio\\.gatling\\.jmh\\..*"` (covers `RedisActionBenchmark`, `SyntaxBenchmark`, `FakerBenchmark`, `jmh/JmhBenchmark`). A single build-level definition of "benchmark sources" (one `val` in `build.sbt` driving the coverage patterns and the scalafix/`-Werror` source exclusions) satisfies FR-022. Ratchet protocol: (1) exclude; (2) run `sbt coverage test "IntegrationTest / test" coverageReport`; (3) set `coverageMinimumStmtTotal`/`coverageMinimumBranchTotal` to measured value rounded down to the nearest whole point, minus at most 5 points total slack (SC-002); (4) record value + date in the ratchet doc. Floor never decreases (current 65/60 is the lower bound).
- **Rationale**: Data-driven per constitution III ("set just under measured"); exclusion patterns are the scoverage-supported mechanism.
- **Alternatives considered**: Moving benchmarks to a separate sbt module — cleaner long-term but a build restructuring outside milestone scope (constitution IV).
- **Docs placement (FR-003, FR-021)**: ratchet policy → `TESTING.md` (authoritative test doc); gate how-to/escape hatches → new "Static analysis & gates" section in `TESTING.md` + command list in `AGENTS.md` Commands block.

## R7. Per-issue technical approach (test fixes)

- **#109 (Thread.sleep flake)**: Already fixed on `main` — `TransactionsSpec` uses a latch-based terminal action (`fixtures.scala:36`, comment at `TransactionsSpec:93`). Remaining work: verify no `Thread.sleep`-synchronization remains in affected suites (LogCapture's `Thread.sleep(1)` bounded retry loop is a probe, not a synchronization sleep — acceptable) and close the issue with evidence. No code change expected.
- **#108 (/tmp path)**: `StorageBackendSpec.scala:30` — `JsonFileBackend("/tmp/nonexistent-test-file-12345.json")`. Replace with `Files.createTempDirectory`-derived paths (existing-file cases) and a path inside a fresh temp dir (nonexistent-file case); cleanup in `afterAll`/finally. Windows-safe, parallel-safe.
- **#110 (asInstanceOf in IT)**: 5 sites in `RedisIntegrationSpec.scala` (36, 103, 135, 143, 153). Replace with a typed-extraction helper (pattern match; on mismatch `fail(...)` naming expected vs. actual runtime type). Also unblocks the DisableSyntax.noAsInstanceOf lint (R1) for `it` sources.
- **#121 (shrink config)**: `RandomFeedersSpec` uses `ScalaCheckDrivenPropertyChecks`. Add explicit `implicit PropertyCheckConfiguration` (minSuccessful, maxDiscardedFactor) at spec level; where shrinking produces misleading counter-examples for constrained generators, pin explicit no-shrink/custom `Shrink` instances. Exact per-property choice at implementation; the FR-014 obligation is an explicit configuration + demonstrably minimal counter-example.
- **#211 (tautological + failure paths)**:
  - `TemplatesSpec`: drop the local `loadTemplateNames` re-implementation; drive the production `Templates` trait discovery and assert the exact expected set (`test_json`, `test_xml`) + negative (missing directory → documented empty/absent behavior).
  - NIF/NIR/PSRNSP (`feeders/faker/GeneratedFeederSpec.scala` — NIR/NINO ~L872–887, NIF ~L897; `feeders/RandomFeedersSpec.scala` ~L403–413): fixed lists of externally-verified valid samples (assert validator accepts) + corrupted-digit invalid samples (assert rejects). No production-formula reuse.
  - Cyrillic checks (corrected paths — analysis I1; issue #211 cited files that do not exist): Java facade smoke test `src/test/java/org/galaxio/gatling/javaapi/JavaApiExampleSmokeTest.java:255` (`.+` → `[Ѐ-ӿ]{6}`) and Scala feeder spec `src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala:402-405` (length-only → `[Ѐ-ӿ]{10}` content assert); Latin-string negative in both. Layer: Unit/Functional + Facade Delegation, NOT examples e2e — `examples/` overlays untouched.
  - `RedisAction` failure branches (`RedisAction.scala:51, 67–88`): DSL/action-component layer — Mocks/ActorSystem harness + a stubbed failing Redis client; assert exact KO status, error message propagation, stats-engine recording, and that `next` still fires.
  - `THttpClient` transport failures: **non-container `it`** layer, loopback-only — connection-refused (connect to an ephemeral port bound-then-closed), timeout (socket that accepts but never responds), TLS failure (plaintext socket answering an HTTPS request), redirect via JDK built-in `com.sun.net.httpserver.HttpServer` (302 then 200) pinning BOTH modes: `Redirect.NEVER` (the client's default, `THttpClient.scala:126`) returns the 302 response itself; a follow mode returns the exact final body. No new dependency; no external network.
- **#81 (remaining: template pipeline)**: JDBC IT already exists and conforms (`JdbcStorageIntegrationSpec` — real Postgres 17 container, exact round-trip, empty-table boundary). Remaining: end-to-end template render test — build the template body from a real resource file via the production `Templates` path, resolve it against a real Gatling `Session` using `GatlingConfiguration.loadForTest()` bootstrap (real code path, no mocked runtime), assert exact rendered output + missing-variable failure. Unit/functional layer, no container.

## R8. CI wiring & budget

- **Decision**: Extend the existing `.github/workflows/ci.yml` with three changes — `scalafixAll --check` step (after scalafmt check), an advisory MiMa step (`mimaReportBinaryIssues` under `continue-on-error: true`, findings re-emitted as `::warning::` annotations — the Report task's non-zero exit is what makes step-level `continue-on-error` meaningful; the local advisory command remains `mimaFindBinaryIssues`, which never fails), and `-Werror` inside the normal `compile`. Steward workflow already exists — extended, not created (R4). Budget: total verification wall-clock increase ≤ ~25% (SemanticDB compile overhead ~10–20%, MiMa ~seconds, scalafix check ~tens of seconds); measured once during implementation, recorded in the PR description.
- **Rationale**: One pipeline, fail-fast ordering (format → lint → compile(-Werror) → mima(advisory) → tests); keeps `release.yml` untouched (ask-first boundary respected).

## R9. Issue/milestone admin

- **Decision**: Four new issues (lint gate, MiMa gate, strict diagnostics, dependency hygiene/Steward) filed into milestone 10 (v1.24.0) before implementation; standing "maintenance" milestone created for bot PRs. Existing open issues map: #80+#210 → US1, #211 → US2, #81 → US3, #108/#109/#110/#121 → US4. Expected via `/speckit-taskstoissues`.
