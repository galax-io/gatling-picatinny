# Phase 0 Research: Transactions Reliability (v1.16.0)

All findings verified against the **Gatling 3.13.5 sources** (coursier cache: `gatling-core-3.13.5-sources.jar`, `gatling-shared-util_2.13` for `Clock`) and the project's current `transactions/` code. No open NEEDS CLARIFICATION.

---

## R1. How does `ChainableAction.recover` behave? (VU-hang remediation, #201)

**Decision**: Fix the hang by handling the `Validation.Failure` branch explicitly in each action: call `statsEngine.logRequestCrash(...)` under a **stable static label**, then advance via `next ! session.markAsFailed`. Use the `recover` helper for the advance, but do **not** rely on it for the stats record.

**Rationale**: `ChainableAction.recover` (Action.scala:91-95) is:

```scala
def recover(session: Session)(v: Validation[_]): Unit =
  v.onFailure { message =>
    logger.error(s"'$name' failed to execute: $message")
    next ! session.markAsFailed
  }
```

It only writes to the SLF4J logger and advances the VU — **it does not touch the `StatsEngine`**. So `recover` alone fixes the hang (FR-001/FR-003) but leaves **SC-002 / FR-002 unmet** (no failure recorded in run statistics). The canonical Gatling pattern that *does* record is `RequestAction.execute`/`logCrash` (Action.scala:119-135): on a build `Failure` it calls `statsEngine.logRequestCrash(scenario, groups, requestNameValue, error)` then `next ! session.markAsFailed`. We mirror that.

**Label**: the failing expression is the transaction *name* itself, so no resolved name is available. Use a stable static label so the FR-010 regression can assert on it — e.g. `"startTransaction"` / `"endTransaction"` (constant, not the `genName(...)` value, which is non-deterministic). The label string is pinned in [contracts/transactions-dsl.md](./contracts/transactions-dsl.md).

**Alternatives considered**:
- `recover` only → rejected: VU advances but nothing in stats (SC-002 fails).
- `logRequestCrash` under `genName("startTransactionAction")` → rejected: name carries a counter suffix, not assertable.
- Route a failure message into the actor → rejected: on a name-resolution failure there is no resolved message to send; the start path has no actor round-trip.

---

## R2. Can the Gatling actor mailbox be bounded? (#70)

**Decision**: No — bound at the Picatinny boundary instead. The mailbox is an **unbounded MPSC queue** and `actorOf` exposes **no mailbox configuration**.

**Rationale**: `ActorSystem.actorOf` (ActorSystem.scala:39-42) takes only an `Actor[Message]`. The ref's mailbox (ActorSystem.scala:61) is:

```scala
private val mbox: MessagePassingQueue[Message] = PlatformDependent.newMpscQueue[Message]()...
```

`newMpscQueue()` with no capacity is jctools' **unbounded** growable queue; `!` always `offer`s and succeeds (lines 67-71). There is no hook to swap in a bounded queue or a rejection policy. Therefore the bound must be enforced before the message reaches the actor — in `TransactionTracker` — using a shared in-flight counter that the `TransactionsActor` decrements as it processes.

**Alternatives considered**:
- Configure a bounded mailbox via Gatling → rejected: API absent in 3.13.5.
- Replace the actor with a custom bounded actor → rejected: large, framework-coupled, out of scope / Constitution IV.

---

## R3. What drop policy is safe, and how is the metric exposed? (#70)

**Decision**: **Drop-newest** on enqueue, with a hard rule: **a dropped `TransactionEnded` must still `next ! session.markAsFailed`** so the VU is never stranded. Expose a process-wide `AtomicLong` dropped-event counter; log a WARN on first drop and a final summary at actor termination (via `ActorSystem.registerOnTermination`), plus one `logRequestCrash` summary entry so the drop is visible in run output.

**Rationale**: The boundary can only reject incoming messages (it cannot evict the head of a queue it does not own), so the only feasible policy is drop-newest. `TransactionEnded(name, ts, session, next)` carries the VU's continuation — silently dropping it re-creates the exact #201 hang. So End-drops convert to a recorded failure + advance; Start-drops just increment the counter (the later End then hits the existing "wasn't started" path, which already advances the VU). This keeps FR-003 (no non-signalled VU) invariant even under overload.

**Bound value**: a constant default (`Constants.DefaultMaxInFlight` = `100000`), read through the project's existing config plumbing `SimulationConfig.getIntParam("galaxio.transactions.maxInFlight", default)` — overridable via `simulation.conf` or the equivalent `-D` JVM system property. This reuses the established wrapper (no hand-rolled `sys.props` parsing) and adds only an **optional, additive** key with a default → no PureConfig key, no change to any existing serialized-config format (Constitution IV).

