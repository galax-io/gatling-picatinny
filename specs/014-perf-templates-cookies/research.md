# Phase 0 Research: Perf — Templates & Cookies, Locale Correctness, OpenNFR v0.8.0

**Feature**: [spec.md](spec.md) · **Date**: 2026-09-01

All findings are read from the artefacts this project pins — Gatling 3.13.5, gatling-shared-model
0.0.11, Scala 2.13.18, sbt 1.13.0 (`project/build.properties`) and sbt 2.0.6 — and from an upstream
OpenNFR checkout at tag `v0.8.0` (`c1a1761`). Every decision below was produced by research and then
attacked by an independent verifier; where the verifier found a defect it is recorded in place.

---

## R1 — The broken benchmark harness

**Decision.** Add one `Jmh`-scoped setting to `build.sbt`, immediately after the existing `Provided`
rewrite:

```scala
Jmh / dependencyClasspathAsJars ++= (Compile / dependencyClasspathAsJars).value,
```

**Rationale.** sbt-jmh 0.4.8 sets `run / fork := true` and then patches only
`dependencyClasspath ++= (Compile / dependencyClasspath).value`. A *forked* run does not read
`dependencyClasspath` — it reads `fullClasspathAsJars`, assembled from `dependencyClasspathAsJars`,
a key the plugin never touches. `build.sbt:123-125` demotes every `org.openjdk.jmh` module to
`Provided`; that demotion is invisible to the `Jmh` config's `…AsJars` value, so the fork starts
without `jmh-core`. The recommended line is literally the plugin's own line applied to the key the
fork actually reads — it restores the plugin's stated intent on the code path its author missed.

This is a **regression, not a missing feature**. The `Provided` rewrite entered in `1231a64`
(2026-08-22, *"build(sbt): cross-build on sbt 1 and sbt 2 from a single source tree"*), two days after
`specs/012-cross-build-sbt/tasks.md:160` recorded `Jmh/run` as verified working on both majors. A
one-line targeted fix matches a one-line regression.

**Verified.** Without the setting, `Jmh/run -l` dies with `ClassNotFoundException:
org.openjdk.jmh.Main`; with it, benchmarks list and the task exits `[success]`. Reproduced
independently on sbt 1.13.0 and on sbt 2.0.6. sbt's own impact report names exactly one consumer of
the new value: `Jmh / fullClasspathAsJars`. `Compile / packageBin / mappings` and
`Compile / dependencyClasspath` are byte-identical with and without it, so the dependency-hygiene
report's inputs do not move.

**Correction this forced in the spec.** The published POM is **not** free of `org.openjdk.jmh` today
and this fix does not make it so: all three jmh modules are declared at `<scope>provided</scope>`,
before and after, and the same is true of the 1.26.0 artifact on Maven Central (POMs byte-identical,
md5 `1ad8fcd5…`). The remedy is POM-*neutral*, not POM-*cleaning*. Spec US1 scenario 7 said the
tooling must be "absent" from the descriptor; that was false about the baseline, and the scenario has
been rewritten to state the property that actually matters and is actually preserved — jmh is never at
`compile` scope, hence never transitive. Releases 1.23.0–1.25.0 *did* ship it at compile scope
(GPLv2 modules transitively attached to consumers of an Apache-2.0 library), which is precisely what
the `Provided` rewrite exists to prevent — and why no remedy may touch `libraryDependencies`.

**Alternatives rejected.**

| Option | Rejected because |
|---|---|
| Drop the `Provided` rewrite; strip jmh from the POM via `pomPostProcess` | Trades a loud failure for a silent one. Today the breakage is a `ClassNotFoundException` visible in three seconds; under this, a lost or renamed XML rewrite ships GPLv2 `jmh-core` at compile scope to every consumer and nobody notices. Published POMs for 1.23.0–1.25.0 prove that exact state is reachable. Solves nothing option (a) does not. |
| Move benchmarks to a separate unpublished subproject | Architecturally the cleanest long-term shape, but it contradicts this spec: US1 scenario 6 states the naming-convention mechanism as a property to preserve, and the whole benchmark layout rests on benchmarks living on the production classpath. Turns a one-line unblocker into a build restructure touching aggregation, the coverage denominator, `packageBin`, the scalafix source filter and two workflows — before any of #126/#137 can start. File separately if wanted. |
| `Jmh / run / fork := false` | Would "work" by routing to a key the plugin does patch, but defeats the measurement: unforked, JMH's child JVMs inherit `java.class.path` from the sbt process rather than a classpath sbt controls. Unacceptable when `gc.alloc.rate.norm` is the acceptance metric. |

