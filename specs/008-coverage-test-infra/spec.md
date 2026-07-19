# Feature Specification: Coverage & Test Infra Hardening

**Feature Branch**: `008-coverage-test-infra`

**Created**: 2026-07-03

**Status**: Draft

**Input**: User description: "https://github.com/galax-io/gatling-picatinny/milestone/10 — v1.24.0 Coverage & test infra: #80 floor, #81 IT, #108 /tmp, #110 asInstanceOf, #121 shrink, coverageExcludedPackages (#210), tautological/failure-path cleanup (#211), flaky sleep (#109). Follow-up request (2026-07-03): add an idiomatic-Scala linter and further static-analysis improvements that maintain and protect the repository."

## Clarifications

### Session 2026-07-03

- Q: Where does the static-analysis work (US5–US8) land, release-wise? → A: v1.24.0 (milestone 10, same as the rest of this spec); the four new issues are filed into it.
- Q: Policy for bot-authored dependency-update PRs vs. the PR↔issue↔milestone linkage gate? → A: Rolling milestone — bot PRs are auto-assigned to a standing "maintenance" milestone so the linkage rule holds for every PR, human or bot.
- Q: Which compile scopes do strict diagnostics and the lint gate cover? → A: All scopes — production, test, and integration sources; framework-induced findings suppressed per-site with justification.
- Q: Dependency-hygiene check (undeclared + unused compile deps) — CI gate or report? → A: Report only — both checks are on-demand tasks run manually before releases; no CI enforcement.
- Q: Do the new gates also cover `examples/` overlay projects? → A: Library build only; overlays unchanged (their smoke tests gate behavior).

### Session 2026-07-04

- Q: Binary-compatibility check — blocking gate or advisory? → A: Advisory (warning-only) — the check reports incompatibilities visibly in CI but never fails the build; enforcement stays with review + the release checklist.

### Session 2026-07-19

- Q: Keep the standing "maintenance" milestone for bot dependency PRs? → A: No — supersedes Q2 of 2026-07-03. Bot dependency PRs are auto-assigned to the current ACTIVE milestone (the lowest-numbered open one, same definition as `scripts/check-linkage.sh`), so dependency updates ship — and are tracked — with the release they land in. The "maintenance" milestone (№29) is retired: its merged PRs were reassigned to the active release milestone, and the milestone deleted.

## User Scenarios & Testing *(mandatory)*

<!--
  TEST-MODEL HOOK (Constitution III): for each acceptance scenario below, keep in mind
  the REAL case it exercises and the test LAYER it maps to (see TESTING.md: unit/functional,
  DSL/action component, external integration, full Gatling e2e, compile guard, facade).
  `/speckit-plan` will expand these into the plan's mandatory code-free "Test Model" table.
-->

### User Story 1 - Honest Coverage Measurement (Priority: P1)

As a library maintainer, I want the coverage figure to measure only shippable production code — benchmark code that never runs in tests must not inflate or deflate the denominator — so that the enforced coverage floor gives real (not false) confidence, and I want the floor ratcheted to the honest measured value and the ratchet policy written down so future contributors keep raising it instead of letting it stagnate.

