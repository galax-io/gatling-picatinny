# Implementation Plan: Cross-build on sbt 1 and sbt 2

**Branch**: `012-cross-build-sbt` | **Date**: 2026-08-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-cross-build-sbt/spec.md`

## Summary

Make one unmodified source tree build, test, gate and publish under **both** sbt 1.x and sbt 2.x.
sbt 1.12.15 stays the declared default pin and the publishing major (FR-017); sbt 2.0.6 is carried
as a verified secondary major, selected per-invocation with `sbt --sbt-version 2.0.6`. PR
[#319](https://github.com/galax-io/gatling-picatinny/pull/319) is closed as superseded.

**Scope decision (2026-08-20)**: dual-major support here is **insurance against a future forced
migration**, not a capability anything currently depends on. Nobody is blocked today — the sbt
launcher is version-agnostic — and the published artifact is identical either way. So sbt 2 is
verified by a **daily scheduled run plus a path filter on build-definition pull requests**, not by a
full per-pull-request matrix. That keeps the property this feature exists for (flipping the default
is one line, already proven green) without doubling CI compute permanently to protect something
nothing consumes. Promoting the scheduled run to a full matrix later is a workflow change, not a
rework.

The approach, established empirically in [research.md](research.md), is **convergence rather than
conditionals**: rewrite each divergent build construct into the form both majors accept, and admit
a per-major conditional only where no shared form exists. That reduces to exactly one exemption
(`sbt-explicit-dependencies`, which publishes no sbt 2 artifact).

Three findings shape the work:

1. **The library source needs no change.** `Test/compile` already succeeds under sbt 2.0.6 with the
   full `-Werror` flag set — 130 main sources, zero errors (D-11). Scope is build files, CI,
   overlay, docs.
2. **The `it` configuration is the one hard blocker.** `config("it") extend Test` still *compiles*
   under sbt 2 but sbt 2 will not resolve `it` as a key axis (`Not a valid key: it`), so
   integration tests are unreachable there. Migrating them to an `integration` subproject — a
   construct identical on both majors — is mandatory and is the largest work item (D-07).
3. **Nearly the whole plugin set is already sbt-2 ready.** Ten of eleven plugins publish an
   `_sbt2_3` artifact at the exact version pinned today; `addSbtPlugin` picks the right suffix
   automatically (D-03).
4. **`test` IS `testQuick` on sbt 2 — and that is the biggest risk in the feature** (D-15).
   `<proj>/test` returns `[success]` having run zero tests, off a global cache that survives
   `clean`. Every invocation must use `testOnly`. Combined with a report glob that matches nothing
   under sbt 2's layout, an unfixed CI would go green having cross-built nothing.
5. **sbt 2 does not copy resources into `classDirectory`, and that breaks the library at runtime**
   (D-16). `Test/products` is `[test-classes, src/test/resources]` on sbt 2 versus `[test-classes]`
   on sbt 1, so `Templates` hands Gatling an absolute path inside its configured resources
   directory and Gatling refuses it. `products` must be pinned; `exportJars := false` is also
   correct but is not what fixes it.
6. **The overlay is not blocked** (D-17). `Gatling` is a custom configuration too, but unlike `it`
   it resolves fine on sbt 2 — it needs only the `gatling-sbt` bump. The real overlay blocker is
   that CI never builds those files at all (D-13).

**Verified end-to-end**: a full prototype of the restructure ran the complete gate suite from cold
on both majors with identical results — 725/824 unit tests, 44 integration tests against real
Testcontainers, and coverage of **81.44% stmt / 75.49% branch on both**, higher than the 77.75/68.29
recorded in `build.sbt`. The migration costs no coverage.

## Technical Context

**Language/Version**: Scala 2.13.18 (library, unchanged); build definitions compile under Scala
2.12.21 on sbt 1 and Scala 3.8.4 on sbt 2

**Primary Dependencies**: sbt 1.12.15 (default) and sbt 2.0.6 (secondary); 11 sbt plugins; Gatling
3.13.5 `Provided` (unchanged)

**Storage**: N/A

**Testing**: ScalaTest + ScalaMock (unit), JUnit 5 via sbt-jupiter-interface (facade),
Testcontainers (integration), Gatling + WireMock (overlay e2e), JMH (benchmarks)

**Target Platform**: JVM — Java 17 compile target, CI on Temurin 17 and 21 (unchanged)

**Project Type**: Published Scala library with a Java/Kotlin facade and three example overlays

**Performance Goals**: N/A — no runtime behaviour changes. CI cost rises by roughly **one extra full
run per day** (~2700s compute) plus an sbt 2 leg on build-definition pull requests only. Per-pull-
request cost for ordinary changes is **unchanged**. A full per-major matrix was scoped out on
2026-08-20 (research D-14 measured it at ~2x compute for insurance nothing currently depends on)

**Constraints**: Single unmodified tree (FR-001); identical gate verdicts across majors (FR-007);
equivalent published artifact (FR-008); no library source change (FR-009); default-major switch is
one line per build (FR-004); the scheduled run must exercise the full gate suite, never a cheap
subset (FR-013)

**Scale/Scope**: 2 root build files, 2 meta-build files, 4 integration test sources relocated,
1 CI workflow + 1 composite action, 1 example overlay, 4 documentation files

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

This feature changes how the project is built, not what it does at runtime. The honest reading of
Constitution III here is that **the existing suites are the test**: the requirement is that each
layer, unchanged in content, produces the same verdict under both majors. Rows below therefore
name the real case each requirement must survive and the layer that proves it. Requirements whose
subject is CI configuration or documentation are verified by the CI run and by a reader following
the instructions — those rows say so plainly rather than inventing a Scala test that would assert
nothing real.

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | A clean checkout is loaded by sbt 1.12.15 and by sbt 2.0.6 with no tracked file edited between the two runs | Compile Guard | Load the build under each major and assert the root project resolves and its name is exactly `gatling-picatinny` under both; negative case — assert `git status --porcelain` is empty after both runs, so a load that silently rewrites a tracked file fails the check |
| FR-002 | A contributor runs bare `sbt` and gets the documented default | Compile Guard | Assert the sbt version reported by a bare invocation is exactly the version in `project/build.properties`, and that it is 1.x; negative case — a bare run that reports 2.x means the pin and the documentation disagree |
| FR-003 | A reader needs to know which majors are supported | Compile Guard | Assert the supported-majors list in the docs names both majors with a minimum tested version each, and that each named version matches what CI actually runs; negative case — a version present in docs but absent from the CI matrix fails |
| FR-004 | The maintainer flips the default to sbt 2 later | Compile Guard | Change only the `sbt.version=` line, assert the full gate suite still passes on both majors, then revert; negative case — if any second file must also change, the requirement is unmet |
| FR-005 | Every declared capability is exercised on both majors | Unit/Functional + External Integration | Run compile, unit tests, integration tests, format check, lint gate, coverage and publishLocal under each major; assert each produces a result rather than an "unknown key"/"unresolved" failure. Negative case is the one already observed: under sbt 2 an `it/…` invocation returns `Not a valid key: it` — after the subproject migration that invocation must not exist and `integration/test` must resolve on both |
| FR-006 | The hygiene report has no sbt 2 build | Compile Guard | Assert the exemption record names the capability, the unsupported major and the reason, and that the opt-in hygiene procedure still produces a report under sbt 1; negative case — an exemption entry with no reason, or a capability missing on one major with no entry at all |
| FR-007 | A deliberate violation of each enforced gate | Unit/Functional | Seed a badly formatted source, an unused import, and a failing assertion in turn; assert each gate fails under **both** majors with the same violation reported. Negative case is the dangerous one — a gate that passes on one major while failing on the other means it is inert there and the run is a false green |
| FR-008 | Artifacts published from each major at the same commit | Compile Guard | `publishLocal` under each major; assert identical coordinates, identical POM dependency set and scopes (Gatling entries still `provided`), and equivalent class content; negative case — any dependency appearing in one POM but not the other |
| FR-009 | The library compiles identically regardless of build tool | Unit/Functional | Assert `Test/compile` succeeds under both with the strict flag set unchanged and produces classes targeting Java 17; negative case — a diagnostic raised under one major and not the other means the flags are not reaching the compiler identically. Already observed passing under sbt 2 (D-11) |
| FR-010 | A day passes with no build-definition change | Compile Guard | Assert the scheduled sbt 2 run executed against the default branch within the last 24 hours and is separately identified as an sbt 2 run; negative case — a schedule that silently stopped firing (a disabled or never-triggered workflow reads identically to "nothing broke") |
| FR-011 | A change breaks exactly one major | Compile Guard | Introduce a construct valid only under sbt 1; assert the scheduled run fails, names sbt 2 and the failing gate, and notifies a maintainer; negative case — a green run because the sbt 2 job uploaded no reports and reported zero tests, which is the failure mode this whole feature is most likely to ship with |
| FR-012 | A pinned plugin or sbt version moves | Compile Guard | Change `project/build.properties` (then `plugins.sbt`, then a root `*.sbt`) and assert the sbt 2 verification runs on that pull request; negative case — a path filter that misses one of the build-definition files, so the one change class most likely to break sbt 2 is the one that skips the check |
| FR-013 | The scheduled run executes | Compile Guard | Assert compile, unit tests, integration tests, format, lint, coverage and the MiMa report all run in the sbt 2 job; negative case — any gate omitted to save schedule time, or a run reporting zero tests, either of which makes the job read as coverage that does not exist |
| FR-014 | The overlay is built under each major | Full Gatling e2e | Assert the overlay's build loads under both majors against the locally published artifact; negative case — a plugin or config in the overlay that resolves on only one major |
| FR-015 | A user copies the overlay and runs the load test | Full Gatling e2e | Run the overlay's existing end-to-end scenario against WireMock under each major; assert Gatling `check`s on the RESPONSES pass — feeder value and JWT round-tripped through the echo — and `global.failedRequests.count.is(0)` holds under both. Never assert on what the mock received. Negative case — a scenario that reports zero requests executed, which would pass assertions vacuously |
| FR-016 | The Java/Maven and Kotlin/Gradle overlays | Full Gatling e2e | Assert both still build and run their scenarios unchanged; negative case — any diff to their build files, which would mean the feature leaked outside its scope |
| FR-017 | Release publication | Compile Guard | Assert the release path runs under sbt 1 and that the documented default and the pin agree; negative case — release tooling silently invoked under sbt 2 |
| FR-018 | PR #319 | Compile Guard | Assert the pull request is closed with a comment stating the supersession, and that dependency automation no longer proposes the same single-major sbt bump; negative case — the next bot run re-opens an equivalent PR |
| FR-019 | A contributor reads the docs | Compile Guard | Assert contributor docs state both majors, which one a bare run uses, and the exact command for the other; negative case — a documented command that fails when pasted into a clean checkout |
| FR-020 | A release engineer reads the release process | Compile Guard | Assert the release documentation names the major that performs the official publish; negative case — the process describing a publish step without naming a major |
| FR-021 | A new build plugin is proposed | Compile Guard | Assert the contribution guidance requires stating availability on both majors; negative case — guidance that mentions only the default major |

**Coverage note (TESTING.md ratchet)**: moving integration tests from the `it` configuration into
an `integration` subproject changes what `coverageReport` sees. The measured figure MUST be re-taken
via `coverageAggregate` across both projects on both majors, and the floors in `build.sbt` reset
just under the new measurement with the value and date recorded in the comment. Floors only move
up; if the restructure appears to lower measured coverage, that is a signal the aggregation is
misconfigured, not a licence to lower the floor.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — No facade or DSL code is touched. The Java/Kotlin facade
  is unchanged; FR-009 and D-11 confirm no library source change is required.
- [x] **II. Backward Compatibility** — No public API, DSL behaviour, or serialized config/profile
  format changes. One published-metadata change is in scope and flagged: the POM `licenses` entry
  moves from `"Apache 2"` + an `http://` URL to the canonical values `License.Apache2` carries
  (D-05). This is POM metadata, not an API surface, so MiMa is unaffected — but it is a visible
  change in what consumers download and MUST be called out in the PR description.
