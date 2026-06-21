# Contract: Transactions DSL & Behavior (v1.16.0)

The library's external contract is its **public DSL** plus the **observable behavior** of transaction tracking. This document states what MUST stay stable and what changes.

## 1. Public API signatures — UNCHANGED (binary + source compatible)

### Scala (`org.galaxio.gatling.transactions.Predef.TransactionsOps`)
```scala
def startTransaction(tName: Expression[String]): ScenarioBuilder
def endTransaction(tName: Expression[String]): ScenarioBuilder                            // end ts ← Gatling clock
def endTransaction(tName: Expression[String], stopTime: Expression[Long]): ScenarioBuilder // explicit end ts
```
- The previous single default-arg method is replaced by **two overloads** — the same call surface (`endTransaction("t")` and `endTransaction("t", expr)` both compile), matching the Java facade's existing two-method shape. **Source-compatible.** Binary note: the synthesized `endTransaction$default$2` is dropped, so a no-recompile JAR swap using the no-time form needs a recompile (no MiMa gate; MINOR bump). No sentinel, no `eq`, no `System.currentTimeMillis()`.

### Java/Kotlin facade (`org.galaxio.gatling.javaapi.Transactions`)
```java
static ChainBuilder startTransaction(String name)
static ChainBuilder endTransaction(String name)
static ChainBuilder endTransaction(String name, Long time)
```
- Unchanged. `endTransaction(name)` (the no-time path) now sources the default end timestamp from the Gatling clock instead of `System.currentTimeMillis()`. A single `EndTransactionActionBuilder(tName, stopTime: Option[Expression[Long]] = None)` covers both forms (`None` → clock, `Some` → caller value).

### `SimulationWithTransactions`
- Unchanged (Scala `Predef.SimulationWithTransactions` and Java facade subclass).

## 2. Behavioral contract — CHANGES

| ID | Behavior | Before | After (v1.16.0) |
|----|----------|--------|-----------------|
| B1 | Unresolvable EL in `startTransaction`/`endTransaction` | VU silently stalls forever; nothing logged | VU advanced as failed (`next ! session.markAsFailed`) **and** a crash recorded in run stats |
| B2 | Default end timestamp source | `System.currentTimeMillis()` | `coreComponents.clock.nowMillis` (monotonic-epoch) |
| B3 | Ordering under wall-clock backward jump (default path) | false "cannot end before it started" KO possible | never false-fails (single monotonic source) |
| B4 | Reported duration (default path) | could be negative/skewed across two clocks | always ≥ 0, single source |
| B5 | Transaction-event buffering under overload | unbounded → OOM | bounded (drop-newest); dropped count observable; End-drops still advance the VU |

## 3. Behavioral contract — PRESERVED (must not regress, SC-005)

| Scenario | Expected (unchanged) |
|----------|----------------------|
| Valid open/close (default end) | one `REQUEST` record, `OK`, `startTs ≤ endTs`, group timings logged |
| Nested transactions | inner & outer both `OK`; `inner.startTs ≥ outer.startTs`, `inner.endTs ≤ outer.endTs` |
| Close a not-opened transaction | `ERROR` `"Transaction '<n>' close error"`, KO, msg `"transaction '<n>' wasn't started"` |
| Close with explicit early `stopTime` (e.g. `1L`) | `ERROR` `"Transaction '<n>' illegal state"`, KO, msg `"transaction cannot end before it started"` |
| Incorrect close sequence (unclosed inner) | `ERROR` `"Transaction '<n>' close error"`, KO, msg `"has unclosed transaction <inner>"` |
| Scala `before`/`after` hooks & Java override-style hooks | run in order `before`, `after` |

## 4. Pinned identifiers (load-bearing for FR-010 regression assertions)

| Purpose | Stable label/string |
|---------|---------------------|
| Start-action resolution-failure crash label | `"startTransaction"` (static constant) |
| End-action resolution-failure crash label | `"endTransaction"` (static constant) |
| #70 dropped-events summary crash label | `"transactions dropped"` (static constant), error message includes the dropped count |
| #70 bound override (config path / `-D` system property, read via `SimulationConfig`) | `galaxio.transactions.maxInFlight` (default `100000`) |

> Exact constant strings are finalized in implementation; tests MUST reference the same constants (no string duplication) so the contract and assertions cannot drift.

## 5. Acceptance hooks (which SC each contract item proves)

- B1 → SC-001 (no stalled VU), SC-002 (100% recorded), FR-010 regression
- B2/B4 → SC-004 (duration non-negative, accurate)
- B3 → SC-003 (zero false ordering failures)
- B5 → SC-006 (bounded memory, dropped observable), and FR-003 (End-drop never strands)
- Section 3 → SC-005 (no regression)
