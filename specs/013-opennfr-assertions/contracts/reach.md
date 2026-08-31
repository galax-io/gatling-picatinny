# Contract: the reach rows this renderer implements

**Source**: upstream `README.md` § *What any tool can actually run*, at release `v0.6.0`.
**Transcription checked against that source 2026-08-30**, at upstream commit `1e394f1`: the four Selection
**can** rows, the Metrics row, the aggregation rows and the two `cannot` rows this library follows under
protest were each compared line by line (FR-010, T053). That section
is the tables' **only** home; `specs/004-strip-to-schema/contracts/gatling-reach.md` is a redirect and
must not be cited (FR-010).

This file is the source transcribed **as data**, so the implementation is a lookup and the tests are a
cross-product. It adds no rule. Where a shape is absent from a **can** row it is refused with the
reason given — an allowlist, never accept-by-default (FR-009).

## Selection

| Selector | Renders as | |
|---|---|---|
| `{}` | the global scope | **can** |
| `{loadtest.request.name: X}`, `X` a string other than `"*"` | the one-part path `X` | **can** — root-anchored: it resolves only to a request with no enclosing group |
| `{loadtest.group.name: [G₁…Gₙ], loadtest.request.name: X}`, all strings other than `"*"` | the ordered path `G₁ / … / Gₙ / X` | **can** — any depth, matched whole and never as a prefix |
| `{loadtest.request.name: "*"}` | the per-request scope | **can** — one statement per recorded request position |
| `{loadtest.group.name: [G₁…Gₙ], loadtest.request.name: "*"}` | — | **cannot** — no scope both quantifies and carries a path |
| `{loadtest.group.name: [… "*" …], loadtest.request.name: X}` | — | **cannot** — a group at that position with any name, and no scope carries a wildcard path part |
| `{loadtest.group.name: [G₁…Gₙ]}`, no request name | — | **cannot** — no scope denotes the requests a path encloses; a group path resolves to the group, whose statistics are its own |
| `loadtest.group.name` as a string, or `[]` | — | **cannot** — a hierarchy has one spelling; "no enclosing group" is said by omitting the key |
| any path value that is not a string | — | **cannot** — a path part is a string; `200` and `"200"` are different documents |
| any other attribute | — | **cannot** — Gatling addresses assertions by recorded group and request names only |

## Metric

| `metric` | Renders as | |
|---|---|---|
| `http.client.request.duration` | the response-time statistic | **can** |
| anything else, or a name for a span an author bracketed | — | **cannot** — no other rendering has been checked and dated |

## Aggregation

Over a `metric`:

| | | |
|---|---|---|
| any percentile the schema admits | the response-time percentile | **can** |
| `max`, `min`, `avg`, `stddev` | the matching response-time statistic | **can** |
| `sum` | — | **cannot** |
| `count`, `rate` | — | **cannot** — per the published row. **Local note (FR-011)**: this row is reported as over-refusing — found from here on 2026-08-30, **not yet filed upstream** — Gatling computes both from the same buffer as the percentiles. The published row is followed until upstream settles it, and the divergence is recorded here rather than acted on. |

Over a fraction (`bad: {error.type: "*"}`), and over the requests themselves (neither `metric` nor
`bad`/`good`):

| | | |
|---|---|---|
| `rate` with `bad` | the failed-request share | **can** |
| `count` with `bad` | the failed-request count | **can** |
| any narrower `bad`, or `bad: {}` | — | **cannot** — a filtered numerator has no correspondence |
| `good`, in any form | — | **cannot** — a selector matches presence, never absence |
| `count` with neither | the request count | **can** |
| `rate` with neither | the throughput | **can** |

## Operator

`lt`, `lte`, `gt`, `gte` map directly; `eq` maps to Gatling's `is`. **`neq` cannot** — there is no
negating condition.

## Unit, per statistic

A unit valid for one statistic is not thereby valid for another.

| Statistic | Accepts | Native | Target |
|---|---|---|---|
| response time | `ms`, `s` | milliseconds | whole number |
| failed-request share | `%`, `1` | percent | fractional |
| counts | `{request}` | count | whole number |
| throughput | `{request}/s` | per second | fractional |

**Where the target is a whole number, the converted threshold must be one** — refused, never rounded,
because rounding moves the bar the author wrote. Conversion is exact decimal arithmetic (FR-008).

`ns`, `us`, `min`, `h`, the byte units and the iteration units are refused per the published table.
**Local note (FR-011)**: the four duration units are reported as wrongly excluded at `opennfr#74` —
`responseTime` reaches all four by the same conversion that makes `s` work. Following the published
table until upstream settles it; this is the one open upstream issue that would change this file.
