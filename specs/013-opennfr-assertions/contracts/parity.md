# Contract: parity with the deprecated path, row by row

**Feature**: `013-opennfr-assertions`. Serves **FR-015**, **FR-017**, **SC-001** and **SC-006**.

The deprecated builder is the oracle (research R5). This file enumerates what "equal assertion sets"
means, so the claim is checkable rather than asserted: every key the deprecated format recognises,
every scope form, and every one of the eleven assertions `src/test/resources/nfr.yml` builds.

**Status of the evidence.** A throwaway spike on 2026-08-30 built both ways and compared the sets:
ten equal, one excluded, as below. The spike was deleted; **this table is a design input, and the test
in User Story 3 is what re-establishes it.** Nothing here may be taken as passing until that test does.

---

## 1. Metric keys

| Deprecated key | OpenNFR predicate | |
|---|---|---|
| `99 перцентиль времени выполнения` | `{metric: http.client.request.duration, aggregation: p99, op: lt, unit: ms}` | ✅ |
| `95 перцентиль времени выполнения` | `… aggregation: p95 …` | ✅ |
| `75 перцентиль времени выполнения` | `… aggregation: p75 …` | ✅ |
| `50 перцентиль времени выполнения` | `… aggregation: p50 …` | ✅ |
| `Максимальное время выполнения` | `… aggregation: max …` | ✅ |
| `Процент ошибок` | `{bad: {error.type: "*"}, aggregation: rate, op: lt, unit: "%"}` | ✅ |
| any unrecognised key | — | **behaviour differs, deliberately** — the deprecated builder logs a WARN and skips it; OpenNFR has no "recorded, not checked" construct, so such a requirement cannot sit in the document at all (`opennfr#63`). A file mixing checkable and unassertable requirements must be split. |

The Russian key itself is not lost: it survives as `displayName`, which the format allows in any
script. The machine identifier `name` is ASCII-only and is a separate field.

**The operator is implicit in the old format and explicit in the new.** The deprecated builder always
emits `lt`; every row above therefore carries `op: lt`. A translation that writes `lte` is a different
requirement, and the parity test must fail on it.

## 2. Scope forms

| Deprecated scope | OpenNFR selector | |
|---|---|---|
| `all` | `{}` | ✅ |
| `GET /test/email` | `{loadtest.request.name: GET /test/email}` | ✅ — both root-anchored |
| `myGroup / GET /test/id` | `{loadtest.group.name: [myGroup], loadtest.request.name: GET /test/id}` | ✅ |
| `A / B / C` (depth ≥ 3) | `{loadtest.group.name: [A, B], loadtest.request.name: C}` | ✅ — any depth |
| `myGroup` (a group, no request) | — | ❌ **permanently excluded**, see §4 |

## 3. The eleven assertions of `nfr.yml`

| # | Assertion the deprecated path builds | In the translation |
|---|---|---|
| 1 | global · responseTime p99 · lt 1500 | ✅ `whole-run` |
| 2 | `myGroup / GET /test/id` · responseTime p99 · lt 1500 | ✅ `get-test-id` |
| 3 | `GET /test/email` · responseTime p99 · lt 400 | ✅ `get-test-email` |
| 4 | global · responseTime p95 · lt 1200 | ✅ `whole-run` |
| 5 | `myGroup / GET /test/id` · responseTime p95 · lt 1200 | ✅ `get-test-id` |
| 6 | **`myGroup` · responseTime p95 · lt 1600** | ❌ **excluded — §4** |
| 7 | `GET /test/email` · responseTime p95 · lt 320 | ✅ `get-test-email` |
| 8 | global · failedRequests percent · lt 5.0 | ✅ `whole-run` |
| 9 | `GET /test/uuid` · failedRequests percent · lt 1.0 | ✅ `get-test-uuid` |
| 10 | global · responseTime max · lt 2000 | ✅ `whole-run` |
| 11 | `GET /test/uuid` · responseTime max · lt 1000 | ✅ `get-test-uuid` |

**Ten equal, one excluded.** The comparison is over sets: a wrong scope, a wrong condition, a
truncated target or an extra assertion each fail it. Row 9 is the boundary that catches truncation —
its target is fractional and must stay fractional.

`nfr.yml` also carries `APDEX` and `Система должна выдерживать, RPS`. Neither appears above because
the deprecated builder **ignores both**, so neither is a parity obligation. Throughput gains a home in
OpenNFR as a guard; APDEX has none in either format.

## 4. The eleventh, and what blocks it

`myGroup: '1600'` is a group-only scope. Upstream's Selection table recorded it as a `cannot` at
`v0.6.0`, and this renderer followed that row.

> **RESOLVED at upstream `v0.8.0` (feature 014, #328).** `loadtest.group.duration` was minted — the
> name the refusal below was waiting on — so the row is now **can** and parity with the deprecated
> builder is WHOLE: eleven of eleven, with nothing subtracted by name. The paragraph below records
> why it was refused, which is still the right explanation of the gap that `v0.8.0` closed.

**The blocker is a missing metric name, not a missing scope.** Gatling asserts on a group path:
`AssertionValidator.resolvePath` sends a `StatsPath.Group` to
`groupCumulatedResponseTimeGeneralStats` (read from bytecode at `3.13.5`). What it returns is the
group's **cumulated** response time — the sum of the durations of the requests one traversal encloses
— and OpenNFR admitted one metric, `http.client.request.duration`, which is not true of a sum over
several requests. Upstream's row says exactly this: *"which no name in § Names is true of"*.

**What the deprecated assertion measures is not what its author wrote**, either. `myGroup: '1600'`
under the key `95 перцентиль времени выполнения` names a percentile of response time and gets a
percentile of cumulated time. The old format lets that be written; OpenNFR refuses rather than name
the quantity falsely — which is why the deprecated path keeps this row and OpenNFR does not.

FR-017 must say so: a user told only "your file no longer translates" is owed the reason, and is owed
being told the deprecated path still covers it. The gap itself is filed at `opennfr#89`, tracked here
at `#328`. **This file states the cause, not the outcome** — how upstream answers is not settled.

**The workaround is forbidden.** `{loadtest.request.name: myGroup}` renders the identical Gatling
call, because a one-part path is one-part whatever produced it. It is a document that says *request*
about a group, and FR-007 forbids it.

## 5. What the test must do, and must not

- Compare **sets**, not counts, and not field-by-field spot checks.
- Exclude the eleventh **by naming it** and assert separately *why* it is excluded.
- **Never shrink the expected set to make the comparison pass** — that turns the strongest test in the feature into one that proves nothing.
- Assert the deprecated path still builds all eleven, the excluded one included, in the same test class.
