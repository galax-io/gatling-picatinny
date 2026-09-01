---

description: "Task list for 014-perf-templates-cookies"
---

# Tasks: Perf — Templates & Cookies, Locale Correctness, OpenNFR v0.8.0

**Input**: Design documents from `specs/014-perf-templates-cookies/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. Constitution III mandates test-first (red → green → refactor). Every behavioural
task below is preceded by its failing test.

**Organization**: Grouped by user story. Each story is one or more semantic commits, each green on its
own (`sbt compile "Test/testOnly"`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US6, matching the spec's user stories
- Exact file paths in every description

## Path Conventions

Single published library. Production Scala in `src/main/scala/org/galaxio/gatling/…`, Java facade in
`src/main/java/…`, tests in `src/test/scala/…`, benchmarks on the **production** classpath under
`src/main/scala/…` (load-bearing — see FR-004).

## Commit map

Ten commits. `1 issue = 1 commit`, each independently green.

Commit 0 is mandatory and comes first: AGENTS.md requires the `specs/NNN-*/` artifacts to land as
their own `docs(speckit)` commit **before** any `feat`/`fix`, never folded into implementation.

#333 is a tracking issue covering four independent defects, so it cannot satisfy `1 issue = 1 commit`
as it stands — four commits would all close one issue. **T085 splits it before the PR is raised**;
until then the Closes column names the split rather than #333 itself.

| # | Commit | Story | Closes |
|---|---|---|---|
| 0 | `docs(speckit): add 014-perf-templates-cookies spec/plan/tasks` | — | — |
| 1 | `build(jmh): restore the forked benchmark run classpath` | US1 | split of #333 (T085) |
| 2 | `build(release): keep benchmark output out of the published artifact` | US1 | split of #333 (T085) |
| 3 | `test(benchmarks): escaping-heavy fixtures and a cookie benchmark` | US1 | #143, #144 |
| 4 | `fix(config): match secret-bearing keys independently of the host locale` | US2 | split of #333 (T085) |
| 5 | `fix(storage): match cookie attribute names independently of the host locale` | US2 | split of #333 (T085) |
| 6 | `test(parity): freeze escaping and cookie-parsing behaviour` | US3 | — |
| 7 | `perf(templates): append escaped text in place (#126)` | US4 | #126 |
| 8 | `perf(storage): parse each cookie line in one pass (#137)` | US5 | #137 |
| 9 | `feat(assertions): follow opennfr v0.8.0 metric axis (#328)` | US6 | #328 |

#127 closes with no commit — see T078.

---

## Phase 1: Setup — record the starting conditions

**Purpose**: Several later gates are only meaningful against a recorded failure. Capture them first, or
the checks prove nothing.

- [X] T001 Run `sbt "Jmh/run -l"` and save the `ClassNotFoundException: org.openjdk.jmh.Main` output to `specs/014-perf-templates-cookies/evidence/before-jmh-run.txt`
- [X] T002 [P] Package with and without a prior benchmark run per [quickstart.md](quickstart.md) §2 and record the entry-count delta and the leaked entries in `specs/014-perf-templates-cookies/evidence/before-packaging.txt` — the summary only; the full listings are regenerable and are not committed
- [X] T003 [P] Record the `sbt makePom` baseline comparison for FR-027 in `specs/014-perf-templates-cookies/evidence/pom-baseline.md` — the generated POM is dynver-stamped and regenerable, so the finding is recorded, not the file
- [X] T004 [P] Confirm from `src/main/scala/org/galaxio/gatling/templates/SyntaxBenchmark.scala` that no fixture string contains any character from the escape sets in [contracts/escaping.md](contracts/escaping.md) §2–§3, and record the finding in `specs/014-perf-templates-cookies/evidence/before-benchmark-coverage.md`

**Checkpoint**: The three defects US1 and US2 fix are now documented as failing.

---

## Phase 2: Foundational — the locale test harness

**Purpose**: Two stories need to exercise code under a non-ASCII-folding default locale.

**⚠️ APPROACH CHANGED DURING IMPLEMENTATION — 2026-09-01.** The plan called for a forked
`Test / testGrouping`. That is **not expressible across both sbt majors**: sbt 2 caches task values
and has no `JsonFormat[Seq[Tests.Group]]`, so the build fails to load on 2.0.6 unless the value is
wrapped in `Def.uncached` — and `javap` on `main_2.12-1.13.0.jar` confirms `sbt.Def.uncached` does
**not exist** on sbt 1.13.0, so wrapping it breaks the pinned major instead. Both failures were
reproduced. Constitution IV forbids a single-major build capability without a recorded exemption, and
an exemption here would mean the locale tests only run on one major — unacceptable for a
correctness fix.

Adopted instead: the **in-repo precedent** (`src/test/scala/org/galaxio/gatling/testutil/LogCapture.scala`),
which already solves this exact class of problem — a test that must mutate JVM-global state while
suites run in parallel. **No build change at all**, identical on both majors, no exemption.

Why it is safe here specifically: after both locale fixes land, no production code consulted by a
concurrent suite still uses the default locale for a decision that a Turkish locale would change.
The other default-locale `toLowerCase` sites were enumerated during research and are unreachable —
`IntensityConverter:36` (`rps`/`rpm`/`rph`), `ClaimsBuilder:178` (`true`/`false`),
`VaultFeeder:199,202` (`http`, loopback names) — none of those tokens carries an affected letter.

- [X] T005 ~~Add a `Test / testGrouping` partition in `build.sbt`~~ — **attempted, reverted on evidence**; see the note above. `build.sbt` carries no locale-related change.
- [X] T006 Create `src/test/scala/org/galaxio/gatling/testutil/LocaleFixture.scala` following the `LogCapture` pattern: a `synchronized` window that saves the default locale, sets the requested one, runs the body, and restores it in a `finally`, with a scaladoc stating why global mutation is safe here and which sites were checked
- [ ] T007 [X-verified] Confirm the JUnit 5 facade suites are unaffected — `sbt "Test/testOnly org.galaxio.gatling.javaapi.*"` ran 105 tests, 0 failed
- [X] T008 Confirm the fixture behaves identically on both majors once the first locale suite exists, via `sbt "Test/testOnly *Locale*"` and `sbt --sbt-version 2.0.6 "Test/testOnly *Locale*"`

**Checkpoint**: A locale can be pinned deterministically without disturbing the rest of the suite.

---

## Phase 3: User Story 1 — the benchmark gate (Priority: P1) 🎯 MVP

**Goal**: The harness runs, measures the cases the milestone is about, and never reaches consumers.

**Independent test**: `sbt "Jmh/run -l"` lists every benchmark and exits successfully; `-lprof` offers
the GC profiler; packaging is identical with and without a prior benchmark run.

### Commit 1 — restore the forked run classpath

- [X] T009 [US1] Add `Jmh / dependencyClasspathAsJars ++= (Compile / dependencyClasspath).value` to the root project's settings in `build.sbt`, immediately after the `org.openjdk.jmh` → `Provided` rewrite, with a comment explaining that a forked `Jmh / run` reads `fullClasspathAsJars` and that the plugin only patches the non-forked key
- [X] T010 [US1] Verify `sbt "Jmh/run -l"` now lists every benchmark and exits successfully, and that removing the line reproduces T001's failure — the negative control that proves the fix rather than the ambient state
- [X] T011 [US1] Verify `sbt "Jmh/run -lprof"` reports the GC profiler as supported, and record in `specs/014-perf-templates-cookies/evidence/` that the sampling profilers are unavailable so no criterion is written against them
- [X] T012 [US1] Verify the fix on the secondary sbt major with `sbt --sbt-version 2.0.6 "Jmh/run -l"`
- [X] T013 [US1] Verify `sbt makePom` output is byte-identical to T003's baseline apart from the version string — the fix must be dependency-neutral
- [X] T014 [US1] Correct the stale claim in `TESTING.md` § "Supported sbt majors and per-major gate availability" that `Jmh/run` "was verified working on both majors on 2026-08-20"; state that it regressed and when, and that it is verified again
- [X] T015 [US1] Run `sbt scalafmtSbtCheck` and confirm `build.sbt` is clean

### Commit 2 — keep benchmark output out of the artifact

- [X] T016 [US1] Add a shared "is this path present only because benchmarks were run?" predicate beside the existing shared benchmark definitions at the top of `build.sbt`, covering benchmark classes, JMH's two `META-INF` resources, and bare generated-package directory mappings
- [X] T017 [US1] Replace the `Compile / packageBin / mappings` filter in `build.sbt` with that predicate, keeping the comment that explains why benchmarks are on the production classpath at all
- [X] T018 [US1] Verify packaging is now identical with and without a prior benchmark run per [quickstart.md](quickstart.md) §2, and that reverting the filter reproduces T002's difference
- [X] T019 [US1] Confirm the `build.sbt` filter excludes no production path by accident: regenerate the clean-build jar entry list per [quickstart.md](quickstart.md) §2 and assert it is unchanged

### Commit 3 — the benchmarks the gate needs

- [X] T020 [P] [US1] Add escaping-heavy JSON fixtures to `src/main/scala/org/galaxio/gatling/templates/SyntaxBenchmark.scala` exercising the complete JSON escape set from [contracts/escaping.md](contracts/escaping.md) §2, including a control character, plus a case whose only escapable character is last so the prefix copy is exercised
- [X] T021 [P] [US1] Add escaping-heavy XML fixtures to the same file exercising the complete XML escape set from [contracts/escaping.md](contracts/escaping.md) §3; keep every pre-existing escape-free fixture so the fast path is measured too
- [X] T022 [P] [US1] Create `src/main/scala/org/galaxio/gatling/storage/CookieParserBenchmark.scala` extending the project's benchmark base class, parsing a realistic multi-cookie newline-separated header with a full attribute set per cookie (path, domain, max-age, secure, http-only) — attribute count per cookie is what drives the allocation, not cookie count
- [X] T023 [US1] Write a test in `src/test/scala/org/galaxio/gatling/jmh/BenchmarkNamingSpec.scala` that reflects over every subtype of the benchmark base class and asserts each simple name contains the benchmark marker substring, with a deliberately misnamed local subtype as the negative case (FR-004)
- [X] T024 [US1] Verify the new benchmarks are absent from the coverage report and from the packaged artifact by running `sbt clean coverage compile "Test/testOnly" coverageReport` and re-running the T018 packaging check
- [X] T025 [US1] Record the **before** measurement pair for both changed paths — `sbt "Jmh/run -prof gc …"` over the escaping and cookie benchmarks — into `specs/014-perf-templates-cookies/evidence/before-*.json`, since US4 and US5 compare against it

**Checkpoint**: The measurement gate exists and is trustworthy. US4 and US5 are now unblocked.

---

## Phase 4: User Story 2 — locale never decides recognition (Priority: P2)

**Goal**: Secrets stay masked and cookie domains stay honoured whatever locale the generator runs in.

**Independent test**: Under a Turkish-pinned JVM, a capitalised secret-bearing key is masked and a
capitalised cookie domain attribute is honoured — both failing before, passing after.

**⚠️ Depends on Phase 2** (the forked locale group).

### Commit 4 — secret-key matching (the disclosure path)

- [X] T026 [US2] Write a failing suite `src/test/scala/org/galaxio/gatling/config/ConfigValueMaskingTurkishLocaleSpec.scala` asserting that capitalised secret-bearing keys are masked, covering all three decision points independently: the camel/snake/kebab word splitting, the separator-less suffix floor, and an operator-supplied extra sensitive key
- [X] T027 [US2] Extend that suite with the exact terms at risk — those containing an ASCII capital `I` after upper-casing: the credential, authorization, api-key, private-key and client-secret spellings — and assert each is masked
- [X] T028 [US2] Confirm `src/test/scala/org/galaxio/gatling/config/ConfigValueMaskingTurkishLocaleSpec.scala` fails at this commit's parent, proving it pins the defect rather than the fix
- [X] T029 [US2] Change the word-lowering in `src/main/scala/org/galaxio/gatling/config/ConfigValueMasking.scala` to a locale-independent rule, applying it at every place the matching decision is made — including the suffix floor and the extra-key normalisation, not only the primary splitter
- [X] T030 [US2] Add a one-directional guard to `src/test/scala/org/galaxio/gatling/config/ConfigValueMaskingSpec.scala` asserting every key masked before the change is still masked after it, over the full key set that suite exercises (FR-008), with a deliberately narrowed word list as the negative case
- [X] T031 [US2] Run `sbt "Test/testOnly org.galaxio.gatling.config.*"` and confirm the pre-existing masking suites pass with no edited expectations

### Commit 5 — cookie attribute-name matching

- [X] T032 [US2] Write a failing suite `src/test/scala/org/galaxio/gatling/storage/CookieParserTurkishLocaleSpec.scala` asserting a header spelling the domain attribute in ASCII capitals yields the domain the server sent, not the fallback default
- [X] T033 [US2] Add to that suite the **deliberate narrowing** case from [contracts/cookie-parsing.md](contracts/cookie-parsing.md) §7: a header spelling the attribute with the dotted capital is recognised today under Turkish and must **stop** being recognised, pinned so it is not later mistaken for a regression
- [X] T034 [US2] Assert in `src/test/scala/org/galaxio/gatling/storage/CookieParserTurkishLocaleSpec.scala` that every other recognised attribute name, in every ASCII letter case, is unaffected under both an affected and an unaffected locale
- [X] T035 [US2] Confirm `src/test/scala/org/galaxio/gatling/storage/CookieParserTurkishLocaleSpec.scala` fails at this commit's parent
- [X] T036 [US2] Change the attribute-key lowering in `src/main/scala/org/galaxio/gatling/storage/CookieParser.scala` to the locale-independent rule — this commit changes nothing else in that file
- [X] T037 [US2] Run `sbt "Test/testOnly org.galaxio.gatling.storage.*"` and confirm the pre-existing `CookieParserSpec` passes with no edited expectations

**Checkpoint**: Both disclosure and misrouting defects are closed, each in its own reviewable commit.

---

## Phase 5: User Story 3 — behaviour frozen before anything is optimized (Priority: P3)

**Goal**: Today's escaping and cookie parsing become a reference the optimizations are checked against.

**Independent test**: The equivalence suite is green at the commit that introduces it, with no
production file yet modified by US4 or US5.

**Ordering note — this is deliberate and was verified.** The oracle is frozen **after** the Phase 4
locale fixes, not before. Outside Turkish the two lowering rules agree on every attribute key, but under
Turkish they differ on two spellings by design; freezing beforehand would make the equivalence suite
fail inside the Phase 2 locale group. Freezing afterwards makes it valid under every locale.

- [X] T038 [P] [US3] Create `src/test/scala/org/galaxio/gatling/parity/FrozenSyntaxReference.scala` as a verbatim copy of the current escaping and body-assembly region of `src/main/scala/org/galaxio/gatling/templates/Syntax.scala`, wrapped in an object, importing the public ADT from the live object and adapted in no other way; the header carries the exact `git show <sha>:<path>` command that reproduces it
- [X] T039 [P] [US3] Create `src/test/scala/org/galaxio/gatling/parity/FrozenCookieParserReference.scala` the same way from `src/main/scala/org/galaxio/gatling/storage/CookieParser.scala`. It **must** live in the `parity` package, not `storage` — the logging framework derives logger names from the class, so an oracle under `storage` would have its warnings captured by the live suite's log capture and make warning-parity vacuously true (see [data-model.md](data-model.md) §4)
- [X] T040 [US3] Create `src/test/scala/org/galaxio/gatling/parity/ParityGenerators.scala` with the field-name, field-value and raw-header generators, plus boundary tables that **enumerate** rather than sample: the complete JSON and XML escape sets, both ends of the control-character range, empty strings, strings whose only escapable character is first or last, and surrogate halves
- [X] T041 [US3] Extend the cookie alphabet in `src/test/scala/org/galaxio/gatling/parity/ParityGenerators.scala` with the dotless and dotted Turkish letters. Research measured three separate 20,000-input corpora reporting zero differences for behaviour-changing variants until those two characters were present — an ASCII-only generator cannot see the defect class this suite exists to catch
- [X] T042 [US3] Add the enumerated cookie boundary shapes from [contracts/cookie-parsing.md](contracts/cookie-parsing.md): duplicate attributes, an empty-valued domain, a valueless attribute, trailing and repeated separators, a separator-only line, a value containing the separator, a line with no separator, and a blank line
- [X] T043 [US3] Create `src/test/scala/org/galaxio/gatling/parity/SyntaxParitySpec.scala` asserting live-vs-frozen equality of produced text over the generated population and the boundary tables, failing with the offending input and both outputs
- [X] T044 [US3] Create `src/test/scala/org/galaxio/gatling/parity/CookieParserParitySpec.scala` asserting equality of the **full observable outcome** — parsed result, thrown exception class and message, and the complete ordered list of emitted warnings ([data-model.md](data-model.md) §6). Comparing the return value alone would let a rewrite change an error or a warning and still pass
- [X] T045 [US3] Confirm the parity suites need **no** locale exclusion, and record why in the header of `src/test/scala/org/galaxio/gatling/parity/CookieParserParitySpec.scala`: Phase 2 ships no test-locale grouping at all, and Phase 5 freezes the oracle *after* the locale fixes, so live and frozen agree under every locale — an exclusion here would suppress exactly the Turkish inputs that must now agree
- [X] T046 [US3] Run `sbt "scalafixAll --check"` over the new test sources, resolving the research question left open in R6 about semantic-database availability for these files
- [X] T047 [US3] Run `sbt "Test/testOnly org.galaxio.gatling.parity.*"` and confirm green with no production file modified; add a note to the headers of `FrozenSyntaxReference.scala` and `FrozenCookieParserReference.scala` that any later diff to them is a hard review stop — the drift failure mode has no automated guard

**Checkpoint**: Both optimizations now have a reference to be judged against.

---

## Phase 6: User Story 4 — escaping stops allocating per name and per value (Priority: P4)

**Goal**: Same escaped text, written straight into the body, with the untouched case costing nothing.

**Independent test**: Parity suite green with unmodified expectations, plus a before/after measurement
pair showing strictly lower per-operation allocation on both an escaping-heavy and an escape-free case.

**⚠️ Depends on Phase 3 (measurement) and Phase 5 (parity).**

- [X] T048 [US4] Add unit cases to `src/test/scala/org/galaxio/gatling/templates/SyntaxSpec.scala` asserting exact output for every character in the JSON escape set, every character in the XML escape set, and control characters at both ends of the range — including escapable characters in **field names**, which are escaped on every branch and are the half a value-only rewrite misses
- [X] T049 [US4] Add a concurrency case to `src/test/scala/org/galaxio/gatling/templates/SyntaxSpec.scala` asserting the same inputs assembled from several threads produce outputs identical to the single-threaded results (FR-017)
- [X] T050 [US4] Replace the two string-returning escape helpers in `src/main/scala/org/galaxio/gatling/templates/Syntax.scala` with append-in-place helpers plus a first-escape-index scan, per [contracts/escaping.md](contracts/escaping.md) §5. When the scan finds nothing, append the whole string in one bulk copy with no allocation; otherwise bulk-copy the clean prefix and run the existing branch list verbatim, in the same order, from that index
- [X] T051 [US4] Replace the control-character escape's general-purpose text formatting in `src/main/scala/org/galaxio/gatling/templates/Syntax.scala` with direct hex-digit writes, preserving four lowercase zero-padded digits exactly
- [X] T052 [US4] Update the call sites in `src/main/scala/org/galaxio/gatling/templates/Syntax.scala` so no caller needs a string; the XML field-name path keeps an index rather than re-escaping for the closing tag
- [X] T053 [US4] Add the comment tying the fast-path predicate to the branch list, per [contracts/escaping.md](contracts/escaping.md) §6 — the predicate covers the short-escape characters only because they sit below the printable boundary, and a future escape added to one and not the other emits raw output silently
- [X] T054 [US4] Record the **after** measurement pair into `specs/014-perf-templates-cookies/evidence/after-syntax.json` and compare per-operation allocation against T025's baseline; confirm strictly lower on the escaping-heavy and escape-free cases and no throughput regression. If the pair shows no improvement, revert and close #126 on the recording (FR-006)
- [X] T055 [US4] Run `sbt "Test/testOnly org.galaxio.gatling.parity.* org.galaxio.gatling.templates.*"` and confirm every suite passes with **no edited expectations**

**Checkpoint**: #126 is closed with its measurement pair attached.

---

## Phase 7: User Story 5 — cookie restoration reads each header once (Priority: P5)

**Goal**: Same parsed cookies from a single traversal, with no intermediate attribute map.

**Independent test**: Parity suite green with unmodified expectations, plus a before/after pair on a
multi-attribute header.

**⚠️ Depends on Phase 3 (measurement) and Phase 5 (parity).**

- [X] T056 [US5] Add unit cases to `src/test/scala/org/galaxio/gatling/storage/CookieParserSpec.scala` pinning the contract points nothing pins today: duplicate-attribute precedence, flag stickiness across a valued spelling, empty-valued domain versus absent domain, and — critically — that a separator-only line raises the error it raises today rather than returning empty
- [X] T057 [US5] Add a case to `src/test/scala/org/galaxio/gatling/storage/CookieParserSpec.scala` asserting exactly one warning fires for a repeated unusable lifetime value, naming the **last** occurrence
- [X] T058 [US5] Rewrite the per-line parse in `src/main/scala/org/galaxio/gatling/storage/CookieParser.scala` as a fold over the existing separator split, per [contracts/cookie-parsing.md](contracts/cookie-parsing.md) §8 — dropping the array-wide trim, the tail array, the tuple array and the intermediate map, and accumulating into a private case class. Keep the total-access-free first-part access, keep **both** the key trim and the value trim, and carry the lifetime value **unparsed** so the warning fires once after the fold. Research measured 12,150 / 7,601 / 408 divergences for each of those three misreadings
- [X] T059 [US5] Record the **after** measurement pair into `specs/014-perf-templates-cookies/evidence/after-cookie.json` and compare against T025's baseline and confirm strictly lower per-operation allocation with no throughput regression; if not, revert and close #137 on the recording (FR-006)
- [X] T060 [US5] Confirm the parity suites and `CookieParserSpec` pass with no edited expectations, and that no banned string-handling construct was introduced (FR-019) — the fold needs no Complexity Tracking exemption

**Checkpoint**: #137 is closed with its measurement pair attached.

---

## Phase 8: User Story 6 — the OpenNFR metric axis follows upstream (Priority: P6)

**Goal**: Group requirements render; the retired metric name is refused with a migration message.

**Independent test**: A group-only requirement renders one assertion; the pairing refuses in both
directions; the parity suite compares the two assertion sets whole and finds eleven.

**Independent of every other story** — it touches no file US1–US5 touch.

- [X] T061 [P] [US6] Add a group scope case to the resolved-selection ADT in `src/main/scala/org/galaxio/gatling/assertions/opennfr/`, per [data-model.md](data-model.md) §2, so the pairing rule becomes total in the types
- [X] T062 [US6] Change the selection resolver in `Reach.scala` to accept a hierarchy with no request name, producing the new case. **Keep the non-path-key guard above the new match** — research found the proposed rewrite silently dropped it, which would render a global assertion from a selector upstream marks *cannot*, the worst available silent drift
- [X] T063 [US6] Thread the resolved scope into the metric resolution in `Reach.scala` and implement the bidirectional pairing from [contracts/reach-v0.8.0.md](contracts/reach-v0.8.0.md) §4: the group metric only under the group scope, the operation metric under every scope except it, the retired name refused everywhere
- [X] T064 [US6] Give each new refusal in `src/main/scala/org/galaxio/gatling/assertions/opennfr/Reach.scala` a message quoting the row that refused it, and make the retired-name refusal name **both** the retirement and the replacement to write instead (FR-022)
- [X] T065 [US6] Widen the locally-decided-refusal register's native-statistic comparison in `Reach.scala` from equality to membership, or its guard becomes one-sided once a second native statistic exists
- [X] T066 [P] [US6] Move the tracked upstream release to `v0.8.0` in `Model.scala`, `OpenNfrAssertions.scala` and `src/main/java/org/galaxio/gatling/javaapi/OpenNfrAssertions.java`
- [X] T067 [P] [US6] Migrate the retired metric name in `src/test/resources/opennfr/nfr.yaml` and `src/test/resources/opennfr/group-only.yaml`, and add the eleventh requirement to `nfr.yaml` so the parity set is whole
- [X] T068 [P] [US6] Migrate the retired metric name in `examples/scala-sbt-example/src/test/resources/opennfr-e2e.yaml`
- [X] T069 [US6] Rewrite `src/test/scala/org/galaxio/gatling/assertions/opennfr/ParitySpec.scala` to compare the two assertion sets **whole**, removing the by-name subtraction, asserting eleven with no duplicates, and re-purposing the "not recoverable by calling the group a request" case to document that both spellings now render the identical value
- [X] T070 [US6] Split the cross-product in `src/test/scala/org/galaxio/gatling/assertions/opennfr/ReachSpec.scala` into a request-side product and a group-side product. Merging the group selection into the existing list would make 45 of 50 new combinations legitimately refuse and silently weaken the suite that exists to catch over-refusal
- [X] T071 [US6] Add refusal cases to `ReachSpec.scala` for the group metric under each non-group selection, the operation metric under the group selection, a metric-less predicate under the group selection, and a wildcard hierarchy element with and without a request name
- [X] T072 [P] [US6] Migrate the retired metric name in `ModelSpec.scala`, `OpenNfrAssertionsSpec.scala` and `FacadeParitySpec.scala`
- [X] T073 [US6] Update `docs/opennfr.md`: state that a group assertion judges the sum of the enclosed operations' durations rather than elapsed traversal time, state that the report-only charting setting never reaches an assertion so charts and assertions disagree by design when it is set, state the new tracked release, and keep the warning against the workaround spelling
- [X] T074 [US6] Invert the documentation guards in `src/test/scala/org/galaxio/gatling/assertions/opennfr/MigrationTableSpec.scala` that today assert the group-only case has no equivalent, and confirm the tracked-release guard passes for all five files it names
- [X] T075 [P] [US6] Update `specs/013-opennfr-assertions/contracts/reach.md` and `parity.md` to point at [contracts/reach-v0.8.0.md](contracts/reach-v0.8.0.md) for the metric and selection axes, transcribing the **statistic** name and not upstream's builder-shaped phrasing, which names no API that exists
- [ ] T076 [US6] Add an e2e case to `examples/scala-sbt-example/src/test/scala/org/galaxio/performance/picatinny/OpenNfrAssertionsE2E.scala` exercising a group-only requirement against a real run — the only place the statistic-selection claim is exercised live rather than read from the target's source
- [X] T077 [US6] Run `sbt mimaReportBinaryIssues || true` and confirm zero new issues — never `mimaFindBinaryIssues`, which prints nothing and looks clean when it is not; the changed rendering behaviour is an experimental surface outside the compatibility guarantee, recorded in [plan.md](plan.md) Complexity Tracking

**Checkpoint**: #328 is closed and parity is eleven of eleven.

---

## Phase 9: Polish & cross-cutting

- [X] T078 Close #127 with the recorded evidence: the request-builder body entry points assemble the body once at scenario-definition time, so the per-request allocation the issue asserts does not occur, and the suggested overload cannot be expressed alongside the existing variable-argument form. Add a compile-guard case in `src/test/scala/org/galaxio/gatling/templates/HttpBodyExtSpec.scala` asserting a body built through the DSL entry point equals one built from an equivalent pre-built field list (FR-028)
- [ ] T079 [P] Assign every commit's PR to milestone v1.27.0 and confirm #126, #127, #137, #143, #144, #328 and #333 are all referenced
- [X] T080 Run the full gate on the default sbt major: `sbt scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" compile "Test/testOnly"`
- [X] T081 Run the full gate on the secondary major: `sbt --sbt-version 2.0.6 scalafmtCheckAll scalafmtSbtCheck compile "Test/testOnly"` — use `testOnly`, never `test`, which is `testQuick` on sbt 2 and passes having run zero tests
- [X] T082 Run `sbt clean coverage compile "Test/testOnly" coverageReport` and confirm the floor (75% statement / 66% branch) holds and no benchmark appears in the report
- [ ] T083 Run the overlay e2e: `cd examples/scala-sbt-example && sbt "Gatling/testOnly *"`
- [ ] T085 Split [#333](https://github.com/galax-io/gatling-picatinny/issues/333) into four issues — one per defect (harness, packaging, masking locale, cookie locale) — assign them to milestone v1.27.0, and re-point this file's commit map Closes column at them. Required before the PR: four commits closing one issue cannot satisfy AGENTS.md's `1 issue = 1 commit`
- [ ] T084 Collect every measurement pair into `specs/014-perf-templates-cookies/evidence/` and reference them from the PR body, per FR-002

---

## Dependencies

```text
Phase 1 (record starting conditions)
   ↓
Phase 2 (locale test harness) ────────┐
   ↓                                  │
Phase 3 / US1 (benchmark gate) ───┐   │
   ↓                              │   ↓
   │                              │  Phase 4 / US2 (locale fixes)
   │                              │   ↓
   │                              │  Phase 5 / US3 (frozen parity)
   │                              │   ↓
   └──────────────────────────────┴→ Phase 6 / US4 (escaping)
                                      Phase 7 / US5 (cookie fold)
                                      ↓
Phase 8 / US6 (OpenNFR) — independent of all of the above
   ↓
Phase 9 (polish)
```

- **US1 blocks US4 and US5** — they cannot produce a measurement pair without it.
- **US2 blocks US3** — the oracle must freeze post-locale-fix behaviour (see Phase 5 ordering note).
- **US3 blocks US4 and US5** — the parity net must exist before behaviour-preserving rewrites.
- **US6 is fully independent** and may run concurrently with everything from Phase 3 onward.

## Parallel opportunities

- Phase 1: T002, T003, T004 in parallel after T001.
- Phase 3: T020, T021, T022 in parallel — three different files.
- Phase 5: T038 and T039 in parallel — two different oracles.
- Phase 8: T061, T066, T067, T068, T072, T075 in parallel — disjoint files.
- **US6 in parallel with US1–US5 entirely** — it shares no file with any of them, so it can be worked
  by a second person from the start.

## Implementation strategy

**MVP = Phase 1 + Phase 3 (US1).** That alone restores a benchmark harness that has been broken since
`1231a64` and stops benchmark output reaching consumers — valuable whether or not any optimization
follows, and a precondition for the milestone's own acceptance criteria.

**Then the safety-first spine**: Phase 2 → US2 → US3. This delivers the disclosure fix (the most severe
defect in the milestone) and the parity net before any behaviour-preserving rewrite is attempted.

**Then the optimizations**: US4 and US5, each gated on its measurement pair, each droppable on
evidence without affecting the other.

**US6 runs alongside** from the start if a second person is available; otherwise it lands last, since it
is the largest single body of work and blocks nothing.
