# OpenNFR assertions

**Experimental.** This path reads an [OpenNFR](https://github.com/galax-io/opennfr) `RequirementSet` —
a tool-agnostic format for load testing requirements — and produces Gatling assertions.

It tracks upstream release **`v0.8.0`**. OpenNFR is pre-1.0 and its schema has changed materially
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

`myGroup: '1600'` **now has an OpenNFR spelling**, as of upstream `v0.8.0`:

```yaml
    - name: mygroup-itself
      selector:
        loadtest.group.name: [myGroup]
      criteria:
        - {metric: loadtest.group.duration, aggregation: p95, op: lt, threshold: 1600, unit: ms}
```

Until `v0.8.0` this was refused, and the reason was never a limit of the target: Gatling asserts on a
group perfectly well. What was missing was a *name*. The format admitted one duration metric, and a
sum over several requests is not an HTTP request's duration — so no metric name in the format was
true of the quantity Gatling would return. [`opennfr#89`](https://github.com/galax-io/opennfr/issues/89)
closed that gap by minting `loadtest.group.duration`, and this renderer follows.

**Know what the number is.** `loadtest.group.duration` renders to Gatling's **cumulated** response
time: the **sum of the durations of the operations the group encloses**, not the elapsed time of one
traversal. A run that pauses inside a group is not charged for the pause. If what you want is the
requests inside the group, assert them individually, or use `{loadtest.request.name: "*"}` to state
the bar once for every recorded request — the group number is not that.

**Charts and assertions can disagree, by design.** `gatling.charting.useGroupDurationMetric`
(default `false`) switches the *report* between the two group quantities and never reaches the
assertion path — the wall-clock quantity is not exposed on the interface assertions read, so no
configuration makes it assertable. With that flag set, a run shows wall clock in its charts while its
assertions judged cumulated response time. Same run, same group, two numbers.

**The pairing binds both ways.** `loadtest.group.duration` is admitted *only* under a selector naming
a group hierarchy with no request name, and under that selector it is the *only* admissible metric —
`loadtest.request.duration`, any other metric, and a predicate with no metric at all are all refused
there.

**`http.client.request.duration` is retired.** It was the format's duration metric until `v0.8.0` and
is **not aliased**: it named the vantage, the protocol and the granularity in one string when a load
generator fixes only the first, and it is published by other producers, so one string could carry two
measurements. Documents carrying it are refused, with a message naming the replacement. Write
`loadtest.request.duration` for the duration of one recorded operation.

**Do not write `{loadtest.request.name: myGroup}`.** It renders the identical Gatling call, because a
one-part path is one-part whatever produced it — and it is a document that says *request* about a
group. It will pass, and it will be a lie in your requirements file. Now that the group spelling
exists there is no reason to reach for it: the two render to the same assertion, and only one of them
says what you mean.

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
