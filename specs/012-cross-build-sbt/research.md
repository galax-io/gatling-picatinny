# Phase 0 Research: Cross-build on sbt 1 and sbt 2

**Feature**: [012-cross-build-sbt](spec.md) | **Date**: 2026-08-19

All findings below are **empirical** — produced by resolving artifacts against Maven Central
and by loading this repository's actual build under sbt 2.0.6 on Java 17, not by reading
release notes alone. Probe logs are reproducible with the commands in
[quickstart.md](quickstart.md).

---

## D-01: Overall strategy — converge on the intersection, conditionalise only where empty

**Decision**: Rewrite the build so a single unmodified source tree satisfies **both** majors by
using only constructs valid in each. Introduce a per-major conditional **only** where no shared
construct exists. Today exactly one such case survives (D-06).

**Rationale**: Conditionals in build files are the thing that rots — they compile under the major
you are running and silently drift on the other. Every divergence found in this research turned
out to have a shared form that both majors accept, so the conditional count is one, not eight.
This directly serves FR-001 (single unmodified tree) and FR-007 (identical verdicts).

**Alternatives considered**:
- *Per-major build files selected at load time* — sbt loads every `*.sbt` in the base directory
  unconditionally; there is no supported exclusion mechanism. Not possible without generating
  files, which breaks FR-001's "unmodified tree".
- *Fork an sbt-2 branch* — two trees to keep green, guarantees drift, defeats the feature.

---

## D-02: Selecting the sbt major — no file edit required

**Decision**: `project/build.properties` keeps `sbt.version=1.12.15` as the declared default
(FR-017). The secondary major is selected per-invocation with the launcher flag
`sbt --sbt-version 2.0.6`.

**Rationale**: Verified working — the Coursier-installed sbt launcher honours `--sbt-version`
and loads this repository's build under 2.0.6 while `build.properties` still says 1.12.15. This
satisfies FR-001 and FR-004 exactly: switching majors edits nothing, and changing the default is
the single `sbt.version=` line.

**Evidence**: `sbt --sbt-version 2.0.6 "print name"` → `welcome to sbt 2.0.6 (Homebrew Java 17.0.10)`,
then `set current project to gatling-picatinny`.

**Three silent no-ops to guard against** (adversarial verification returned PARTIAL, not confirmed):
- **`SBT_OPTS` outranks the flag.** `SBT_OPTS="-Dsbt.version=1.12.15" sbt --sbt-version 2.0.6` runs
  **sbt 1**, exit 0. Launcher precedence is `JAVA_TOOL_OPTIONS` > `SBT_OPTS` > CLI flag >
  `JAVA_OPTS` > `build.properties`. A stale `SBT_OPTS` entry defeats a correct command line.
- **`--numeric-version` / `--version` ignore the flag**, reporting the `build.properties` value — so
  the obvious CI guard is useless. Use `sbt $FLAG "show sbtVersion"` instead. Note `show` works on
  both majors; **`print` is sbt-2-only**, so the `print name` probe quoted above must not be copied
  into any shared script.
- **The native client ignores the flag when a server is live.** This becomes the default path the
  moment `build.properties` names 2.x — so the documented escape hatch back to sbt 1 stops working
  exactly when FR-004's flip happens. Requires `sbt shutdown` or `SBT_NATIVE_CLIENT=false`.

Positive result: a nonexistent version fails loudly (`sbt --sbt-version 9.9.9` → exit 1,
`could not retrieve sbt 9.9.9`), so a typo'd CI pin cannot silently degrade to `build.properties`.

**Alternatives considered**:
- `-Dsbt.version=2.0.6` — equivalent **as a CLI argument only**. Placed in `SBT_OPTS` it strictly
  outranks `--sbt-version`, so the two are not interchangeable in CI.
- Committing a second `build.properties` — no mechanism selects between them.

---

## D-03: Plugin resolution is automatic per major

**Decision**: Keep one `project/plugins.sbt`. No conditional plugin syntax is needed for any
plugin whose version exists for both majors.

**Rationale**: `addSbtPlugin` rewrites the artifact suffix based on the running sbt. Under sbt 1
it resolves `<name>_2.12_1.0`; under sbt 2 it resolves `<name>_sbt2_3` (Scala 3). Confirmed by
running the real meta-build: the sbt 2 load reported unresolved coordinates in exactly the
`_sbt2_3` form, and the other nine plugins resolved silently.

