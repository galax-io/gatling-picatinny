# Implementation Plan: Discrete Date-Time Offset Generation

**Branch**: `009-faker-datetime-offset` | **Date**: 2026-07-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-faker-datetime-offset/spec.md`

## Summary

Close the migration gap left by the deprecated `RandomDateFeeder` (issue [#294](https://github.com/galax-io/gatling-picatinny/issues/294)): add a discrete whole-unit offset generator for `LocalDateTime` to `Faker.date` — `offset(from, minOffset, maxOffset, unit = DAYS): Generator[LocalDateTime]`, inclusive bounds, uniform distribution, fail-fast validation — plus thin Java/Kotlin facade delegates (`FakerApi.dateOffset`, two overloads) and a corrected, test-backed migration mapping in the docs. Key planning discovery: the legacy feeder's upper delta is **exclusive** (`randomValue(min, max)` excludes `max`), so the documented parity mapping is `RandomDateFeeder(P, N, …, unit)` → `offset(from, −N, P−1, unit)`; the existing `docs/faker-api.md` migration example is wrong today (maps a future-offset feeder to `date.past`) and gets fixed as part of FR-009. Design alternatives and rationale in [research.md](research.md).

## Technical Context

**Language/Version**: Scala 2.13.18, compile target Java 17 (`--release 17`); facade consumed from Java 17+/Kotlin

**Primary Dependencies**: none added — JDK `java.time` (`LocalDateTime`, `TemporalUnit`, `ChronoUnit`) + existing internal `Faker.number.long` (inclusive-bounds uniform generator over `ThreadLocalRandom`)

**Storage**: N/A

**Testing**: ScalaTest (`GeneratedFeederSpec`, `Test` scope), JUnit 5 via sbt-jupiter-interface (`JavaFeedersTest` facade layer), MiMa advisory gate, scoverage floor 75%/66%

**Target Platform**: JVM library published to Maven Central; Gatling 3.13.x host stays `Provided`

**Project Type**: published library (Scala core) + thin Java/Kotlin facade (`javaapi`)

**Performance Goals**: `sample()` cost = one RNG call + one `LocalDateTime.plus` — same order as existing `offset(LocalDate, …)`; cheaper than `between(LocalDateTime, …)` (no range measurement per construction); no additional allocation beyond the produced value

**Constraints**: purely additive public API (constitution II; MiMa clean vs 1.24.0 baseline); no new dependencies (constitution IV); existing `between`/`offset` behavior byte-for-byte unchanged; `-Werror` diagnostics and scalafix gates stay green

**Scale/Scope**: 1 core method (overload), 2 facade delegates, ~7 unit cases + 1–2 facade cases, 2 documentation files touched (`docs/faker-api.md`, `docs/migration.md`), 1 issue (#294), milestone v1.25.0

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | "0..5 whole days from 2025-06-01T12:30:15" — the issue's motivating case | Unit/Functional | Sample repeatedly; for each value derive k = whole-day distance from base and assert exact reconstruction (base plus k equals the value to the nanosecond) with k inside 0..5. Boundary: sub-day remainder anywhere fails the exact-reconstruction assertion by construction. |
| FR-002 | Equal bounds 3..3 weeks — deterministic corner | Unit/Functional | Every sample equals exactly base plus 3 weeks (exact-value assertion). Inclusivity boundary: for a 0..1-day range both endpoint values are observed across a sample run — distinguishes inclusive upper bound from the legacy exclusive one. |
| FR-003 | Range spanning zero: −10..10 hours from one base | Unit/Functional | Across samples, at least one value strictly before and one strictly after the base occur; every value sits on the whole-hour grid inside [base−10h, base+10h]; endpoints −10 and +10 reachable. |
| FR-004 | Inverted bounds (5..0); unsupported unit (FOREVER) | Unit/Functional | Both constructions throw the standard invalid-argument error naming the offending input BEFORE any sampling happens (negative-path row). |
| FR-005 | Discreteness vs the continuous generator; no drift of `between` | Unit/Functional | Offset samples show zero sub-unit remainder (exact grid reconstruction). Control: the pre-existing `between(LocalDateTime)` spec cases remain untouched and still pass — proves the continuous generator's behavior did not change. |
| FR-006 | Java caller builds the generator via facade, with and without explicit unit | Facade Delegation | JUnit test feeds facade-built generators; values are whole-step in-bounds; the no-unit overload behaves as whole days (default). Negative: facade construction with inverted bounds surfaces the same invalid-argument error (delegation, no facade-local softening). |
| FR-007 | Formatted output like the legacy feeder produced (pattern string) | Unit/Functional | Deterministic equal-bounds offset generator piped through the existing date-time formatter yields the exact expected formatted string; parsed back, it lands on the grid inside bounds. |
| FR-008 | Public surface stays additive | Compile Guard | Existing compile-guard specs locking public DSL signatures compile unchanged; MiMa advisory step reports zero incompatibilities against the 1.24.0 baseline (zero-findings gate). |
| FR-009 | Legacy config (P=30, N=0, days, "yyyy-MM-dd") migrated per the documented mapping | Unit/Functional | Executable doc-parity test: the documented replacement produces only base+0..29-day values (upper = P−1, legacy-exclusive semantics), formatted output shape identical to the legacy feeder's. Negative: the naive mapping (upper = P) would emit base+30 — asserted never produced by the documented expression. |

## Constitution Check

- [x] **I. Scala DSL as Source of Truth** — core logic lives in `Faker.date`; `FakerApi.dateOffset` overloads are pure delegates (the no-unit overload delegates to the Scala default), zero facade-local logic.
- [x] **II. Backward Compatibility** — purely additive overload + facade methods; no signature, DSL behavior, or serialized-format change; ships in MINOR v1.25.0; MiMa advisory must stay clean vs 1.24.0.
- [x] **III. Test Discipline** — Test Model above covers all 9 FRs across Unit/Functional, Facade Delegation, Compile Guard; test-first (spec tests written red before the method exists); exact-value + negative cases per row; no mocking anywhere (pure value generation); coverage floor 75/66 unaffected (new branches fully covered).
- [x] **IV. Small, Focused Changes** — no new dependencies; public API addition authorized by issue #294 + maintainer approval (conversation 2026-07-19); no opportunistic refactors — the wrong `docs/faker-api.md` migration example is corrected because FR-009 owns exactly that mapping, nothing else is touched.
- [x] **V. Release Integrity** — not a release PR; lands on `main` via PR into milestone v1.25.0.

## Project Structure

### Documentation (this feature)

```text
specs/009-faker-datetime-offset/
├── spec.md              # Feature specification (/speckit-specify)
├── plan.md              # This file
├── research.md          # Phase 0 — decisions & alternatives (incl. from-scratch API analysis)
├── data-model.md        # Phase 1 — entities & validation rules
├── quickstart.md        # Phase 1 — validation scenarios
├── contracts/
│   └── faker-date-api.md  # Phase 1 — public API contract (core + facade)
├── checklists/
│   └── requirements.md  # Spec quality checklist (16/16 pass)
└── tasks.md             # Phase 2 (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/main/scala/org/galaxio/gatling/feeders/faker/
└── Faker.scala                    # object date — ADD offset(LocalDateTime, Long, Long, TemporalUnit=DAYS) after the LocalDate offset; + TemporalUnit import

src/main/java/org/galaxio/gatling/javaapi/
└── FakerApi.scala                 # "--- Date ---" block — ADD dateOffset(from,min,max) and dateOffset(from,min,max,unit) delegates; + TemporalUnit import

src/test/scala/org/galaxio/gatling/feeders/faker/
└── GeneratedFeederSpec.scala      # ADD datetime-offset cases after "generate date offset within bounds" (FR-001..005, 007, 009 doc-parity)

src/test/java/org/galaxio/gatling/javaapi/
└── JavaFeedersTest.java           # ADD facade dateOffset usage (FR-006)

docs/
├── faker-api.md                   # catalog: date.offset now LocalDate AND LocalDateTime; FIX wrong RandomDateFeeder→past example → offset mapping
└── migration.md                   # Deprecations: exact RandomDateFeeder(P,N,…,unit) → offset(from,−N,P−1,unit)+format mapping table (FR-009)
```

**Structure Decision**: single-module library layout as it exists today; feature touches exactly two production files (core + facade), two test files, two docs files. No new modules, no build changes.

## Complexity Tracking

No constitution violations — table not applicable.