**Note so nobody chases a phantom.** `Jmh/run -l` prints every benchmark twice. That is correct JMH
behaviour: `JmhBenchmark` declares `@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))`, so the
generated `META-INF/BenchmarkList` holds one record per mode — 31 methods × 2 modes = 62 lines.

**Open.** Whether a `Jmh/run -l` smoke gate belongs in CI, and in which workflow, is a policy call;
AGENTS.md classes CI changes as *ask first*. See R7 below.

---

## R2 — The packaging leak

**Decision.** Replace the `Compile / packageBin / mappings` filter (`build.sbt:117-120`) with a shared
predicate that also matches JMH's two `META-INF` resources and the bare directory mappings, defined
beside the existing shared benchmark definitions at `build.sbt:10-11`.

**Measured — exactly what survives today** (sbt 1.13.0, after `Jmh/compile`).
`Compile/packageBin/mappings` holds 674 entries, six of them benchmark-derived and all six passing the
current filter:

| # | Entry | Why the filter misses it |
|---|---|---|
| 1 | `META-INF/BenchmarkList` (16.7 KB) | not a `.class`, not under `org/galaxio/gatling/jmh/` |
| 2 | `META-INF/CompilerHints` (2.3 KB) | same |
| 3 | `org/galaxio/gatling/jmh` | bare directory mapping — no trailing slash, so `startsWith("org/galaxio/gatling/jmh/")` misses it |
| 4–6 | `…/{templates,redis,feeders/faker}/jmh_generated` | bare directory mappings |

The jar itself gains three entries over a clean build (671 vs 668): the two `META-INF` files plus the
`META-INF/` directory entry created for them. The four directory mappings produce no jar entry — sbt's
writer only materialises directories that end up holding a file — but they are wrong at the mappings
level, which is the contract the build controls.

**Rationale.** `Jmh / classDirectory` *is* the Compile class directory, so `Jmh/compile` writes three
shapes of artefact straight into what `packageBin` reads. One shared predicate answering "is this path
present only because someone ran the benchmarks?" keeps the answer in one place, which is the
convention the file already uses for the coverage, scalafix and packaging gates.

**Risks recorded.** The two exact-string clauses go stale if a future JMH emits a third resource — which
is exactly what US1 scenario 9 (package with and without a prior benchmark run, assert identical entry
lists) is for. A `jmh_generated` path-segment test would also exclude a production package literally
named `jmh_generated`; none exists, and the name is JMH's hardcoded generated-package name.

**Second leak found, and it WAS folded in.** The **sources jar** shipped the benchmark sources on
every release, unconditionally — the `packageBin` filter never covered `packageSrc`. Since the whole
point of the shared predicate is one answer to "is this a benchmark artefact?", leaving the second
archive unfiltered would have half-closed the leak, so `Compile / packageSrc / mappings` now consumes
the same predicate and the predicate matches sources as well as classes.

---

## R3 — The single-pass cookie parser, and the locale fix

**Decision (algorithm).** Rewrite `parseSingle` as a **fold over the existing `split(";")` parts**, not
a character scan. Keep `trimmed.split(";")` and `parts.head`; drop the array-wide `.map(_.trim)`, the
`.tail`, the tuple array and the `.toMap`; replace the per-attribute `split("=", 2)` with
`span(_ != '=')`; lower the key with `Locale.ROOT` and `match` it; accumulate into a private case class
via `foldLeft`.

**No Complexity Tracking exemption is needed.** The result uses only `split`, `span`, `trim`,
`foldLeft` and pattern matching — no `indexOf`, no `substring` — so it clears AGENTS.md's style rule and
every machine-enforced rule in `.scalafix.conf`. This is why a character-scan parser was rejected: it
would buy little beyond the fold and would need an exemption.

