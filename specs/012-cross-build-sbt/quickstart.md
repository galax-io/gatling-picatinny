# Quickstart: Verifying the sbt 1 / sbt 2 cross-build

**Feature**: [012-cross-build-sbt](spec.md) | **Phase**: 1

How to prove locally that both majors work. Every command here was run against this repository
while producing [research.md](research.md); the "before" outputs are the real ones observed on
2026-08-19 with sbt launcher 2.0.6 on Java 17.

> **Before you run anything**: on sbt 2, `test` is `testQuick` and will report `[success]` having
> run **zero** tests, off a global cache that survives `clean` and `rm -rf target`. Every command
> below uses `testOnly` for that reason. If you substitute `test`, your verification is worthless.

## Prerequisites

- A Coursier- or `sbt/setup-sbt`-installed **sbt launcher** (any recent version). The launcher is
  not the build tool — it downloads whichever sbt the build asks for.
- Java 17 or 21 (Temurin in CI).
- Docker, for the integration and coverage scenarios only.
- A clean checkout: `git status --porcelain` must be empty before you start, and after.

Confirm the launcher can reach both majors:

```bash
sbt --script-version
```

## Scenario 1 — Both majors load the same tree (FR-001, FR-002, SC-001)

```bash
sbt "show sbtVersion" && sbt --sbt-version 2.0.6 "show sbtVersion"
```

**Expected**: the first prints `1.12.15`, the second `2.0.6`. Use `show`, not `print` — `print`
is sbt-2-only and fails on the sbt-1 leg. Do **not** use `--numeric-version` or `--version` as the
check: both ignore the flag and report the `build.properties` value.

Then confirm nothing was rewritten — this is the actual FR-001 assertion, not the load itself:

```bash
git status --porcelain
```

**Expected**: empty output.

**Before this feature** the second command fails during meta-build resolution:

```text
not found: .../gatling-sbt_sbt2_3/4.18.3/gatling-sbt_sbt2_3-4.18.3.pom
not found: .../sbt-explicit-dependencies_sbt2_3/0.3.1/...pom
```

## Scenario 2 — Compile and unit tests on both (FR-005, FR-009)

```bash
sbt "Test/testOnly"
```

```bash
sbt --sbt-version 2.0.6 "Test/testOnly"
```

**Expected**: same test count, same result. Compilation runs with the unchanged strict flag set
(`-Xlint:_,-infer-any -Wunused -Wdead-code -Werror`) in both cases.

**Measured 2026-08-20**: 725 ScalaTest / 824 total, 0 failures — **identical on both majors**.

**Already verified**: `Test/compile` under sbt 2.0.6 succeeds today with the D-04/D-05/D-06 fixes
applied — 107 Scala + 23 Java main sources, 46 Scala + 11 Java test sources, zero errors
(research D-11). What is *not* yet proven is that the tests pass; that is what this scenario adds.

## Scenario 3 — Integration tests on both (FR-005, D-07)

Requires Docker.

```bash
sbt integration/testOnly
```

```bash
sbt --sbt-version 2.0.6 integration/testOnly
```

**Expected**: the Testcontainers-backed Redis, Vault and JDBC suites run and pass under both.

**Measured 2026-08-20**: 4 suites / 44 tests / 0 failures — identical on both majors.

**Before this feature** the sbt 2 form is impossible — the old invocation fails at key resolution:

```bash
sbt --sbt-version 2.0.6 "show it/scalaSource"
```

```text
[error] Not a valid key: it
```

This is the blocker that forces the `integration` subproject migration. If this scenario passes on
both majors, the largest risk in the feature is retired.

## Scenario 4 — Gate parity, including a seeded failure (FR-007, SC-002)

On a clean tree, both majors must agree that there is nothing to report:

```bash
sbt scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check"
```

```bash
sbt --sbt-version 2.0.6 scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check"
```

**Expected**: both pass with no violations.

Now the part that actually proves the gates are live rather than inert. Introduce one unused import
into any file under `src/main/scala`, then re-run both commands.

**Expected**: **both** majors fail, and both name the same file and rule. A gate that fails on one
major and passes on the other is the defect FR-007 exists to catch — a green run on the secondary
leg that proves nothing.

Revert the seeded violation and confirm `git status --porcelain` is empty again.

## Scenario 5 — Coverage on both (FR-005, ratchet)

Requires Docker.

```bash
sbt clean coverage "Test/testOnly" "integration/testOnly" coverageOff coverageReport coverageAggregate
```

