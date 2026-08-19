# Data Model: Cross-build on sbt 1 and sbt 2

**Feature**: [012-cross-build-sbt](spec.md) | **Phase**: 1 | **Date**: 2026-08-19

The entities here are configuration facts, not runtime types. Each one has a single place in the
repository that owns it; "Owned by" names that place, and it is the only file allowed to change
the value.

---

## Supported sbt Major

A build-tool major line the project commits to keeping green.

| Field | Type | Rules |
|---|---|---|
| `majorLine` | `1.x` \| `2.x` | Exactly two entries exist. Adding or removing one is an explicit decision, not a side effect. |
| `minimumTestedVersion` | version string | MUST equal a version CI actually runs. A version documented but absent from the CI matrix is invalid (FR-003). |
| `role` | `default` \| `secondary` | Exactly one `default` at any time. |
| `selectionMechanism` | pin \| launcher flag | The `default` is selected by `project/build.properties`; the `secondary` by `sbt --sbt-version <v>`. No other mechanism. |

**Instances**

| majorLine | minimumTestedVersion | role | selectionMechanism |
|---|---|---|---|
| 1.x | 1.12.15 | **default** | `project/build.properties` → `sbt.version=1.12.15` |
| 2.x | 2.0.6 | secondary | `sbt --sbt-version 2.0.6` |