**Why this priority**: Every other quality claim in this milestone rests on the coverage gate meaning something. Today four benchmark files live on the production classpath and are counted by the coverage tool but only ever executed by the benchmark runner — the reported percentage is distorted, so the gate certifies a number nobody can trust. (Issues #210, #80)

**Independent Test**: Run the coverage report before and after the change; verify benchmark sources no longer appear in the report, the floor equals the documented data-driven value, and a build whose coverage drops below the floor fails.

**Acceptance Scenarios**:

1. **Given** the coverage report is generated, **When** the list of measured files is inspected, **Then** no benchmark source (JMH harness or `*Benchmark` classes) appears in it.
2. **Given** benchmarks are excluded, **When** coverage is re-measured on the full test suite, **Then** the enforced statement and branch floors are re-set just under the new measured values (data-driven, per constitution) and the build fails if coverage falls below them.
3. **Given** a contributor reads the project documentation, **When** they look for the coverage policy, **Then** they find the ratchet rule (floor tracks measured coverage upward, never padded with low-value tests) and the current floor values with their measurement date.
4. **Given** a hypothetical change that removes tests and drops coverage below the floor, **When** the verification build runs, **Then** it fails with a coverage error (negative case).

---

### User Story 2 - Tests That Can Actually Fail (Priority: P1)

As a library maintainer, I want tautological tests — tests that re-implement the production logic and assert against their own copy, or use patterns so broad they match anything — replaced with tests that assert independently known-correct values, so that breaking the production code actually turns the suite red.

**Why this priority**: A green suite that stays green when production breaks is worse than no suite: it actively certifies broken code. Known offenders: template-discovery test re-implements the discovery walk; three document-number feeders (NIF, NIR, PSRNSP) recompute the check digit with the production formula; two loose Cyrillic-output checks (the Java facade smoke test and the Scala feeder spec) use match-anything/length-only patterns. In addition, known failure paths (Redis action error/crash/stats branches, HTTP client transport errors: connection refused, timeout, TLS failure, redirect) have zero coverage. (Issue #211)

**Independent Test**: For each fixed test, temporarily break the production behavior it guards (mutation check) and verify the test fails; restore and verify it passes. Failure-path tests are verified by asserting exact error outcomes.

**Acceptance Scenarios**:

1. **Given** the template-discovery test suite, **When** production template discovery is driven directly (not a re-implementation), **Then** assertions compare against fixed expected file sets, and breaking the production discovery logic makes the test fail.
2. **Given** the document-number feeder tests (NIF, NIR, PSRNSP), **When** validity is asserted, **Then** the assertion uses independently known-valid and known-invalid sample values, not a recomputation of the production check-digit formula.
3. **Given** the loose Cyrillic-output checks (Java facade smoke test; Scala feeder spec), **When** output is validated, **Then** the pattern accepts only Cyrillic strings of that test's expected length and rejects a Latin or empty string (negative case).
4. **Given** the Redis action failure branches (operation failure, crash, stats reporting), **When** each branch is exercised with a controlled failing collaborator, **Then** the test asserts the exact propagated status/error and session outcome.
5. **Given** the HTTP client used by feeders, **When** the transport fails (connection refused, timeout, TLS handshake failure) or the server redirects, **Then** each outcome is asserted exactly (error type/message or followed redirect result).

---

### User Story 3 - Integration Coverage for JDBC Storage and Template Pipeline (Priority: P2)

As a library maintainer, I want the JDBC session-storage backend and the end-to-end template rendering pipeline covered by infrastructure-real integration tests, so that the reliability claims in the README are backed by repeatable tests and regressions in these areas cannot slip through.

**Why this priority**: These are the two remaining externally-visible subsystems with no integration coverage (JWT was covered by earlier milestones). They persist user data and generate user-visible output — silent regressions here surface in consumers' load tests. (Issue #81, narrowed per #210: JWT already tested)

**Independent Test**: Run the integration suite against a real database container; verify stored values are read back exactly. Run the template pipeline from source template to rendered output and compare against exact expected output.

**Acceptance Scenarios**:

1. **Given** a real database container, **When** the JDBC storage backend writes session values and reads them back, **Then** the read values equal the written values exactly, and a read of a non-existent key yields the documented empty/absent result (negative case).
2. **Given** a source template with substitution variables, **When** the full template pipeline renders it, **Then** the output matches the exact expected rendered text, and a template referencing a missing variable produces the documented error outcome (negative case).

---

### User Story 4 - Deterministic, Portable Test Suite (Priority: P3)

As a contributor running the suite locally or in CI, I want tests free of timing sleeps, hardcoded absolute temp paths, and unguarded type casts, and property-test counter-examples configured to shrink to readable minimal cases, so that the suite passes reliably in parallel, on slow runners, and on any operating system, and failures are diagnosable at a glance.

**Why this priority**: These are hygiene defects — they don't hide broken production code, but they waste contributor time with flakes, non-portable failures, and unreadable diagnostics. (Issues #108, #109, #110, #121)

**Independent Test**: Run the affected suites repeatedly (including in parallel) and verify stable results; inspect failure output of a deliberately failing run for meaningful messages.

**Acceptance Scenarios**:

1. **Given** the storage backend test suite, **When** two runs execute in parallel, **Then** each run uses its own isolated temporary directory (created per-run, cleaned up after) and neither interferes with the other; no test references an absolute fixed temp path.
2. **Given** the transactions test suite, **When** it waits for asynchronous completion, **Then** it waits on a deterministic completion signal or bounded polling probe — no fixed-duration sleep — and passes consistently on a slow runner.
3. **Given** the Redis integration suite, **When** a result has an unexpected type, **Then** the test fails with a descriptive assertion message naming the expected and actual shape — not a raw class-cast error.
4. **Given** the random-feeder property tests, **When** a property fails, **Then** the reported counter-example is a shrunk minimal case under an explicit shrink/discard configuration.

---

### User Story 5 - Idiomatic Scala Lint Gate (Priority: P2)

As a library maintainer, I want an automated linter that enforces the project's idiomatic-Scala rules (no null comparisons, no unsafe casts, no unused imports/values, deterministic import layout, no discouraged syntax) as part of the verification build, so that idiom violations are caught by machine at review time instead of by humans, and routine violations are fixable with one local command.

**Why this priority**: The repo's idiom rules today live only in prose (AGENTS.md) and reviewer memory — this milestone itself removes `asInstanceOf` casts by hand (#110) that a linter would have rejected at commit time. Machine enforcement stops the same classes of defect from re-entering. (Follow-up request; no pre-existing issue)

**Independent Test**: Introduce a seeded violation (e.g. a `null` comparison or unused import) on a scratch branch; verify the verification build fails naming the rule and location; run the documented auto-fix command; verify the violation is gone and the build passes.

**Acceptance Scenarios**:

1. **Given** the verification build, **When** a source file contains a discouraged construct (null comparison, unsafe cast outside guarded extraction, unused import/value, procedure syntax), **Then** the build fails with a message naming the rule, file, and line (negative case).
2. **Given** a clean checkout of the feature branch, **When** the verification build runs, **Then** the lint step passes with zero violations.
3. **Given** auto-fixable violations, **When** the contributor runs the single documented fix command, **Then** the violations are rewritten automatically and formatting remains consistent with the existing formatter.

---

### User Story 6 - Binary Compatibility Advisory Check (Priority: P2)

As a library maintainer, I want every build checked for binary compatibility against the most recently published release, with incompatibilities surfaced as prominent warnings (not build failures), so that an accidental removal or signature change of public API is visible at review time instead of being discovered by consumers after publication — while intentional evolution is never blocked by tooling.

**Why this priority**: Backward compatibility is constitution principle II (NON-NEGOTIABLE) yet has zero automated visibility today; a single mis-merged refactor could ship a binary-breaking artifact to Maven Central, which permanently rejects re-publishing a fixed artifact under the same version. Clarified 2026-07-04: advisory mode — warnings inform the reviewer and the release checklist; the build stays green. (Follow-up request; no pre-existing issue)

**Independent Test**: On a scratch branch, remove or change the signature of a public method; verify the build still succeeds but emits a visible warning citing the incompatibility against the previous release; restore it and verify the check reports clean.

**Acceptance Scenarios**:

1. **Given** the current codebase and the most recently published release artifact, **When** the compatibility check runs, **Then** it reports zero incompatibilities and the build passes.
2. **Given** a change that removes or alters a public method/class visible to consumers, **When** the verification build runs, **Then** the build still succeeds but the check emits a clearly visible warning naming the incompatible change (negative case: the warning appears; the build does not fail).
3. **Given** an intentional, authorized breaking change, **When** it is shipped, **Then** its warning is acknowledged via an explicit, per-change documented exclusion accompanied by the constitution-mandated version bump — keeping the warning stream clean so real regressions stand out.
4. **Given** the release checklist, **When** a release is prepared, **Then** reviewing the compatibility warnings (and confirming each is acknowledged or fixed) is a mandatory checklist step — enforcement lives in the release process, not in CI failure.

---

### User Story 7 - Stricter Compiler Diagnostics (Priority: P3)

As a library maintainer, I want the compiler's own static analysis turned up (curated warning set: unused symbols, missing exhaustiveness, deprecated API usage, suspicious inference) and treated as errors in the verification build, so that the cheapest static-analysis layer — the compiler — stops real defect classes before any test runs.

**Why this priority**: Near-zero cost, immediate signal; but lower urgency than the lint and compatibility gates because the compiler already emits some diagnostics today — they are just ignorable. (Follow-up request; no pre-existing issue)

**Independent Test**: Introduce a non-exhaustive pattern match or an unused private value on a scratch branch; verify the verification build fails; remove it and verify the build passes.

**Acceptance Scenarios**:

1. **Given** the curated warning configuration, **When** the current codebase compiles in the verification build, **Then** it compiles with zero warnings-as-errors failures.
2. **Given** a source change introducing a non-exhaustive match or unused private symbol, **When** the verification build runs, **Then** compilation fails naming the diagnostic (negative case).
3. **Given** a diagnostic that must be tolerated (e.g. deprecation from the provided host-runtime upgrade path), **When** it is suppressed, **Then** the suppression is narrow (per-site) and carries a justification — never a global downgrade of the category.

---

### User Story 8 - Dependency Hygiene Automation (Priority: P3)

As a library maintainer, I want automated dependency-update proposals and a build-time report of undeclared/unused compile dependencies, so that the dependency graph stays current, minimal, and intentional without manual audits.

**Why this priority**: Keeps the repository maintained over time (the stated goal of the follow-up request), but does not gate correctness of the current release. (Follow-up request; no pre-existing issue)

**Independent Test**: Verify the update-automation configuration is active on the default branch (proposals arrive for a stale dependency); run the dependency-hygiene check and verify it reports zero undeclared and zero unused compile-scope dependencies.

**Acceptance Scenarios**:

1. **Given** the repository's automation configuration, **When** a dependency falls behind upstream, **Then** an automated update proposal (pull request) is opened against the default branch and auto-assigned to the current active milestone (lowest-numbered open; clarified 2026-07-19), satisfying the linkage gate.
2. **Given** the on-demand dependency-hygiene report (not CI-gated), **When** it is run on the feature branch, **Then** it reports zero undeclared compile-scope dependencies (code compiling only via transitive luck) and zero unused declared compile-scope dependencies.
3. **Given** a deliberately added unused library declaration on a scratch branch, **When** the report is run, **Then** it flags exactly that declaration (negative case).

---

### Edge Cases

- Coverage floor after benchmark exclusion: excluding never-tested benchmark code raises measured coverage; the floor must be re-measured and re-set from the new honest baseline, not guessed.
- Container runtime absent (no Docker): integration tests must fail or skip with a clear infrastructure message, never hang or report false success.
- Parallel CI runs: temp-directory isolation must hold when the same suite runs concurrently on one machine.
- Slow CI runner: async waits must tolerate arbitrary scheduling delays up to the probe timeout without false failure.
- Windows filesystem: no test may assume POSIX absolute paths (`/tmp`).
- Mutation blind spots: a fixed "tautological" test must be demonstrated to fail under production mutation before being accepted as fixed.
- Redirect behavior of the HTTP client: both follow and non-follow outcomes must be pinned so a transport-layer library upgrade cannot silently change semantics.
- Lint/diagnostic noise on benchmark and generated sources: exclusions must stay consistent with the coverage exclusions (US1) so the same code is treated uniformly by all gates.
- Formatter vs. linter interplay: an auto-fix must not produce output the formatter then rewrites (fix order documented; the two must converge, not fight).
- Binary-compatibility baseline when a class is new in this release: new API has no baseline and must pass without exclusions; the gate compares only against the latest published version, not unreleased history.
- Warnings from the provided host runtime (Gatling) or third-party macros that the project cannot fix: must be suppressible per-site without weakening the category globally.
- Automated dependency-update pull requests vs. repository linkage rules: resolved — bot-authored PRs are auto-assigned to the current active milestone (lowest-numbered open; amended 2026-07-19, previously a standing "maintenance" milestone), so the every-PR-needs-a-milestone rule holds without exempting bots; the linkage-gate mechanics for bot authorship are a planning detail.
- Existing violations at gate-introduction time: each new gate lands only after the codebase is brought to zero findings for its rule set — a gate that starts red or starts with blanket exclusions is not accepted.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The coverage measurement MUST exclude all benchmark sources (JMH harness and `*Benchmark` classes) from the coverage denominator; excluded sources MUST NOT appear in the coverage report. (#210)
- **FR-002**: The enforced coverage floors (statement and branch) MUST be re-set to data-driven values just under the honest measured coverage after benchmark exclusion, and the verification build MUST fail when coverage falls below either floor. (#80)
- **FR-003**: The coverage ratchet policy — floors track measured coverage upward, are never lowered without authorization, and are never satisfied by padding with low-value tests — MUST be documented in the project's contributor-facing documentation together with current floor values and measurement date. (#80)
- **FR-004**: The template-discovery test MUST drive the production discovery implementation directly and assert against fixed expected results; it MUST fail when production discovery logic is broken. (#211)
- **FR-005**: Document-number feeder validity tests (NIF, NIR, PSRNSP) MUST assert against independently known-valid and known-invalid sample values rather than recomputing the production check-digit formula. (#211)
- **FR-006**: The Cyrillic-output checks (Java facade smoke test; Scala feeder spec) MUST assert content and length (Cyrillic-only characters, that test's expected count) and MUST reject non-Cyrillic output. (#211)
- **FR-007**: Every failure branch of the Redis action (operation failure, crash handling, stats reporting) MUST be covered by tests asserting the exact propagated status and session outcome. (#211)
- **FR-008**: HTTP client transport failure modes — connection refused, timeout, TLS handshake failure — and redirect handling MUST each be covered by a test asserting the exact outcome. (#211)
- **FR-009**: The JDBC session-storage backend MUST be covered by an integration test against a real database instance, asserting exact round-trip of stored values plus at least one negative case (absent key). (#81)
- **FR-010**: The template rendering pipeline MUST be covered by an end-to-end test from source template to rendered output, asserting exact output plus at least one negative case (missing variable). (#81)
- **FR-011**: No test may reference a hardcoded absolute temporary path; tests needing scratch storage MUST create an isolated per-run temporary directory and clean it up afterwards. (#108)
- **FR-012**: No test may synchronize on a fixed-duration sleep; asynchronous outcomes MUST be awaited via a deterministic completion signal or a bounded polling probe. (#109)
- **FR-013**: Integration tests MUST extract typed values via guarded extraction that produces a descriptive assertion failure (expected vs. actual shape) instead of an unguarded cast that surfaces as a class-cast error. (#110)
- **FR-014**: Property-based feeder tests MUST run under an explicit shrink/discard configuration so failing properties report minimal, readable counter-examples. (#121)
- **FR-015**: All existing test suites MUST continue to pass after the changes; no public API, DSL behavior, or serialized format may change. Scope is test code, build/CI configuration, documentation, and behavior-preserving source remediations required to bring new static-analysis gates to zero findings.
- **FR-016**: The verification build MUST include an automated idiomatic-Scala lint step covering all library compile scopes (production, test, integration) that fails on discouraged constructs — at minimum: null comparisons, unsafe casts outside guarded extraction, unused imports/values, non-deterministic import layout, procedure syntax — reporting rule, file, and line for each violation. The `examples/` overlays are out of scope. (follow-up)
- **FR-017**: Auto-fixable lint violations MUST be remediable by a single documented local command whose output is stable under the project formatter (running fix then format twice changes nothing). (follow-up)
- **FR-018**: The verification build MUST check binary compatibility of the public API against the most recently published release and surface every incompatible change as a clearly visible warning — without failing the build (advisory mode, clarified 2026-07-04). Intentional breaks are acknowledged via explicit per-change exclusions carrying a written justification and the constitution-mandated version bump; reviewing outstanding warnings is a mandatory release-checklist step. (follow-up)
- **FR-019**: The verification build MUST escalate a curated set of compiler diagnostics (unused symbols, non-exhaustive matches, deprecated API usage, suspicious inference) to errors across all library compile scopes (production, test, integration); tolerated diagnostics MUST be suppressed per-site with justification, never by disabling the category. (follow-up)
- **FR-020**: The repository MUST have automated dependency-update proposals configured for the default branch, with each bot-authored pull request auto-assigned to the current active milestone (lowest-numbered open; amended 2026-07-19, previously a standing "maintenance" milestone) so the PR↔issue↔milestone linkage rule holds for every PR. It MUST also provide an on-demand (report-only, not CI-gated) task reporting undeclared and unused compile-scope dependencies, run manually before each release, with zero findings at feature completion. (follow-up)
- **FR-021**: Contributor documentation MUST describe each new gate (lint, binary compatibility, strict diagnostics, dependency hygiene): how to run it locally, how to fix findings, and the authorized escape-hatch procedure. (follow-up)
- **FR-022**: Static-analysis exclusions (benchmark/generated sources) MUST be consistent with the coverage exclusions of FR-001 — one shared definition of "code the gates ignore". (follow-up)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The coverage report lists zero benchmark source files; the reported percentage reflects production code only.
- **SC-002**: Coverage floors are set within 5 percentage points below the honest measured value, and a build with coverage below either floor fails.
- **SC-003**: 100% of the tautological tests named in the milestone (template discovery, NIF/NIR/PSRNSP, Cyrillic output checks) fail when the production behavior they guard is deliberately broken, and pass when it is restored.
- **SC-004**: Previously uncovered failure branches (Redis action failure/crash/stats; HTTP client connection-refused/timeout/TLS/redirect) each have at least one test asserting an exact outcome, and those branches show as covered in the coverage report.
- **SC-005**: JDBC storage and template pipeline each have at least one passing infrastructure-real integration test with at least one negative case.
- **SC-006**: The full unit test suite passes 20 consecutive runs with zero flaky failures, with no fixed-duration sleeps remaining in the affected suites.
- **SC-007**: The affected test suites contain zero hardcoded absolute temp paths and zero unguarded casts in assertions (verified by inspection/search of the affected files).
- **SC-008**: A deliberately failing property test reports a shrunk counter-example (minimal case), not a raw random sample.
- **SC-009**: The lint gate passes with zero violations on the feature branch, and a seeded violation of each enforced rule class fails the verification build naming rule, file, and line.
- **SC-010**: The binary-compatibility check reports clean against the latest published release; a seeded removal of a public method produces a visible warning naming the incompatibility while the build still succeeds.
- **SC-011**: The verification build compiles with zero escalated-diagnostic failures, and a seeded non-exhaustive match fails compilation.
- **SC-012**: The dependency-hygiene report shows zero undeclared and zero unused compile-scope dependencies, and automated update proposals are active on the default branch.
- **SC-013**: A contributor can run every new gate locally with commands documented in one place, each completing without requiring CI.

## Assumptions

- The milestone title (v1.24.0) is authoritative; issue-body references to "v1.14.0" in #210/#211 are stale typos.
- Issue #80 was filed when the floor was 45%; the floor has since been raised to 65/60 (statement/branch). The remaining work under #80 is re-measuring after benchmark exclusion (#210), ratcheting the floors to the new honest baseline per the constitution's data-driven rule ("set just under measured"), and documenting the ratchet policy — not a return to the 55→65→75 schedule proposed in the issue text.
- Issue #81 is narrowed per #210: JWT integration coverage already exists from earlier milestones; only the JDBC storage backend and the template pipeline remain in scope.
- The JDBC integration test uses a real containerized database (constitution Test Discipline layer 3, container-backed); the template pipeline test does not require a container.
- The template pipeline end-to-end test lives at the integration or unit/functional layer within the library (real file templates, real rendering) — it does not require the `examples/` Gatling e2e overlay, which remains unchanged.
- Scope is strictly test code, build configuration (coverage settings), and documentation. No production source changes are expected; if covering a failure branch requires a production seam, that need surfaces at planning time and stays within backward-compatibility rules (internal/package-private only).
- Closed milestone issues (#253, #254, #261) are already delivered and out of scope for this spec.
- The three previously fixed flaky-test items on `main` (LogCapture ThreadLocal work) are complete; #109's sleep removal is the remaining flakiness item.
- The static-analysis stories (US5–US8) come from a direct maintainer follow-up request, not from existing GitHub issues. Clarified 2026-07-03: they ship in milestone v1.24.0 (milestone 10) alongside the rest of this spec; four issues (one per gate) MUST be filed into that milestone before implementation to satisfy the repository's 1-issue-=-1-commit and PR-linkage rules — expected via `/speckit-taskstoissues` or manual filing. A standing "maintenance" milestone was additionally created for bot-authored dependency-update PRs (retired 2026-07-19 — bot PRs now join the current active milestone; see Clarifications).
- All new static-analysis gates apply to the library sbt build only (all its compile scopes: production, test, integration); the `examples/` overlay projects are not gated — their smoke tests continue to gate behavior against the published artifact.
- Candidate tooling, final selection deferred to `/speckit-plan`: Scalafix (semantic lint + rewrites; needs SemanticDB), sbt-mima-plugin (binary compatibility), curated `scalacOptions` (e.g. `-Xlint`/`-Wunused` set or sbt-tpolecat) with `-Werror` in CI, Scala Steward (dependency-update automation; Dependabot does not support sbt), sbt-explicit-dependencies (undeclared/unused deps). WartRemover/Scapegoat overlap the Scalafix rule set; a single primary linter is preferred to avoid duplicate noise.
- All candidate tools are build-time-only sbt plugins or repository automation — none add a runtime dependency to the published artifact, so constitution principle II is unaffected by the tooling itself. The maintainer's follow-up request constitutes the constitution-IV authorization to add these build dependencies; exact versions are still confirmed at plan time.
- The binary-compatibility baseline is the latest published `vX.Y.Z` artifact on Maven Central at build time; the gate is expected to start green (v1.23.x line has no known unreleased breaking changes).
- Current `scalacOptions` contain no warning escalation; bringing the codebase to zero findings for the new gates may require behavior-preserving source edits (removing unused imports, sealing matches), which FR-015's scope explicitly admits.
