# Tasks: Faker & Feeder-Transform Hot-Path Allocation Reduction

**Input**: Design documents from `/specs/010-faker-syntax-perf/`

**Prerequisites**: plan.md, spec.md, research.md (R1–R7 + V-records), data-model.md, contracts/parity-and-gates.md, quickstart.md, harness/ (verification V001–V014)

**Tests**: INCLUDED — Constitution III mandates test-first. Two test styles here:
classic red→green does not apply to pure parity refactors, so parity tests are
**reference-style**: written first, MUST PASS against the OLD code (they encode
current behavior), then MUST STILL PASS unchanged after the optimization. Shape
tests for new invariants (e.g. lowercase-hex charset) are written first as well.

**Organization**: By user story from spec.md. House rule: **1 issue = 1 semantic
commit**, each green standalone (`sbt compile test`). Test task + impl task of the
same issue land in that issue's single commit.

## Format: `[ID] [P?] [Story] Description`

**Line-ref caveat**: `path:line` anchors below were verified on the PRE-rebase branch;
T002's rebase + scalafmt bump may shift them. Symbol names (`nextLongInclusive`,
`selectKeys`, `lorem.words`, …) are authoritative; line numbers are indicative.

## Phase 1: Setup

**Purpose**: Sync artifacts and base the branch on current main

