# OpenNFR assertions

**Experimental.** This path reads an [OpenNFR](https://github.com/galax-io/opennfr) `RequirementSet` —
a tool-agnostic format for load testing requirements — and produces Gatling assertions.

It tracks upstream release **`v0.6.0`**. OpenNFR is pre-1.0 and its schema has changed materially
between consecutive releases, so **this surface is outside the binary-compatibility guarantee** the
rest of the library keeps. The deprecated NFR-YAML path (`assertionFromYaml`) is untouched, still
works, and keeps its guarantees in full.

## Using it

```scala
import org.galaxio.gatling.assertions.opennfr.OpenNfrAssertions

setUp(scn.inject(atOnceUsers(10)))
  .assertions(OpenNfrAssertions.fromYaml("nfr.yaml"))
```

```java
import org.galaxio.gatling.javaapi.OpenNfrAssertions;

setUp(scn.injectOpen(atOnceUsers(10)))
    .assertions(OpenNfrAssertions.fromYaml("nfr.yaml"));
```

A refusal is **total and loud**: a document carrying one unrenderable predicate produces no
assertions at all — a run that silently checked nine of ten is the failure this exists to prevent —
and it lists every reason, so one build surfaces every mistake.

## Migrating from NFR-YAML

### Metric keys

| NFR-YAML key | OpenNFR predicate |
|---|---|
| `99 перцентиль времени выполнения` | `{metric: http.client.request.duration, aggregation: p99, op: lt, unit: ms}` |
| `95 перцентиль времени выполнения` | `… aggregation: p95 …` |
| `75 перцентиль времени выполнения` | `… aggregation: p75 …` |
| `50 перцентиль времени выполнения` | `… aggregation: p50 …` |
| `Максимальное время выполнения` | `… aggregation: max …` |
| `Процент ошибок` | `{bad: {error.type: "*"}, aggregation: rate, op: lt, unit: "%"}` |

**`op: lt` everywhere.** The deprecated builder's operator is implicit and is always `lt`. Writing
`lte` states a different requirement.

The Russian key itself is not lost — it becomes `displayName`, which the format allows in any script.
The machine identifier `name` is lower-case-and-hyphens and is a separate field.

### Scopes

| NFR-YAML scope | OpenNFR selector |
|---|---|
| `all` | `selector: {}` |
| `GET /test/email` | `{loadtest.request.name: GET /test/email}` |
| `myGroup / GET /test/id` | `{loadtest.group.name: [myGroup], loadtest.request.name: GET /test/id}` |
| `A / B / C` | `{loadtest.group.name: [A, B], loadtest.request.name: C}` |
| `myGroup` — a group with no request | **no equivalent, see below** |

### A worked example

```yaml
apiVersion: opennfr.io/v1
kind: RequirementSet
metadata:
  name: my-service

spec:
  requirements:
    - name: whole-run
      selector: {}
      criteria:
        - {displayName: 99 перцентиль, metric: http.client.request.duration, aggregation: p99, op: lt, threshold: 1500, unit: ms}
        - {displayName: Процент ошибок, bad: {error.type: "*"}, aggregation: rate, op: lt, threshold: 5, unit: "%"}

    - name: checkout
      selector:
        loadtest.group.name: [Checkout]
        loadtest.request.name: POST /checkout
      criteria:
        - {metric: http.client.request.duration, aggregation: p95, op: lt, threshold: 500, unit: ms}
```

## What does not survive the move

### A scope naming only a group

`myGroup: '1600'` has **no OpenNFR spelling today** — and the reason is worth being exact about,
because it is not the one you would guess.

**Gatling asserts on a group perfectly well.** `details("myGroup")` resolves to the group and answers
with its **cumulated** response time: the sum of the durations of the requests one pass through the
block encloses. What is missing is a *name*. OpenNFR admits exactly one metric,
`http.client.request.duration`, and a sum over several requests is not an HTTP request's duration —
so **no metric name in the format is true of the quantity Gatling would return**. The document cannot
say what it would have to say, and this renderer refuses rather than name the quantity falsely.

That is a gap in the format's metric axis, not a limit of the target. It is being decided upstream at
[`opennfr#89`](https://github.com/galax-io/opennfr/issues/89) — the same gap that leaves Kafka, JDBC
and bracketed spans unnameable — so this page will change when that does.

**Meanwhile the deprecated path still covers this one case.** `assertionFromYaml` is untouched and
keeps working; keep it for the file that needs it. Be clear about what its number means, though: if
you wrote `myGroup: '1600'` expecting the 95th percentile of the requests inside the group, it has
never measured that. NFR-YAML let the assertion be written under a name (`responseTime`) that is not
true of the number computed.

If what you want is the requests inside the group, assert them individually, or use
`{loadtest.request.name: "*"}` to state the bar once for every recorded request.

**Do not write `{loadtest.request.name: myGroup}`.** It renders the identical Gatling call, because a
one-part path is one-part whatever produced it — and it is a document that says *request* about a
group. It will pass, and it will be a lie in your requirements file.

### A requirement this tool cannot check

NFR-YAML logs a warning and skips a key it does not recognise, so one file could hold both checkable
and unassertable requirements. OpenNFR has no "recorded, not checked" construct: such a document is
refused whole. A mixed file has to be split.

### Anything Gatling cannot assert

`neq`, `sum`, body-size metrics, an error budget narrowed to a status code, `good`, and units other
than `ms`/`s` for response time are each refused with the reason the upstream reach tables give.

## A guard is quantified with the criteria it sits beside

A requirement's `selector` is written once and binds its `guards` as well as its `criteria`. Under
`{loadtest.request.name: "*"}` that means the guard renders as `forAll()` too — and on a run that
recorded nothing, `forAll()` expands to **zero** assertions and passes.

```yaml
# Does NOT catch a run that never happened.
- name: every-endpoint
  selector: {loadtest.request.name: "*"}
  guards:
    - {aggregation: rate, op: gte, threshold: 200, unit: "{request}/s"}
  criteria:
    - {metric: http.client.request.duration, aggregation: p99, op: lt, threshold: 500, unit: ms}
```

**Put the guard that says the run happened on a `{}` requirement**, where it renders as a global
assertion and fails an empty run. This is upstream
[`opennfr#69`](https://github.com/galax-io/opennfr/issues/69); the behaviour here is the format's,
implemented faithfully rather than worked around.

## Known disagreements with upstream

This library follows the published tables even where it has reason to think a row is wrong, and says
so in the refusal message rather than quietly diverging:

| Shape | Upstream |
|---|---|
| `count` / `rate` over a metric | reported from here; Gatling computes both from the same buffer as the percentiles, so the row looks over-refusing |
| units `ns`, `us`, `min`, `h` | [`opennfr#74`](https://github.com/galax-io/opennfr/issues/74) — response time reaches all four exactly |
