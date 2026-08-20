# Tasks: Cross-build on sbt 1 and sbt 2

**Input**: Design documents from `specs/012-cross-build-sbt/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: This feature ships no new Scala tests. Its "tests" are the project's existing gate suites, run under both majors — see the plan's Test Model. Verification tasks below are therefore real, and they are the point of the feature.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Single sbt build at the repository root, becoming a two-project build: `root` (published library, `src/main` + `src/test` unchanged) and `integration` (relocated Testcontainers tests). Example overlays live under `examples/`.

---

## ⚠️ Read before starting: the defect this feature exists to avoid

**sbt 2.0.6's `test` task IS `testQuick`.** Two independent probes observed `sbt --sbt-version 2.0.6 <proj>/test` print `[success]` having run **zero** tests, and the "already passed" record lives in the *global* disk cache (`~/.cache/sbt`, `~/Library/Caches/sbt/v2`), surviving `clean` and `rm -rf target`. `sbt/setup-sbt@v1` enables `disk-cache: true` by default with a key containing no sbt major.

Combined with the report glob (`**/target/test-reports/*.xml` matches nothing under sbt 2's layout), `if-no-files-found: warn`, and EnricoMi's `action_fail_on_inconclusive: false`, run #1 is honest and **every run after it is vacuously green**. Fixing only the glob leaves the trap fully armed.

All four defences live in T053 (the scheduled sbt 2 workflow) and must land together: `testOnly` not `test`; widened globs `**/test-reports/*.xml`; `if-no-files-found: error` + `action_fail_on_inconclusive: true`; and positive assertions that the run used sbt 2 **and** executed a non-zero number of tests. T057 carries the same `testOnly` convergence into the sbt 1 workflows and T058 into the git hook.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the baseline that every later comparison is measured against. Nothing here changes the build.

- [X] T001 Record the sbt 1 baseline: run `sbt clean coverage Test/test "IntegrationTest / test" coverageOff coverageReport` at HEAD and save the statement/branch percentages and test counts to `specs/012-cross-build-sbt/baseline.md`
- [X] T002 [P] Record the current dependency-hygiene report output (`sbt undeclaredCompileDependencies unusedCompileDependencies`) verbatim into `specs/012-cross-build-sbt/baseline.md` — the opt-in overlay in T020-T023 must reproduce it exactly (expected: 1 undeclared `jackson-databind`, 1 unused `jackson-core`)
- [X] T003 [P] Record the current POM `<licenses>` block and full dependency set from `sbt publishLocal` into `specs/012-cross-build-sbt/baseline.md`, for the FR-008 artifact-equivalence comparison in T048
- [X] T004 [P] Confirm branch protection does not pin CI job names: `gh api repos/galax-io/gatling-picatinny/branches/main/protection` and the active ruleset. Record the result in `specs/012-cross-build-sbt/baseline.md`. (Verified 2026-08-19: 404 "Branch not protected", ruleset 15574909 has no `required_status_checks`. With the matrix scoped out no existing job is renamed, so this is now confirmation-only rather than a prerequisite)

**Checkpoint**: Baseline captured. Every later "is it the same?" question has an answer to compare against.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Make the build *load* under sbt 2 at all. Until this phase completes, no gate can run on the secondary major and no user story can be verified.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

**⚠️ ATOMICITY**: T010-T011 must land in ONE commit (filters without plugin = `Not found: unusedCompileDependenciesFilter`, build.sbt fails to typecheck on *both* majors). T020-T023 must land in the SAME commit (AGENTS.md's mandatory pre-tag hygiene step is broken in the interim otherwise).

- [X] T010 Bump `gatling-sbt` 4.18.3 → 4.19.1 and delete the `com.github.cb372 % sbt-explicit-dependencies % 0.3.1` line in `project/plugins.sbt` (4.18.3 publishes no `_sbt2_3` artifact; 4.19.1 is published for both majors — authorized by maintainer 2026-08-19)
- [X] T011 Delete the dependency-hygiene comment block and its five filter settings from `build.sbt` lines 48-69 (`undeclaredCompileDependenciesFilter -=` at 50/54/58, `unusedCompileDependenciesFilter -=` at 62/66), preserving each justification comment verbatim for reuse in T021
- [X] T012 Run `sbt scalafmtSbt` and commit the reflow in `project/plugins.sbt` and `build.sbt` — removing the longest artifact name re-aligns the `%` columns of every remaining plugin line, and removing the filter block realigns the `:=` column of `mimaPreviousArtifacts`, `semanticdbEnabled`, `semanticdbVersion`, `Compile / scalafix / unmanagedSources` and `scalacOptions`. Expect ~10 insertions/11 deletions in plugins.sbt, not 2
- [X] T013 Replace the tuple form with `ThisBuild / licenses := List(License.Apache2)` in `publish.sbt` line 27 — use the **bare** unqualified form, which compiles with no import on both majors. Leave the four `url(...)` calls at lines 4, 5, 10, 21 unchanged (sbt 2 deprecation warning only; sbt 1 still requires `URL`)
- [X] T014 Add `ThisBuild / exportJars := false` to `build.sbt`. **BLOCKING and easily misdiagnosed**: `exportJars` defaults to `true` in sbt 2, putting a jar on the Test classpath; `src/main/scala/org/galaxio/gatling/templates/Templates.scala:44` calls `Paths.get(resource.toURI)` on the `templates` resource, which throws `FileSystemNotFoundException` for a `jar:file:...!/templates` URI. Without this, ~6 sbt-2 tests fail and get blamed on the `integration` migration. Cite `Templates.scala:44` in the comment
- [X] T015 Add the `Compile / products` and `Test / products` overrides to `build.sbt` root settings to pin sbt 1's product shape (`copyResources` into `classDirectory`; `classDirectory` alone as the product). **Verified 2026-08-20**: without it, 2 `TemplatesSpec` tests fail cold under sbt 2 (`Test/products` = `[test-classes, src/test/resources]`, and sbt 2's `test-classes` has no `templates/`); with it, 725/725 + 824/824 pass cold. Note the real mechanism is resource copying, not `jar:` URIs — see corrected research D-16
- [X] T016 Verify the meta-build now resolves on both majors: `sbt "show sbtVersion"` and `sbt --sbt-version 2.0.6 "show sbtVersion"` both load `build.sbt` without error. Use `show`, not `print` — `print` is sbt-2-only and dies on the sbt-1 leg
- [X] T020 [P] Create `project/hygiene/plugins.sbt` containing the `addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")` line **plus** `Compile / unmanagedSources += baseDirectory.value / "hygiene" / "HygieneFilters.scala"` — the flag attaches a meta-build file, which alone cannot carry project-scoped settings
- [X] T021 [P] Create `project/hygiene/HygieneFilters.scala` as an `AutoPlugin` carrying the five filters from T011 with their justification comments intact. Scope them to `root` explicitly rather than relying on `trigger = allRequirements`, which would also report on the `integration` subproject — output today's build cannot produce
- [X] T022 Document the opt-in invocation in `AGENTS.md` Release Process and `TESTING.md` static-analysis table: `sbt --batch --addPluginSbtFile=project/hygiene/plugins.sbt undeclaredCompileDependencies unusedCompileDependencies` (sbt 1 only). `--batch` is **mandatory** for non-TTY use — without it the run is SIGKILLed at exit 137 after "done compiling", looking like a hang. Do **not** build a copy-in/`trap`-cleanup script: `trap` does not fire on SIGKILL and a killed run leaves untracked files
- [X] T023 Verify the overlay reproduces T002's baseline exactly — same two findings, no spurious extras — and that `git status --porcelain` is empty afterwards. Note `scalafmtSbtCheck` **does** recurse into `project/hygiene/`, so `HygieneFilters.scala` must be scalafmt-clean even though sbt 2 never compiles it
- [X] T024 Record the FR-006 capability exemption in the `TESTING.md` "Static analysis & gates" table: capability = dependency hygiene, unsupported major = 2.x, reason = no `_sbt2_3` artifact published, revisit condition = `sbt-explicit-dependencies_sbt2_3` appears on Maven Central. This table is the single normative record; other files get one-line pointers only

**Checkpoint**: `sbt --sbt-version 2.0.6` loads the build. The secondary major is reachable; user story work can begin.

---

## Phase 3: User Story 1 - Maintainer builds and gates under either sbt major (Priority: P1) 🎯 MVP

**Goal**: One unmodified tree compiles, tests, and gates identically under sbt 1.12.15 and sbt 2.0.6.

**Independent Test**: Run the full gate suite twice from a clean checkout — once bare, once with `--sbt-version 2.0.6` — and confirm identical verdicts with no tracked file edited between runs.

**⚠️ ATOMICITY**: T030-T034 must land in ONE commit. Deleting `config("it")` before the sources move produces **no build error** — the four files under `src/it/scala` simply stop being compiled by anything. That is a silent loss of 44 tests, the lint gate's 4-source scope, and ~5 points of coverage.

- [X] T030 [US1] Delete the custom configuration from `build.sbt`: `lazy val IntegrationTest = config("it") extend Test` (line 5), `.configs(IntegrationTest)` (16), `inConfig(IntegrationTest)(Defaults.testSettings)` (17), `IntegrationTest / parallelExecution` (46), `IntegrationTest / unmanagedResourceDirectories` (47), and `.settings(inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)))` (119). The last becomes **nothing**, not a renamed call — ScalafixPlugin injects `scalafixConfigSettings(Compile)` and `(Test)` into every project automatically
- [X] T031 [US1] `git mv` the four integration specs from `src/it/scala/org/galaxio/gatling/{feeders/VaultIntegrationSpec,redis/RedisIntegrationSpec,storage/JdbcStorageIntegrationSpec,utils/THttpClientTransportSpec}.scala` to `integration/src/test/scala/org/galaxio/gatling/<same subpath>/`, with zero content edits, and remove the now-empty `src/it/`
- [X] T032 [US1] Add the `integration` subproject to `build.sbt`: `(project in file("integration"))`, `.dependsOn(root % "compile->compile;test->test")`, `name := "gatling-picatinny-integration"`, `publish / skip := true`, `Test / parallelExecution := false`, `mimaFailOnNoPrevious := false`, plus `semanticdbEnabled`/`semanticdbVersion` (required or scalafix's semantic rules cannot run on the relocated sources)
- [X] T033 [US1] Re-declare root's `Provided` bundles (`gatlingCore`, `gatlingShared`, `fastUUID`) in the `integration` subproject's own `libraryDependencies`, keeping the `Provided` scope. **`Provided` does not propagate across `dependsOn`** — verified on a scratch build. Omitting this is a compile error on `io.gatling.core.feeder.Record` and `com.redis.RedisClientPool`
- [X] T034 [US1] Rewrite the Ivy configuration strings in `project/Dependencies.scala` — eleven module lines across seven vals (75, 79, 83, 87, 121, 122, 133, 139, 146, 147, 148) change `% "test,it"` / `% "it"` to `% Test`. Split `scalaTesting` (now scalaCheck ++ scalaTest ++ scalaMock ++ scalaTestPlus) from a new `integrationTesting = testcontainers ++ jdbcDrivers` consumed only by the subproject. Rewrite the `jdbcDrivers` comment at 136-137: "MUST stay `it` scope, never published" becomes "lives on the `integration` subproject, which sets `publish / skip := true`"
- [X] T035 [US1] Hoist the strict compiler flags and `javacOptions` from root's settings block into a shared `lazy val strictScalacOptions` / `commonSettings` in `build.sbt` and apply to **both** projects, keeping values byte-identical including the `-Xlint:_,-infer-any` comment block. Do **not** use `ThisBuild / scalacOptions` — a plugin doing `scalacOptions ++=` at project scope would silently win. Without this the relocated sources lose `-Werror`/`-Wunused` and the strict-diagnostics gate goes inert on 4 files
- [X] T036 [US1] Add `.aggregate(LocalProject("integration"))` to root in `build.sbt` — `LocalProject("integration")`, **not** the bare forward reference, which fails the load. Aggregation is required or `scalafmtAll`/`scalafixAll`/`Test/compile`/`clean` silently skip the subproject (the FR-007 false-green)
- [X] T037 [US1] Add the aggregation opt-outs to root's settings in `build.sbt`: `Test / test / aggregate := false`, `Test / testOnly / aggregate := false`, `Test / testQuick / aggregate := false` — otherwise `sbt compile test` pulls Docker into the unit gate. Note `Test / testFull / aggregate` is deliberately left enabled (sbt-2-only task, nothing invokes it); record that asymmetry in a comment
- [X] T038 [US1] [P] Add `/integration/target/` to `.gitignore` — the existing `/target/` is root-anchored and does not cover a subproject, so FR-001's "`git status --porcelain` empty" acceptance check fails on every sbt 1 build without it
- [X] T039 [US1] Keep root-only settings on root in `build.sbt`: coverage floors and exclusions, `mimaPreviousArtifacts`, `Compile / scalafix / unmanagedSources` benchmark filter, JMH, and the `benchmarkFilePattern`/`benchmarkPackagePattern` definitions. Do **not** copy coverage floors to `integration` — its default `coverageFailOnMinimum := false` is correct for a project with zero Compile statements
- [X] T040 [US1] Verify the integration suite on both majors: `sbt integration/testOnly` and `sbt --sbt-version 2.0.6 integration/testOnly` each run 4 suites / 44 tests / 0 failures with Docker present. Use `testOnly`, never `test` — see the warning at the top of this file
- [X] T041 [US1] Verify no unit-test regression on both majors: `sbt "Test/testOnly"` and `sbt --sbt-version 2.0.6 "Test/testOnly"` each report 725 ScalaTest / 824 total, 0 failures
- [X] T042 [US1] Re-measure coverage **cold** on both majors: `sbt [--sbt-version 2.0.6 --sbt-cache <fresh-dir>] clean coverage "Test/testOnly" "integration/testOnly" coverageOff coverageReport coverageAggregate`. Expected 81.44% stmt / 75.49% branch, identical on both — **higher** than the recorded 77.75/68.29, so the migration costs no coverage. Warm caches drift (81.40/75.33 observed), so `clean` is mandatory or the gate flaps
- [X] T043 [US1] Update the coverage comment in `build.sbt` line 39 with the new measured figure and date, and decide with the maintainer whether to ratchet the floors up from 75/66 per the TESTING.md rules (floors only move up; a drop would mean misconfiguration, not licence to lower)
- [X] T044 [US1] Run the seeded-violation parity check (SC-002): inject a failing assertion, then an unused import, then a formatting violation in turn, and confirm each gate fails on **both** majors with the same violation reported. Revert each and confirm `git status --porcelain` is empty
- [X] T045 [US1] Verify the full gate suite green from cold on both majors: `clean scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" coverage "Test/testOnly" "integration/testOnly" coverageOff coverageReport coverageAggregate mimaReportBinaryIssues`. Confirm scalafix reaches the subproject (expect "103 Scala sources" root Compile / "46" root Test / "4" integration Test)
- [X] T046 [US1] Verify `show integration/publish/skip` is `true` and that `sbt publishLocal` emits **one** artifact whose POM contains no postgresql and no testcontainers, with all test deps `<scope>test</scope>`. Aggregation means `publish / skip` is the only thing stopping a second artifact reaching Maven Central — verify by inspecting `~/.ivy2/local`, not by reading the setting

**Checkpoint**: US1 delivered — one tree, both majors, identical verdicts. This is a viable MVP even if CI is not yet matrixed.

---

## Phase 4: User Story 2 - sbt 2 breakage caught early, without taxing every PR (Priority: P2)

**Goal**: sbt 2 is verified daily and on build-definition PRs. Ordinary PRs cost exactly what they cost today.

**Independent Test**: Break sbt 2 only, confirm the scheduled run fails and names the major. Separately, open a PR touching `project/build.properties` and confirm the sbt 2 check runs on it.

**Scope note (2026-08-20)**: the full per-PR `sbt-major: [1,2]` matrix was scoped OUT. Dual-major support is insurance, not a shipped capability — nobody is blocked today and consumers are unaffected. Existing jobs stay sbt 1 only and are **not** renamed, which also sidesteps the check-name churn entirely.

**⚠️ The anti-vacuity rules in T053 are not optional.** A once-a-day job that passes having run nothing is worse than no job: it reads as coverage that does not exist, and it is cheap enough that nobody scrutinises it.

- [X] T050 [US2] Add an `sbt-version` input to `.github/actions/sbt-setup/action.yml` with default `''` (**empty**, not `'1.12.15'`) — empty means "use `project/build.properties`", which keeps all nine existing callers working unchanged
- [X] T051 [US2] Add a resolver step to `.github/actions/sbt-setup/action.yml` exporting the launcher flag as a **dedicated** env var `SBT_VERSION_FLAG`. **Never append `-Dsbt.version=` to `SBT_OPTS`**: `ci.yml:20` and `:64` set `SBT_OPTS`, clobbering it, so the sbt 2 job would silently run sbt 1 and pass. At the launcher level `SBT_OPTS` also strictly outranks the flag (`JAVA_TOOL_OPTIONS` > `SBT_OPTS` > CLI flag > `JAVA_OPTS` > `build.properties`)
- [X] T052 [US2] Partition caches per major in `.github/actions/sbt-setup/action.yml` — major in both `key` and `restore-keys`, `extraKey` on `coursier/cache-action`, and add sbt 2's `~/.config/sbt` and `~/.cache/sbt` to `path:` (current `~/.sbt` is sbt 1 only)
- [X] T053 [US2] Create `.github/workflows/sbt2-compat.yml`: triggers `schedule` (daily), `workflow_dispatch`, and `pull_request` filtered to `project/build.properties`, `project/plugins.sbt`, `project/Dependencies.scala`, `project/hygiene/**`, `*.sbt`, `examples/scala-sbt-example/**`, `.github/actions/sbt-setup/**`, `scripts/test-scala-sbt-template.sh`. Runs the **full** suite under sbt 2 — `scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" clean coverage "Test/testOnly" "integration/testOnly" coverageOff coverageReport coverageAggregate mimaReportBinaryIssues publishLocal`. All four anti-vacuity defences mandatory: `testOnly` never `test`; globs `**/test-reports/*.xml`; `if-no-files-found: error` + `action_fail_on_inconclusive: true`; and assertions that `sbt $SBT_VERSION_FLAG "show sbtVersion"` prints `2.` **and** the reported test count is non-zero
- [X] T054 [US2] Make failures visible in `.github/workflows/sbt2-compat.yml` — on a scheduled failure, open or update a tracking issue (a red run on a schedule nobody watches is not a signal). Name the job so the major is readable without opening it, e.g. `sbt 2 compatibility (scheduled)`
- [X] T055 [US2] Handle the sbt-2 coverage cache hazard in `.github/workflows/sbt2-compat.yml`: sbt 2's action cache does not restore scoverage's `scoverage-data/`, so a warm run dies with `FileNotFoundException: …/scoverage.measurements.*`. Use a per-run `--sbt-cache` path and keep `clean` at the head — which also guarantees the cold measurement the coverage figures depend on
- [X] T056 [US2] Add the artifact-equivalence step to `.github/workflows/sbt2-compat.yml`: `publishLocal` under sbt 2 and diff the POM against the sbt 1 artifact — coordinates, dependency set, scopes, every `io.gatling` entry still `provided` (FR-008/SC-005). Use `/usr/bin/diff`; the rtk-proxied `diff` returns false "identical" results
- [X] T057 [US2] Update the sbt 1 gate commands in `.github/workflows/ci.yml` and `.github/workflows/release.yml` to the convergent `testOnly` form (`"Test/testOnly"`, `integration/testOnly`) — harmless on sbt 1, and it keeps CI, docs and the sbt 2 job speaking one language. Also fix the stale `IntegrationTest/test` comment at `release.yml:50` and add `skip-dirs: target` to both Trivy steps
- [X] T058 [US2] [P] Fix or delete `.githooks/pre-commit` — its `sbt scalafmtAll scalafmtSbt compile test` runs zero tests for any contributor on an sbt 2 launcher. Note `git config core.hooksPath` is unset and nothing wires it, so it is tracked-but-dead; decide explicitly rather than leaving it
- [ ] T059 [US2] Demonstrate the anti-vacuity requirement (contract M-4, SC-007): introduce a change valid under sbt 1 and invalid under sbt 2, trigger `sbt2-compat.yml` via `workflow_dispatch`, confirm it fails and names the major, then revert. A job never seen to fail has not been shown to work

**Checkpoint**: US2 delivered — sbt 2 verified daily and on the PRs that actually break it, at roughly one extra run per day instead of doubling every PR.

---

## Phase 5: User Story 3 - Example overlay builds under either major (Priority: P3)

**Goal**: The Scala/sbt overlay a new user copies builds and runs its e2e scenario under both majors.

**Independent Test**: With the library published locally, run the overlay's end-to-end scenario under each major and confirm Gatling `check`s pass.

**Note**: `Gatling/scalaSource`, `Gatling/resourceDirectory`, `Gatling/test`, `Gatling/testQuick`, `Gatling/testFull` and `Gatling/testOnly` all resolve under sbt 2 — the `Gatling` config axis is **not** broken the way `it` was, because gatling-sbt contributes it through an AutoPlugin's `projectConfigurations`. `Gatling/fullClasspath` does **not** resolve; unused here, but relevant to IDE run configs.

- [X] T070 [US3] Bump `gatling-sbt` 4.18.3 → 4.19.1 in `examples/scala-sbt-example/project/plugins.sbt` — verified as the only change the checked-in overlay needs to load under sbt 2
- [X] T071 [US3] Leave `examples/scala-sbt-example/build.sbt` and `examples/scala-sbt-example/project/build.properties` unchanged — verified: `enablePlugins(GatlingPlugin)`, the `Gatling / scalaSource` and `Gatling / resourceDirectory` overrides, `Resolver.mavenLocal` and the `% Test` block all load unmodified under sbt 2, and the launcher flag overrides the 1.12.15 pin
- [X] T072 [US3] Parameterise `scripts/test-scala-sbt-template.sh`: add `--set SbtGatlingVersion=4.19.1` and `--set SbtVersion="${SBT1_VERSION:-1.12.15}"` to the `galaxio template init` call (line ~42-43), add an `SBT_MAJOR` launcher switch, and change the run task from `sbt Gatling/test` to `"${sbt_cmd[@]}" 'Gatling/testOnly *'`. **`Gatling/test` is the same false-green as everywhere else** — with the overlay's `target/` deleted it still printed `Passed: Total 0` / `No tests to run` / `[success]`
- [X] T073 [US3] Add the overlay's sbt 2 run to `.github/workflows/sbt2-compat.yml` (not to `template-tests` in `ci.yml`) — publish the library locally under sbt 2, render the scala-sbt template, and run `'Gatling/testOnly *'`. `ci.yml`'s `template-tests` job stays exactly as it is, sbt 1 only, so java-maven and kotlin-gradle are untouched (FR-016) and no artifact-name or check-name churn is introduced
- [ ] T074 [US3] File an upstream issue against `galax-io/templates-gatling` to bump the `scala-sbt` template's `SbtGatlingVersion` default from 4.18.3 to 4.19.1. **The blocker is in a third-party repository** — but with the overlay check now on a schedule rather than gating PRs, T072's `--set SbtGatlingVersion=4.19.1` fully works around it and this upstream fix is no longer on the critical path
- [X] T075 [US3] Verify the overlay on both majors: from `examples/scala-sbt-example` with the library published locally, run `PICATINNY_VERSION=0.0.0-ci-local sbt [--sbt-version 2.0.6] 'Gatling/testOnly *'` and confirm all three simulations report success. Assert a **non-zero** request count — `testOnly` with no matching simulation is a plausible vacuous pass
- [X] T076 [US3] [P] Confirm `examples/java-maven-example` and `examples/kotlin-gradle-example` need no change (FR-016) — they track only `src/**`; their `pom.xml`/`build.gradle.kts` are generated by templates-gatling at CI time, and `scripts/test-kotlin-gradle-template.sh` contains zero sbt references

**Checkpoint**: All three user stories delivered and independently verified.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, governance, and the disposition of PR #319.

**⚠️ ORDERING**: T090-T093 must not assert "runs on both majors" until Phase 5 has landed — asserting it early is the exact false-green this feature exists to prevent. The four coverage-figure mirrors (T091) must move in one commit or they drift immediately.

- [X] T080 [P] Update `AGENTS.md`: Commands block (`IntegrationTest / test` → `integration/testOnly` at lines 19, 21; `test` → `testOnly` throughout), add the supported-majors line and the `sbt --sbt-version 2.0.6 <task>` pattern, Test Model layer 3 (line 48) and layer 4 (49), Boundaries (57, 59), Release Process (82, 98-108, 107), and fix the pre-existing `mimaFindBinaryIssues` contradiction at line 106
- [X] T081 [P] Update `TESTING.md`: layer-3 heading and prose (57, 59-60), `src/it/` paths (26, 67-68), layer-4 run command (70, 80-82), CI-gates table (129-136) including the integration and coverage rows, static-analysis table (162-168) with the per-major column and the T024 exemption, and the Scala Steward paragraph (170-176)
- [X] T082 [P] Update `README.md`: fix `sbt IntegrationTest/test` (line 276), rewrite `sbt Gatling/test` → `sbt 'Gatling/testOnly *'` (line 160), and add the supported-majors block to `## Contributing` (266-283) — the primary home for FR-019. Leave the consumer-facing `## Compatibility` table (52-59) alone; an sbt-major column would wrongly imply consumers must match
- [X] T083 [P] Update `docs/examples.md` (lines 29, 35-39) and `docs/configuration.md` (148) for the `Gatling/testOnly *` form and the secondary-major invocation
- [X] T084 Update `.specify/memory/constitution.md`: layer-3 definition (48, 54), layer-4 command (55), Stack Constraints build-tool row (109) naming both majors and their roles, Development Workflow steps 3 and 4 (124-125), new Principle IV bullet requiring a new plugin to state availability on both majors, new Principle V rule naming sbt 1.x as the publishing major, and the `project/build.properties` pointer (103-104). **Bump the version footer 1.1.5 → 1.2.0 with Last Amended = merge date** (MINOR: adds two normative MUSTs, removes no principle). Check `git log main -- .specify/memory/constitution.md` and open PRs first — a concurrent branch could claim the same number
- [X] T085 [P] Fix the two pre-existing inaccuracies these files carry, since the PR touches them anyway: `AGENTS.md:11` and `.specify/memory/constitution.md:111` claim CI is Temurin 21 while `ci.yml:82` is a `['17','21']` matrix
- [X] T086 [P] Add the sbt pin to `.scala-steward.conf` so the single-major sbt bump is not re-proposed (FR-018), while still allowing sbt 1.x patch bumps, and fix the stale comment at lines 2-3 referring to the retired "maintenance" milestone
- [X] T090 Reconcile FR-004 in `spec.md` and `plan.md`: there are **three** sbt version pins, not one — `project/build.properties`, `examples/scala-sbt-example/project/build.properties`, and the `--set SbtVersion` rendered into the CI template project. Restate the requirement as one pin per build, or as an explicit three-edit flip
- [X] T091 Update the four coverage-figure mirrors in one commit: `build.sbt:39`, `TESTING.md:151-152`, `.specify/memory/constitution.md:67`, `AGENTS.md:53`
- [X] T092 Document the FR-004 post-flip caveat in `README.md` Contributing and `AGENTS.md`: once `project/build.properties` names 2.x, sbt selects the native client from `build.properties` alone and a live server makes `--sbt-version 1.12.15` a **silent no-op** — run `sbt shutdown` or set `SBT_NATIVE_CLIENT=false` before switching down. The documented escape hatch back to sbt 1 otherwise stops working exactly when the flip happens
- [X] T093 (**verified 2026-08-20**: `Jmh/run` executes on BOTH majors — SyntaxBenchmark, 11 methods, comparable throughput/avgtime; no exemption needed, `Jmh` is added to the capability table as available on both) Resolve the `Jmh` capability gap (FR-005, SC-003): `sbt Jmh/run` is invoked only in `release.yml` jobs gated `if: ${{ false }}`, and only `Jmh/compile` has been verified on sbt 2. `Jmh` is a plugin-contributed config axis. Either verify `Jmh/run` on both majors and add it to the capability table in `data-model.md`, or record an explicit exemption — an unverified capability with no exemption violates SC-003
- [X] T094 Run every scenario in [quickstart.md](quickstart.md) end to end and correct any command that has drifted (notably Scenario 2/3's `test` → `testOnly` and Scenario 1's `print name` → `show sbtVersion`)
- [X] T095 (done: PR #320, milestone assigned, `check-linkage.sh --pr 320` → PASS) Assign the PR to the **active milestone — #13 `v1.26.0 — Perf: Templates & cookies`** (maintainer decision 2026-08-20: use the current milestone; AGENTS.md defines active as the lowest-numbered open milestone, confirmed as #13). Confirm PR ↔ issue ↔ milestone linkage before merge — the `check-linkage.sh` PreToolUse hook hard-rejects a PR without it. Re-check the number at PR time in case #13 closes first
- [ ] T096 **After** the PR merges to `main`, close [PR #319](https://github.com/galax-io/gatling-picatinny/pull/319) as superseded, with a comment stating that sbt 2.x is now supported as a verified secondary major and sbt 1.x remains the default pin (FR-018)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — **BLOCKS all user stories** (until it lands, sbt 2 cannot load the build at all)
- **US1 (Phase 3)**: Depends on Phase 2
- **US2 (Phase 4)**: Depends on US1 — the scheduled job cannot run `integration/testOnly` before the subproject exists
- **US3 (Phase 5)**: Depends on Phase 2 for the overlay bump; T073 adds a step to the workflow created in T053, so it follows Phase 4
- **Polish (Phase 6)**: Depends on all three stories; T091 depends on T042's measurement, T096 on merge

### Critical Ordering Hazards

1. **T030-T034 atomic.** Deleting `config("it")` before moving the sources loses 44 tests **silently** — no build error.
2. **T010 + T011 atomic.** Filters without plugin = build.sbt does not typecheck on either major.
3. **T012 after T010/T011.** scalafmt realigns unrelated lines; `scalafmtSbtCheck` fails otherwise.
4. **T036 + T037 + T032 atomic.** Aggregation without opt-outs pulls Docker into the unit gate, aborts MiMa build-wide, and would publish a second artifact.
5. **T014 before any sbt-2 test run.** Otherwise ~6 tests fail and get misattributed to the migration.
6. **T053 is all-or-nothing.** Its four anti-vacuity defences only work together — a scheduled job with three of them still passes having run nothing.
7. **T091 in one commit.** Four mirrors of one figure.
8. **T096 after merge**, never before.

### Parallel Opportunities

- Phase 1: T002, T003, T004 in parallel after T001
- Phase 2: the hygiene overlay (T020, T021) parallel with the build fixes (T013, T014)
- Phase 4: T058 is independent; T050-T052 (composite action) can proceed while T053-T056 (new workflow) are drafted
- Phase 5: T076 independent of everything else
- Phase 6: T080, T081, T082, T083, T085, T086 are separate files — and per the docs-per-file convention, separate commits

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup — capture the baseline
2. Phase 2 Foundational — sbt 2 can load the build
3. Phase 3 US1 — **STOP and VALIDATE**: full gate suite green on both majors, cold, with identical numbers
4. At this point the feature already delivers its core promise — flipping the default is one line, already proven green — with CI entirely unchanged. Phases 4-5 only add the mechanism that keeps it true over time

### Incremental Delivery

1. Setup + Foundational → sbt 2 reachable
2. + US1 → both majors verifiably green locally (MVP)
3. + US2 → sbt 2 proven daily and on build-definition PRs, and proven able to fail
4. + US3 → the consumer-facing overlay works on both
5. + Polish → docs, governance, PR #319 closed

### PR Shape

Per the project's single-PR-per-feature convention, spec artifacts and implementation ship together. Within that PR, keep one semantic commit per atomic group above, each green on its own (`sbt compile "Test/testOnly"` — note `testOnly`).

---

## Notes

- **`test` is `testQuick` on sbt 2** — the single most important fact in this file. Use `testOnly` everywhere, in CI, docs, hooks and scripts.
- `show` works on both majors; `print` is sbt-2-only. Never put `print` in a shared script.
- Verify cold, not warm: sbt 2's global disk cache survives `clean` and `rm -rf target`.
- The rtk proxy in some local environments returns false results for `diff` and `wc`; use `/usr/bin/diff` and `/usr/bin/wc` when a comparison matters.
- [P] tasks = different files, no dependencies
- Commit after each atomic group, never mid-group
