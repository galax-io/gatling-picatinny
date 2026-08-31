# Phase 1 — Data Model: OpenNFR assertions

**Feature**: `013-opennfr-assertions` · **Date**: 2026-08-30

The document model mirrors the OpenNFR schema at upstream release `v0.6.0` and nothing more. The
schema is the authority on what a valid document is; this model is only the shape the renderer needs
in order to decide what a valid document *denotes*.

---

## Entities

### RequirementSet — the document

| Field | Required | Notes |
|---|---|---|
| `apiVersion` | yes | Exactly `opennfr.io/v1`. Any other value is refused, naming both (FR-002). |
| `kind` | yes | Exactly `RequirementSet`. |
| `metadata.name` | yes | Carried but not rendered — Gatling's `Assertion` has no slot for a label. |
| `spec.requirements` | yes | At least one. A document stating nothing is refused. |

### Requirement — one sentence about one set of requests

| Field | Required | Notes |
|---|---|---|
| `name` | yes | Names the requirement in a refusal message. Not rendered. |
| `selector` | yes | Which requests. `{}` is every request, said explicitly. |
| `criteria` | yes | At least one predicate. |
| `guards` | no | Predicates of the same shape. **Every guard renders** (FR-007). |

**Derived**: a requirement's *predicates* are its criteria followed by its guards. The renderer treats
them identically because Gatling has one outcome; the distinction is what a violation *means*, which
the target cannot carry.

### Predicate — one machine-checkable statement

| Field | Required | Notes |
|---|---|---|
| `aggregation` | yes | `avg` `min` `max` `count` `rate` `sum` `stddev`, or a percentile `p⟨1–2 digits⟩[.⟨digits⟩]`. |
| `op` | yes | `lt` `lte` `gt` `gte` `eq` `neq`. |
| `threshold` | yes | A number. **Kept as an exact decimal, never a binary float** (FR-008). |
| `unit` | yes | One of the format's seventeen. |
| `name` | no | The predicate's identity in a message; the `aggregation` when absent. |
| `metric` | no | What to measure of the selected requests. |
| `bad` / `good` | no | Makes the predicate a fraction. Mutually exclusive with `metric` and with each other. |

**Three shapes, and exactly three**: carrying a `metric` (measure a quantity the requests carry),
carrying `bad`/`good` (a fraction of them), carrying neither (the requests themselves). The
combinations the schema forbids are refused here too (FR-004), because this model is decoded rather
than schema-validated until FR-012 lands.

### Selector — which requests

A map of attribute name to value. Heterogeneous by design, so it is carried as raw JSON per attribute
and interpreted rather than decoded into a fixed shape:

| Attribute | Value | Meaning |
|---|---|---|
| *(none)* | — | `{}` is every request, pooled into one statement. |
| `loadtest.group.name` | ordered list of strings, at least one | The request's enclosing groups, outermost first, **at any depth**. Matched whole, never as a prefix. |
| `loadtest.request.name` | string | The recorded request name. **`"*"` is presence and quantifies** — one statement per recorded request position. |
| anything else | string, number or boolean | Valid in the format; not addressable by Gatling. |

**One rule beyond equality**: where a selector names a request, an absent `loadtest.group.name` means
the **empty** hierarchy — the request has no enclosing group. `"*"` is not a name, so it does not
trigger the rule.

### Reach decision — the renderer's own type

Not part of the format. For one predicate, either the resolved Gatling assertion or one reason it
could not be produced. Carrying the reason as a value rather than an exception is what lets FR-003
report every violation at once.

---

## Validation rules, and where each lives

| Rule | Enforced by |
|---|---|
| Envelope: `apiVersion`, `kind`, non-empty requirements | The renderer (FR-002) |
| Structure: required fields, types, enums, the percentile pattern | The decoder; and the schema once FR-012 lands |
| `metric` with `bad`; `metric` with `good`; `bad` with `good` | **The renderer** (FR-004) — the schema states these and nothing here validates against it yet |
| A percentile needs a metric; a fraction takes only `rate`/`count` | The renderer, as a consequence of the three shapes |
| Predicate identity distinct within a requirement's criteria, and within its guards | The renderer, per the format's identity rule |
| Which shapes Gatling can assert | [`contracts/reach.md`](contracts/reach.md) |

## State

None. A document is read once, decoded once, and rendered once, at simulation set-up. There is no
mutable state, no cache and no lifecycle — which is why the whole feature sits in test-model layer 1.