**Alternatives considered**:
- Drop-oldest → rejected (R2: head not owned).
- Drop End silently → rejected: strands VU (re-introduces #201).
- Per-drop `logRequestCrash` → rejected: log/stat flood under overload; use counter + summary instead.

---

## R4. Single clock source for ordering + duration (#69, #201 two-clock)

**Decision**: Source both the start timestamp and the **default** end timestamp from `ctx.coreComponents.clock.nowMillis` (`io.gatling.commons.util.Clock`). Leave the actor's ordering guard unchanged.

**Rationale**: Gatling's `DefaultClock.nowMillis` (verified via bytecode of `gatling-shared-util`):

```
nowMillis = (System.nanoTime() - nanoTimeReference) / 1_000_000 + currentTimeMillisReference
```

It anchors wall-clock **once** at construction, then advances purely by `System.nanoTime()` delta — so it is **monotonic** (never jumps backward on NTP/skew) yet still returns **epoch-millis** (correct for `logResponse`/report timeline). `StartTransactionAction` already uses `ctx.coreComponents.clock.nowMillis` (StartTransactionAction.scala:28). Only the default end timestamp uses raw `System.currentTimeMillis()` (`Predef.scala:47`, `builders.scala:18`). Routing the default end to the same clock makes both ordering operands monotonic from one source → the actor's `started.timestamp > timestamp` check can never fire falsely on the default path. **No change to `TransactionsActor` is required for #69.**

**Preserved behavior**: an explicit user-supplied early `stopTime` (e.g. `endTransaction("t1", 1L)`) is still compared as-is and still yields the existing illegal-state KO → SC-005 holds; the public `stopTime` contract is unchanged (still epoch-millis, same scale).

**Alternatives considered**:
- Raw `System.nanoTime()` for ordering + separate wall-clock for logging (two-stamp actor messages) → rejected: more invasive, and feeding nanos to `logResponse` would corrupt report epochs. The Gatling clock already unifies both properties.

---

## R5. Backward-compatible default-timestamp switch (Constitution II)

**Decision**: Replace the single default-arg method with **two clean overloads** on `TransactionsOps`:
`endTransaction(tName)` and `endTransaction(tName, stopTime)` both route to a single `EndTransactionActionBuilder(tName, stopTime: Option[Expression[Long]] = None)` (`None` for the no-time form, `Some(expr)` for explicit). `EndTransactionAction` keeps a plain `stopTime: Expression[Long]` (no `Option`, no match in `execute`). The builder has `ctx` at `build` time, so `None` resolves to a clock-reading expression — `_ => ctx.coreComponents.clock.nowMillis.success` — evaluated at execute time; `Some` passes the caller's expression unchanged. Since the time source is now uniform (clock) the earlier separate `EndTransactionActionBuilderWithoutTime` is unnecessary and removed. Matches the Java facade's two-method shape (`endTransaction(name)` / `endTransaction(name, time)`).

**Rationale**: This is the idiomatic, clean solution — no sentinel, no reference-equality (`eq`) trick, and crucially **no `System.currentTimeMillis()` anywhere** in the call path. The earlier sentinel approach kept a default-arg whose value still textually read `System.currentTimeMillis()` (never evaluated, but misleading and a latent two-clock hazard if the `eq` ever missed). The overloads remove that entirely. **Source compatibility is preserved**: `endTransaction("t")` and `endTransaction("t", expr)` both still compile. **Binary-compat note**: removing the default-arg drops the synthesized `endTransaction$default$2$extension`, so a consumer that swaps the JAR *without recompiling* and used the no-time form would need a recompile (normal for a Scala-version-coupled library; no MiMa gate exists in this repo). Accepted by the maintainer in favor of clean code (MINOR bump, source-compatible).

**Alternatives considered**:
- Sentinel default value + `eq` check → rejected: hacky, and leaves a misleading `System.currentTimeMillis()` literal in the default.
- Leave `System.currentTimeMillis()` default → rejected: that is the #201 two-clock defect.

---

## R6. Deterministic test substrate (Q4 — SC-001/SC-003/SC-004 + FR-010)

**Decision**: Two seams on the existing real-actor harness (`Mocks.MockedGatlingCtx`):
1. **Injectable `Clock`** — replace `new DefaultClock` in `testCoreComponents` with a controllable `Clock` (a test impl whose `nowMillis` is settable/advanceable). Drives deterministic start/end timestamps for SC-003 (no false ordering failure) and SC-004 (non-negative, accurate duration).
2. **Latch terminal action** — replace `runScenario`'s `Thread.sleep(200)` with a `CountDownLatch` counted down by the terminal `next` action; the test awaits the latch with a bounded timeout. Proving "never stalls" (SC-001) becomes: *latch fires within timeout* (pass) vs *times out* (the hang). This directly encodes FR-003.

**Rationale**: The current `Thread.sleep(200)` is racy and cannot prove a negative (no-hang). A latch is deterministic and bounded. The harness already uses the **real** `ActorSystem`/`TransactionsActor`/`RecordingStatsEngine`, satisfying "no mocked runtime"; the `Clock` interface (`nowMillis`) is trivially implementable as a seam. No external infra ⇒ no Testcontainers (see plan Complexity Tracking).

**Alternatives considered**:
- OS clock manipulation (libfaketime/Testcontainers) → rejected in clarify: heavyweight, no external process to containerize.
- Keep static clocks, unit-test the actor only → rejected: cannot validate the end-to-end "keeps running" claim or the action-layer clock sourcing.

---

## Resolved decisions summary

| # | Area | Decision |
|---|------|----------|
| R1 | VU-hang | Explicit `Failure` handling: `logRequestCrash` (stable static label) + `next ! markAsFailed`; `recover` for the advance only |
| R2 | #70 feasibility | Framework mailbox unbounded & unconfigurable → bound at `TransactionTracker` boundary |
| R3 | #70 policy/metric | Drop-newest + **safe-advance on End drop**; `AtomicLong` counter + termination summary + one crash entry |
| R4 | Clock | Both start & default end from `coreComponents.clock` (monotonic-epoch); actor guard unchanged; #69 fixed structurally |
| R5 | Compat | Two `endTransaction` overloads (no-time → clock, with-time → explicit); no-time builder injects a clock-reading expression, action stays plain `Expression[Long]`. Source-compatible; no sentinel/`eq`/`Option`/wall-clock |
| R6 | Tests | Injectable `Clock` + `CountDownLatch` terminal on the real-actor harness |
