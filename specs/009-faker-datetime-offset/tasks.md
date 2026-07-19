# Tasks: Discrete Date-Time Offset Generation

**Input**: Design documents from `/specs/009-faker-datetime-offset/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/faker-date-api.md](contracts/faker-date-api.md), [quickstart.md](quickstart.md)

**Tests**: MANDATORY — constitution III is test-first (red → green); every story starts with failing tests.

**Commit discipline**: 1 issue = 1 commit — the entire feature (core + facade + tests + docs) lands as ONE semantic commit `feat(faker): discrete LocalDateTime offset generator (#294)` at the end (T011); spec/plan artifacts are already committed separately (`17c092b`, spec-first rule). Working commits along the way are not pushed — squash before review.

**Organization**: grouped by user story; US1 is the MVP increment.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

**Purpose**: sanity baseline — nothing to scaffold (no new deps, no build changes; plan Technical Context)

- [X] T001 Verify branch baseline is green before any change: `sbt compile test` on `009-faker-datetime-offset` (fails fast on a broken environment, isolates regressions to this feature) ✅ 770 tests green (14s)

---

## Phase 2: Foundational (Blocking Prerequisites)

**None** — single additive method on existing infrastructure (`Generator`, `Faker.number.long`, `Faker.date`); no shared prerequisites beyond Setup. User stories may begin immediately after T001.

---

## Phase 3: User Story 1 - Generate a date-time a whole number of units away from a base (Priority: P1) 🎯 MVP

**Goal**: `Faker.date.offset(from: LocalDateTime, minOffset, maxOffset, unit = DAYS)` — inclusive whole-unit uniform offsets (contract: [contracts/faker-date-api.md](contracts/faker-date-api.md))

**Independent Test**: quickstart V1 — every sampled value reconstructs exactly as base + k·unit with k inside inclusive bounds; both rejection cases throw at construction

### Tests for User Story 1 (RED first) ⚠️

- [X] T002 [US1] Add failing ScalaTest cases to `src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala` (after "generate date offset within bounds"), per Test Model rows FR-001..005/007/009: (a) 0..5 whole days from fixed base — exact grid reconstruction, k∈[0,5]; (b) −10..10 hours — values on both sides of base, whole-hour grid, endpoints reachable; (c) 3..3 weeks — deterministic exact value; (d) 0..1 day — BOTH endpoints observed across samples (inclusive upper, unlike legacy exclusive); (e) inverted bounds (5..0) → IllegalArgumentException at construction; (f) `ChronoUnit.FOREVER` → IllegalArgumentException naming the unit; (g) equal-bounds generator through `formatDateTime` → exact formatted string (FR-007); (h) doc-parity: legacy (P=30, N=0, days, "yyyy-MM-dd") replacement `offset(base, 0, 29)` + format yields only base+0..29 formatted values and NEVER base+30 (FR-009). Verify RED: `sbt "testOnly org.galaxio.gatling.feeders.faker.GeneratedFeederSpec"` fails (method absent → compile error) ✅ 8 cases added; RED verified (type mismatch — LocalDateTime overload absent)

### Implementation for User Story 1

- [X] T003 [US1] Implement `offset(from: LocalDateTime, minOffset: Long, maxOffset: Long, unit: TemporalUnit = ChronoUnit.DAYS): Generator[LocalDateTime]` in `src/main/scala/org/galaxio/gatling/feeders/faker/Faker.scala` (object `date`, directly after the `LocalDate` offset): `require(minOffset <= maxOffset, …)`, `require(from.isSupported(unit), …)`, body `number.long(minOffset, maxOffset).map(from.plus(_, unit))`; add `java.time.temporal.TemporalUnit` import. GREEN: all T002 cases pass ✅ implemented; GREEN — GeneratedFeederSpec 175/175
- [X] T004 [US1] Run full unit suite + coverage: `sbt test` green, scoverage floor 75%/66% holds (new require branches fully covered by T002 e/f) ✅ unit suite 778/778 green; branch 68.25% ≥ 66; new statements covered (scoverage invoked>0); statement floor 75% requires `IntegrationTest/test` in the denominator (CI gate command) — Docker unavailable locally, validated by CI on the PR

**Checkpoint**: US1 fully functional — quickstart V1 passes; MVP delivered

---

## Phase 4: User Story 2 - Same capability from Java and Kotlin (Priority: P2)

**Goal**: `FakerApi.dateOffset` — two thin delegates (3-arg whole-days default, 4-arg explicit unit), constitution I

**Independent Test**: quickstart V2 — Java-built generators produce in-bounds grid values; inverted bounds surface the same error

### Tests for User Story 2 (RED first) ⚠️

- [X] T005 [US2] Add failing JUnit 5 usage to `src/test/java/org/galaxio/gatling/javaapi/JavaFeedersTest.java` (existing date-feeder block): `dateOffset(base, 0, 5, ChronoUnit.DAYS)` and 3-arg `dateOffset(base, 0, 5)` through `formatDateTime`/`GeneratedFeeder`, asserting in-bounds whole-day values; plus inverted-bounds construction → IllegalArgumentException. Verify RED (facade method absent → compile error). Depends on T003 (core exists; facade still missing) ✅ RED verified (`cannot find symbol dateOffset`); assertions placed in JavaApiExampleSmokeTest (JUnit 5 facade layer per TESTING.md), usage smoke in JavaFeedersTest dates feeder

### Implementation for User Story 2

- [X] T006 [US2] Implement both `dateOffset` delegates in `src/main/java/org/galaxio/gatling/javaapi/FakerApi.scala` ("--- Date ---" block after `dateBetween`): 4-arg → `Faker.date.offset(from, min, max, unit)`, 3-arg → `Faker.date.offset(from, min, max)` (Scala default = days); add `java.time.temporal.TemporalUnit` import; zero facade-local logic. GREEN: T005 passes ✅ two delegates added; GREEN — JavaApiExampleSmokeTest 59/59 incl. both dateOffset tests

**Checkpoint**: US1 + US2 independently green (quickstart V1 + V2)

---

## Phase 5: User Story 3 - Documented migration from the deprecated random-date feeder (Priority: P3)

**Goal**: correct, test-backed migration mapping (FR-009; executable side already in T002-h)

**Independent Test**: quickstart V3 — doc expressions match the tested mapping verbatim; wrong `past` example gone

### Implementation for User Story 3

- [X] T007 [P] [US3] Fix `docs/faker-api.md`: Generator Catalog — note `date.offset` covers `LocalDate` (days) AND `LocalDateTime` (any supported unit, whole-days default); REPLACE the wrong migration example (`RandomDateFeeder("createdAt", 30, 0)` → `Faker.date.past(days = 30)`, wrong direction + wrong type) with the correct discrete mapping `Faker.date.offset(base, 0, 29)` + format — expression textually identical to the T002-h doc-parity test. Depends on T003 ✅ catalog line + wrong past-example replaced with offset(…, 0, 29) mapping + exclusive-delta note
- [X] T008 [P] [US3] Extend `docs/migration.md` Deprecations section: mapping table `RandomDateFeeder(name, P, N, pattern, from, unit, tz)` → feeder over `formatDateTime(offset(from, −N, P − 1, unit), pattern)`; note the legacy upper delta is EXCLUSIVE (hence P − 1; `randomValue(1, 11)` yields 1..10) and `tz` affected formatting only (research R5/R6). Depends on T003 ✅ RandomDateFeeder→offset subsection with mapping table, P−1 rationale, tz note, FakerApi pointers

**Checkpoint**: all three stories independently verifiable (quickstart V1–V3)

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T009 Format + lint + strict diagnostics to zero findings: `sbt scalafixAll scalafmtAll`, then gates `sbt scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" compile` (`-Werror` clean) ✅ scalafixAll+scalafmtAll applied (1 file reformatted); all gates green (fmt, sbt-fmt, scalafix --check, -Werror compile)
- [X] T010 Full quickstart validation V1–V5 incl. compatibility: `sbt "mimaReportBinaryIssues || true"` → zero incompatibilities vs 1.24.0 (FR-008), full verify chain green ✅ V1 175/175, V2 59/59, V3 doc-parity green + docs match expression, V4 MiMa zero issues vs 1.24.0, V5 unit chain green (IT part deferred to CI — no local Docker)
- [X] T011 Single semantic commit `feat(faker): discrete LocalDateTime offset generator (#294)` (core + facade + tests + docs — 1 issue = 1 commit), push `009-faker-datetime-offset`, open DRAFT PR → `main` with milestone **v1.25.0 — Perf: Faker** and `Closes #294` (ask-before-commit rule: confirm with maintainer before commit/push/PR) ✅ commits 81080d3 (tasks) + 63674b0 (feat); draft PR #296 → main, milestone v1.25.0

---

## Dependencies & Execution Order

- **T001** → everything
- **US1 (T002 → T003 → T004)**: strict TDD chain; blocks US2 and US3
- **US2 (T005 → T006)**: after T003 (facade delegates to core)
- **US3 (T007 ∥ T008)**: after T003 (docs must match shipped expression; executable parity lives in T002-h)
- **Polish (T009 → T010 → T011)**: after all stories

### Parallel Opportunities

- After T003: **T005 ∥ T007 ∥ T008** (three different files, no shared state); T004 can run alongside
- T007 ∥ T008 always mutually parallel (different doc files)

## Implementation Strategy

**MVP first**: T001 → T002 → T003 → T004 = shippable US1 (Scala users unblocked). Then US2 (facade users), US3 (docs), Polish. Single-developer order: T001..T011 sequentially; the parallel set collapses naturally. Stop-points at each checkpoint validate the story in isolation via its quickstart scenario.
