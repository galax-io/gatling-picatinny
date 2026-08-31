# Tasks: OpenNFR assertions

**Input**: Design documents from `specs/013-opennfr-assertions/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: **Mandatory, and written first.** Constitution III makes work test-first (red → green →
refactor), with exact real values and at least one negative or boundary case per test. Every
implementation task below is preceded by the failing test it makes pass.

**Organization**: grouped by user story, in priority order, so each is independently deliverable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisable — different file, no dependency on an incomplete task
- **[USn]**: the user story the task serves

## Path Conventions

- Core: `src/main/scala/org/galaxio/gatling/assertions/opennfr/`
- Facade: `src/main/java/org/galaxio/gatling/javaapi/`
- Core tests: `src/test/scala/org/galaxio/gatling/assertions/opennfr/`
- Facade tests: `src/test/java/org/galaxio/gatling/javaapi/assertions/`
- Fixtures: `src/test/resources/opennfr/`

---

## Phase 1: Setup

**Purpose**: make the oracle's current behaviour a standing guard before anything is added beside it.

- [X] T001 Create `src/main/scala/org/galaxio/gatling/assertions/opennfr/`, `src/test/scala/org/galaxio/gatling/assertions/opennfr/` and `src/test/resources/opennfr/`
- [X] T002 Add a standing guard test in `src/test/scala/org/galaxio/gatling/assertions/opennfr/OracleBaselineSpec.scala` pinning the deprecated builder's eleven assertions from `src/test/resources/nfr.yml` as an exact set, per `contracts/parity.md` § 3 (FR-015). It must be green before any other task starts and stay green throughout.

**Checkpoint**: the deprecated path is fenced. Nothing below may change it.

---

## Phase 2: Foundational (blocking)

**Purpose**: the decoded document model every story needs. **⚠️ No user story can start until this is done.**

- [X] T003 Write failing decoder tests in `src/test/scala/org/galaxio/gatling/assertions/opennfr/ModelSpec.scala`: a minimal document decodes to the expected values; a heterogeneous selector keeps a number, a boolean and a string list distinct; a missing required field fails. Boundary: `threshold` decodes as an exact decimal, not a binary float (FR-008)
- [X] T004 Implement the document model in `src/main/scala/org/galaxio/gatling/assertions/opennfr/Model.scala` per `data-model.md` — `RequirementSet`, `Requirement`, `Predicate`, selector as raw JSON per attribute, threshold as an exact decimal
- [X] T005 Implement the circe decoders in `Model.scala`, including the derived "predicates = criteria ++ guards" accessor (FR-007)
- [X] T006 [P] Write failing envelope tests in `ModelSpec.scala`: a wrong `apiVersion` names both values, a wrong `kind` is refused, an empty `requirements` list is refused (FR-002)
- [X] T007 Implement the envelope check in `src/main/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrAssertions.scala`

**Checkpoint**: a document decodes and the envelope is enforced.

---

## Phase 3: User Story 1 — a requirement becomes Gatling assertions (P1) 🎯 MVP

**Goal**: a valid document produces the assertions it denotes.

**Independent Test**: point the entry point at a one-criterion document and assert the exact assertion value — scope, statistic, condition, target.

### Tests

- [X] T008 [P] [US1] Write failing scope tests in `src/test/scala/org/galaxio/gatling/assertions/opennfr/ReachSpec.scala` for every **can** row of `contracts/reach.md` § Selection: `{}`, a bare request name, a depth-1 hierarchy, a depth-3 hierarchy, and the quantified `"*"` (FR-005)
- [X] T009 [P] [US1] Write failing statistic tests in `ReachSpec.scala`: response-time percentile, max, min, mean, stddev; the failed-request share and count; the request count and throughput. Boundary: a fractional share target stays fractional (FR-006)
- [X] T010 [P] [US1] Write failing unit-and-threshold tests in `ReachSpec.scala`: `1.001 s` is exactly `1001`; `0.5 s` is `500`; a share in `1` converts to percent (FR-008)
- [X] T011 [P] [US1] Write a failing test in `ReachSpec.scala` asserting every operator maps — `lt`, `lte`, `gt`, `gte`, and `eq` to Gatling's `is` (FR-005)
- [X] T012 [P] [US1] Write a failing test in `src/test/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrAssertionsSpec.scala` asserting a requirement carrying both criteria and guards yields one assertion per entry of both, and that two identical predicates yield two assertions (FR-007)

### Implementation

- [X] T013 [US1] Implement scope resolution in `src/main/scala/org/galaxio/gatling/assertions/opennfr/Reach.scala` from `contracts/reach.md` § Selection, transcribed as a lookup, not re-derived
- [X] T014 [US1] Implement statistic resolution in `Reach.scala` — the three predicate shapes of `data-model.md`, mapped per `contracts/reach.md` § Metric and § Aggregation
- [X] T015 [US1] Implement exact-decimal threshold canonicalisation and the whole-number rule in `Reach.scala` (FR-008, research R4)
- [X] T016 [US1] Implement operator mapping in `Reach.scala`, typed to the target so counts and shares stay distinct
- [X] T017 [US1] Implement the entry point and the explicit-configuration test seam in `OpenNfrAssertions.scala` (FR-001), per `contracts/public-api.md`

**Checkpoint**: US1 is independently demonstrable — a document renders.

---

## Phase 4: User Story 2 — a wrong document is refused before the run (P1)

**Goal**: nothing is dropped, approximated or silently green.

**Independent Test**: feed defective documents; assert the load fails and every reason is named.

### Tests

- [X] T018 [P] [US2] Write failing refusal tests in `ReachSpec.scala` for every **cannot** row of `contracts/reach.md`: a hierarchy-only selector, a `"*"` hierarchy element, `"*"` beside a hierarchy, a non-string path part, an unaddressable attribute, `neq`, `sum`, a non-response-time metric, a narrowed `bad`, `good`, an unfitting unit (FR-009)
- [X] T019 [P] [US2] Write the **over-refusal** test in `ReachSpec.scala` — the direction that matters — asserting every shape the reach tables list as reachable is accepted, so the allowlist does not refuse too much (FR-009)
- [X] T020 [P] [US2] Write failing tests in `ReachSpec.scala` for the three schema-forbidden combinations: `metric` with `bad`, `metric` with `good`, `bad` with `good`. Boundary: each key alone is accepted (FR-004)
- [X] T021 [P] [US2] Write a failing test in `ReachSpec.scala` asserting `0.5 ms` is refused with a message saying rounding would move the bar, never rounded (FR-008)
- [X] T022 [P] [US2] Write failing accumulation tests in `OpenNfrAssertionsSpec.scala`: a document with three unrelated defects yields exactly three reasons, each naming its requirement and predicate identity; a single-defect document yields exactly one (FR-003)
- [X] T023 [P] [US2] Write a failing **totality** test in `OpenNfrAssertionsSpec.scala`: one unrenderable predicate among nine good ones yields **zero** assertions, not nine (FR-003, `contracts/public-api.md`)
- [X] T024 [P] [US2] Write a failing test in `OpenNfrAssertionsSpec.scala` asserting an unreadable path fails naming the path (FR-003)
- [X] T025 [P] [US2] Add refusal fixtures under `src/test/resources/opennfr/` for the cases above — **done differently**: `group-only.yaml` is on disk because both surfaces exercise it; the rest are constructed in-test as values and temp documents, which is stronger (a fixture file can drift from the test that reads it, a constructed value cannot)

### Implementation

- [X] T026 [US2] Implement the reason type and per-predicate refusal wording in `Reach.scala`, each reason carrying the words of the reach row that refused it
- [X] T027 [US2] Implement the schema rules FR-004 names in `Reach.scala`, as an exhaustive match so the compiler catches a missing combination
- [X] T028 [US2] Implement reason accumulation in `OpenNfrAssertions.scala` — `Either[List[String], List[Assertion]]` end to end, folded into one exception at the boundary (research R6)
- [X] T029 [US2] Implement the local-decision register (FR-011): each locally-decided rule reachable from one named place, its refusal carrying the upstream issue number, seeded with the two notes in `contracts/reach.md`

**Checkpoint**: US2 is independently demonstrable — a wrong document fails loudly and completely.

---

## Phase 5: User Story 3 — migrating changes nothing that can be kept (P2)

**Goal**: the strongest check in the feature.

**Independent Test**: build both ways from the fixture and its translation; compare the sets.

### Tests

- [X] T030 [US3] Write the translated fixture `src/test/resources/opennfr/nfr.yaml` from `contracts/parity.md` § 1–3 — one requirement per distinct selection, `op: lt` throughout because the deprecated builder's operator is implicit, Russian keys as `displayName`
- [X] T031 [US3] Write the failing parity test in `OpenNfrAssertionsSpec.scala`: the translation and `src/test/resources/nfr.yml` build **equal** assertion sets over the ten, compared as sets. The eleventh is subtracted **by naming it**, never by shrinking the expected set (FR-015, SC-001)
- [X] T032 [P] [US3] Write the failing exclusion test in `OpenNfrAssertionsSpec.scala`: the group-only selector is refused, and the reason is the one `contracts/parity.md` § 4 gives (`no scope denotes the requests a path encloses`)
- [X] T033 [P] [US3] Write a failing test in `src/test/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrAssertionsSpec.scala` asserting the deprecated builder still builds all eleven including the excluded one, in the same class, so coexistence is pinned (FR-015)

### Implementation

- [X] T034 [US3] Make T031–T033 pass without touching `AssertionsBuilder.scala` or `Assertions.java`. Any change to either is a defect in this task, not a fix.

**Checkpoint**: parity is established as a measured fact rather than a design input.

---

## Phase 6: User Story 4 — Java and Kotlin reach the same capability (P3)

**Goal**: facade parity, with every rule still decided once.

**Independent Test**: load one document through both surfaces; compare the assertions.

### Tests

- [X] T035 [P] [US4] Write a failing JUnit 5 test in `src/test/java/org/galaxio/gatling/javaapi/assertions/JavaOpenNfrAssertionsTest.java`: the same document through the Scala entry point and the facade yields equal assertions, field by field (FR-016)
- [X] T036 [P] [US4] Write a failing JUnit 5 test in `src/test/java/org/galaxio/gatling/javaapi/assertions/JavaOpenNfrAssertionsTest.java` asserting an unrenderable document fails through the facade with the same reasons, proving no decision was re-made there
- [X] T037 [P] [US4] Write a compile-guard test in `src/test/java/org/galaxio/gatling/javaapi/assertions/JavaOpenNfrCompileTest.java` pinning the facade's public signatures

### Implementation

- [X] T038 [US4] Expose the resolved decision as a value from `Reach.scala` so the facade can re-issue it without re-deciding (research R2)
- [X] T039 [US4] Implement `src/main/java/org/galaxio/gatling/javaapi/OpenNfrAssertions.java`, re-issuing the resolved decision through the Java DSL, with a comment naming the package-private constructor that forces this and pointing at research R2

**Checkpoint**: all four stories delivered.

---

## Phase 7: Schema validation ⛔ BLOCKED — NOT DONE

**Purpose**: FR-012 and FR-013. **Blocked on authorization of the dependency in research R1**
(`com.networknt:json-schema-validator:1.5.8` — two new jars, no second Jackson major).

**Do not start these until that is granted.** Every other phase is independent of this one; the
feature ships without it, with FR-004 carrying the schema rules that change what renders and FR-017
stating what a user loses by that.

- [ ] T040 ⛔ **NOT DONE — awaiting authorization.** Add the agreed validator to `project/Dependencies.scala` and `build.sbt`, with a comment recording why the version is pinned below the current release (Jackson major, research R1)
- [ ] T041 ⛔ [P] Vendor `requirementset.schema.json` into `src/main/resources/opennfr/`, recording the upstream release and commit beside it (FR-013)
- [ ] T042 ⛔ Write a failing test in `src/test/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrSchemaSpec.scala`: every document in the upstream corpus validates; a document the schema rejects — an unknown key, an empty `criteria`, a malformed `name` — is refused here too (FR-012)
- [ ] T043 ⛔ [P] Write a failing drift test in `src/test/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrSchemaSpec.scala` asserting `src/main/resources/opennfr/requirementset.schema.json` is byte-identical to the recorded release, and that it fails even when a local edit makes the schema *more* permissive (FR-013)
- [ ] T044 ⛔ [P] Write a failing test in `src/test/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrSchemaSpec.scala` asserting no network is reached and a `$ref` to an absolute URL is refused rather than fetched (FR-014)
- [ ] T045 ⛔ Implement validation ahead of rendering in `src/main/scala/org/galaxio/gatling/assertions/opennfr/OpenNfrAssertions.scala`, so no assertion is produced from an invalid document

---

## Phase 8: Polish & cross-cutting

- [X] T046 [P] Write the migration table in `docs/` from `contracts/parity.md` § 1–2: every recognised NFR-YAML key, every scope form, and the group-only row **with its reason** — that Gatling measures a group's cumulated response time, so the old spelling names something it does not measure (FR-017)
- [X] T047 [P] Write a compile-guard test in `src/test/scala/org/galaxio/gatling/assertions/opennfr/MigrationTableSpec.scala` asserting every key `src/main/scala/org/galaxio/gatling/assertions/AssertionsBuilder.scala` recognises has a row in the `docs/` table, so a key added later without a row fails the build (FR-017, SC-006)
- [X] T048 [P] Document the experimental status and the tracked upstream release in `docs/`, and state that the surface is outside the binary-compatibility guarantee (FR-017)
- [X] T049 [P] Add a compile-guard check asserting `gatling-reach.md` is cited nowhere in the feature's sources — it is a redirect carrying no rule (FR-010)
- [X] T050 Add an end-to-end simulation in `examples/` taking its assertions from an OpenNFR document over real HTTP against WireMock, asserting a passing document passes and a breached threshold fails the run (`quickstart.md` § 7)
- [X] T051 Run the full gate on both sbt majors per `AGENTS.md` § Commands — `sbt "scalafixAll --check" scalafmtCheckAll scalafmtSbtCheck compile "Test/testOnly"`, then the same with `--sbt-version 2.0.6` — and record the result in `specs/013-opennfr-assertions/quickstart.md` § 6
- [X] T052 Confirm coverage is at or above the floor recorded in `TESTING.md` § Coverage ratchet (75 % / 66 %), and that `sbt mimaReportBinaryIssues` is clean against the `mimaPreviousArtifacts` pinned in `build.sbt` — the new types are additive, so it must be
- [X] T053 Re-check `specs/013-opennfr-assertions/contracts/reach.md` against upstream `README.md` § *What any tool can actually run* at the pinned release, and record the checked-on date in that file

---

## Dependencies

```
Phase 1 (T001–T002)
   └─> Phase 2 (T003–T007)          ← blocking for everything
          ├─> Phase 3  US1 (T008–T017) 🎯 MVP
          │      └─> Phase 4  US2 (T018–T029)
          │             └─> Phase 5  US3 (T030–T034)   ← needs US1 rendering + US2 refusal
          │                    └─> Phase 6  US4 (T035–T039)
          └─> Phase 7 ⛔ (T040–T045)  ← independent of US1–US4; blocked on authorization
