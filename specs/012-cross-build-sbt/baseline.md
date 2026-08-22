# Baseline: state at HEAD before the cross-build work

**Feature**: [012-cross-build-sbt](spec.md) | **Captured**: 2026-08-20
**Commit**: `60b176e` (branch `012-cross-build-sbt`) | **sbt 1.12.15, Homebrew Java 17.0.10, Docker 25.0.2**

Every "is it still the same?" question later in this feature compares against these numbers.
Tasks T001–T004.

## T001 — Coverage and test counts (sbt 1, old `it` configuration)

```text
sbt clean coverage Test/test "IntegrationTest / test" coverageOff coverageReport
```

| Metric | Value |
|---|---|
| Integration suites | 4 |
| Integration tests | **44** succeeded, 0 failed, 0 canceled, 0 ignored |
| **Statement coverage** | **81.44%** |
| **Branch coverage** | **75.49%** |

**Finding that corrects the plan**: the comment in `build.sbt` records
`77.75% stmt / 68.29% branch, measured 2026-07-04`. The tree at HEAD measures **81.44 / 75.49**
*before any change from this feature*. The recorded figure is simply stale — code has landed since
July.

This matters for T042/T043: the `integration` subproject migration must be judged **coverage-neutral**
(81.44/75.49 → 81.44/75.49). An earlier prototype compared its post-migration measurement against
the stale comment and concluded the migration *raised* coverage. It does not; the comment was just
out of date, independently of this work.

The floors in `build.sbt` (75 / 66) are unchanged and remain satisfied with room to spare.

## T002 — Dependency-hygiene report (the exact output the opt-in overlay must reproduce)

```text
sbt undeclaredCompileDependencies unusedCompileDependencies
```

```text
[warn] gatling-picatinny >>> The project depends on the following libraries for compilation
[warn] but they are not declared in libraryDependencies:
[warn]  - "com.fasterxml.jackson.core" % "jackson-databind" % "2.22.2"

[warn] gatling-picatinny >>> The following libraries are declared in libraryDependencies
[warn] but are not needed for compilation:
[warn]  - "com.fasterxml.jackson.core" % "jackson-core" % "2.22.2"
```

**Exactly two findings.** The `project/hygiene/` overlay (T020–T023) must reproduce these and
nothing else — in particular it must not emit the shapeless / cats / redisclient / gatling-redis /
jmh-generator findings that the five filter settings suppress. Seven spurious findings is the known
failure mode of shipping `--addPluginSbtFile` without the filters.

## T003 — Published artifact (for the FR-008 / SC-005 comparison)

```text
sbt 'set ThisBuild / version := "0.0.0-baseline"' publishLocal
```

POM saved to the scratchpad as `baseline.pom`.

| Property | Value |
|---|---|
| Coordinates | `org.galaxio:gatling-picatinny_2.13` |
| Total dependencies | **53** |
| `provided` | **10** — all 9 `io.gatling` entries + `com.eatthepath:fast-uuid` |
| `test` | 12 |
| compile (no scope element) | 31 |

Current `<licenses>` block — this is the one element expected to change (T013):

```xml
<licenses>
    <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
    </license>
</licenses>
```

After T013 it becomes `Apache-2.0` / `https://www.apache.org/licenses/LICENSE-2.0.txt` — the
canonical SPDX identifier over https. Everything else in the POM must be byte-identical, and in
particular **all 10 `provided` entries must stay `provided`** (constitution: Gatling is the host
runtime, never bundled).

No `postgresql` and no `testcontainers` entry may appear after the migration either — today they are
`it`-scoped and absent from the POM; afterwards they live on the `integration` subproject, which
sets `publish / skip := true`.

## T004 — Branch protection does not pin CI job names

```text
gh api repos/galax-io/gatling-picatinny/branches/main/protection   → 404 "Branch not protected"
gh api repos/galax-io/gatling-picatinny/rulesets                   → 15574909 "main" (active)
   rules: ["deletion", "non_fast_forward"]
```

**No `required_status_checks`.** Renaming or adding CI jobs cannot silently un-gate the repository.

