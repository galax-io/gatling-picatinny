# Implementation Plan: Transactions Reliability (v1.16.0)

**Branch**: `001-transactions-reliability` (git worktree branch `claude/xenodochial-greider-9ff4b1`) | **Date**: 2026-06-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-transactions-reliability/spec.md`

## Summary

Four transaction-reliability defects in the `org.galaxio.gatling.transactions` module, all confirmed against the Gatling 3.13.5 sources:

1. **VU-hang (#201, P1)** — `StartTransactionAction`/`EndTransactionAction` run a `for/yield` over `Validation[Unit]` and discard the result. An EL `Failure` short-circuits the yield, so `next` is never signalled and nothing is logged → the virtual user stalls forever. Fix: handle the `Failure` branch explicitly — record it in stats and advance the VU.
2. **Wall-clock ordering (#69, P2)** — start timestamps already come from the Gatling clock, but the **default** end timestamp comes from `System.currentTimeMillis()`. Sourcing the default end timestamp from the same Gatling clock (which is monotonic-by-construction) makes the actor's `started.timestamp > timestamp` guard skew-immune for the default path, eliminating false "cannot end before it started" failures.
3. **Two-clock timestamps (#201, P3)** — same root cause/fix as #69: one clock source (the Gatling clock) for start and default end.
4. **Unbounded mailbox (#70, P4)** — the Gatling actor mailbox is an unbounded, unconfigurable MPSC queue. Bound transaction-event buffering at the Picatinny tracking boundary with a drop-newest policy and an observable dropped-event counter, **without ever stranding a virtual user** (a dropped `TransactionEnded` must still signal `next`).

**Technical approach**: action-layer fixes for #201/#69/P3 (failure handling + clock sourcing), keeping the public DSL **source-compatible** via two clean `endTransaction` overloads (no-time → Gatling clock, with-time → explicit; the no-time builder injects a clock-reading expression, action stays plain `Expression[Long]`) — no sentinel, no `eq`, no `Option`, no wall-clock; a boundary-level bounded buffer in `TransactionTracker`/`TransactionsActor` for #70; deterministic tests via an injectable `Clock` seam and a `CountDownLatch` on the terminal action (replacing the racy `Thread.sleep`).

## Technical Context

**Language/Version**: Scala 2.13.18 (compile target Java 17, `--release 17`; CI on Temurin 21)

**Primary Dependencies**: Gatling 3.13.5 (`Provided`) — specifically `io.gatling.core.action.{Action, ChainableAction}`, `io.gatling.core.actor.{Actor, ActorSystem, ActorRef}`, `io.gatling.core.stats.StatsEngine`, and `io.gatling.commons.util.Clock` (from `gatling-shared-util`). No new dependencies.

**Storage**: N/A (in-memory transaction tracking only).

**Testing**: ScalaTest (`AnyWordSpec` + Matchers). Transaction-boundary coverage uses the existing **real-actor in-process harness** (`Mocks.MockedGatlingCtx`: real `ActorSystem`, real `TransactionsActor`, `RecordingStatsEngine` capturing `Evt`s) — not stubs/fakes. New seams: an injectable `Clock` and a latch-based terminal action.

**Target Platform**: JVM. Published library consumed by external Scala/Java/Kotlin load-test projects.

**Project Type**: Single Scala library with a thin Java/Kotlin facade (`src/main/java/.../javaapi`).

**Performance Goals**: Per-transaction overhead unchanged (one clock read + one counter op). Transaction-tracking heap bounded under sustained overload (no OOM).

**Constraints**: Backward compatibility (NON-NEGOTIABLE) for public DSL signatures, serialized config, and observable outcomes of valid simulations; durations non-negative and within ≤1 ms of true elapsed time for the default path; no mocked Gatling runtime.

**Scale/Scope**: Transaction events scale as (virtual users × transactions/scenario). The #70 bound caps in-flight unprocessed events; default bound is a compile-time constant (overridable via system property), no PureConfig key added.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — All logic lands in the Scala core (`transactions/`). The Java facade (`Transactions.java`) already delegates to the Scala builders and is unchanged; no facade logic added.
- [x] **II. Backward Compatibility** — Public DSL kept **source-compatible**: the single default-arg `endTransaction` is realized as two overloads (`endTransaction(tName)` → Gatling clock; `endTransaction(tName, stopTime)` → explicit), so both call forms still compile and the shape matches the Java facade. Binary note: the synthesized `endTransaction$default$2` is dropped, so a no-recompile JAR swap using the no-time form needs a recompile (no MiMa gate; MINOR bump) — accepted in favor of clean code over the sentinel hack. The default end-timestamp **source** changes from `System.currentTimeMillis()` to the Gatling clock — both epoch-millis on the same host; a compatible correctness fix (FR-009). Explicit `endTransaction(name, earlyTime)` still produces the existing illegal-state KO (SC-005). The #70 dropped-event metric is purely additive.
- [x] **III. Test Discipline** — Unit/behavioral tests accompany every changed path. Transaction-boundary behavior is exercised through the real-actor in-process harness (real `ActorSystem` + `TransactionsActor` + `RecordingStatsEngine`), which the amended Principle III (constitution v1.0.3) accepts as a real integration path where no external process exists to containerize. No deviation remains.
- [x] **IV. Small, Focused Changes** — Scope limited to the `transactions/` package and its tests. No new build dependencies. The #70 bound uses a constant default (optionally overridable via a system property), avoiding any PureConfig/serialized-config change. No opportunistic refactors.
- [x] **V. Release Integrity** *(release PRs only)* — Not a release PR. When v1.16.0 ships, follow the mandated `release/1.16.0` branch + `v1.16.0` tag process.

## Project Structure

### Documentation (this feature)

```text
specs/001-transactions-reliability/
├── plan.md              # This file
├── spec.md              # Feature spec (+ Clarifications)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── transactions-dsl.md   # Public DSL + behavioral contract
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
src/main/scala/org/galaxio/gatling/transactions/
├── Predef.scala                         # endTransaction: two overloads (no-time → clock, with-time → explicit)
├── TransactionTracker.scala             # #70: bounded send + dropped counter; safe-advance on End drop
├── TransactionsActor.scala              # #70: decrement in-flight on processing; ordering guard unchanged
├── TransactionsComponents.scala         # (unchanged)
├── TransactionsProtocol.scala           # wire shared in-flight counter + bound into tracker/actor
└── actions/
    ├── StartTransactionAction.scala     # #201: explicit Failure handling (logRequestCrash + next ! markAsFailed)
    ├── EndTransactionAction.scala       # #201 + clock: Failure handling; default end ts from Gatling clock
    └── builders.scala                   # one EndTransactionActionBuilder(Option stopTime): None → clock, Some → explicit

