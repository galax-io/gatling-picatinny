# Phase 1 Data Model: Assertions Correctness

The NFR-YAML loader's data is an in-memory tree parsed from a YAML file and mapped to a
collection of Gatling assertions. No persistence; the "model" is the parse shape + the
mapping contract.

## Entities

### NFR (root)
- **Shape**: `{ nfr: [ Record ] }` — a single key `nfr` holding an ordered list.
- **Scala**: `private case class NFR(nfr: List[Record])`. **Java**: `private record NFR(List<RecordNFR> nfr)`.
- Both `private` → not a public/deprecatable surface.

### Record
- **Fields**: `key: String` (the NFR metric name), `value: Map[String, String]`
  (scope → threshold-as-string).
- **Scala**: `private case class Record(key, value)`. **Java**: `private record RecordNFR(String key, HashMap<String,String> value)`.
- `key` may be **recognized** or **unknown** (see mapping).
- `value` map keys are **scopes**: the literal `all` (→ global) or a `group / request`
  path string (→ detail). `value` map values are numeric strings.

### Recognized metric → Gatling assertion mapping (UNCHANGED — FR-009)
| NFR `key` (Cyrillic) | Metric | Threshold type | Builder |
|----------------------|--------|----------------|---------|
| `Процент ошибок` | failed-requests percent | **Double** | `failedRequests.percent.lt(v)` |
| `99 перцентиль времени выполнения` | response-time p99 | Int | `responseTime.percentile(99).lt(v)` |
| `95 перцентиль времени выполнения` | response-time p95 | Int | `responseTime.percentile(95).lt(v)` |
| `75 перцентиль времени выполнения` | response-time p75 | Int | `responseTime.percentile(75).lt(v)` |
| `50 перцентиль времени выполнения` | response-time p50 | Int | `responseTime.percentile(50).lt(v)` |
| `Максимальное время выполнения` | response-time max | Int | `responseTime.max.lt(v)` |
| *(anything else)* | — | — | **WARN + skip** (FR-003; was silent skip) |

> **Type note (F1)**: error-rate is **Double** (fractional percent valid, e.g. `5.5`).
> Java already parses it as `double`; the current Scala `buildErrorAssertion` wrongly
> uses `v.toInt` (crashes on `"5.5"`) — FR-004/T010 switch it to `toDoubleOption`. Time
> metrics stay **Int** (milliseconds). Integer percent like `'5'` is unaffected (→ `5.0`).

### Scope resolution (UNCHANGED)
- `value` entry key `all` → `global` assertion.
- otherwise → `details(key.split(" / "))` detail assertion addressed by `group / request`.

### Assertion (output)
- A Gatling assertion: `(scope, metric, lt-threshold)`. The load result is
  `Iterable[Assertion]` (Scala) / `List<Assertion>` (Java), **one element per `value`
  entry across recognized records** (FR-001).

### AssertionBuilderException (Java, public — deprecatable)
- Fields `msg`, `cause` + accessors `.msg()`, `.cause()`, `equals`/`hashCode`/`toString`.
- **FR-005**: MUST also pass them to `super(msg, cause)` so `getMessage()`/`getCause()`
  are non-null.

## Invariants / validation rules

| Rule | Source | Enforced where |
|------|--------|----------------|
| Result size = Σ `value`-entries over recognized records (no duplication) | FR-001 | Java facade build loop |
| Unknown `key` → no assertion, one WARN naming the key | FR-003 | both builders' match-default |
| Non-numeric `value` → error naming key+value | FR-004 | both builders' parse step |
| Cyrillic `key` matches regardless of default charset | FR-006 | key compared as parsed (toUtf removed) |
| Recognized mapping + scope semantics unchanged | FR-009 | both builders |
| `getMessage()`/`getCause()` non-null when supplied | FR-005 | `AssertionBuilderException` ctor |

## Worked example (the `nfr.yml` fixture)

Records and their `value` counts: APDEX(1, **unknown→skip+WARN**),
`…RPS`(1, **unknown→skip+WARN**), p99(3), p95(4), errors(2), max(2).
**Recognized total = 3+4+2+2 = 11 assertions** (the FR-001 expected size; unknown keys
contribute 0). Detail paths include `myGroup`, `myGroup / GET /test/id`,
`GET /test/email`, `GET /test/uuid`; `all` entries → global.