**Owned by**: `project/build.properties` (the default pin — FR-004's single line) and
`.github/workflows/ci.yml` (the secondary version used by the matrix).

**Invariants**
- Changing `role` requires editing exactly one line (FR-004).
- Both instances appear in every gated CI job's matrix (FR-013).
- The `default` is the major that performs official publication (FR-017, FR-020).

---

## Build Capability

A named thing the build can do, and whether each major can do it.

| Field | Type | Rules |
|---|---|---|
| `name` | string | Unique. |
| `invocation` | command | MUST be identical across majors, or the capability is not shared. |
| `availableOn` | set of majors | Non-empty. If it does not contain both, a Capability Exemption MUST exist (FR-006). |
| `kind` | `gate` \| `report` | `gate` blocks a pull request; `report` never does. |

**Instances after this feature**

| name | invocation | availableOn | kind |
|---|---|---|---|
| Compile | `sbt compile` | 1.x, 2.x | gate |
| Unit tests | `sbt "Test/testOnly"` | 1.x, 2.x | gate |
| Integration tests | `sbt integration/testOnly` | 1.x, 2.x | gate |
| Format check | `sbt scalafmtCheckAll scalafmtSbtCheck` | 1.x, 2.x | gate |
| Lint | `sbt "scalafixAll --check"` | 1.x, 2.x | gate |
| Coverage | `sbt clean coverage "Test/testOnly" "integration/testOnly" coverageOff coverageReport coverageAggregate` | 1.x, 2.x | gate |
| Binary compatibility | `sbt mimaReportBinaryIssues` | 1.x, 2.x | report (advisory, #274) |
| Benchmarks | `sbt Jmh/run` | 1.x, 2.x | report |
| Publish | `sbt publishLocal` / `sbt ci-release` | 1.x, 2.x | gate (official publish: 1.x only) |
| **Dependency hygiene** | opt-in overlay, see exemption | **1.x only** | report (never CI-gated, #276) |

**Owned by**: `build.sbt` (what exists), `.github/workflows/ci.yml` (what runs),
`TESTING.md` "Static analysis & gates" (the normative list).

**State transition** — `Integration tests` is the one capability whose `invocation` changes:
`sbt "IntegrationTest / test"` → `sbt integration/testOnly`. Every reference to the old form
(CI workflow, TESTING.md, AGENTS.md, constitution Development Workflow step 4) must move with it,
or the docs describe a command that no longer exists.

---

## Capability Exemption

A recorded statement that a capability is unavailable on a major. The existence of this entity is
what turns a silent gap into a reviewable decision (FR-006).

| Field | Type | Rules |
|---|---|---|
| `capability` | Build Capability name | MUST exist. |
| `unsupportedMajor` | major line | MUST NOT be the `default`. |
| `reason` | prose | MUST state the external cause, not "not done yet". |
| `revisitCondition` | prose | MUST be observable by someone other than the author. |

**Instances**

| capability | unsupportedMajor | reason | revisitCondition |
|---|---|---|---|
| Dependency hygiene | 2.x | `com.github.cb372:sbt-explicit-dependencies` publishes no `_sbt2_3` artifact at any version, and `build.sbt` referenced its keys symbolically, so the build file would not compile under sbt 2 | `sbt-explicit-dependencies_sbt2_3` appears on Maven Central → fold plugin and filters back into `build.sbt` and delete this entry |

**Owned by**: `TESTING.md` "Static analysis & gates" (the exemption table lives beside the gate it
qualifies, so a reader checking gates cannot miss it).

**Benchmarks — verified, no exemption needed.** `Jmh` is a plugin-contributed config axis, the same
construct that failed for `it`, so it was flagged as an open gap. Resolved 2026-08-20 by running
`Jmh/run` on both majors: all 8 `SyntaxBenchmark` measurements produced comparable numbers
(e.g. `makeJsonNested` 0.616 vs 0.629 us/op). The capability is available on both.

**Invariant**: exactly one exemption exists today. A pull request that adds a second without an
explicit decision is adding drift, which is what FR-021 exists to catch.

---

## Verification Run

One execution of the gate suite bound to one supported major. Not a stored record — the shape CI
must produce so a reader can tell what actually ran (FR-010, FR-011).

| Field | Type | Rules |
|---|---|---|
| `major` | major line | MUST appear in the job's display name. |
| `trigger` | `pull_request` \| `schedule` \| `dispatch` | The default major runs on `pull_request` (unchanged from today). The secondary runs on `schedule` (daily), on `dispatch`, and on `pull_request` **only** when a build-definition path matched. |
| `capabilitiesExercised` | set | MUST equal every `gate`-kind capability available on that major (FR-013). A reduced subset on the scheduled run is not acceptable. |
| `verdict` | pass \| fail | A fail on the default major blocks the pull request. A fail on the secondary major MUST notify a maintainer; it blocks only when the run was triggered by a build-definition pull request. |
| `testsExecuted` | count | MUST be non-zero, and MUST be asserted. This is the single most important field: on sbt 2 a run can legitimately report `[success]` having executed nothing. |
| `evidencePaths` | globs | MUST resolve under that major's output layout, or the run reports zero tests and passes vacuously. |

**Owned by**: `.github/workflows/ci.yml` (default major, unchanged) and
`.github/workflows/sbt2-compat.yml` (secondary major, new).

**Layout hazard** — the two majors write test reports to different paths:

| major | test report location |
|---|---|
| 1.x | `target/test-reports/*.xml` |
| 2.x | `target/out/jvm/scala-2.13.18/<project>/test-reports/*.xml` |

The current glob `**/target/test-reports/*.xml` matches the first and **not** the second. Widening
it to `**/test-reports/*.xml` is required; leaving it would produce a passing sbt 2 run that
uploaded nothing and asserted nothing.

**Cadence rationale**: the secondary major is verified daily rather than per pull request because
dual-major support is insurance against a future forced migration, not a capability anything
currently consumes (decision 2026-08-20). A once-a-day run catches configuration decay; the
path filter catches the change class that actually causes it.

## Relationships

```text
Supported sbt Major (2)
        │
        │ 1 Verification Run per pull request (default major)
        │ 1 Verification Run per day + on build-definition PRs (secondary major)
        ▼
Build Capability (10) ──── availableOn ────► Supported sbt Major
        │
        │ 0..1  (required when availableOn omits a major)
        ▼
Capability Exemption (1)
```

**Cross-cutting rule**: every Build Capability is reachable from every Supported sbt Major, either
directly or through a Capability Exemption. There is no third state — an unreachable capability
with no exemption is the defect FR-006 defines.