With the per-major matrix scoped out (2026-08-20) no existing job is renamed anyway, so this is
confirmation rather than a prerequisite — but it is worth recording, because the reflex worry when
a plan proposes touching every job name is exactly this, and re-deriving the answer costs an API
call nobody remembers to make.


---

## Post-migration comparison (recorded 2026-08-20, after Phases 1-3)

Everything below was measured cold (`clean`, and a fresh `--sbt-cache` on sbt 2).

| Metric | sbt 1.12.15 | sbt 2.0.6 | vs baseline |
|---|---|---|---|
| Unit tests | 725 ScalaTest / 824 total, 0 failed | 725 / 824, 0 failed | unchanged |
| Integration tests | 44, 0 failed | 44, 0 failed | unchanged |
| Statement coverage | 81.40-81.44% | same as sbt 1, exactly | **identical within each run** |
| Branch coverage | 75.33-75.49% | same as sbt 1, exactly | **identical within each run** |
| `scalafixAll --check` | pass | pass | unchanged |
| `scalafmtCheckAll` + `scalafmtSbtCheck` | pass | pass | unchanged |
| Full gate exit code | 0 | 0 | — |

**On the coverage range**: two independent cold full-gate runs produced 81.44/75.49 and 81.40/75.33.
In both runs the two majors reported the *same* figure as each other to the digit; the variance is
between runs, not between majors. So the durable assertion is "the majors agree", not "coverage
equals 81.44" — a constant would flap. Floors of 75/66 have ample headroom either way.

**Seeded-violation parity (SC-002)**: an unused import injected into a *relocated* source
(`integration/src/test/.../RedisIntegrationSpec.scala`) failed on **both** majors with byte-identical
diagnostics — same file, line 3, col 33, `Unused import`, `No warnings can be incurred under -Werror`.
This is the check that proves the subproject did not quietly lose the compiler contract it had under
the `it` configuration.

### Published artifact delta

| | baseline | after | note |
|---|---|---|---|
| artifacts published | 1 | **1** | `publish / skip := true` on `integration` verified — no second artifact |
| total dependencies | 53 | 52 | see below |
| `provided` | 10 | **10** | unchanged — every `io.gatling` entry plus fast-uuid stays `provided` |
| `test` | 12 | 11 | see below |

Two POM changes, both intended:

1. **`<licenses>`** — `Apache 2` / `http://…` → `Apache-2.0` / `https://…` (the canonical SPDX
   identifier over https). Authorized 2026-08-19.
2. **`testcontainers-scala-scalatest_2.13` no longer appears.** It was declared `"test,it"`, so the
   `test` half put it in the published POM; it now lives only on the `integration` subproject.
   Test-scoped dependencies are not transitive for consumers, so nothing downstream changes — but
   it is a real difference in published output and belongs in the PR description alongside the
   licence change. `postgresql` was already `it`-only and was never in the POM.

No other dependency, scope, or coordinate changed.


---

## Anti-vacuity demonstration (T059 / SC-007 / contract M-4), 2026-08-21

A gate never observed failing has not been shown to work. Demonstrated on a throwaway branch
(`chore/verify-sbt2-gate-fails`, deleted afterwards) so PR #320's own check history stays clean.

**The break**: removed the `Compile/Test products` pin. That is a genuine sbt-2-only defect — sbt 1
copies resources into `classDirectory` regardless, sbt 2 does not, so `TemplatesSpec` hands Gatling
an absolute path inside its configured resources directory.

**Result** — run [32450643390](https://github.com/galax-io/gatling-picatinny/actions/runs/32450643390):

| | outcome |
|---|---|
| sbt 2 (CI) | **FAILED** — 723 passed / **2 failed**, `TemplatesSpec`, `... is incorrect`. Job `sbt 2 compatibility (build change)`; failing step `Full gate suite (unit + integration + coverage + MiMa)` |
| sbt 1 (local, same commit) | **PASSED** — 725 succeeded / 0 failed, 824/824 |

Both halves of FR-011 hold: the failure is detected, and it is attributed to the right major without
opening logs. The `Test results (sbt 2)` check also went red, confirming the report path is wired.