**Evidence** — Maven Central survey of all 11 pinned plugins:

| Plugin | pinned | sbt 1 (`_2.12_1.0`) | sbt 2 (`_sbt2_3`) | verdict |
|---|---|---|---|---|
| `com.github.sbt:sbt-ci-release` | 1.12.0 | 1.12.0 | 1.12.0 | ✅ same version |
| `com.github.sbt:sbt-git` | 2.1.0 | 2.1.0 | 2.1.0 | ✅ |
| `com.github.sbt.junit:sbt-jupiter-interface` | 0.19.0 | 0.19.0 | 0.19.0 | ✅ |
| `org.scalameta:sbt-scalafmt` | 2.6.2 | 2.6.2 | 2.6.2 | ✅ |
| `ch.epfl.scala:sbt-scalafix` | 0.14.7 | 0.14.7 | 0.14.7 | ✅ |
| `com.typesafe:sbt-mima-plugin` | 1.1.6 | 1.1.6 | 1.1.6 | ✅ |
| `org.scoverage:sbt-scoverage` | 2.4.4 | 2.4.4 | 2.4.4 | ✅ |
| `pl.project13.scala:sbt-jmh` | 0.4.8 | 0.4.8 | 0.4.8 | ✅ |
| `ch.epfl.scala:sbt-bloop` | 2.1.1 | 2.1.1 | 2.1.1 | ✅ |
| `io.gatling:gatling-sbt` | **4.18.3** | 4.19.1 | **4.19.1 only** | ⚠️ bump to 4.19.1 |
| `com.github.cb372:sbt-explicit-dependencies` | 0.3.1 | 0.3.1 | **none published** | ❌ see D-06 |

**Alternatives considered**:
- Conditional `libraryDependencies ++= if (sbtVersion.value…)` — unnecessary for ten of eleven
  plugins, and it would not help the eleventh anyway (D-06).

---

## D-04: `gatling-sbt` bumps to 4.19.1 for both majors

**Decision**: Bump `gatling-sbt` from 4.18.3 to 4.19.1 unconditionally.

**Rationale**: 4.18.3 has no `_sbt2_3` artifact; 4.19.1 is published for **both** majors and is
also the current sbt 1 release. One version, both majors, no conditional. This is the sbt *build
plugin*, not the Gatling runtime — the `io.gatling` runtime dependency stays at 3.13.5 and stays
`Provided`, so the constitution's Gatling constraint is untouched.

**Evidence**: sbt 2 load with 4.18.3 →
`not found: …/gatling-sbt_sbt2_3/4.18.3/gatling-sbt_sbt2_3-4.18.3.pom`. With 4.19.1 the meta-build
resolves and the root project loads.

**Note**: `.scala-steward.conf` currently ignores all of `groupId = "io.gatling"`, so this bump is
a maintainer action, not a bot PR.

---

## D-05: `licenses` — `License.Apache2` is the shared form

**Decision**: Replace `licenses := List("Apache 2" -> …toURL())` with
`licenses := List(License.Apache2)` in `publish.sbt`.

**Rationale**: The key's type changed between majors — `Seq[(String, URL)]` in sbt 1,
`Seq[License]` in sbt 2 — so the tuple form is a hard compile error under sbt 2. But
`sbt.librarymanagement.License.Apache2` exists in **both** majors, typed as whatever that major's
key wants. It is a genuine common subset, not a conditional.

**Evidence** — `javap` on each major's `librarymanagement-core`:

| major | `License$.Apache2` returns | `licenses` expects |
|---|---|---|
| sbt 1.12.15 (`_2.12-1.12.3`) | `scala.Tuple2[String, java.net.URL]` | `Seq[(String, URL)]` ✅ |
| sbt 2.0.6 (`_3-2.0.6`) | `sbt.librarymanagement.License` | `Seq[License]` ✅ |

**Confirmed by compiling, not just by `javap`**: under sbt 1.12.15, `ThisBuild / licenses :=
List(License.Apache2)` compiles with the **bare, unqualified** `License.Apache2` — no import and no
fully-qualified `sbt.librarymanagement.License` prefix is needed on either major — and
`show licenses` prints `(Apache-2.0,https://www.apache.org/licenses/LICENSE-2.0.txt)`.