**Decision (the Max-Age warning).** The fold must carry the **raw** Max-Age string and warn **after**
the fold, never inside it. Each `max-age` occurrence *replaces* the slot, which is what reproduces
`.toMap`'s last-wins; the parse-and-warn expression currently at `CookieParser.scala:37-42` is then
applied once to the surviving value. Flags are set to `true` structurally and never derived from the
attribute's value, which preserves stickiness. Unrecognised attributes fall through unchanged.

**Verified by differential test.** The fold was built and diffed against the live parser over
40,071 inputs (71 hand-enumerated adversarial cases + 40,000 generated), comparing the parsed result,
the thrown exception class and message, *and* the complete ordered list of WARN messages captured
through the repo's own `LogCapture`. Zero differences under the host locale, and zero under `en` and
`lt`. Three traps were quantified by deliberately implementing the prose wrongly:

| Misreading | Divergences / 20,082 |
|---|---|
| omit the key `.trim` | 12,150 |
| omit the value `.trim` | 7,601 |
| use `parts.headOption` instead of `parts.head` | 408 |

The third is the important one: **a line consisting only of `;` raises an error today**, and the
defensive rewrite silently turns that into an empty result. Nothing in `CookieParserSpec` pins it. The
spec now carries it as an edge case.

**Decision (the locale fix), and a defect it exposed in the spec.** Use `toLowerCase(Locale.ROOT)`. The
verifier found a case the research missed: `"DOMAİN".toLowerCase(tr)` is `"domain"` — so the dotted
capital `İ` is *recognised today* on Turkish hosts — while `"DOMAİN".toLowerCase(Locale.ROOT)` keeps the
combining dot and is not. The fix therefore **widens** (`DOMAIN` starts working) and **narrows**
(`DOMAİN` stops working) on the same host, measured at 5,035 divergences / 20,082 inputs under `tr`.

That is correct behaviour and the spec has been amended to say so: cookie attribute names are ASCII,
`DOMAİN` is not a valid spelling, and it was only ever matched by accident on one family of hosts —
accepting it *is* the locale dependence being removed. It must be pinned by a test so it is not
mistaken for a regression later.

**Consequence for the equivalence suite (R6).** An ASCII-only generator cannot see this: three separate
20k-input corpora reported zero differences for behaviour-changing variants until `ı`/`İ` entered the
attribute alphabet. The cookie generator **must** include them.

**Decision (how to test a locale).** *(AMENDED 2026-09-01 during implementation — the decision below
was attempted and REVERTED. It is not what ships.* `Test / testGrouping` cannot be expressed across
both supported sbt majors: sbt 2.0.6 refuses the setting without `Def.uncached`, and `javap` on
`main_2.12-1.13.0.jar` confirms `sbt.Def.uncached` does not exist on the pinned major, so wrapping it
breaks sbt 1 instead. Both failures were reproduced. Constitution IV forbids a single-major build
capability without a recorded exemption, and an exemption would mean the locale tests run on only one
major. **What ships instead**: the in-repo `LogCapture` pattern — a `synchronized` save/set/restore
window in a test utility, no build change, identical on both majors. It is safe here because after
both locale fixes no production code a concurrent suite touches still consults the default locale for
a decision Turkish would change. See `tasks.md` Phase 2.*)

The original reasoning, retained for the record: do not call `Locale.setDefault` inside the shared test JVM — it is
global mutable state and a parallel-execution hazard. Pin it at JVM level for a dedicated forked group:
`Test / testGrouping` partitions on a suite-name convention, leaves everything else in-process, and runs
the locale suites in a subprocess with the Turkish language and country properties set.

**Open.** *(Moot — the approach was reverted; see the amendment above.)* Whether `Test / testGrouping` with a subprocess group co-exists cleanly with scoverage's
measurement-file collection and with sbt-jupiter-interface in *this* build was not established
(research was read-only on `build.sbt`). Verify with a throwaway group before committing to it; the
`LogCapture`-shaped JVM-serialised fallback is the escape hatch. Behaviour on sbt 2 is likewise
unverified.

---

## R4 — The escaping rewrite

