# Tasks: Coverage & Test Infra Hardening

**Input**: Design documents from `/specs/008-coverage-test-infra/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/build-gates.md](contracts/build-gates.md), [quickstart.md](quickstart.md)

**Tests**: Tests ARE this feature (constitution III, TDD). The red phase for test-fix tasks is the mutation probe (break production → test must fail); for gate tasks it is the seeded violation (quickstart V1–V9).

**Organization**: Grouped by user story. Phases are in EXECUTION order, not raw priority order — US4 (P3) runs before US5 (P2) because the lint gate must land at zero findings and #110's `asInstanceOf` removal is a lint prerequisite (see Dependencies).

**Commit discipline** (AGENTS.md): 1 issue = 1 semantic commit, green on its own; spec artifacts committed FIRST as `docs(speckit)`; every PR carries milestone 10 (v1.24.0). `#L/#M/#D/#S` are placeholders for the four gate issues filed in T002 — substitute real numbers.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (admin + spec-first commit)

- [ ] T001 Commit all `specs/008-coverage-test-infra/` artifacts as `docs(speckit): add 008-coverage-test-infra spec/plan/tasks` — BEFORE any implementation commit (requires explicit user approval per repo policy)
- [ ] T002 [P] File 4 gate issues into milestone 10 via `gh issue create`: (1) "Build: Scalafix idiomatic lint gate" [FR-016/017/021/022, US5]; (2) "Build: MiMa binary-compatibility advisory check" [FR-018, US6]; (3) "Build: strict compiler diagnostics (-Werror)" [FR-019, US7]; (4) "Build: Scala Steward automation + dependency-hygiene report" [FR-020, US8]. Record numbers; substitute `#L/#M/#D/#S` in later tasks
- [ ] T003 [P] Create standing "maintenance" milestone (no due date) via `gh api repos/galax-io/gatling-picatinny/milestones -f title=maintenance` for bot PRs (clarification Q2)

**Checkpoint**: issues exist, spec committed — implementation may start

---

## Phase 2: Foundational (baseline measurements)