**Consequence to flag**: the emitted POM license name/URL changes from `"Apache 2"` +
`http://www.apache.org/licenses/LICENSE-2.0.txt` to the canonical values `License.Apache2` carries.
This is POM metadata only — not a code API — so Constitution II is not engaged, but it is a
visible change in published metadata and belongs in the PR description.

**Alternatives considered**:
- Per-major conditional — unnecessary once `License.Apache2` was found to exist in both.
- Dropping `licenses` and letting sbt-ci-release default it — Maven Central requires a license in
  the POM; silently relying on a default is worse than stating it.

---

## D-06: `sbt-explicit-dependencies` — the one genuine exemption

**Decision**: Remove `sbt-explicit-dependencies` from `project/plugins.sbt` and move the plugin
line **and** its five `…DependenciesFilter` settings into an opt-in overlay under
`project/hygiene/`, applied by a small script only when the pre-release hygiene report is run,
under sbt 1. Record this as the FR-006 capability exemption.

**Rationale**: The plugin publishes no `_sbt2_3` artifact at any version — the only plugin in the
set with no sbt 2 build at all. Worse, it cannot simply be left in place and ignored: `build.sbt`
references its keys (`undeclaredCompileDependenciesFilter`, `unusedCompileDependenciesFilter`)
symbolically, so under sbt 2 the build file fails to *compile* with five
`Not found: undeclaredCompileDependenciesFilter` errors. Both the plugin and its filter settings
must leave the always-loaded build. This is acceptable because the report is already documented
in `build.sbt` and TESTING.md as **report-only, never CI-gated**, run manually before each release
(AGENTS.md Release Process) — so a manual opt-in step costs nothing that was automated before.

**Evidence**: sbt 2 load with the plugin absent → five `[E006] Not Found Error` diagnostics at
`build.sbt:50,54,58,62,66` (comment block 48-49), aborting the load. *(An earlier draft of this
document cited 41,45,49,53,57 — those line numbers were stale; the values here were re-read from
the file at branch HEAD.)*

**Alternatives considered**:
- *Conditional `addSbtPlugin`* — does not help. Even with the plugin conditionally absent, the
  `build.sbt` filter settings still fail to compile under sbt 2, because settings are typechecked
  regardless of which branch would run.
