# Implementation Plan: Perf — Templates & Cookies, Locale Correctness, OpenNFR v0.8.0

**Branch**: `327-perf-templates-cookies` | **Date**: 2026-09-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-perf-templates-cookies/spec.md`

## Summary

Milestone v1.27.0 removes allocation from two hot paths (#126 escaping, #137 cookie parsing), closes
#127 on refuted evidence, follows upstream OpenNFR to `v0.8.0` (#328), and fixes three defects found
while scoping (#333). Every performance change must be confirmed by a benchmark run before and after —
which is why the first work item is not a performance change at all: **`sbt Jmh/run` does not start**,
so the gate cannot be met until it does.

The technical approach, in the order the work must land:

1. **Unblock and instrument.** One `Jmh`-scoped classpath line restores the forked harness; the
   `packageBin` filter is widened to cover JMH's `META-INF` resources and bare directory mappings; the
   benchmarks #143/#144 ask for are added, including the **escaping-heavy fixtures the current suite
   entirely lacks**.
2. **Freeze the behaviour.** A verbatim copy of today's escaping and cookie parsing becomes a test-only
   oracle; ScalaCheck drives live-vs-oracle equality over generated inputs plus enumerated boundaries,
   comparing results, thrown exceptions **and** captured warnings. This lands before any production
   file is touched.
3. **Fix the locale defects.** `Locale.ROOT` in secret-key matching (a disclosure path) and in cookie
   attribute matching — two separate commits, each with a test that fails before and passes after under
   a pinned Turkish JVM.
4. **Optimize.** Escaping becomes append-in-place with a no-escape fast path; cookie parsing becomes a
   fold that never builds the intermediate attribute map. Each carries its own before/after measurement.
5. **Follow upstream.** The metric axis gains two names and a bidirectional pairing rule, the group-only
   selector starts rendering, and parity becomes a whole-set comparison.

The load-bearing insight from research is that **none of the optimizations needs new machinery** — the
escaping fix removes two helpers rather than adding any, the cookie fix stays inside the project's
string-handling style rules (no exemption needed), and rendering a group duration needs **no new Gatling
call**, only a validation rule. What needs care is not the code but the parity: three separate
misreadings of the cookie fold were measured against the live parser and produced 12,150, 7,601 and 408
divergences respectively.

## Technical Context

**Language/Version**: Scala 2.13.18 (Java 17 `--release` target); Java 17 for the facade

**Primary Dependencies**: Gatling 3.13.5 (`Provided`), gatling-shared-model 0.0.11 (`Provided`),
Circe, PureConfig, json4s, Jackson, Scala Logging. **No new dependency is introduced by this feature.**

**Storage**: N/A

**Testing**: ScalaTest + ScalaCheck 1.20.0 (already `Test` scope, `project/Dependencies.scala:78-83`),
ScalaMock for leaf collaborators, JUnit 5 via sbt-jupiter-interface for facade delegation, JMH 1.37 for
measurement

**Target Platform**: JVM library published to Maven Central; consumed inside a Gatling load generator

**Project Type**: Published Scala library with a thin Java/Kotlin facade

**Performance Goals**: Strictly lower `gc.alloc.rate.norm` (B/op) on every changed path, with no
throughput regression. Research measured 50–93% allocation reduction for escaping depending on fixture;
the cookie fold's ratios are not yet reproduced under JMH.

**Constraints**: Byte-identical observable output on every optimized path (FR-012). Coverage floor
**75% statement / 66% branch** (`build.sbt:90-91` — note the plan template's "65%/60%" is stale). Every
gate green on both sbt majors (1.13.0 pinned, 2.0.6 secondary). MiMa reports zero new issues against
1.26.0.

**Scale/Scope**: 9 commits across 4 modules (`templates`, `storage`, `config`, `assertions/opennfr`),
3 build settings, ~40 occurrences of a retired metric name to migrate, 5 new test-scope files.

**Measurement tooling**: `-prof gc` only. `async`, `perf`, `perfnorm`, `perfasm` and `dtraceasm` are all
unsupported on a stock development machine here, so no acceptance criterion may be written against them.

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | A maintainer runs the benchmark harness on a clean checkout | Build gate | Listing the benchmarks exits successfully and names every benchmark the project defines. Negative control: with the new setting removed the same command fails with the missing-main-class error, so the gate proves the fix rather than the ambient state. Repeated on both sbt majors. |
| FR-002 | A perf commit claims an allocation win | Build gate | For each perf commit, a before and an after run of the benchmarks covering its path, recorded in the commit or PR body, reporting per-operation allocation. Absence of either recording blocks the commit. |
| FR-003 | A rewrite that fast-paths escape-free text regresses the escaping path | Unit/Functional | Assert the benchmark fixture set contains at least one JSON case and one XML case whose strings contain characters from the escape set, including a control character, and that the pre-existing escape-free cases survive. Boundary: a fixture whose only escapable character is its last. |
| FR-004 | Someone adds a benchmark class without the naming marker | Unit/Functional | Reflect over the test classpath for every subtype of the benchmark base class and assert each simple name contains the marker substring. Negative: a deliberately misnamed local subtype in the test source makes the assertion fail. |
| FR-005 | A maintainer runs benchmarks locally, then packages a release | Build gate | Package after a clean build and after a benchmark run; assert the two artifact entry lists are identical, and that neither contains benchmark classes, benchmark metadata resources, or generated-package directory entries. Negative: with the widened filter reverted, the two lists differ by the metadata entries. |
| FR-006 | A candidate optimization turns out measurement-neutral | Build gate | The measurement pair is compared; where it shows no improvement the change is reverted and the issue closed citing the recording. Evidenced by the closure text naming the measurement, not by a code test. |
| FR-007 | A configuration key spelled `AUTHORIZATION` on a Turkish-locale host | Unit/Functional | Under a JVM pinned to Turkish, assert the value of a capitalised secret-bearing key is replaced by the mask, for keys exercising all three decision points: word splitting, the separator-less suffix floor, and an operator-supplied extra sensitive key. Fails before the fix, passes after. Boundary: the same keys under an unaffected locale are unchanged. |
| FR-008 | A masking change accidentally narrows what is treated as secret | Unit/Functional | Over the full set of keys the existing masking suite exercises, assert every key masked before the change is still masked after it. Negative: a deliberately narrowed word list makes the assertion fail. |
| FR-009 | A server sends `DOMAIN=` in capitals to a Turkish-locale generator | Unit/Functional | Under a JVM pinned to Turkish, assert the parsed cookie carries the domain the header sent, not the fallback default. Fails before the fix, passes after. |
| FR-010 | A reviewer needs to see one behavioural change at a time | Build gate | The masking fix, the cookie locale fix and the cookie rewrite are three commits; each locale commit contains a test that fails at its parent and passes at its tip. Verified by checking out the parent and running the new test. |
| FR-011 | A rewrite changes an escape sequence nobody wrote a test for | Unit/Functional | Over a generated population plus enumerated boundary tables, assert the live implementation and the frozen oracle agree on the produced text, on the thrown exception class and message, and on the ordered list of emitted warnings. On any difference the failure names the offending input and both outcomes. Boundary tables enumerate the escape set exhaustively rather than sampling it, and the cookie alphabet includes the dotless and dotted Turkish letters. |
| FR-012 | Any optimized path | Unit/Functional | Every pre-existing suite for the changed files passes unmodified; no expected value in an existing test is edited as part of an optimization commit. |
| FR-013 | An optimization that would change output | Unit/Functional | The equivalence suite is the arbiter: where it reports a difference the optimization is narrowed or dropped rather than the expectation being changed. Evidenced by the suite staying green with unmodified expectations. |
| FR-014 | A five-field object of plain strings | Unit/Functional | Assert produced JSON and XML are byte-identical to the frozen oracle's for the same fields, including field names containing escapable characters — the name path is escaped on every branch and is the half a value-only rewrite would miss. |
| FR-015 | A field name and value with nothing escapable | Unit/Functional | Assert output is identical and that the escape helper takes its no-allocation path — observed by asserting the scan reports no escapable character for that input, which is the condition the fast path branches on. Boundary: empty string. |
| FR-016 | A control character with no dedicated short escape | Unit/Functional | Assert the emitted escape is four lowercase hexadecimal digits, zero-padded, for every control character in range including both ends, and identical to the frozen oracle's. Negative: the five characters that do have dedicated short escapes must not reach the hexadecimal form. |
| FR-017 | Concurrent body assembly on load-generator worker threads | Unit/Functional | Assert the escaping helpers hold no shared mutable state — evidenced by the same inputs assembled concurrently from several threads producing outputs identical to the single-threaded results. |
| FR-018 | A header with duplicate attributes and an unusable lifetime value | Unit/Functional | Assert the parsed cookie equals the frozen oracle's for duplicate domain, duplicate lifetime where only one occurrence is valid, a flag repeated after a valued spelling, an empty-valued domain, a trailing separator, a separator-only line, and a value containing the separator. Assert exactly one warning fires, naming the last occurrence. Boundary: the separator-only line must raise the same error it raises today, not return empty. |
| FR-019 | The rewritten parser and escaper are reviewed against house style | Build gate | The lint gate passes with no new suppression; no banned string-handling construct is introduced. Where one were needed it would appear in Complexity Tracking — none is. |
| FR-020 | A group metric written under a request selector, and vice versa | Unit/Functional | Assert a group-duration predicate under every non-group selection is refused, and a request-duration predicate under the group-only selection is refused, each with the message quoting the row that refused it. Boundary: a predicate carrying no metric under the group-only selection is also refused. |
| FR-021 | A requirement naming only a group hierarchy | Unit/Functional | Assert one assertion is produced, scoped to that group's path, with the statistic and threshold the document states. Negative: a hierarchy containing a wildcard element is still refused, with or without a request name. |
| FR-022 | An existing document carrying the retired metric name | Unit/Functional | Assert rendering is refused and the message names both the retirement and the replacement to write instead. Negative: the refusal is not a generic unknown-metric message. |
| FR-023 | The reference document that previously rendered ten of eleven | Unit/Functional | Assert the rendered set equals the deprecated builder's set with nothing subtracted, and that the count is eleven with no duplicates. Negative: the previously subtracted assertion is now a member of the rendered set. |
| FR-024 | An engineer reconciling a chart against a failing assertion | Unit/Functional | Assert the shipped documentation states the group quantity is the sum of enclosed operation durations rather than elapsed traversal time, and that the report-only setting never reaches an assertion. Scoped to the section that makes the claim, so page-wide prose cannot satisfy it. |
| FR-025 | The tracked upstream release moves | Unit/Functional | Assert every file that states the tracked release states the new one, and that the documentation guard which today asserts the group-only case has no equivalent now asserts it has one and passes. Negative: a file left at the old version fails the guard by name. |
| FR-026 | A fixture left on the retired metric name | Unit/Functional | Assert no fixture, example or shipped document carries the retired name, and that each migrated document still renders to the same assertions it did under the old name where the selection is unchanged. |
| FR-027 | A consumer upgrades from 1.26.0 | Build gate | The binary-compatibility check reports zero new issues, and the generated dependency descriptor is byte-identical to the previous release's apart from the version string. |
| FR-028 | An issue closed on refuted evidence | Unit/Functional | For #127, assert the body produced through the request-builder entry point equals the body produced from an equivalent pre-built field list, and record the refutation in writing. For the rest, each issue's regression test asserts exact output parity with at least one boundary case. |

**Layer note.** Seven rows above (FR-001, 002, 005, 006, 010, 019, 027) are marked *Build gate* rather than one of the six model layers. These
are requirements about the build, the artifact, the measurement record and the commit shape — none has
a code-test form, and inventing a unit test for "the harness starts" would assert the test classpath
rather than the forked one. This deviation is recorded in Complexity Tracking below.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — The Java facade is untouched by the optimizations. For
  #328, research established the facade needs **no new call**: its DSL exposes one time-metric entry
  point exactly as the Scala side does, so the change is symmetric and stays in the Scala core. No
  facade-only logic is added.
- [x] **II. Backward Compatibility** — No public Scala/Java signature changes; no serialized format
  changes; no feeder output or session-variable changes. Two deliberate behavioural changes are
  justified in Complexity Tracking: the OpenNFR rendering change (explicitly experimental surface,
  outside the binary-compatibility guarantee) and the cookie attribute-name narrowing for one non-ASCII
  spelling. MiMa must report zero new issues.
- [x] **III. Test Discipline** — Test Model above is filled per FR with real cases and code-free
  sketches. Work is test-first: the frozen-oracle equivalence suite lands **before** any production
  file is touched, and each locale fix carries a test that fails at its parent commit. Layers follow
  TESTING.md; no Testcontainers layer is needed (nothing here touches Redis, Vault or JDBC); the
  Gatling runtime is not mocked.
- [x] **IV. Small, Focused Changes** — No new dependencies. Two build settings change, both minimal and
  both verified on **each** supported sbt major as the constitution requires. No opportunistic
  refactors: the sources-jar leak and the CI benchmark pipeline are both left out and recorded.
- [x] **V. Release Integrity** *(release PRs only)* — N/A; this is not a release PR.

## Project Structure

### Documentation (this feature)

```text
specs/014-perf-templates-cookies/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── escaping.md      # observable contract of body-text escaping
│   ├── cookie-parsing.md# observable contract of Set-Cookie parsing
│   └── reach-v0.8.0.md  # OpenNFR metric/selection pairing, transcribed
├── checklists/
│   └── requirements.md  # spec quality checklist
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
build.sbt                                    # Jmh run classpath; packageBin filter
TESTING.md                                   # correct the stale "Jmh/run verified" claim

