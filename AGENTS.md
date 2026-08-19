# gatling-picatinny — Agent Guide

Gatling DSL extension library: config, feeders, transactions, assertions, JWT, Redis, diagnostics, profiles, and shared utilities, with a Java/Kotlin-facing facade. Published library — treat all public Scala/Java APIs, DSL behavior, and serialized config/profile formats as compatibility-sensitive.

## Role

Principal Engineer: Scala 2.13, Gatling DSL, Java/Kotlin facade design, HTTP/Redis, load testing. Prefer small, clear, backward-compatible changes unless the task explicitly requires otherwise.

## Stack

Scala 2.13.18, **sbt 1.12.15 (default pin) / sbt 2.0.6 (verified secondary)**, Java 17 (compile target; CI runs on Temurin 17 and 21), Gatling 3.13.5 (`Provided`). PureConfig, Circe, json4s, Jackson, Scala Logging, Generex, JWT, fast-uuid. ScalaTest + JUnit (sbt-jupiter-interface), Testcontainers (Redis integration), JMH (benchmarks).

## Commands

Supported sbt majors: **1.12.15** (default — what a bare `sbt` uses, pinned in
`project/build.properties`) and **2.0.6** (secondary). Run any task on the secondary with
`sbt --sbt-version 2.0.6 <task>`. Use `testOnly`, never `test`: on sbt 2 `test` is `testQuick` and
reports success having run zero tests.

```bash
sbt scalafmtAll scalafmtSbt                                          # format
sbt scalafixAll scalafmtAll                                          # lint fix (then format; they converge)
sbt "scalafixAll --check"                                            # lint gate (CI-enforced; TESTING.md "Static analysis & gates")
sbt scalafmtCheckAll scalafmtSbtCheck compile "Test/testOnly" integration/testOnly  # verify
sbt compile "Test/testOnly"                                         # CI (unit only)
sbt integration/testOnly                                            # integration (Docker / Redis)
sbt Jmh/run                                                         # benchmarks
```

## Structure

`config/` simulation config loading, masking, defaults, typed params. `feeders/` random/faker/HTTP/CSV/Vault feeders + feeder syntax. `transactions/` protocol, actors, trackers, actions, builders, Java helpers. `templates/`, `utils/jwt/`, `assertions/`, `profile/` templating, JWT generation, NFR/assertion builders, profile DSL/runtime. `redis/`, `storage/`, `diagnostics/`, `utils/` Redis actions, session storage, startup diagnostics, cross-cutting utils. `src/main/java/.../javaapi` thin Java/Kotlin facade. `examples/` overlay projects (Scala sbt, Java Maven, Kotlin Gradle).

## Architecture

Scala DSL/runtime is the source of truth; Java/Kotlin facades stay thin and delegate. Facade reimplementing core logic = wrong:

```scala
// ✅ thin facade delegates to Scala core
object JHttpFeeder { def apply(url: String): FeederBuilder = ScalaHttpFeeder(url) }
// ❌ duplicates logic already in Scala core
object JHttpFeeder { def apply(url: String): FeederBuilder = { /* duplicate logic */ } }
```

Review runtime-sensitive behavior carefully: transaction boundaries, feeder determinism, JWT generation, Redis side effects, startup diagnostics, masking, profile expansion.

## Test Model

Authoritative: **[TESTING.md](TESTING.md)** (constitution §III). Test-first; assert exact values + ≥1 negative case; ScalaMock (not Mockito) for leaf deps only. Six layers, apply what fits:

1. Unit/functional (`Test`) — pure fns + HTTP (`HttpJsonFeeder`/`THttpClient`) via ScalaMock; no server in the library.
2. DSL/action component (conditional, `Test`) — `transactions/Mocks` ActorSystem harness; feeder-determinism + tx boundaries.
3. External integration (`integration` subproject) — Testcontainers Redis/Vault/JDBC; JWT/diagnostics non-container.
4. E2e — real `Simulation` driving picatinny DSL (feeders/JWT/transactions/converters) over real HTTP vs **WireMock** in `examples/`, `sbt 'Gatling/testOnly *'`. Assert RESPONSES with Gatling `check` (values round-trip via the mock echo); never `WireMock.verify`/re-decode the request (mock-testing-mock). WireMock overlay-only.
5. Compile guard (`Test`).
6. Facade delegation (`Test`, JUnit 5).

Coverage floor 75/66 (stmt/branch; measured 81.40-81.44/75.33-75.49 on 2026-08-20, majors agree exactly within a run; data-driven ratchet — TESTING.md "Coverage ratchet"). Every `/speckit-plan` fills the code-free "Test Model" table (gate).

## Boundaries

