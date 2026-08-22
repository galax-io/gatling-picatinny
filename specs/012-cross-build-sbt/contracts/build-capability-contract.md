# Contract: Build Capability Surface

**Feature**: [012-cross-build-sbt](../spec.md) | **Phase**: 1

The build is an interface. Contributors, CI, and the release process all invoke it by name, and
this feature changes some of those names. This contract fixes what callers may rely on.

## C-1: Invocation is major-independent

Every command below MUST produce the same verdict whether run under sbt 1.12.15 or sbt 2.0.6, and
MUST be spelled identically in both cases. A caller never branches on the major.

| Capability | Contract command | Notes |
|---|---|---|
| Compile | `sbt compile` | |
| Unit tests | `sbt "Test/testOnly"` | **`test` is `testQuick` on sbt 2** and runs zero tests off a warm global cache |
| Integration tests | `sbt integration/testOnly` | **CHANGED** — was `sbt "IntegrationTest / test"`; `testOnly`, not `test` |
| Format check | `sbt scalafmtCheckAll scalafmtSbtCheck` | |
| Format fix | `sbt scalafmtAll scalafmtSbt` | |
| Lint check | `sbt "scalafixAll --check"` | |
| Lint fix | `sbt scalafixAll scalafmtAll` | |
| Coverage | `sbt clean coverage "Test/testOnly" "integration/testOnly" coverageOff coverageReport coverageAggregate` | **CHANGED**; `clean` is mandatory — warm runs drift (81.40/75.33 vs 81.44/75.49) |
| Binary compatibility | `sbt mimaReportBinaryIssues \|\| true` | advisory; the `\|\| true` is still required |
| Benchmarks | `sbt Jmh/run` | root project only; **unverified on sbt 2** — only `Jmh/compile` has been checked |
| Publish (local) | `sbt publishLocal` | |
| Publish (release) | `sbt ci-release` | official publication runs under sbt 1.x only |

**Selecting the secondary major**: prefix any command with the launcher flag —
`sbt --sbt-version 2.0.6 <task>`. No file is edited. Three caveats: `SBT_OPTS` containing
`-Dsbt.version=` silently outranks the flag; `--numeric-version`/`--version` ignore it (use
`show sbtVersion`, never `print`, which is sbt-2-only); and once `build.properties` names 2.x the
native client ignores it while a server is live.

## C-2: Breaking changes to existing callers

These are the only caller-visible breaks. Every occurrence in the repository must move in the same
pull request, or the documentation describes commands that no longer exist.

| Old | New | Known call sites |
|---|---|---|
| `sbt "IntegrationTest / test"` | `sbt integration/testOnly` | `.github/workflows/ci.yml:309`, `.github/workflows/release.yml:50` (comment), `TESTING.md:134`, `AGENTS.md:19,21`, `README.md:276`, `.specify/memory/constitution.md:125` |
| `sbt Test/test` / `sbt compile test` | `sbt "Test/testOnly"` | `.github/workflows/ci.yml:89`, `.github/workflows/release.yml:52`, `.githooks/pre-commit`, `AGENTS.md`, `.specify/memory/constitution.md:124` |
| `sbt Gatling/test` (overlay) | `sbt 'Gatling/testOnly *'` | `scripts/test-scala-sbt-template.sh:76`, `README.md:160`, `docs/examples.md:39`, `docs/configuration.md:148`, `TESTING.md:70,80,135`, `AGENTS.md:49` |
| `sbt clean coverage test "IntegrationTest/test" …` | `sbt clean coverage "Test/testOnly" "integration/testOnly" …` | `.github/workflows/ci.yml` (coverage job), `TESTING.md` CI-gates table |
| `sbt undeclaredCompileDependencies unusedCompileDependencies` | opt-in overlay procedure (C-4) | `AGENTS.md` Release Process, `TESTING.md` Static-analysis table |

## C-3: What MUST NOT change

- `src/main` and `src/test` layout, and every command that targets them.
- The library's `scalacOptions`, `javacOptions`, and Java 17 release target.
- Gatling's `Provided` scope and its 3.13.5 version.
- Published artifact coordinates: `org.galaxio %% gatling-picatinny`.
- MiMa's advisory (never-blocking) status and its existing `mimaBinaryIssueFilters` entries.
- The Java/Maven and Kotlin/Gradle overlays.

## C-4: Dependency-hygiene report (sbt 1 only)

The report is no longer available from a bare invocation, because its plugin has no sbt 2 build and
its settings would break the sbt 2 load (research D-06). Contract for the replacement:

- The plugin line and the five `…DependenciesFilter` settings live under `project/hygiene/`, outside
  anything sbt loads by default.
- A single documented command applies the overlay, runs the report under sbt 1, and removes the
  overlay again — leaving `git status` clean whether it succeeds or fails.
- The report output is unchanged in content from today's.
- It remains **report-only**. It MUST NOT become a CI gate on either major.

## C-5: CI check-name contract

The existing pull-request checks are **unchanged and not renamed** — they remain sbt 1 only. The
secondary major appears as one additional, separately named job whose name states the major, so a
reader can tell at a glance what it covers:

```text
sbt 2 compatibility (scheduled)      # daily + workflow_dispatch
sbt 2 compatibility (build change)   # pull_request, build-definition paths only
```

A failure MUST name the major and the failing gate without requiring log inspection (FR-011). A
scheduled failure MUST notify a maintainer — a red run on a schedule nobody watches is not a signal.

*(An earlier draft of this contract required every job to be renamed with an `(sbt N)` suffix under
a per-major matrix. That matrix was scoped out on 2026-08-20; the renames are no longer needed,
which also removes the `upload-artifact` duplicate-name hazard entirely.)*

## C-6: Evidence-path contract

Any CI step that collects build output MUST use globs that resolve under **both** layouts:

| Artifact | sbt 1 path | sbt 2 path | Required glob |
|---|---|---|---|
| JUnit XML | `target/test-reports/*.xml` | `target/out/jvm/scala-2.13.18/<proj>/test-reports/*.xml` | `**/test-reports/*.xml` |
| Compiled classes | `target/scala-2.13/classes` | `target/out/jvm/scala-2.13.18/<proj>/classes` | derive, never hardcode |
| Scoverage report | `target/scala-2.13/scoverage-report` | under `target/out/jvm/...` | derive, never hardcode |

A glob that silently matches nothing is worse than a failing step: the job goes green and the
test-results publisher reports zero tests, which is a false pass that defeats the whole feature.