**Decision.** Delete the two `String`-returning escapers and replace them with append-in-place helpers
plus a first-escape-index scan. **No caller needs a String**: all 20 call sites either append the
result straight into the caller's `StringBuilder`, or — for XML field names — reuse it twice for the
open and close tag, which the design solves with an index rather than a String.

The shape:

- a `needsEscape` predicate per format;
- an `escapeStart(s)` scan returning the index of the first character needing escape, or `s.length`;
- an append helper which, when that index is past the end, appends the whole string in **one bulk copy
  with zero allocation**, and otherwise bulk-copies the clean prefix and then runs the existing branch
  list verbatim, in the same order, from that index.

**The control-character path.** The current interpolated format string allocates a `Formatter` per
control character. It is replaced by direct nibble writes against a hex-digit table. Branch order in
`Syntax.scala:142-151` already guarantees the five characters with dedicated short escapes never reach
the hex path, so the only characters that do are the remaining C0 controls; output must remain four
lowercase hex digits, zero-padded, at both ends of the range.

**Measured.** Allocation falls 50–93% depending on fixture. Throughput was **not** measured — the
harness does not start until R1 lands, so the FR-002 measurement pair is still owed.

**Risk recorded, and it is subtle.** The `needsEscape` predicate encodes an invariant the branch list
does not restate: the five short-escape characters are covered only because they all sit below the
printable range. If anyone later adds a dedicated escape for a printable character — the forward slash
being the usual candidate — to the match without adding it to the predicate, the fast path skips it and
emits it raw. Silent corruption, and no current test would catch it. Mitigation: tie the predicate to
the match in a comment, and have the equivalence suite **enumerate** the escape set rather than sample
it.

**Surrogates.** The current loop passes surrogate halves through unchanged, and so does the fast path;
appending a `String` in bulk is observably identical to appending it character by character.

---

## R5 — The OpenNFR bidirectional pairing

**Decision.** Thread the resolved scope into the measure resolution, on top of a **new group scope
case** — the two are not alternatives; the new case is the data-model half the threading requires. A
joint post-check that leaves the two resolutions independent was rejected: it can express the rule but
not make it total in the types, so a future metric name could be added without the compiler demanding a
pairing decision.

**What v0.8.0 requires**, transcribed from the local upstream checkout (`git show v0.8.0:README.md`):

| Selection | `loadtest.request.duration` | `loadtest.group.duration` | retired name | any other / none |
|---|---|---|---|---|
| `{}`, request-named, hierarchy+request, quantified | **can** → response time | refuse | refuse | per the fraction rows |
| hierarchy only, no request | refuse | **can** → group cumulated response time | refuse | refuse |

**Two defects the verifier found in the research's own write-up**, both to be carried into
implementation rather than discovered there:

1. **The proposed scope rewrite silently drops the non-path-key refusal.** As printed, a selector such
   as `{http.route: …}` maps to an empty key pair and would return the global scope — rendering a
   global assertion from a selector upstream marks **cannot**. That is the worst silent drift available
   in this feature. The subset guard at `Reach.scala:104-108` must survive above the new match. Two
   existing tests would catch it (`ReachSpec:177`, `OpenNfrAssertionsSpec:75-87`), so it is a write-up
   defect rather than a shipping risk — but it must be stated.
2. **The locally-decided-refusal register is native-blind.** The guard at `Reach.scala:163` compares the
   native statistic by equality with response time; once a second native statistic exists it must widen
   to a membership test, or `ReachSpec`'s "point every locally-decided refusal at the register" guard
   becomes one-sided.

**Falls out for free and must be picked up.** v0.8.0 also broadened the wildcard-hierarchy row to cover
a wildcard group element **with or without a request name** → **cannot**. Routing the group-only
selector through the existing `hierarchy` helper produces that refusal already.

**Migration surface.** `RequirementSet.TracksRelease` moves to `v0.8.0`, and
`MigrationTableSpec:104-116` requires five named files to contain that string.
`MigrationTableSpec:70-76` asserts the docs say the group-only case has *no* equivalent and must be
inverted. `ReachSpec:242-277` is the one test that cannot be migrated mechanically: it iterates
selections × predicates × operators asserting zero refusals, and adding the group selection makes 45 of
50 new combinations legitimately refuse — it must become two cross-products, and a careless merge into
one list would silently weaken the suite that exists to catch over-refusal. `group-only.yaml` is
referenced by name from `FacadeParitySpec:30`, `JavaOpenNfrAssertionsTest:39` and the docs narrative,
and flips meaning from *refused* to *renders*.

