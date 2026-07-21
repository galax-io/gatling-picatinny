# Implementation Plan: Faker & Feeder-Transform Hot-Path Allocation Reduction

**Branch**: `010-faker-syntax-perf` | **Date**: 2026-07-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-faker-syntax-perf/spec.md`

## Summary

Remove per-call allocation waste from eight verified hot paths — five Faker
generator sites (lorem words, IPv6, CPF formatted branch, German TIN, email
normalization ×2 call sites), the shared `nextLongInclusive` narrow/wide `BigInt`
classification, and two feeder-transform closures (`selectKeys`, `withDefaults`;
`prefixKeys`/`suffixKeys` = #130 closes on refutation evidence, verification V002 —
the compiler already emits a single indy concat there) — under a strict
behavior-parity contract: identical
value sets, formats, and distributions, with RNG draw order preserved wherever the
source is unchanged (ipv6 deliberately moves off the legacy shared-`Random` util onto
`ThreadLocalRandom` — research R4/V014); all pre-existing tests pass
unchanged; zero public-surface change. Technical approach per site is fixed in
[research.md](research.md) (R2–R6): single-pass StringBuilder assembly, one shared
locale-safe email-normalization helper (keeps `toLowerCase`, drops the regex chain),
pure-Long span classification with a written equivalence proof, construction-time
hoisting of static transform config, and JMH `-prof gc` before/after evidence via the
existing `FakerBenchmark`.

## Technical Context

**Language/Version**: Scala 2.13.18 (compile target Java 17, CI Temurin 21)

**Primary Dependencies**: Gatling 3.13.5 (`Provided`); caelum-stella (CPF, untouched); `java.util.concurrent.ThreadLocalRandom` (RNG substrate). Legacy `RandomDataGenerators` (shared `scala.util.Random`, pre-Faker surface): ipv6 STOPS calling it (research R4); other delegates out of scope

**Storage**: N/A

**Testing**: ScalaTest AnyWordSpec + ScalaCheck property tests in existing `GeneratedFeederSpec`; JMH (`sbt Jmh/run`) for allocation evidence

**Target Platform**: JVM library (published to Maven Central)

**Project Type**: Library — single sbt project, Java/Kotlin facade untouched

**Performance Goals**: strictly lower `gc.alloc.rate.norm` (B/op) on every optimized path; zero `BigInt` allocation on narrow-range long sampling

**Constraints**: byte-identical observable behavior (FR-001/FR-008 override speed); zero public API / MiMa drift; no new dependencies; wide-range long path (#300) explicitly frozen

**Scale/Scope**: 8 GitHub issues = 7 code changes + 1 evidence closure (#130, V002-refuted premise); 2 source files (`feeders/faker/Faker.scala`, `feeders/faker/Syntax.scala`), 1 test file extended, 1 benchmark file extended; ~9 semantic commits (1 per code issue + benchmarks + spec/plan docs). Foundational prerequisite: rebase onto origin/main (PR #300 fix + test + scalafmt bumps — V010)

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | 10k-vuser payload template samples emails/IPv6/CPF/TIN/lorem every iteration; values must not drift after optimization | Unit/Functional | Whole pre-existing `GeneratedFeederSpec` suite passes with zero edits; per-generator format assertions re-run (ipv6 = 8 colon-groups; TIN = 11 digits first 1–9; CPF passes stella validator both formatted and raw; lorem words all from catalog). Negative: `lorem.words(0)` still rejected with the same message. |
| FR-002 | Author generates a 50-word lorem payload body per request | Unit/Functional | New parity cases: words(1) is a single catalog word with no separator (boundary); words(N) has exactly N−1 single spaces and N catalog words; ipv6 sample matches 8×4 lowercase-hex shape; TIN first digit never 0 across a large sample (boundary of digit ranges); CPF formatted output matches `ddd.ddd.ddd-dd` and validates. |
| FR-003 | Discrete date-offset generator (feature 009) draws a long offset per virtual user per iteration | Unit/Functional | Property test: for adversarial (min,max) pairs — equal bounds, distance Long.MaxValue−1 / Long.MaxValue / Long.MaxValue+1, Long.MinValue and Long.MaxValue corners — an inline arbitrary-precision reference classifier agrees with production classification, and sampled values stay inside inclusive bounds with both endpoints reachable for tiny ranges. Negative/boundary: distance exactly Long.MaxValue must take the wide path (classification boundary must not shift by one); the #300 wide-range test — present after the foundational rebase (V010) — passes unchanged. |
| FR-004 | Feeder chain `selectKeys → prefixKeys → withDefaults` processes every record of a CSV-driven simulation | Unit/Functional | Multi-record feeder run asserts output-map equality with expected records; defaults case has present-key (record wins) and absent-key (default appears) in one record — boundary: empty record and empty selection yield the same outputs as today; duplicate default keys keep last-wins semantics. Static hoisting verified behaviorally: transform built once processes many records with correct results (allocation delta is FR-007 evidence, not a unit assertion). |
| FR-005 | Downstream project upgrades the library without recompiling | Compile Guard | Existing compile-guard specs compile unchanged against the frozen signatures listed in [contracts/parity-and-gates.md](contracts/parity-and-gates.md); `sbt mimaFindBinaryIssues` reports zero new findings. Negative: any signature edit fails the gate. |
| FR-006 | Reviewer traces each milestone issue to its safety net | Unit/Functional | Each of the 8 issue commits adds/extends named test case(s) covering that issue's path with ≥1 boundary/negative each (enumerated in FR-002/FR-003/FR-004 sketches; email in FR-008 sketch); commit message references the issue number. |
| FR-007 | Perf engineer verifies the optimization is real, not folklore | Unit/Functional | Benchmark methods for lorem-words and narrow-range long added to the existing FakerBenchmark; a unit-level smoke asserts each benchmark method returns a validly-shaped value (guards against dead benchmarks). Allocation numbers themselves come from before/after `Jmh/run -prof gc` runs recorded in the PR (quickstart §3) — strictly lower B/op is the pass condition. |
| FR-008 | Locale-sensitive name (e.g. dotted-İ) flows into email normalization | Unit/Functional | Property test: new shared normalization helper output equals an inline reference of the old regex chain for generated names plus adversarial fixtures (uppercase, accents, `"--a--"`, `"..."`, symbols-only, digits-only, empty → `user` fallback on the email path). Negative: symbols-only input collapses to the fallback, never an empty local part; this test is the tripwire that would force dropping a non-parity optimization. |

*Sample-size floor: every "large sample" assertion in these sketches and in tasks.md draws ≥1000 values.*

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — facade untouched; all changes in Scala core method bodies and private helpers; facade keeps delegating to identical signatures.
- [x] **II. Backward Compatibility** — zero public signature/DSL/format change; internal `private` refactors exempt per constitution; MiMa gate enforced per commit (contract doc).
- [x] **III. Test Discipline** — Test Model filled above (one row per FR, real case + valid layer + code-free sketch, each with negative/boundary); work is test-first (parity/property tests written red against a reference before each body change where applicable — for pure-refactor parity, the reference-comparison tests are written first and must pass on OLD code, then still pass on new); layer 1 only + compile guard, no mocked Gatling runtime, no Testcontainers (nothing container-backed changed); coverage floor 75/66 holds (benchmark sources excluded from denominator).
- [x] **IV. Small, Focused Changes** — scope = 8 issues, 2 source files; CNPJ same-shaped pattern-per-call explicitly left untouched as out-of-scope (research R4); no new dependencies; prefix-keys memo-cache rejected as over-engineering (research R5).
- [x] **V. Release Integrity** — not a release PR; feature lands on `main` via PR under milestone v1.25.0; release cut follows the standard process afterward.

*Post-design re-check (after Phase 1)*: PASS — design artifacts introduce no facade logic, no public-surface change, no new deps; Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/010-faker-syntax-perf/
├── plan.md              # This file
├── research.md          # Phase 0 — 8-issue ground truth + decisions R1-R7 (incl. #304 equivalence proof)
├── data-model.md        # Phase 1 — frozen output shapes / parity invariants
├── quickstart.md        # Phase 1 — 4-step validation guide (parity, lint+MiMa, JMH evidence, spot checks)
├── contracts/
│   └── parity-and-gates.md  # Phase 1 — frozen public surface + per-commit gates
├── benchmarks.md        # created during implementation (T005/T016/T021) — BEFORE/AFTER gc.alloc.rate.norm tables
└── tasks.md             # Phase 2 (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/main/scala/org/galaxio/gatling/
├── feeders/faker/
│   ├── Faker.scala           # #123 lorem.words:805 · #124 ipv6:234 · #125 cpf:628-632 + steuerId:709-714
│   │                         # #139 companyEmailName:196 + emailFromName:238 · #304 nextLongInclusive:92-105
│   ├── Syntax.scala          # #129 selectKeys:96-99 · #131 withDefaults:102-103 · (#130: evidence-only, no edit — V002)
│   └── FakerBenchmark.scala  # FR-007: + lorem-words & narrow-long benchmark methods
src/test/scala/org/galaxio/gatling/
└── feeders/faker/
    └── GeneratedFeederSpec.scala  # per-issue parity/property tests added; zero existing cases edited
```

**Structure Decision**: Single-project library layout (existing). Exactly two
production files change (both in `feeders/faker/`), plus the existing benchmark and
the existing spec file. `templates/Syntax.scala`, the Java facade, and `examples/`
overlays are explicitly untouched.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Constitution III "red → green" read literally (failing test first) is adapted to **reference-style parity tests** (written first, green on OLD code, must stay green after) | Pure parity refactor: FR-001 forbids any behavior change, so a genuinely failing-first test is impossible — there is no new behavior to be red against | Fake-red (assert a wrong value, then "fix" the assertion) is test theater and violates "assert exact real values"; reference-style preserves the principle's substance — tests precede code, exact values, ≥1 negative/boundary per issue |