**Always:** format before commit, branch from `main`, keep commits semantic and green, preserve backward compat for published Scala/Java APIs and example overlays. `build.sbt`/`project/` = dependency truth, `project/build.properties` = the single default-sbt-major pin, `.github/workflows/` = CI/release truth.

**Ask first:** new deps or upgrades, changing public API signatures / DSL behavior / serialized config/profile formats, editing another repo, release/publish workflow changes. A new sbt plugin or build capability MUST state its availability on **both** supported majors; a single-major capability needs a recorded exemption (TESTING.md).

**Never:** force-push or commit to `main`, merge commits in PR branches (rebase only), commit broken code, opportunistic refactors outside scope, mock Gatling runtime where a real integration path exists.

## Milestones (ALWAYS)

Every piece of work is tied to a milestone. No exceptions unless explicitly told otherwise.

- **Every PR** must be assigned to the active milestone before merging. No milestone = do not merge.
- **Every issue** fixed by a PR must be closed when that PR lands on `main`. Do not leave completed issues open.
- **Spec work** (`specs/NNN-*/`) belongs to the milestone that owns the spec. Link the spec PR to the milestone immediately when creating it.
- **Active milestone** = the lowest-numbered open milestone that matches the current spec/plan. Check `gh api repos/galax-io/gatling-picatinny/milestones` if unsure.

## Commits & PRs

- **Spec-first.** `specs/NNN-*/` artifacts → `docs(speckit): add NNN-<feature> spec/plan/tasks` commit BEFORE any `feat`/`fix`. Never folded into implementation.
- **1 issue = 1 commit.** Each tracked GitHub issue maps to one semantic commit (`feat(scope): … (#NNN)`), green on its own (`sbt compile "Test/testOnly"` on the default major). Docs, tweaks, and out-of-scope improvements go in separate PRs — never mixed with issue commits.
- **Intent, not path.** No add-then-remove within a PR. Squash churn before review.
- **1 concern per PR.** Feature ≠ docs/README. Stack dependent PRs; update with `--force-with-lease`.
- **Idiomatic Scala.** `Try`/`Option`/`Either`, pattern matching, `flatMap`/`collect`. No `try/catch` for control flow, `== null`, `indexOf`/`substring`, or double conversions.

## Release Process (MANDATORY)

Trunk-based with release branches. Trunk is `main`; `release/*` branches are cut from `main` for stabilization. Pushing a `vX.Y.Z` tag on `main` or a `release/*` branch publishes to Maven Central (via sbt-ci-release / dynver) and creates a GitHub Release with git-cliff notes. **Official publication runs on sbt 1.x**, the default pin.

### Minor/Major release (e.g. 1.2.0, 2.0.0)

1. `git checkout -b release/X.Y.0 main` — cut release branch from `main`
2. `git push -u origin release/X.Y.0`
3. `git tag vX.Y.0` on the release branch
4. `git push origin vX.Y.0` — triggers release workflow

### Patch release (e.g. 1.2.1)

1. Fix lands on `main` first (via PR as usual)
2. `git cherry-pick <fix-sha>` onto `release/X.Y.0`
3. `git tag vX.Y.1` on the release branch
4. `git push origin vX.Y.1` — triggers release workflow

### Rules

- **Every minor version gets its own `release/X.Y.0` branch** — no exceptions
- **Tags ONLY on `release/*` branches or `main`** — release.yml validates this
- **Branch name must match tag version**: `release/1.2.0` → `v1.2.0`, `v1.2.1`, etc.
- **Never delete a release tag** after Sonatype deployment starts — creates stuck deployments
- **Never reuse a version number** — Sonatype Central rejects duplicates permanently
- **Before tagging**: every PR merged since the previous tag must be assigned to the milestone; every issue in the milestone whose fix is on `main` must be closed
- **Before tagging**: confirm the full gate suite is green on **both** supported majors (`sbt <task>` and `sbt --sbt-version 2.0.6 <task>`)
- **Before tagging**: review outstanding MiMa binary-compatibility warnings (`sbt mimaReportBinaryIssues || true` — `mimaFindBinaryIssues` is silent and looks clean even when it is not) — each must be fixed or acknowledged via a justified `mimaBinaryIssueFilters` entry with the constitution-mandated version bump
- **Before tagging**: run the dependency-hygiene report and triage findings (report-only, **sbt 1 only** — see TESTING.md "Static analysis & gates" for why):
  `sbt --batch --addPluginSbtFile=project/hygiene/plugins.sbt undeclaredCompileDependencies unusedCompileDependencies`
- **After releasing**: bump `mimaPreviousArtifacts` in `build.sbt` to the just-published version
