---
description: "Task list for Transactions Reliability (v1.16.0)"
---

# Tasks: Transactions Reliability (v1.16.0)

**Input**: Design documents from `specs/001-transactions-reliability/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/transactions-dsl.md](./contracts/transactions-dsl.md), [quickstart.md](./quickstart.md)

**Tests**: REQUIRED — FR-010 mandates regression tests; Constitution III mandates test discipline; each user story has an Independent Test. TDD: write each test to FAIL first, then implement.

**Organization**: Grouped by user story (P1→P4). MVP = User Story 1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete dependencies)
- **[Story]**: US1–US4 (user-story phases only)

## Path Conventions

Single Scala library. Production: `src/main/scala/org/galaxio/gatling/transactions/`. Tests: `src/test/scala/org/galaxio/gatling/transactions/`. Java facade: `src/main/java/org/galaxio/gatling/javaapi/` (unchanged this feature).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish a known-green baseline before any change (also the SC-005 regression reference).

- [x] T001 Establish green baseline: run `sbt scalafmtCheckAll scalafmtSbtCheck compile test` and confirm the existing `org.galaxio.gatling.transactions.TransactionsSpec` suite passes; note current observable outcomes (valid/nested/not-opened/illegal-state/bad-sequence/hooks) as the SC-005 baseline.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared test seams + pinned constants used by EVERY user story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T002 [P] Add shared pinned identifiers (start resolution-failure label `"startTransaction"`, end label `"endTransaction"`, dropped-events summary label `"transactions dropped"`, bound key `galaxio.transactions.maxInFlight` + default `100000` resolved through the existing `SimulationConfig.getIntParam` plumbing — not hand-rolled `sys.props`) as constants in a new `src/main/scala/org/galaxio/gatling/transactions/Constants.scala` (referenced by both production code and tests — no string duplication). See [contracts/transactions-dsl.md](./contracts/transactions-dsl.md) §4.
- [x] T003 Add deterministic test seams to the harness: a controllable `io.gatling.commons.util.Clock` implementation (settable/advanceable `nowMillis`) and a latch-capable terminal `Action` factory (counts down a `CountDownLatch` on execute) in `src/test/scala/org/galaxio/gatling/transactions/fixtures.scala`; wire `MockedGatlingCtx` in `src/test/scala/org/galaxio/gatling/transactions/Mocks.scala` to inject the controllable `Clock` in place of `new DefaultClock`.
- [x] T004 Refactor `runScenario` in `src/test/scala/org/galaxio/gatling/transactions/TransactionsSpec.scala` to await the terminal `CountDownLatch` with a bounded timeout instead of `Thread.sleep(200)`; confirm all existing tests still pass (depends on T003).

**Checkpoint**: Test harness can drive a real-actor scenario deterministically (controllable clock + latch); pinned constants available.

---

## Phase 3: User Story 1 — Virtual users never hang on an unresolvable expression (Priority: P1) 🎯 MVP

**Goal**: An unresolvable EL expression in `startTransaction`/`endTransaction` fails the VU visibly and advances it; the simulation never stalls.

**Independent Test**: Run a scenario whose transaction name references a missing session attribute; the latch fires within timeout (no hang) and a crash is recorded in stats under the pinned label.

### Tests for User Story 1 ⚠️ (write first, ensure they FAIL)

- [x] T005 [P] [US1] Regression test in `src/test/scala/org/galaxio/gatling/transactions/TransactionsSpec.scala`: `startTransaction("#{missing}")` → latch fires within timeout (no hang), VU marked failed, and an `ERROR`/crash `Evt` exists under the `Constants` start label (SC-001, SC-002, FR-010).
- [x] T006 [P] [US1] Regression test in `src/test/scala/org/galaxio/gatling/transactions/TransactionsSpec.scala`: `endTransaction("#{missing}")` and an unresolvable `stopTime` expression → latch fires, VU marked failed, crash recorded under the `Constants` end label (SC-001, SC-002, FR-010).
- [x] T007 [P] [US1] Edge-case regression test in `src/test/scala/org/galaxio/gatling/transactions/TransactionsSpec.scala`: an unresolvable expression on a **throttled** action still fast-fails and advances (latch fires, VU failed) — confirms the `Failure` branch is evaluated before the throttler fold, not stalled behind throttling (spec.md Edge Case "Resolution failure combined with throttling", FR-001/FR-003).

### Implementation for User Story 1

- [x] T008 [P] [US1] Fix `src/main/scala/org/galaxio/gatling/transactions/actions/StartTransactionAction.scala`: replace the silent `for/yield` with explicit handling — on name-resolution `Failure`, call `statsEngine.logRequestCrash(session.scenario, session.groups, Constants.startLabel, message)` then advance via `recover(session)` / `next ! session.markAsFailed`; on `Success`, keep `ctx.coreComponents.clock.nowMillis` start timestamp and the throttler path (FR-001, FR-002, FR-003).
- [x] T009 [US1] Fix `src/main/scala/org/galaxio/gatling/transactions/actions/EndTransactionAction.scala`: on name or `stopTime` resolution `Failure`, `logRequestCrash(..., Constants.endLabel, message)` + `next ! session.markAsFailed`; preserve the throttler path and the existing actor handoff on success (FR-001, FR-002, FR-003). (Same file is extended in US2/T012 — do T009 first.)

**Checkpoint**: No EL-resolution failure (including throttled) can stall a VU; failures are recorded. MVP complete and independently testable.

---

## Phase 4: User Story 2 — No false transaction failures from clock corrections (Priority: P2)

**Goal**: Source start and the default end timestamp from the monotonic Gatling clock so the ordering guard never false-fails under wall-clock backward jumps; preserve explicit-`stopTime` semantics.

**Independent Test**: A default-end transaction closes `OK` with the injected clock; no false "illegal state"; the existing explicit-`endTransaction("t1", 1L)` case still produces the illegal-state KO.

### Tests for User Story 2 ⚠️ (write first, ensure they FAIL)

- [x] T010 [P] [US2] Test in `src/test/scala/org/galaxio/gatling/transactions/TransactionsSpec.scala`: with the injected controllable `Clock`, a default-end transaction closes `OK` with a non-negative duration and produces no "illegal state" KO (SC-003); assert the existing explicit-`1L` scenario still yields the illegal-state KO (SC-005 preservation).

### Implementation for User Story 2

- [x] T011 [US2] Keep the DSL source-compatible while changing the default source: in `src/main/scala/org/galaxio/gatling/transactions/Predef.scala` replace the single default-arg `endTransaction` with **two overloads** — `endTransaction(tName)` and `endTransaction(tName, stopTime)`; in `src/main/scala/org/galaxio/gatling/transactions/actions/builders.scala` **remove the hardcoded `System.currentTimeMillis()` default** by collapsing to one `EndTransactionActionBuilder(tName, stopTime: Option[Expression[Long]] = None)` whose `build` (which has `ctx`) resolves `None` to a clock-reading expression `_ => ctx.coreComponents.clock.nowMillis.success` and `Some` to the caller's expression — so `EndTransactionAction` stays a plain `stopTime: Expression[Long]` (no `Option`, no match in `execute`). No sentinel, no `eq`, no wall-clock anywhere; both call forms still compile; Java facade passes `Option.empty()`/`Some(expr)` (FR-009, Constitution II). See [research.md](./research.md) R5 and [contracts/transactions-dsl.md](./contracts/transactions-dsl.md) §1.
- [x] T012 [US2] In `src/main/scala/org/galaxio/gatling/transactions/actions/EndTransactionAction.scala`, when no explicit `stopTime` is supplied (sentinel/`None`), source the end timestamp from `ctx.coreComponents.clock.nowMillis`; otherwise evaluate the user expression (FR-004, FR-005, FR-006). Depends on T009 (same file) and T011 (builders carry the distinction). No change to `TransactionsActor` — #69 is fixed structurally by the single monotonic source.

**Checkpoint**: Default-path ordering and timestamps come from one monotonic-epoch source; no false failures; existing behavior preserved.

---

## Phase 5: User Story 3 — Accurate transaction durations from a single time source (Priority: P3)

**Goal**: Confirm reported durations are non-negative and accurate for the default path (delivered by US2's single-source change).

**Independent Test**: Advance the controllable clock by a known Δ across open/close; reported duration equals Δ (≤1 ms), non-negative.

### Tests for User Story 3 ⚠️ (write first, ensure they FAIL)

- [x] T013 [P] [US3] Test in `src/test/scala/org/galaxio/gatling/transactions/TransactionsSpec.scala`: with the controllable `Clock` advanced by a known Δ between `startTransaction` and default `endTransaction`, the recorded `REQUEST` `Evt` has `endTimestamp - startTimestamp == Δ`, non-negative, both stamps from the same source (SC-004). Depends on US2 (T012).

### Implementation for User Story 3

- [x] T014 [US3] Verify in `src/main/scala/org/galaxio/gatling/transactions/TransactionsActor.scala` that `executeNext` logs `logResponse(start, stop)` and `logGroupRequestTimings(start, stop)` with the single-source timestamps and non-negative spans; no code change expected — if T013 reveals a gap (e.g. a residual `System.currentTimeMillis()` path), fix it here.

**Checkpoint**: Durations accurate and non-negative on the default path.

---

## Phase 6: User Story 4 — Bounded memory under sustained backpressure (Priority: P4)

**Goal**: Bound transaction-event buffering at the Picatinny boundary (drop-newest), expose a dropped-event counter, and never strand a VU on an End-drop.

**Independent Test**: Drive events past `maxInFlight` against a slowed/stalled actor behavior; in-flight stays ≤ bound, `droppedEvents` increments, no unbounded growth, and dropped `TransactionEnded` events still advance their VU.

### Tests for User Story 4 ⚠️ (write first, ensure they FAIL)

- [x] T015 [P] [US4] Test in `src/test/scala/org/galaxio/gatling/transactions/TransactionsSpec.scala`: with a low `maxInFlight` override and a stalled/slow actor behavior, sending more events than the bound keeps in-flight ≤ bound and increments `droppedEvents`; after triggering the actor-system termination (harness `stop()`), assert the dropped-events summary is observable — a crash `Evt` under `Constants.droppedLabel` whose message includes the dropped count (SC-006 — both bounded-memory and observable-metric halves).
- [x] T016 [P] [US4] Test in `src/test/scala/org/galaxio/gatling/transactions/TransactionsSpec.scala`: a dropped `TransactionEnded` still advances its VU — the latch fires and the session is marked failed (FR-003 under overload; no strand).

### Implementation for User Story 4

- [x] T017 [US4] Wire the bound state in `src/main/scala/org/galaxio/gatling/transactions/TransactionsProtocol.scala`: create the shared `inFlight` and `droppedEvents` `AtomicLong`s and read `maxInFlight` (from `Constants`/system property), passing them to both `TransactionTracker` and `TransactionsActor`; update the `TransactionTracker` instantiation in `src/test/scala/org/galaxio/gatling/transactions/Mocks.scala` to match the new constructor (internal class — no public API change).
- [x] T018 [US4] In `src/main/scala/org/galaxio/gatling/transactions/TransactionTracker.scala`: enforce drop-newest — if `inFlight >= maxInFlight`, increment `droppedEvents` and DO NOT enqueue; on an End-event drop, call `next ! session.markAsFailed` (never strand); otherwise increment `inFlight` and send (depends on T017). See [research.md](./research.md) R3.
- [x] T019 [US4] In `src/main/scala/org/galaxio/gatling/transactions/TransactionsActor.scala`: decrement `inFlight` as each message is processed; register a termination summary via `ActorSystem.registerOnTermination` that, when `droppedEvents > 0`, logs a WARN and emits one `statsEngine.logRequestCrash(..., Constants.droppedLabel, "<n> events dropped")` (depends on T017). Keep the ordering guard unchanged.

**Checkpoint**: Transaction tracking is heap-bounded under overload; drops are observable end-to-end; no VU stranded.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T020 [P] Run `sbt scalafmtAll scalafmtSbt` then `sbt scalafmtCheckAll scalafmtSbtCheck compile test` — full suite green.
- [x] T021 [P] Note the user-facing behavior changes (B1–B5 from [contracts/transactions-dsl.md](./contracts/transactions-dsl.md)) for the release changelog (git-cliff notes / CHANGELOG), per Constitution workflow.
- [x] T022 Run the [quickstart.md](./quickstart.md) validation table end-to-end (SC-001…SC-006) and confirm each criterion.
- [x] T023 Backward-compatibility re-check: diff public signatures in `Predef.scala` and `Transactions.java` (must be unchanged), confirm no new build dependency and no PureConfig/serialized-config key added (Constitution II/IV).

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1: T001)** → no deps.
- **Foundational (P2: T002–T004)** → after Setup; **blocks all user stories**. T004 depends on T003; T002 is independent.
- **User Stories (P3–P6)** → after Foundational.
  - US1 (P3) — independent; MVP.
  - US2 (P4) — T012 depends on US1/T009 (same file `EndTransactionAction.scala`) and on T011.
  - US3 (P5) — depends on US2/T012 (rides on the single-clock change; tests only).
  - US4 (P6) — independent of US1–US3 (different files: Tracker/Actor/Protocol); only needs Foundational.
- **Polish (P7)** → after all desired stories.

### Within each story

- Tests (write first, FAIL) → implementation.
- US2: T011 (Predef + builders) → T012 (action).
- US4: T017 (wire counters) → T018 (tracker) + T019 (actor).

### Parallel opportunities

- T002 ∥ (T003→T004) in Foundational.
- US1: T005 ∥ T006 ∥ T007 (tests); T008 ∥ T009 (different files).
- US4: T018 ∥ T019 after T017; T015 ∥ T016 (tests).
- Across stories after Foundational: US1 and US4 can proceed fully in parallel (disjoint files). US2/US3 share `EndTransactionAction.scala` with US1, so sequence US1→US2→US3 on that file.

---

## Parallel Example: User Story 1

```bash
# Tests first (different concerns, same test file — coordinate edits or split):
Task: "T005 [US1] startTransaction unresolvable-name regression"
Task: "T006 [US1] endTransaction unresolvable-name/stopTime regression"
Task: "T007 [US1] throttled + unresolvable fast-fail regression"

# Implementation (different files → true parallel):
Task: "T008 [US1] StartTransactionAction failure handling"
Task: "T009 [US1] EndTransactionAction failure handling"
```

---

## Implementation Strategy

### MVP first (User Story 1 only)

1. Phase 1 Setup (T001) → 2. Phase 2 Foundational (T002–T004) → 3. Phase 3 US1 (T005–T009).
4. **STOP & VALIDATE**: no VU hangs on a bad expression (including throttled); failures recorded. Ship-able correctness fix.

### Incremental delivery

US1 (no-hang) → US2 (single clock, no false ordering) → US3 (accurate durations) → US4 (bounded memory). Each is an independently testable increment; none regresses the preserved scenarios (SC-005).

---

## Notes

- [P] = different files, no incomplete dependency.
- `EndTransactionAction.scala` is touched by US1 (T009) then US2 (T012) — not parallel; sequence them.
- Test harness changes (T003/T004) must keep the existing suite green (SC-005) at every step.
- No public DSL signature change; no new dependency; no serialized-config key (Constitution II/IV).
- Commit after each task or logical group; keep the build green.