src/main/java/org/galaxio/gatling/javaapi/
└── Transactions.java                    # no-time path passes Option.empty() → clock; with-time passes Some(expr)

src/test/scala/org/galaxio/gatling/transactions/
├── TransactionsSpec.scala               # new regression cases; replace Thread.sleep with latch
├── Mocks.scala                          # inject controllable Clock; latch terminal action
├── fixtures.scala                       # latch-capable terminal action helper
├── FakeEventLoop.scala                  # (unchanged)
└── Evt.scala                            # (unchanged)
```

**Structure Decision**: Existing single-library layout. All production changes are confined to `transactions/` (5 Scala files + builders); the Java facade is untouched. Tests extend the existing real-actor harness.

## Complexity Tracking

| Violation / Deviation | Why Needed | Simpler Alternative Rejected Because |
|-----------------------|------------|--------------------------------------|
| Default end-timestamp **source** changes (`System.currentTimeMillis()` → Gatling clock) — touches observable default behavior (Constitution II) | Root-cause fix for #69 + #201 two-clock; the Gatling clock is monotonic-by-construction so it removes skew-induced false failures and guarantees a single consistent source | Clamping negative durations only (rejected in clarify): leaves #69 false failures under NTP skew. Keeping two sources: the original defect. |
| Transaction-boundary tests are real-actor **in-process**, not Testcontainers | Transactions have no external process/infra to containerize; the real `ActorSystem`+`TransactionsActor`+`RecordingStatsEngine` harness is a genuine integration path, not a stub | Spinning a container would add nothing real to bind to; mocking the runtime is explicitly forbidden and weaker. *(No longer a deviation — Constitution v1.0.3 explicitly permits an in-process real-runtime harness where no external process exists.)* |
| #70 bound enforced at the Picatinny boundary (not the actor mailbox) | Gatling's actor mailbox is an unbounded MPSC queue with no configuration hook (`actorOf` takes no mailbox arg) — bounding it inside the framework is impossible | Configuring a bounded mailbox (rejected): the API does not exist in Gatling 3.13.5. |
| #70 drop policy is **drop-newest** with safe-advance, not drop-oldest | The boundary can only reject on enqueue (it does not own the queue head); a dropped `TransactionEnded` must still signal `next` or it re-introduces the #201 hang | Drop-oldest (rejected): requires owning/mutating the framework queue head, which is inaccessible. |

## Phase 0: Research

See [research.md](./research.md). All unknowns resolved by reading the Gatling 3.13.5 sources directly; no open NEEDS CLARIFICATION.

## Phase 1: Design & Contracts

- [data-model.md](./data-model.md) — entities: transaction span, actor messages, open-transaction stack, in-flight counter + bound, clock seam.
- [contracts/transactions-dsl.md](./contracts/transactions-dsl.md) — public DSL signatures (preserved) + the behavioral contract changes and their acceptance hooks.
- [quickstart.md](./quickstart.md) — how to run and validate each Success Criterion.

**Post-design Constitution re-check**: PASS — design introduces no new public signatures, no new build deps, no serialized-config change; all five principles hold with the deviations justified above.