- [x] **III. Test Discipline** — Test Model above is filled with a row per FR, real cases, valid
  layers and code-free sketches. No new mocking is introduced; the Gatling runtime is still not
  mocked. Coverage floors are re-measured, never lowered, per the ratchet note above.
- [x] **IV. Small, Focused Changes** — No opportunistic refactors. The `integration` subproject
  restructure is large but is forced by FR-005 and unavoidable (D-07); it is justified in Complexity
  Tracking. One dependency version moves (`gatling-sbt` 4.18.3 → 4.19.1) — a build plugin, not a
  runtime dependency, required because 4.18.3 has no sbt 2 artifact. **This bump needs maintainer
  authorization** under Constitution IV.
- [x] **V. Release Integrity** — Not a release PR. The release *documentation* changes (FR-020) to
  name sbt 1 as the publishing major; branch/tag rules are untouched.

## Project Structure

### Documentation (this feature)

```text
specs/012-cross-build-sbt/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output — 11 decisions, all empirically grounded
├── data-model.md        # Phase 1 output — supported majors, capabilities, exemptions
├── quickstart.md        # Phase 1 output — how to verify both majors locally
├── contracts/           # Phase 1 output — build-capability and CI contracts
├── checklists/
│   └── requirements.md  # Spec quality checklist (16/16)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
project/
├── build.properties            # sbt.version=1.12.15 — THE single default-major pin (FR-004)
├── plugins.sbt                 # gatling-sbt 4.18.3 → 4.19.1; sbt-explicit-dependencies removed
├── Dependencies.scala          # 11 module lines across 7 vals: `"test,it"`/`"it"` → `Test`;
│                               # new integrationTesting bundle for testcontainers + jdbcDrivers
└── hygiene/
    ├── plugins.sbt             # NEW — addSbtPlugin + unmanagedSources injection (opt-in, sbt 1)
    └── HygieneFilters.scala    # NEW — AutoPlugin carrying the 5 dependency-hygiene filters
└── hygiene/                    # NEW — opt-in, sbt-1-only dependency-hygiene overlay (D-06)

build.sbt                       # custom `it` config deleted; `integration` subproject added via
                                # .aggregate(LocalProject("integration")) + aggregate opt-outs;
                                # hygiene filters removed; exportJars := false + products overrides
                                # ADDED (absent at HEAD); MiMa/JMH stay on root
publish.sbt                     # licenses := List(License.Apache2) (D-05)

integration/                    # NEW subproject — replaces the `it` configuration (D-07)
└── src/test/
    ├── scala/org/galaxio/...   # MOVED from src/it/scala (4 sources)
    └── resources/              # or inherited from root via "test->test"

src/                            # UNCHANGED — main/, test/, and src/it/ removed after the move

.github/
├── actions/sbt-setup/action.yml  # NEW `sbt-version` input; sbt major in the cache key
└── workflows/ci.yml              # sbt-major matrix on format, lint, test, coverage,
                                  # redis-integration, binary-compat; report globs widened (D-09)

examples/scala-sbt-example/
├── project/build.properties      # UNCHANGED — launcher flag overrides the pin
├── project/plugins.sbt           # gatling-sbt → 4.19.1 (the ONLY overlay change needed)
└── build.sbt                     # UNCHANGED — verified loading under both majors

scripts/test-scala-sbt-template.sh # --set SbtGatlingVersion=4.19.1; Gatling/test → 'Gatling/testOnly *'
.githooks/pre-commit               # `test` → `testOnly`, or delete (tracked but unwired)
.gitignore                         # add /integration/target/ (existing /target/ is root-anchored)

.scala-steward.conf             # pin sbt to the 1.x line so #319 is not re-proposed (FR-018)
AGENTS.md, TESTING.md, README.md, .specify/memory/constitution.md   # supported majors, commands,
                                                                    # publishing major (FR-019/020/021)
```