```bash
sbt --sbt-version 2.0.6 --sbt-cache /tmp/sbt2-cold clean coverage "Test/testOnly" "integration/testOnly" coverageOff coverageReport coverageAggregate
```

**Expected**: both report the same aggregated statement and branch percentages, and both satisfy the
floors in `build.sbt`.

**Measured 2026-08-20**: **81.40-81.44% stmt / 75.33-75.49% branch**, against floors of 75/66.
Within any one run the two majors agree **exactly**; across runs the figure moves slightly even with
`clean`. So assert "the two majors agree", not "the number equals X". This matches the pre-migration
figure at HEAD — the `integration` subproject migration is coverage-neutral.

**Measured**: 81.44% stmt / 75.49% branch, identical on both majors from cold — **higher** than the
77.75/68.29 recorded in the `build.sbt` comment. The subproject migration costs no coverage.

**Watch for two traps**: warm runs drift (81.40/75.33 observed), so `clean` is mandatory or a parity
gate will flap. And sbt 2's action cache does not restore scoverage's `scoverage-data/` directory,
so a warm sbt-2 coverage run dies with `FileNotFoundException: …/scoverage.measurements.*` — hence
the fresh `--sbt-cache` above.

## Scenario 6 — Published artifact equivalence (FR-008, SC-005)

```bash
sbt publishLocal && cp -r ~/.ivy2/local/org.galaxio /tmp/picatinny-sbt1
```

```bash
sbt --sbt-version 2.0.6 publishLocal && cp -r ~/.ivy2/local/org.galaxio /tmp/picatinny-sbt2
```

Then compare the POMs:

```bash
diff -r /tmp/picatinny-sbt1 /tmp/picatinny-sbt2
```

**Expected**: identical coordinates and an identical dependency set with identical scopes — in
particular every `io.gatling` entry still `provided`.

**Expected difference to accept**: the `<licenses>` block. This feature changes it from
`"Apache 2"` + `http://www.apache.org/licenses/LICENSE-2.0.txt` to the canonical values that
`License.Apache2` carries (research D-05). That change appears on **both** majors, so it must be
identical between the two POMs — it is a change versus the previous release, not a difference
between majors.

## Scenario 7 — Example overlay on both (FR-014, FR-015)

```bash
sbt 'set ThisBuild / version := "0.0.0-ci-local"' publishLocal
```

Then, from `examples/scala-sbt-example`:

```bash
PICATINNY_VERSION=0.0.0-ci-local sbt 'Gatling/testOnly *'
```

```bash
PICATINNY_VERSION=0.0.0-ci-local sbt --sbt-version 2.0.6 'Gatling/testOnly *'
```

**Expected**: the end-to-end scenario runs against WireMock under both majors and Gatling's
`check`s on the responses pass — the feeder value and the JWT round-trip through the echo, and
`global.failedRequests.count.is(0)` holds.

**Watch for** a run that reports zero requests executed: assertions pass vacuously in that case, so
confirm the request count is non-zero before believing a green result.

## Scenario 8 — Dependency-hygiene report still works (FR-006, C-4)

sbt 1 only, by design — see the recorded exemption in [data-model.md](data-model.md).

```bash
sbt --batch --addPluginSbtFile=project/hygiene/plugins.sbt undeclaredCompileDependencies unusedCompileDependencies
```

`--batch` is mandatory — without it a non-TTY run is SIGKILLed after "done compiling" and looks like
a hang. The flag form writes nothing to the tree, so there is no cleanup step to forget.

**Expected**: exactly two findings — `jackson-databind` undeclared, `jackson-core` unused — matching
[baseline.md](baseline.md). Seven extra findings (shapeless, cats, redisclient, gatling-redis,
jmh-generator) means the `HygieneFilters` AutoPlugin is not being applied.

Then confirm the tree is clean afterwards:

```bash
git status --porcelain
```

**Expected**: empty output.

## Scenario 9 — Default-major switch is one line (FR-004, SC-006)

Edit `sbt.version` in `project/build.properties` to `2.0.6`, then re-run Scenarios 1 and 2 with the
launcher flag inverted (bare `sbt` now gives you sbt 2; `--sbt-version 1.12.15` gives you sbt 1).

**Expected**: everything still passes, and `git diff --stat` shows exactly one file and one changed
line. If any second file must also change, FR-004 is unmet.

Revert the edit when done.