**Open.** Nothing in this repository exercises a group assertion against a real Gatling run today. The
statistic-selection claim rests on reading `AssertionValidator` and `AssertionStatsRepository`; only the
proposed e2e addition would exercise it live.

---

## R6 — The behaviour-parity equivalence suite

**Decision.** A **frozen-oracle** suite in `Test` scope: a verbatim, unmodified copy of today's
implementation, compared against the live one over ScalaCheck-generated inputs plus enumerated boundary
tables. Five new Test-scope files, zero production changes, zero new dependencies (ScalaCheck 1.20.0
and the scalatestplus binding are already `Test`-scope at `project/Dependencies.scala:78-83`).

Golden files were rejected — they pin a corpus, not a function, so they cannot answer a *newly
generated* input and go stale invisibly. Property-only assertions were rejected — invariants cannot
express "identical to what it did before", which is the whole requirement.

**Comparison is the full observable outcome**, not just the return value: the parsed result, the thrown
exception class and message, and the complete ordered list of WARN messages.

**One placement detail is load-bearing.** The frozen cookie oracle must live in its own package, not in
`org.galaxio.gatling.storage` — scala-logging derives the logger name from the class name, so an oracle
in `…storage` would log under a child of `org.galaxio.gatling.storage` and
`LogCapture.warns("org.galaxio.gatling.storage")` would swallow the oracle's warnings into the live
capture, making warning-parity vacuously true.

**Generators must include** the dotless and dotted Turkish letters in the cookie attribute alphabet (see
R3) and the full escape set — enumerated, not sampled — plus control characters and surrogate pairs for
body assembly.

**Risk recorded: oracle drift.** If someone edits a frozen reference to make a red build green, the
safety net silently disappears, and no automated guard can prevent it (a checksum test is just another
thing to regenerate). Mitigation is procedural and belongs in the task order: the frozen files land in
the **first** commit, before any production file is touched; each carries the exact `git show` command
that reproduces it; and any later diff to one is a hard review stop.

**Open.** `scalafix --check` on the oracle files could not be verified from an out-of-tree probe
(a `MissingSemanticdbError`, which is a probe artefact rather than a finding). Re-run once the files sit
under `src/test/scala`.

---

## R7 — The CI benchmark pipeline (found while reading the workflows)

**Finding.** A before/after JMH pipeline already exists in `.github/workflows/release.yml` —
`benchmarks-current`, `benchmarks-base`, `benchmarks-report` — and it is broken four ways:

1. all three jobs are hard-disabled with an always-false condition (`:135`, `:184`, `:214`), residue of
   #158 (*Temporarily disable JMH benchmarks in CI*, closed with nothing re-enabling them);
2. `benchmarks-base` runs its build in a `base-branch` working directory, but its only checkout has no
   path and no ref, so that directory never exists — the "before" half fails on its first step;
3. the comparison step is gated on a pull-request event inside a workflow triggered only by version
   tags, so it can never run;
4. everything downstream would fail anyway while the harness does not start.

`ci.yml`, the workflow that actually runs on pull requests, has no benchmark job at all.

**Decision.** FR-002's before/after evidence is satisfied by **recorded local runs attached to each perf
commit**, which is also the precedent set by feature 010 ("benchmark measurements are evidence attached
to the work, not a CI-enforced performance gate"). Repairing and re-enabling the CI comparison is
CI/release engineering, overlaps #99 and #100, and belongs to milestone 19 (*CI hygiene & release
safety*). It is **out of scope here** and recorded in the plan rather than silently left broken.

**Correction owed to `TESTING.md`.** Its "Supported sbt majors" section states that `Jmh/run` "was
verified working on both majors on 2026-08-20 — no exemption needed". That claim is now false, and the
commit that broke it landed two days after it was written. The harness commit must correct that
sentence in the same change.