- *Drop the hygiene report entirely* — loses a release-checklist gate the project deliberately
  added (#276) for a plugin that may yet ship sbt 2 support.
- *Wait for upstream sbt 2 support* — blocks the whole feature on a third party.

**Revisit condition**: when `com.github.cb372:sbt-explicit-dependencies_sbt2_3` appears on Maven
Central, fold the plugin and filters back into the main build and delete the exemption.

---

## D-07: The `it` configuration does **not** work under sbt 2 — subproject migration is required

**Decision**: Delete the custom `IntegrationTest` configuration and move integration tests into a
dedicated `integration` subproject that depends on the root project, with sources relocated from
`src/it/scala` to `integration/src/test/scala`. The CI gate changes from
`sbt "IntegrationTest / test"` to `sbt integration/testOnly` (see D-15 — `test` is `testQuick` on sbt 2).

**Rationale**: This is the single largest work item and the one place where "just keep what we
have" is not an option. `config("it") extend Test` still *compiles* under sbt 2.0.6 — it emits
only a Scala 3 infix-syntax warning — which makes it look survivable. It is not: sbt 2 refuses to
resolve the configuration as a key axis, so every `it/…` and `IntegrationTest / …` invocation
fails. The custom-configuration axis was deprecated across sbt 1.x and is gone as a usable feature
in sbt 2; the documented replacement is a separate subproject. A plain subproject is the oldest
construct in sbt and behaves identically under both majors, so this is convergence on the
intersection (D-01), not an sbt-2-specific hack.

**Evidence**:
- Compiles: `build.sbt:2:43 … Alphanumeric method extend is not declared infix` (warning only).
- Does not work: `sbt --sbt-version 2.0.6 "show it/scalaSource"` →
  `[error] Not a valid key: it`, after the project had otherwise loaded successfully.

**Knock-on changes this forces**:
- `inConfig(IntegrationTest)(Defaults.testSettings)` and
  `inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest))` are deleted; the subproject
  gets ordinary `Test` settings.
- `IntegrationTest / unmanagedResourceDirectories ++= Seq((Test / resourceDirectory).value)` is
  replaced by a `root % "compile->compile;test->test"` dependency, which is how the subproject
  reaches the shared test fixtures.
- The subproject must set `publish / skip := true` — it must never reach Maven Central (FR-008).
- MiMa, the hygiene filters, and JMH stay on `root` only.
- Coverage measurement must be re-taken and the mechanism chosen deliberately. An earlier draft of
  this document asserted that `coverageAggregate` is required; that is **questionable and must be
  measured, not assumed**. scoverage bakes root's data-directory path into the instrumented
  bytecode at instrumentation time, and every instrumented statement belongs to `root` — so
  `integration/test` writes its measurements into *root's* scoverage data dir, and plain
  `root/coverageReport` may reproduce today's combined unit+it figure with no aggregation at all.
  `coverageAggregate` additionally only works over aggregated projects, so choosing it does not
  avoid the aggregation decision, it hides it. Measure **both** forms on **both** majors before
  writing any number into `build.sbt`; a drop is evidence of misconfiguration, not licence to lower
  a floor.
- **`project/Dependencies.scala` carries `it` in its Ivy configuration strings** and must be
  rewritten alongside the build. This is broader than first estimated: **eleven module lines across
  seven vals** name the configuration being deleted — lines 75, 79, 83, 87 (scalatest, scalacheck,
  scalatestplus, scalamock), 121, 122 (personnummer, hibernate-validator), 133 (testcontainers),
  139 (postgresql, `% "it"`), and 146-148 (junit-jupiter, jupiter-interface, assertj). Once the `it`
  configuration no longer exists these strings name nothing. Each becomes `% Test` on whichever
  project needs it; `testcontainers` + `jdbcDrivers` move into a new integration-only bundle, and
  the "never published" intent that the `it` scope used to enforce is carried instead by
  `publish / skip := true` on the subproject. `junit` does **not** need re-declaring on the
  subproject — all four relocated specs are ScalaTest, and root's Test-scope deps reach the
  subproject through `test->test` anyway.
- **`Provided` scope does NOT propagate across `dependsOn`** — the highest-probability way this
  restructure fails. The `integration` subproject must re-declare root's `Provided` bundles
  (`gatlingCore`, `gatlingShared`, `fastUUID`) in its own `libraryDependencies`, keeping the
  `Provided` scope. Verified on a scratch two-project build under sbt 1.12.15: with
  `b.dependsOn(a % "compile->compile;test->test")`, `b/Test/dependencyClasspath` contained a's
  compile and Test dependencies but **not** a's `Provided` ones; re-declaring them on `b` fixed it.
  Missing this is a loud compile error on `io.gatling.core.feeder.Record` and
  `com.redis.RedisClientPool`, but only after the sources have already moved.
- **`.aggregate(integration)` is required, with targeted opt-outs.** Without aggregation,
  `scalafmtCheckAll`, `scalafixAll`, `Test/compile` and `clean` silently skip the subproject —
  precisely the FR-007 "gate inert / false green" defect. With it, `sbt compile test` would start
  pulling Docker into the unit gate, so `Test / test / aggregate := false` (plus `testOnly`,
  `testQuick`) is required on root. Both halves verified empirically on a scratch build: without
  the opt-out `a/test` ran and failed on b's specs; with it `a/test` succeeded while `b/test` still
  ran them.
- **`.gitignore` needs `integration/target/`** — the existing entry is root-anchored `/target/` and
  does not cover a subproject's output.

**Alternatives considered**:
- *Keep the `it` config, run integration tests on sbt 1 only* — violates FR-005 (integration
  tests must work on both) and FR-007 (a gate inert on one major).
- *Custom config on sbt 1, subproject on sbt 2* — two structures in one tree; violates FR-001.
- *Fold integration tests into `Test` behind a ScalaTest tag* — changes what a plain `Test/test`
  run does on both majors, drags Docker into the unit gate, and diverges from TESTING.md's layer
  model.

---

## D-08: Cosmetic / low-risk divergences

| Item | Under sbt 1 | Under sbt 2 | Shared form |
|---|---|---|---|
| `config("it") extend Test` | fine | infix warning | moot — removed by D-07 |
| `url(…)` in `publish.sbt` | fine | deprecated since 2.0.2 | keep `url(…)`; sbt 1's `homepage`/`ScmInfo` still require `URL`, and build files are not compiled under `-Werror`, so the warning is harmless. Revisit when sbt 1 is dropped. |
| `GitVersioning` keys | wired | `lintUnused` reports 4 unused keys (`useGitDescribe`, `versionProperty`, `gitDescribedVersion`) | cosmetic under sbt 2; verify release versioning still resolves before trusting a publish from sbt 2 (D-09) |

---

## D-09: Build output layout changed — CI globs must be widened

**Decision**: Change CI artifact globs from `**/target/test-reports/*.xml` to
`**/test-reports/*.xml`, and re-derive the scoverage report path per major rather than hardcoding it.

**Rationale**: sbt 2 writes to `target/out/jvm/scala-<ver>/<project>/…` instead of
`target/scala-<ver>/…`. The current glob has a literal `target/test-reports` segment that simply
does not exist under sbt 2, so the sbt 2 matrix leg would upload nothing and the test-results
publisher would silently report zero tests — a green check that proves nothing, which is exactly
the failure mode FR-011 exists to prevent.

**Evidence**: sbt 2 compiled this project to
`target/out/jvm/scala-2.13.18/gatling-picatinny/classes`; the meta-build to
`target/out/jvm/scala-3.8.4/…`.

---

## D-10: CI shape

**Decision** *(revised 2026-08-20)*: Add an `sbt-version` input to `.github/actions/sbt-setup`
(defaulting to empty so existing callers are unchanged) and include the sbt major in the cache key.
Run the full gate suite under sbt 2 in a **separate scheduled workflow** (daily +
`workflow_dispatch` + a build-definition path filter on pull requests) rather than a
`sbt-major: [1, 2]` matrix across the existing jobs.

**Rationale**: The original decision followed FR-013's "full suite on both, every PR". That was
revisited once the cost was measured against what actually depends on dual-major support: nothing.
sbt 1 remains the default and the publishing major, the launcher is version-agnostic so no
contributor is blocked, and the artifact is identical either way — dual support is insurance against
a future forced migration. A permanent ~2x CI bill on every pull request is disproportionate to
that. A daily run catches decay at roughly one extra run per day, and the path filter puts the check
on exactly the changes that break it. A pleasant side effect: no existing job is renamed, so the
check-name churn and the `upload-artifact` duplicate-name hazard both disappear.

Cache keys must still include the major — the two write different artifact layouts (D-09) and
resolve different meta-build classpaths, so a shared key cross-contaminates.

**Promotion path**: if the project ever decides to actually migrate, this becomes a per-PR matrix.
That is a workflow-only change; none of the build-side work needs revisiting.

**Open verification for Phase 2**: whether `sbt ci-release` under sbt 2 produces an identical POM
and artifact set (FR-008, SC-005). Publication stays on sbt 1 (FR-017), so this is a
verify-and-record task, not a blocker.

---

## D-11: The library source itself is already sbt-2 clean

**Decision**: No Scala or Java source change is required by this feature. Scope is confined to
build definitions, CI workflows, the example overlay, and documentation.

**Rationale**: With D-04, D-05 and D-06 applied as throwaway probe patches, `Test/compile` ran to
completion under sbt 2.0.6 — 107 Scala + 23 Java main sources and 46 Scala + 11 Java test sources
— with **zero errors**, under the project's full strict-diagnostics flag set
(`-Xlint:_,-infer-any -Wunused -Wdead-code -Werror`, unchanged). This is the single most important
de-risking result in this research: the sbt major does not reach the library's own compilation,
because the library is Scala 2.13.18 in both cases and `scalacOptions` are set by the build, not
by the build tool. FR-009 therefore holds by construction rather than by careful maintenance.

**Evidence**: `sbt --sbt-version 2.0.6 "Test/compile"` →
`compiling 107 Scala sources and 23 Java sources to target/out/jvm/scala-2.13.18/gatling-picatinny/classes`,
`done compiling`, `[success] elapsed time: 54 s`. Zero `[error]` lines.

**Not yet proven**: that the tests *pass* under sbt 2, only that everything compiles. Running the
suites on both majors is a Phase 2 task, and is what FR-007/SC-002 actually gate on.

---

## D-12: CI must be built to fail loudly — three false-green traps

**Decision**: The sbt-major selection MUST travel to the build via a dedicated env var (e.g.
`SBT_VERSION_FLAG` holding `--sbt-version 2.0.6`), never by appending `-Dsbt.version=` to
`SBT_OPTS`. Test-report collection MUST use `**/test-reports/*.xml`, with
`if-no-files-found: error` on uploads and `action_fail_on_inconclusive: true` on the publisher.
Artifact names MUST be unique per matrix leg.

**Rationale**: A cross-build matrix is worth nothing if the secondary leg can pass without having
run. Three independent mechanisms in the current workflow would let exactly that happen:

1. **`SBT_OPTS` override (most severe).** `ci.yml` sets `SBT_OPTS` at workflow level, and the
   binary-compat step overrides it again at step level. Anything the composite action exports into
   `SBT_OPTS` via `$GITHUB_ENV` is clobbered by those, so every "sbt 2" leg would silently run
   sbt 1 and pass. Nothing in the logs would say so except the sbt banner. The matrix would be
   pure decoration.
2. **The report glob.** `**/target/test-reports/*.xml` has a literal `target/test-reports` segment
   that does not exist under sbt 2's `target/out/jvm/scala-2.13.18/<proj>/` layout. Combined with
   the current `if-no-files-found: warn` and the publisher's default
   `action_fail_on_inconclusive: false`, the sbt 2 leg publishes a 0-test check and stays green.
3. **Duplicate artifact names.** `upload-artifact` v4+ refuses a duplicate artifact name within a
   run. This one at least fails loudly — but it fails the *upload step*, not the tests, so it must
   be fixed alongside the others rather than discovered during the first matrix run.

**Bonus finding**: `sbt/setup-sbt@v1`'s `sbt-runner-version` input **already defaults to 2.0.6**, so
CI is already running an sbt 2 *launcher* today — the launcher is version-agnostic and obeys
`project/build.properties`. No launcher change is needed, which removes a whole class of risk from
D-02's mechanism. The flip side is that this default floats with the `v1` tag, so FR-003's
"minimum tested version" can drift without any PR showing it.

**Also required**: Codecov needs per-major `flags`, or two legs uploading against one commit SHA
merge and the per-major coverage signal is lost while both jobs stay green. The cache `path:` list
covers `~/.sbt` (sbt 1's global base) but not sbt 2's `~/.config/sbt` or `~/.cache/sbt`, so sbt 2
legs start cold every run; and both the cache key and its `restore-keys` must carry the major, or
the sbt 2 leg restores an sbt 1 cache.

---

## D-13: The scala-sbt template job does not build our overlay — an external pin blocks it

**Decision**: Treat the overlay's dual-major verification as a **separate, explicit CI concern**
from the existing `template-tests` job, and record that the external template pack pins a
gatling-sbt version with no sbt 2 artifact.

**Rationale**: This was missed by the spec, the plan and the first research pass, and it undercuts
an assumption FR-014/FR-015 rest on. The `template-tests` job's scala-sbt leg does **not** compile
`examples/scala-sbt-example/{build.sbt,project/plugins.sbt,project/build.properties}`. It generates
a project from the **external** `templates-gatling` pack pinned at `TEMPLATES_GATLING_VERSION`
(`v0.15.0`) and copies only simulation sources in. Two consequences:

- The overlay build files that plan.md schedules for a gatling-sbt bump are **never built by CI at
  all** — today or after this feature — so "verified under both majors" would be an empty claim
  unless something new actually builds them.
- The template pack's own default is gatling-sbt **4.18.3**, which publishes no `_sbt2_3` artifact.
  Until the template render is parameterised (e.g. passing `SbtGatlingVersion` / `SbtVersion`
  through `scripts/test-scala-sbt-template.sh`), an sbt 2 template leg cannot resolve its
  meta-build. This is a **third-party pin outside this repository** and is the most likely single
  cause of FR-014/FR-015 slipping.

**Consequence for the spec**: FR-014/FR-015 need an explicit decision — verify the overlay's own
build files with a new CI step, or renegotiate the requirement. This is a scope question for the
maintainer, not something the plan can resolve unilaterally.

---

## D-14: CI cost — compute doubles, wall-clock does not

**Decision**: Correct the cost framing from "roughly doubles CI wall-clock" to "roughly doubles CI
**compute minutes**; wall-clock grows ~10-20%" — and then, on the strength of that number, scope the
full matrix out entirely (D-10, revised 2026-08-20) in favour of a daily scheduled run.

**Rationale**: Measured per-job seconds from a real warm-cache run: Coverage 229s, kotlin-gradle
190s, scala-sbt 139s, java-maven 136s, Redis 131s, Publish Local 111s, Test J21 107s, Test J17
100s, Lint 92s, Binary-compat 75s, Format 55s. Today ≈1365s compute across 11 jobs, critical path
≈301s. After the matrix ≈2715s compute (≈2.0×) but the legs fan out in parallel, so the critical
path becomes ≈325s — about +8%. Job count goes 11 → 20, arriving in two waves (16 then 4), which
sits under GitHub's 20-concurrent-job limit even on the Free tier. That concurrency headroom is the
load-bearing assumption; if it is ever violated, wall-clock degrades toward the 45-minute compute
sum. Dominant jobs, in order: the sbt-2 Coverage leg, then the `publish-local → template-tests`
chain, then Redis Integration.

**What this number bought**: presenting "+1350s of compute on every pull request, permanently" next
to "nothing currently depends on sbt 2" made the trade explicit, and the scope was changed. The
scheduled shape costs ~2700s **per day** instead of ~1350s **per pull request** — on a repository
with more than two pull requests a day, it is strictly cheaper, and it stays flat as activity grows.

---

## D-15: `test` IS `testQuick` on sbt 2 — the dominant false-green

**Decision**: Every test invocation in CI, docs, scripts and git hooks MUST use `testOnly`
(or `integration/testOnly`, or `'Gatling/testOnly *'`), never `test`.

**Rationale**: This is the single most important finding of the whole investigation, and it was
found twice independently — once on the root build, once on the example overlay. sbt 2.0.6's `test`
task is `testQuick`: it runs only tests that failed before, were not run, or whose transitive
dependencies changed. `sbt --sbt-version 2.0.6 <proj>/test` printed `[success]` having run **zero**
tests. The "already passed" record lives in the **global** disk cache
(`~/.cache/sbt`, `~/Library/Caches/sbt/v2`), so it survives both `clean` and `rm -rf target` —
proven by round trip: moving the cache aside made the same command compile and run everything;
restoring it made the command vacuous again.

`sbt/setup-sbt@v1` enables `disk-cache: true` **by default**, with a key containing no sbt major and
a prefix-only `restore-keys` fallback. So in CI, run #1 would be honest and every run after it
vacuous.

**Why `testOnly` and not `testFull`**: `testFull` does not exist on sbt 1
(`Not a valid key: testFull`). Only `testOnly` is convergent across both majors. If someone later
"simplifies" to `testFull` the sbt-1 leg dies; if they simplify to `test` the sbt-2 leg goes
vacuously green. Both directions need a guarding comment at each call site.

**The four defaults that line up**: (1) `test` ≡ `testQuick`; (2) the pass record is global and
survives `clean`; (3) `**/target/test-reports/*.xml` matches nothing under sbt 2's layout, so no XML
contradicts the green; (4) `if-no-files-found: warn` plus EnricoMi's
`action_fail_on_inconclusive: false` publish a 0-test check as green. **Fixing only the glob leaves
the trap fully armed** — all four must move together.

---

## D-16: sbt 2 does not copy resources into `classDirectory` — `products` must be pinned

**Decision**: Add `ThisBuild / exportJars := false` **and** pin `Compile / products` and
`Test / products` to `Seq(classDirectory)` (depending on `compile` + `copyResources`) in
`build.sbt`. Neither existed at HEAD.

**Rationale** *(corrected 2026-08-20 — an earlier draft of this decision named the wrong
mechanism, and the wrong mechanism would have led to the wrong fix)*:

The observed failure is that two `TemplatesSpec` tests fail cold under sbt 2:

```text
Failure("Your resource's path .../src/test/resources/templates/test_json.json is incorrect.
It should not be an absolute path pointing to a directory that belongs to your classpath.
Instead, it should be relative to your classpath root ...")
  was not equal to Success("{"userId": "42", "action": "test"}")
```

That is **not** a `jar:` URI and **not** a `FileSystemNotFoundException`. The actual mechanism, read
off the build rather than inferred:

| | sbt 1.12.15 | sbt 2.0.6 |
|---|---|---|
| `Test / products` | `[target/scala-2.13/test-classes]` | `[target/out/.../test-classes, src/test/resources]` |
| `test-classes/templates/` | present (`test_json.json`, `test_xml.xml`) | **absent** |

sbt 2 does not copy unmanaged resources into `classDirectory`; it puts `src/test/resources` on the
classpath directly. `Templates.scala:35` resolves its registry with
`getResource("templates")` and then hands Gatling `ElFileBody(f.getCanonicalPath)` — an **absolute**
path. Under sbt 1 that path is the copied `test-classes/templates/...` and Gatling accepts it. Under
sbt 2 it resolves to `src/test/resources/templates/...`, which sits inside Gatling's configured
resources directory, and Gatling refuses absolute paths there by design.

So the fix is to restore sbt 1's product shape, not to chase jar packaging. `exportJars := false` is
kept as well — it is independently correct (sbt 2 defaults it to `true`), but it is **not** what
fixes this test.

**Why it matters beyond two tests**: this is a build-tool default reaching the library's *runtime*
resource resolution. The natural misdiagnosis is to blame the `integration` subproject migration and
narrow what the sbt 2 gate runs, which would hollow out the feature.

**Verification hazard**: a warm cache masks it. Validate with `clean` plus a fresh `--sbt-cache`.

**Cross-major syntax trap found while writing the fix**: two `val _ = …` bindings in one block
compile under Scala 3 (sbt 2's build compiler) but fail under Scala 2.12 (sbt 1's) with
`_ is already defined as value _`. One binding per block — a tuple if two task values are needed.
The build definition itself has a common-subset problem, not only the settings it contains.

---

## D-17: The `Gatling` config axis works on sbt 2 — the overlay is not blocked

**Decision**: `examples/scala-sbt-example` needs exactly one change to load under sbt 2 — the
`gatling-sbt` bump to 4.19.1. Its `build.sbt` and `build.properties` are unchanged.

**Rationale**: This was the highest-risk open question: since sbt 2 refused to resolve our custom
`it` configuration, `Gatling` — also a custom configuration — looked likely to fail the same way.
It does not. `Gatling/scalaSource`, `Gatling/resourceDirectory`, `Gatling/test`, `Gatling/testQuick`,
`Gatling/testFull` and `Gatling/testOnly` all resolve, and all three overlay simulations run to
success under sbt 2.

**Contrary evidence worth recording against D-07's stated rationale**: the difference appears to be
*how* the configuration reaches the project. `Gatling` is contributed by gatling-sbt's AutoPlugin
through `projectConfigurations`; our `it` was a bare `config("it")` val. So the sbt-2 failure may be
about configuration *registration* rather than the custom-configuration axis being gone. This does
not change D-07's recommendation — the `integration` subproject is still the sbt-endorsed target and
is verified working on both majors — but the rationale is weaker than first stated, and plugin-
contributed custom configs demonstrably keep working on sbt 2.

**Not resolved by this**: `Gatling/fullClasspath` returns `No such setting/task` under sbt 2 while
`Test/fullClasspath` resolves. Unused by this overlay, but it would bite IDE run configurations.

**Still blocked upstream**: CI's scala-sbt template leg does not build these files at all (D-13).

---

## Residual risks

| Risk | Impact | Mitigation |
|---|---|---|
| Coverage figure shifts when `it` moves to a subproject | Floor in `build.sbt` stops matching TESTING.md's recorded measurement | Re-measure with `coverageAggregate` on both majors; record the new measured value + date per the TESTING.md ratchet rules; floors only move up |
| sbt 2 task caching is on by default; custom tasks need `JsonFormat` | A custom task fails only under sbt 2 | Few custom tasks exist here; surfaced by running the full suite under the sbt 2 matrix leg |
| `exportJars` defaults to `true` in sbt 2 | Compile classpath differs subtly between majors | Covered by running compile + tests on both; watch for MiMa or scoverage surprises |
| Release publishing verified only on sbt 1 | An sbt-2-only publish regression goes unnoticed | Acceptable — FR-017 keeps publication on sbt 1; D-10 records the sbt 2 publish check as a task |
| `examples/scala-sbt-example` overlay not yet probed under sbt 2 | Unknown overlay-specific breakage | Overlay probe is an explicit Phase 2 task; the overlay is a separate, much smaller build (one plugin, no custom configs) |