- [x] T001 Commit all Spec Kit artifacts on branch `010-faker-syntax-perf` — specs/010-faker-syntax-perf/{plan.md, research.md, data-model.md, quickstart.md, tasks.md, contracts/parity-and-gates.md, harness/*, spec.md amendments} — as `docs(speckit): add 010-faker-syntax-perf plan/tasks` into PR #305 (per-action commit permission required)
- [x] T002 Rebase branch onto origin/main picking up 23f2243 (#300 `nextLongInclusive` fix + wide-range regression test) and scalafmt bumps 4aa5592/54c58da; rerun `sbt scalafmtAll scalafmtSbt` with the new formatter; push `--force-with-lease` (V010/K001/K010 — prerequisite for all code work)
- [x] T003 Post-rebase baseline: `sbt compile test` green; confirm "correctly generate longs when range is wider than Long.MaxValue" now present and passing in src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Benchmark instrumentation + BEFORE numbers — FR-007 evidence needs a pre-change baseline, so this MUST precede any optimization

- [x] T004 Add JMH benchmark methods `fakerLoremWordsSample` (count 50), `fakerNarrowLongSample` (e.g. `number.long(0L, 1_000_000L)`), `fakerIpv6Sample` to src/main/scala/org/galaxio/gatling/feeders/faker/FakerBenchmark.scala (the pre-existing `fakerEmailSample` already covers the email path — reuse, do not duplicate) + a layer-1 smoke case in src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala asserting each returns a validly-shaped value (plan FR-007 row); commit `test(faker): benchmark coverage for lorem/long/ipv6 hot paths`
- [x] T005 Capture BEFORE allocation numbers: `sbt 'Jmh/run -prof gc -f 1 -wi 3 -i 5 .*FakerBenchmark.*'`; record `gc.alloc.rate.norm` (B/op) table in specs/010-faker-syntax-perf/benchmarks.md (BEFORE column; quickstart §3)

**Checkpoint**: baseline green + BEFORE numbers on disk — story work may begin

---

## Phase 3: User Story 1 — Fake-value generators, identical values, less garbage (Priority: P1) 🎯 MVP

**Goal**: lorem/IPv6/CPF/TIN/email produce byte-identical output shapes with single-pass assembly (issues #139, #124, #125, #123)

**Independent Test**: sample each generator — formats/value sets identical to data-model.md frozen shapes; existing tests untouched and green; per-value B/op strictly lower than T005 baseline

All tasks below touch src/main/scala/org/galaxio/gatling/feeders/faker/Faker.scala + src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala → sequential, no [P].

- [x] T006 [US1] #139 tests (reference-style, green on OLD code): property test comparing an inline reference of the old chain (`toLowerCase.replaceAll("[^a-z0-9]+", ".").stripPrefix(".").stripSuffix(".")`) against `person.companyEmailName()`/`internet.email(name, domain)` local-part output for generated names + adversarial fixtures: uppercase, accented, `"--a--"`, `"..."`, symbols-only (→ `user` fallback on email path), digits-only, U+0130 dotted-İ (E003) — in GeneratedFeederSpec.scala
- [x] T007 [US1] #139 impl: shared private normalization helper in Faker.scala — keep `name.toLowerCase`, replace regex chain with one char-loop into StringBuilder (collapse non-`[a-z0-9]` runs to single `.`, no leading dot, trim trailing dot); rewire `companyEmailName` (:196) and `emailFromName` (:238); T006 stays green; commit `perf(faker): single-pass email local-part normalization (#139)`
- [x] T008 [US1] #124 tests (written first): strengthen ipv6 case in GeneratedFeederSpec.scala — 8 colon-separated groups, each exactly 4 chars of lowercase `[0-9a-f]` over a large sample; negative: never uppercase hex, never group length ≠ 4
- [x] T009 [US1] #124 impl: rewrite `internet.ipv6()` (Faker.scala:234) — one StringBuilder, 32 hex digits drawn from `ThreadLocalRandom` over explicit `"0123456789abcdef"`, `:` separators; MUST NOT call `RandomDataGenerators.hexString` (K011/E014 — legacy shared-`Random` Iterator chain); commit `perf(faker): allocation-free IPv6 assembly off legacy RNG util (#124)`
- [x] T010 [US1] #125 tests (written first): TIN — 11 digits, first digit 1–9 across large sample, digits-only charset; CPF — `formatted=true` matches `\d{3}\.\d{3}\.\d{3}-\d{2}` AND stella-validates, `formatted=false` is 11 digits with no separators (negative) — in GeneratedFeederSpec.scala
- [x] T011 [US1] #125 impl: `de.steueridentifikationsnummer` (Faker.scala:709-714) → single StringBuilder (first digit 1–9 + ten digits 0–9, no Range.map/prepend); `br.cpf` (:628-632) → hoist format regex to private compiled `Pattern`, formatted branch reuses it (stella + CNPJ untouched, research R4); commit `perf(faker): single-pass TIN assembly + precompiled CPF format pattern (#125)`
- [x] T012 [US1] #123 tests (written first): `lorem.words(1)` = single catalog word, no separator (boundary); `lorem.words(N)` = exactly N catalog words joined by single spaces (N−1 spaces); negative: `words(0)` still rejected with the existing message — in GeneratedFeederSpec.scala
- [x] T013 [US1] #123 impl: `lorem.words` (Faker.scala:805) → StringBuilder loop over `FakerData.loremWords` draws with space separators, `require(count > 0)` untouched; commit `perf(faker): single-pass lorem words assembly (#123)`

**Checkpoint**: US1 independently verifiable — all P1 generators parity-green

---

## Phase 4: User Story 2 — Bounded whole-number sampling without BigInt (Priority: P2)

**Goal**: `nextLongInclusive` narrow/wide classification in pure Long arithmetic (issue #304); wide path (#300) byte-frozen

**Independent Test**: classification agrees with BigInt reference on all corner pairs; inclusive bounds hold; #300 test unchanged-green; narrow-path B/op shows zero BigInt

- [x] T014 [US2] #304 tests (reference-style, green on post-rebase OLD code): classification-equivalence test in GeneratedFeederSpec.scala — inline BigInt reference predicate vs production behavior for corner pairs (equal bounds; distance `Long.MaxValue−1`/`Long.MaxValue`/`Long.MaxValue+1`; `Long.MinValue`/`Long.MaxValue` corners; full range) + ScalaCheck random pairs; endpoint-inclusivity sampling on tiny ranges (both endpoints observed). Boundary law: distance exactly `Long.MaxValue` → wide path (V001/E001, research R2)
- [x] T015 [US2] #304 impl: `nextLongInclusive` (Faker.scala) — `span = max - min`; narrow iff `span >= 0 && span < Long.MaxValue`, bound `span + 1`; keep post-#300 full-range branch FIRST and both-bounds rejection loop byte-identical; no `BigInt` anywhere in the method; T014 + #300 test + :301-304 inclusive tests green; commit `perf(faker): allocation-free narrow-range long classification (#304)`
- [x] T016 [US2] Narrow-long AFTER numbers: rerun `sbt 'Jmh/run -prof gc -f 1 -wi 3 -i 5 .*FakerBenchmark.*NarrowLong.*'`; append to specs/010-faker-syntax-perf/benchmarks.md; assert strictly lower B/op vs T005 (SC-002)

**Checkpoint**: US2 independently verifiable

---

## Phase 5: User Story 3 — Transforms do static work once (Priority: P3)

**Goal**: `selectKeys` single-pass, `withDefaults` hoisted (issues #129, #131); #130 closes on refutation evidence (V002)

**Independent Test**: multi-record transform runs produce map-equal outputs vs today incl. boundary records; static config computed once per construction

- [x] T017 [US3] #129+#131 tests (reference-style, green on OLD code) in src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala: selectKeys over records with mixed present/absent/unselected keys + empty record + empty selection (boundary); withDefaults present-key-wins / absent-key-fills / duplicate-default-keys-last-wins in one multi-record run; chain regression `selectKeys → prefixKeys → withDefaults` over ≥3 heterogeneous records asserting full map equality, plus a `suffixKeys` case and empty-prefix/empty-suffix boundary (renamed keys identical in content — data-model "empty affix = identity" invariant; together these lock #130 behavior with its own boundary case per FR-006)
- [x] T018 [US3] #129 impl: `selectKeys` (src/main/scala/org/galaxio/gatling/feeders/faker/Syntax.scala:96-99) — replace `.view.filterKeys(keySet.contains).toMap` with single-pass `filter` on the widened record; keep hoisted `keySet`; commit `perf(feeders): single-pass selectKeys record filtering (#129)`
- [x] T019 [US3] #131 impl: `withDefaults` (Syntax.scala:102-103) — hoist `defaults.toMap` into construction-time `val`; per-record stays `defaultsMap ++ record` (record wins); commit `perf(feeders): hoist withDefaults static map to construction (#131)`
- [x] T020 [P] [US3] #130 evidence closure: draft GitHub comment for issue #130 from harness E002/V002 — `javap` of `Syntax$FeederOps$.$anonfun$prefixKeys$2` showing single `invokedynamic makeConcatWithConstants` (Scala 2.13 lowers simple interpolation; `prefix + key` bytecode-identical; premise obsolete) — propose closing as not-planned. **Requires maintainer confirmation before posting/closing** (no self-closing)

**Checkpoint**: all stories independently verifiable

---

## Phase 6: Polish & Cross-Cutting

**Purpose**: Evidence completion, full gates, delivery

- [x] T021 Full AFTER benchmark sweep: `sbt 'Jmh/run -prof gc -f 1 -wi 3 -i 5 .*FakerBenchmark.*'`; complete BEFORE/AFTER table in specs/010-faker-syntax-perf/benchmarks.md; verify every benchmarked path (lorem, ipv6, narrow-long, email — SC-002 scope) strictly lower B/op; numbers go into the implementation PR description (FR-007)
- [x] T022 (correction: the enforced coverage gate aggregates unit + IntegrationTest — CI "Coverage" job is GREEN on this PR at the 75/66 floor; the earlier local 74.59% was a unit-only under-measurement, not main drift) Full gate run (contracts/parity-and-gates.md): `sbt scalafmtAll scalafmtSbt` then `sbt scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" compile test` + coverage ≥75/66 + `sbt mimaFindBinaryIssues` zero new findings (SC-001/SC-005)
- [x] T023 Execute quickstart.md §1–§4 end-to-end as final validation; fix any drift found
- [x] T024 Implementation PR: push branch, open PR to main (separate concern from docs PR #305), assign milestone "v1.25.0 — Perf: Faker", body: closes #123 #124 #125 #129 #131 #139 #304 (+#130 per T020 decision) + benchmark tables (per-action permission for push/PR)

---

## Dependencies & Execution Order

- **Phase 1 → 2 → 3/4/5 → 6** strictly; T002 (rebase) blocks ALL code tasks (T004+) — #300 reshaped the exact function #304 edits
- **T004 → T005** (methods before baseline); **T005 blocks T007/T009/T011/T013/T015** (BEFORE numbers must predate optimizations)
- Within each story: test task immediately before its impl task, same commit (test-first, reference-style)
- **US1/US2/US3 are logically independent** but share Faker.scala/Syntax.scala/GeneratedFeederSpec.scala and follow 1-issue-1-commit → execute sequentially P1 → P2 → P3
- **T020 is the only [P] task** (different surface: GitHub issue, no repo files) — may run any time after Phase 1
- T021–T023 after all story phases; T024 last

## Parallel Example

```text
# Only genuine parallelism (single-file feature, commit-per-issue discipline):
Task T020: draft #130 evidence comment (GitHub, no repo files)
# — runs alongside any Phase 3-5 task
```

## Implementation Strategy

- **MVP = Phase 1 + 2 + US1 (T001–T013)**: the four P1 generator issues alone deliver the milestone's core value; stop-and-validate at the US1 checkpoint
- Incremental: each issue commit is independently green and revertable; each story checkpoint is a demoable increment
- Behavior parity overrides speed everywhere (FR-008): any parity test regression → narrow or drop that optimization, never adjust the test