**Structure Decision**: The repository becomes a two-project sbt build — `root` (the published
library, unchanged in content) and `integration` (Testcontainers-backed tests, `publish / skip`).
This is not an sbt-2 accommodation: a plain subproject is the construct both majors implement
identically, and it is the migration path sbt itself documents for the removed custom-configuration
axis. Everything else in the tree keeps its current shape; `src/main` and `src/test` do not move.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `integration` subproject restructure — larger than the "minimal change" Constitution IV defaults to | FR-005 requires integration tests to work on both majors. sbt 2 will not resolve the custom `it` configuration as a key axis (`Not a valid key: it`, D-07), so the tests are simply unreachable there. A separate subproject is the replacement sbt documents, and it behaves identically on sbt 1 | *Keep `it`, run integration tests on sbt 1 only* — violates FR-005 and leaves a gate inert on one major (FR-007). *Custom config on sbt 1, subproject on sbt 2* — two structures in one tree, violates FR-001. *Fold into `Test` behind a tag* — drags Docker into the unit gate and breaks TESTING.md's layer model on both majors |
| Dependency-hygiene report leaves the always-loaded build (D-06) | `sbt-explicit-dependencies` publishes no sbt 2 artifact at any version, and `build.sbt` references its keys symbolically, so the build file fails to compile under sbt 2 with the plugin absent. Both plugin and filters must move out | *Conditional `addSbtPlugin`* — does not help; the `build.sbt` filter settings are typechecked regardless of which branch would run. *Drop the report* — discards a gate the project deliberately added (#276). The report is already report-only and manual before releases, so an opt-in step costs nothing that was automated |
| `gatling-sbt` 4.18.3 → 4.19.1 — a version bump inside a feature PR | 4.18.3 has no `_sbt2_3` artifact; 4.19.1 is published for both majors, so one version serves both and no conditional is needed | *Conditional per-major plugin version* — adds the only kind of drift-prone construct this plan is built to avoid, to save a patch-level bump of a build plugin that does not ship to consumers |