- [ ] T004 Record pre-feature baselines: run `sbt clean coverage test "IntegrationTest / test" coverageReport` and time the full verification chain; note measured stmt/branch %, wall-clock, and report location in `specs/008-coverage-test-infra/baseline.md` (feeds T006 ratchet + T029 ≤25% budget check; the file rides in the coverage PR, PR-B — created after T001's spec-first commit)

**Checkpoint**: honest starting numbers captured — ratchet and budget claims become verifiable

---

## Phase 3: User Story 1 — Honest Coverage Measurement (P1) 🎯 MVP

**Goal**: benchmark code out of the coverage denominator; floors ratcheted to honest values; ratchet policy documented (#210, #80)

**Independent Test**: quickstart V1 — report lists zero benchmark sources; floor-above-measured negative probe fails the build

- [ ] T005 [US1] In `build.sbt`: add single shared benchmark-exclusion definition (files `.*Benchmark.*` + package `org.galaxio.gatling.jmh.*`) and wire `coverageExcludedFiles`/`coverageExcludedPackages` from it (FR-001, FR-022). Verify report has zero benchmark sources; negative probe: with patterns removed benchmark files reappear. Commit `chore(coverage): exclude benchmark sources from coverage (#210)`
- [ ] T006 [US1] Re-measure: `sbt clean coverage test "IntegrationTest / test" coverageReport`; set `coverageMinimumStmtTotal`/`coverageMinimumBranchTotal` in `build.sbt` just under measured (never below 65/60, ≤5pp slack — SC-002); negative probe: floor above measured → build fails
- [ ] T007 [US1] Document ratchet policy in `TESTING.md` (floor tracks measured upward, never padded, never lowered; current floors + measurement date; FR-003) and refresh the floor comment in `build.sbt`. Commit `chore(coverage): ratchet floors and document policy (#80)`

**Checkpoint**: coverage gate honest — MVP delivered

---

## Phase 4: User Story 2 — Tests That Can Actually Fail (P1)

**Goal**: five #211 items — de-tautologize + failure-path coverage; single commit (1 issue = 1 commit)

**Independent Test**: quickstart V5 mutation probes — breaking production discovery/check-digit turns suites red; coverage report shows RedisAction/THttpClient failure branches covered

- [ ] T008 [P] [US2] Rewrite `src/test/scala/org/galaxio/gatling/templates/TemplatesSpec.scala`: drop local `loadTemplateNames` re-implementation, drive production `Templates` discovery; assert exact set (`test_json`, `test_xml`, no extensions); negative: missing directory → documented empty/absent result. Red phase: mutation probe (rename scanned resource dir in production → test fails)
- [ ] T009 [P] [US2] Replace check-digit recomputation with fixed known-valid + corrupted-digit invalid samples in `src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala` (NIR/NINO ~L872–887, NIF ~L897) and `src/test/scala/org/galaxio/gatling/feeders/RandomFeedersSpec.scala` (PSRNSP ~L403–413)
- [ ] T010 [US2] Tighten the loose Cyrillic-output checks (AFTER T009 — shares `GeneratedFeederSpec.scala`): `src/test/java/org/galaxio/gatling/javaapi/JavaApiExampleSmokeTest.java:255` — `Arguments.of("cyr", cyrillic(6), 6, ".+")` → pattern `[Ѐ-ӿ]{6}`; `src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala:402-405` — length-only assert → `[Ѐ-ӿ]{10}` content+length; Latin-string negative in both. (Corrected from phantom `examples/` paths — analysis I1; overlays untouched)
- [ ] T011 [P] [US2] Extend EXISTING `src/test/scala/org/galaxio/gatling/redis/RedisActionSpec.scala` with failure-branch component tests for `src/main/scala/org/galaxio/gatling/redis/RedisAction.scala:51,67-88`: transactions/Mocks ActorSystem harness + stubbed failing Redis client; assert exact KO status, propagated error message, stats-engine recording, `next` still fired; success-path control case
- [ ] T012 [US2] New `src/it/scala/org/galaxio/gatling/utils/THttpClientTransportSpec.scala` (non-container `it`, loopback only): connection-refused (closed ephemeral port), timeout (accepting-silent socket), TLS handshake failure (plaintext socket vs HTTPS), and BOTH redirect modes via JDK `com.sun.net.httpserver` 302→200 — `Redirect.NEVER` (client default, `THttpClient.scala:126`) returns the 302 itself, follow mode returns exact final body (spec edge case: both pinned); each asserts exact error type/outcome (plan Complexity Tracking justification applies)
- [ ] T013 [US2] Run full unit + IT suites; confirm previously-0% branches now covered in coverage report (SC-004). Single commit `fix(test): de-tautologize suites and cover failure paths (#211)`

**Checkpoint**: SC-003 mutation probes pass — suite can no longer stay green on broken production

---

## Phase 5: User Story 4 — Deterministic, Portable Suite (P3, pulled early — unblocks US5)

**Goal**: 4 hygiene issues, 4 independent commits (#108, #109, #110, #121)

**Independent Test**: quickstart V5 — 20 consecutive transaction-suite runs green; forced Redis IT mismatch shows typed message; shrunk counter-example on property failure

- [ ] T014 [P] [US4] `src/test/scala/org/galaxio/gatling/storage/StorageBackendSpec.scala:30`: replace hardcoded `/tmp` path with `Files.createTempDirectory`-based per-run dirs + cleanup; nonexistent-file case uses a path inside a fresh temp dir (FR-011). Commit `fix(test): isolate storage specs in per-run temp dirs (#108)`
- [ ] T015 [P] [US4] #109 verification only (fix already on `main` — latch in `src/test/scala/org/galaxio/gatling/transactions/fixtures.scala:36`): run `TransactionsSpec` 20× (quickstart V5 loop), confirm zero flakes and no synchronization sleeps remain; post evidence comment and close #109 (no commit)
- [ ] T016 [P] [US4] `src/it/scala/org/galaxio/gatling/redis/RedisIntegrationSpec.scala:36,103,135,143,153`: replace all 5 `asInstanceOf` with typed-extraction helper (pattern match; mismatch → `fail(...)` naming expected vs actual type; FR-013). Commit `fix(test): typed extraction in Redis IT (#110)`
- [ ] T017 [US4] `src/test/scala/org/galaxio/gatling/feeders/RandomFeedersSpec.scala` (AFTER T009 — same file): add explicit `PropertyCheckConfiguration` (minSuccessful, maxDiscardedFactor) + pin Shrink instances where needed; probe: deliberately failing property reports shrunk minimal counter-example (FR-014). Commit `test(feeders): explicit property-check and shrink configuration (#121)`

**Checkpoint**: suite deterministic + portable; `it` sources free of `asInstanceOf` → lint gate can land clean

---

## Phase 6: User Story 3 — JDBC & Template Pipeline Integration (P2)

**Goal**: close #81 (narrowed: JWT covered earlier, JDBC IT already exists)

**Independent Test**: quickstart V6 — render test exact output + missing-variable negative; JDBC IT green under Docker

- [ ] T018 [US3] Verify existing `src/it/scala/org/galaxio/gatling/storage/JdbcStorageIntegrationSpec.scala` green against real Postgres 17 container (`sbt "IntegrationTest / testOnly *JdbcStorageIntegrationSpec"`); capture output as issue evidence (FR-009, no code change expected); also confirm the Docker-absent behavior: with the daemon stopped the suite fails fast with a clear Testcontainers infrastructure message, no hang/false success (spec edge case)
- [ ] T019 [US3] Template-pipeline end-to-end render test (AFTER T008 — same area): real resource template through production `Templates` path resolved against a real Gatling session (`GatlingConfiguration.loadForTest()` bootstrap, real code path — no mocked runtime); assert exact rendered output + missing-session-variable negative (FR-010); in `src/test/scala/org/galaxio/gatling/templates/TemplatesSpec.scala` or new `TemplatePipelineSpec.scala`. Commit `test(templates): end-to-end template render coverage (#81)`; close #81 noting the narrowing

**Checkpoint**: all externally-visible subsystems have infra-real coverage

---

## Phase 7: User Story 5 — Idiomatic Scala Lint Gate (P2)

**Goal**: Scalafix gate, zero findings at landing, auto-fix documented (#L)

**Independent Test**: quickstart V2 — seeded `== null` + unused import fail check naming rule/file/line; fix command converges with formatter

- [ ] T020 [US5] Add `sbt-scalafix` 0.14.7 to `project/plugins.sbt`; in `build.sbt` enable SemanticDB on all scopes + add `-Wunused:imports,privates,locals,patvars` (NO `-Werror` yet — that is US7); create `.scalafix.conf` (DisableSyntax: noNulls/noReturns/noAsInstanceOf/noIsInstanceOf/noFinalize/noProcedureSyntax; RemoveUnused; OrganizeImports — procedure syntax mandated by FR-016); scalafix source filter reuses the shared benchmark-exclusion definition from T005 (FR-022). Depends: T005, T016
- [ ] T021 [US5] Remediate to zero findings across Compile/Test/IntegrationTest: `sbt scalafixAll scalafmtAll` for auto-fixables; manual fixes for DisableSyntax findings; genuinely unavoidable sites get `// scalafix:ok <Rule>` + justification comment (files owned by #110/#211 must already be clean)
- [ ] T022 [US5] Add `scalafixAll --check` step to `.github/workflows/ci.yml` (after scalafmt check, before compile); document check/fix commands + escape hatch in `TESTING.md` "Static analysis & gates" section (normative — SC-013) with a mirror line in `AGENTS.md` Commands block; run quickstart V2 seeded probes. Commit `build(lint): add scalafix idiomatic lint gate (#L)`

**Checkpoint**: idiom rules machine-enforced; violations name rule/file/line

---

## Phase 8: User Story 6 — Binary Compatibility Advisory (P2)

**Goal**: MiMa advisory check — warnings, never failures (#M, clarification 2026-07-04)

**Independent Test**: quickstart V3 — seeded public-method removal prints warning AND exits green

- [ ] T023 [US6] Add `sbt-mima-plugin` 1.1.6 to `project/plugins.sbt` (AFTER T020 — same file); in `build.sbt` set `mimaPreviousArtifacts := Set("org.galaxio" %% "gatling-picatinny" % "1.23.0")`; local advisory command is `mimaFindBinaryIssues` (never fails)
- [ ] T024 [US6] Add CI advisory step to `.github/workflows/ci.yml`: run `mimaReportBinaryIssues` under `continue-on-error: true` (the Report task's failing exit is what the flag absorbs — analysis A1), re-emitting findings as `::warning::` annotations; document acknowledgement flow (`mimaBinaryIssueFilters` + justification + constitution-II version bump) in `TESTING.md`, and add the two release-checklist entries (bump baseline after release; review outstanding warnings before tagging) to `AGENTS.md` Release Process — the operative checklist — referenced from `TESTING.md`; run quickstart V3 probe (warning present + workflow green both asserted). Commit `build(compat): add advisory MiMa binary-compatibility check (#M)`

**Checkpoint**: API breaks visible on every PR without blocking merges

---

## Phase 9: User Story 7 — Strict Compiler Diagnostics (P3)

**Goal**: curated `-Xlint` + `-Werror` on all scopes (#D)

**Independent Test**: quickstart V4 — seeded non-exhaustive match fails compile; narrow `@nowarn` compiles

- [ ] T025 [US7] In `build.sbt`: extend `scalacOptions` with `-Xlint:_` (minus exclusions found noisy during remediation, each documented), `-Wdead-code`, and `-Werror` on Compile/Test/IntegrationTest (AFTER T021 — unused warnings already remediated); fix remaining warnings; tolerated diagnostics (e.g. Provided-Gatling deprecations) get per-site `@nowarn("cat=…")` + justification (FR-019)
- [ ] T026 [US7] Run quickstart V4 seeded probes (non-exhaustive match → compile error; `@nowarn` narrow escape works); document flag set + suppression policy in `TESTING.md`; verify examples overlays still compile untouched (clarification Q5). Commit `build(diagnostics): escalate curated compiler warnings to errors (#D)`

**Checkpoint**: compiler is a gate, not a suggestion

---

## Phase 10: User Story 8 — Dependency Hygiene Automation (P3)

**Goal**: Steward automation + report-only hygiene tasks (#S, clarifications Q2/Q4)

**Independent Test**: quickstart V7 — report zero findings; manual workflow dispatch opens PRs carrying "maintenance" milestone

- [ ] T027 [P] [US8] Add `sbt-explicit-dependencies` 0.3.1 to `project/plugins.sbt` (AFTER T023 — same file); FIRST verify sbt 1.12.13 compatibility (research R5 fallback: drop plugin, document `dependencyTree` review instead); run `undeclaredCompileDependencies unusedCompileDependencies`, triage to zero findings; document as manual pre-release task in `TESTING.md` (NOT CI-gated — clarification Q4)
- [ ] T028 [US8] EXTEND the existing `.github/workflows/scala-steward.yml` (already present: weekly cron + `workflow_dispatch`, `scala-steward-action@v2` — do NOT recreate/overwrite, analysis I2): add a post-step assigning every opened Steward PR to the "maintenance" milestone via `gh pr edit --milestone` (dep: T003), and create the missing `.scala-steward.conf` update policy; validate by manual dispatch after merge — opened PRs carry the milestone (FR-020). Commit `build(deps): steward milestone wiring and hygiene report (#S)`

**Checkpoint**: dependency graph stays current + intentional without manual audits

---

## Phase 11: Polish & Cross-Cutting

- [ ] T029 Run full regression chain (quickstart V8): `sbt scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" compile mimaFindBinaryIssues test "IntegrationTest / test"`; compare wall-clock vs T004 baseline — ≤ ~25% increase, record delta in PR description (FR-015, R8)
- [ ] T030 [P] Docs completeness sweep (quickstart V9 / FR-021): every gate has local command + fix flow + escape hatch in `TESTING.md` (normative) + `AGENTS.md` (mirror); execute each documented command verbatim once — all succeed. Plus mechanical SC-007 sweep: `grep -rn "/tmp" src/test src/it`, synchronization `Thread.sleep` in test sources, `asInstanceOf` in `src/test`+`src/it` — all zero hits (bounded probe loops and justified `scalafix:ok` sites excepted)
- [ ] T031 [P] Flake probe (SC-006): 20 consecutive full unit-suite runs, zero failures
- [ ] T032 Milestone audit: every PR assigned to milestone 10; close #80 #81 #108 #109 #110 #121 #210 #211 + #L #M #D #S as their commits land on `main` (AGENTS.md milestone rules)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Ph1)** → everything; T001 spec-first commit precedes all implementation commits
- **Foundational (Ph2)** T004 baselines → T006 (ratchet numbers), T029 (budget check)
- **US1 (Ph3)** → US5/US7 (shared benchmark-exclusion definition consumed by their source filters)
- **US2 (Ph4)** T008 → T019 (TemplatesSpec same file); T009 → T017 (RandomFeedersSpec same file); T009 → T010 (GeneratedFeederSpec same file)
- **US4 (Ph5)** T016 (#110) → US5 T021 (lint zero-findings requires `asInstanceOf`-free `it` sources) — reason US4 runs before US5 despite P3 < P2
- **US5 (Ph7)** T020 (`-Wunused`) + T021 (remediation) → US7 T025 (`-Werror` escalation lands on already-clean warnings)
- **`project/plugins.sbt` serial chain**: T020 → T023 → T027 (same file, three commits)
- **Verify workflow serial chain**: T022 → T024 (same file)
- **US6, US8**: no cross-story code deps (only the file chains above); T028 needs T003 (milestone exists)

### Parallel Opportunities

- Ph1: T002 ∥ T003
- US2: T008 ∥ T009 ∥ T011 (T010 after T009 — same `GeneratedFeederSpec.scala`; T012 next; T013 last)
- US4: T014 ∥ T015 ∥ T016 (T017 after T009)
- Polish: T030 ∥ T031

## Parallel Example: User Story 2

```bash
# Three independent files simultaneously:
Task: "De-tautologize TemplatesSpec (drive production discovery)"
Task: "Known-sample check digits in feeders/faker/GeneratedFeederSpec + RandomFeedersSpec"
Task: "Extend RedisActionSpec with failure-branch tests via Mocks harness"
# Then (same file as check-digit task):
Task: "Tighten Cyrillic patterns in JavaApiExampleSmokeTest + GeneratedFeederSpec"
```

## Implementation Strategy

- **MVP** = Phase 1–3 (US1): honest coverage floor — smallest shippable value, everything else builds on trusted numbers.
- **Incremental delivery, 1 concern per PR** (stacked, rebase-only): PR-A spec docs (T001); PR-B coverage (#210+#80); PR-C #211; PR-D hygiene (#108/#110/#121 — 3 commits); PR-E #81; PR-F lint (#L); PR-G MiMa (#M); PR-H diagnostics (#D); PR-I steward (#S). Each PR green standalone, milestone 10 attached before merge.
- **Stop-and-validate** at every checkpoint via the matching quickstart section (V1–V9).