src/main/scala/org/galaxio/gatling/
├── templates/Syntax.scala                   # #126 append-in-place escaping
├── templates/SyntaxBenchmark.scala          # #143 escaping-heavy fixtures
├── storage/CookieParser.scala               # #137 fold; #333 cookie locale
├── storage/CookieParserBenchmark.scala      # #144 new
├── config/ConfigValueMasking.scala          # #333 masking locale
└── assertions/opennfr/                      # #328
    ├── Reach.scala                          # scope threading + pairing
    ├── Model.scala                          # tracked release
    └── OpenNfrAssertions.scala              # tracked release

src/main/java/org/galaxio/gatling/javaapi/OpenNfrAssertions.java   # tracked release

src/test/scala/org/galaxio/gatling/
├── parity/                                  # NEW — frozen oracles + equivalence suites
├── templates/SyntaxSpec.scala               # unchanged expectations
├── storage/CookieParserSpec.scala           # + locale suite (forked, Turkish JVM)
├── config/…MaskingSpec                      # + locale suite
└── assertions/opennfr/                      # ParitySpec, ReachSpec, MigrationTableSpec, …

src/test/resources/opennfr/{nfr,group-only}.yaml    # metric-name migration
examples/scala-sbt-example/…/opennfr-e2e.yaml       # metric-name migration
docs/opennfr.md                                     # group quantity + tracked release
```

**Structure Decision**: Single published library, unchanged. Benchmarks stay on the production
classpath under `src/main/scala` — that placement is what makes them discoverable by the JMH generator
and is why the `*Benchmark*` naming rule is load-bearing rather than cosmetic. Moving them to a
separate subproject was considered and rejected in research (R1).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Seven Test Model rows use a *Build gate* layer rather than one of the six model layers | They are requirements about the build, the published artifact, the measurement record and the commit shape. "The forked benchmark harness starts", "the artifact contains no benchmark metadata" and "a measurement pair exists" have no code-test form. | Writing unit tests for these would assert the *test* classpath, not the forked benchmark classpath, and would assert an in-memory mapping rather than the packaged artifact — green tests that prove nothing about the property. Honest gate designation beats a vacuous test. |
| OpenNFR rendering behaviour changes within a MINOR release, where Constitution II defaults to MAJOR for behavioural redefinition | Upstream retired a metric name outright with no acceptance window, and the renderer's stated contract is to transcribe the published tables and derive nothing of its own. | The surface ships explicitly experimental and outside the binary-compatibility guarantee, stated in `OpenNfrAssertions.scala`/`.java` and carried from feature 013. A MAJOR bump for an experimental surface would freeze the rest of the library behind a pre-1.0 upstream's release cadence. |
| The cookie locale fix **narrows** one spelling: a dotted capital letter that a Turkish host currently folds onto a valid attribute name stops being recognised | Locale-independent matching is the whole point of the fix; the current acceptance is an accident of one host locale, and the spelling is not a valid ASCII cookie attribute name. | Preserving it would mean keeping a locale-dependent branch — reintroducing the defect being fixed. Pinned by a test so it cannot later be mistaken for a regression. |
| Three `build.sbt` settings change in a feature whose headline is library performance | The measurement gate the feature is required to meet cannot run otherwise, and the packaging defect is caused by making benchmark runs routine. | Deferring either would leave the milestone unable to satisfy its own acceptance criteria (FR-002) or shipping benchmark metadata to consumers. Both are minimal and separately committed. |

## Out of scope, recorded rather than left silent

- **The CI benchmark pipeline.** `release.yml` already contains a before/after comparison, hard-disabled
  and broken three further ways (research R7). Repairing it is CI/release engineering, overlaps #99 and
  #100, and belongs to milestone 19.
- **The sources-jar benchmark leak.** `packageSrc` was never covered by the `packageBin` filter, so the
  sources artifact ships benchmark sources unconditionally. Different mapping key, different decision.
- **Moving benchmarks to their own subproject** — the structurally clean fix for the whole class of
  packaging/coverage/lint exclusions, rejected here as out of proportion (research R1).