Phase 8 (T046–T053)                  ← after the stories it documents
```

**Story independence**: US1 stands alone. US2 needs US1's renderer to have something to refuse
*around*, but its refusal paths are separately testable. US3 is the comparison and needs both. US4 is
a surface over what US1–US3 settled. Phase 7 touches none of them.

## Parallel opportunities

- **T008–T012** — five test files' worth of failing tests, different concerns, no shared state
- **T018–T025** — eight refusal tests, all independent
- **T032, T033** — after T031 exists
- **T035–T037** — three facade tests
- **T041, T043, T044** — after T040
- **T046–T049** — four documentation and guard tasks

## Implementation strategy

**MVP is Phase 1 + 2 + 3.** That is a library that turns a valid OpenNFR document into Gatling
assertions — demonstrable on its own, and the smallest thing worth showing.

**Then Phase 4**, because a renderer that cannot refuse is worse than none: it is the silent green the
whole feature exists to prevent. **Then Phase 5**, which is what makes the claim in the spec's title
true rather than asserted.

**Phase 7 is severable in both directions** — it can land first if the dependency is agreed early, or
never, in which case FR-017 must say what the user loses.

---

## Notes

- **T002 runs first and stays green.** If it ever fails, a task has changed the deprecated path, which FR-015 forbids.
- **`contracts/reach.md` and `contracts/parity.md` are transcriptions, not derivations.** Implementation reads them; it does not re-reason about Gatling. Where they and Gatling disagree, that is a finding to report upstream (FR-018), not a licence to edit the table.
- Test-first is not optional here: Constitution III requires red → green → refactor, exact values, and a negative or boundary case per test.
