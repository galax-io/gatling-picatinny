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

## 4. The eleventh, and why it never joins

`myGroup: '1600'` is a group-only scope. Upstream settled in `v0.6.0` that it is a permanent
`cannot`: Gatling has three scopes and none denotes the requests a path encloses; a group path
resolves to the *group*, whose statistics are its own.

**What the deprecated assertion actually measures is not what its author wrote.** Gatling computes
the group's **cumulated** response time — the summed duration of one pass through the block — not the
95th percentile of the requests inside it. The old format lets that be written under a name that is
not true of the measurement; OpenNFR refuses rather than name it falsely.

So this row is **a mislabelling declined, not a capability lost**, and FR-017 must say so — a user
told only "your file no longer translates" is owed the reason.

**The workaround is forbidden.** `{loadtest.request.name: myGroup}` renders the identical Gatling
call, because a one-part path is one-part whatever produced it. It is a document that says *request*
about a group, and FR-007 forbids it.

## 5. What the test must do, and must not

- Compare **sets**, not counts, and not field-by-field spot checks.
- Exclude the eleventh **by naming it** and assert separately *why* it is excluded.
- **Never shrink the expected set to make the comparison pass** — that turns the strongest test in the feature into one that proves nothing.
- Assert the deprecated path still builds all eleven, the excluded one included, in the same test class.
