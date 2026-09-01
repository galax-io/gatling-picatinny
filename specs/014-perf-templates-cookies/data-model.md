# Data Model: Perf — Templates & Cookies, Locale Correctness, OpenNFR v0.8.0

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-09-01

Most of this feature changes *behaviour*, not *shape*. Only one published type gains a case; everything
else is either internal, test-only, or a record kept outside the code. This document states which is
which, because the compatibility argument depends on it.

---

## 1. Types that do NOT change

Recording these explicitly is the point: the optimizations are behaviour-preserving, so their data
model must be provably untouched.

| Type | Where | Note |
|---|---|---|
| `ParsedCookie` | `storage/CookieParser.scala:5-13` | Public case class. Field names, order, types and defaults all unchanged. The fold rewrite changes how it is *built*, never what it *is*. |
| `Field`, `FieldVal` and its seven cases | `templates/Syntax.scala:25-49` | Public DSL ADT. Untouched — the escaping change is confined to private helpers. |
| `Assertion`, `AssertionPath`, `Target`, `TimeMetric` | Gatling, `Provided` | Not ours, and not extendable: `TimeMetric` is sealed with exactly one case. This is *why* a group duration needs no new emitted shape. |
| `Requirement`, `Predicate`, `RequirementSet` | `assertions/opennfr/Model.scala` | Document model mirrors the upstream schema, which did not change its shape at `v0.8.0` — only the admissible metric *values* changed. `RequirementSet.TracksRelease` changes value, not type. |

## 2. `Scope` — the one type that gains a case

`assertions/opennfr` currently models the resolved selection as three cases. Upstream `v0.8.0` makes a
fourth selection renderable, and it is the only one whose admissible metric differs, so it must be
distinguishable in the type rather than inferred later.

| Case | Selection it denotes | Admissible metric |
|---|---|---|
| Global | `{}` — every recorded operation, pooled | operation duration; or a fraction; or none |
| ForAll | quantified request name — one statement per request position | operation duration; or a fraction; or none |
| Details | a request, optionally under a hierarchy | operation duration; or a fraction; or none |
| **Group** *(new)* | a hierarchy with **no** request name | **group duration only** |

**Why a new case rather than a flag on Details.** Both render through the same path constructor, so a
boolean would work mechanically — but the pairing rule is what the type exists to enforce. With a
distinct case, the metric-resolution function takes the scope as input and the compiler demands a
decision for every scope when a metric name is added or removed. With a flag, a future metric could be
added and silently inherit the wrong admissibility. Research R5 compared both and a third option (an
independent post-check) and recommended this.

**Invariant.** A group scope never carries an empty path. The existing hierarchy helper already rejects
an empty list, a non-array, a non-string element and a wildcard element, so the new case cannot be
constructed with nothing in it — which matters because Gatling collapses an empty path to the global
scope, silently widening the assertion.

## 3. Internal accumulator for cookie parsing (private, new)

The fold needs somewhere to accumulate attributes without building a map. A private case class holds
exactly the five things the parser reads back, and nothing else:

| Field | Meaning | Duplicate handling |
|---|---|---|
| domain | last `domain` attribute value seen, if any | replace |
| path | last `path` attribute value seen, if any | replace |
| maxAgeRaw | last `max-age` attribute value seen, **unparsed** | replace |
| secure | whether a `secure` attribute was seen | sticky — set true, never cleared |
| httpOnly | whether an `httponly` attribute was seen | sticky — set true, never cleared |

Two decisions in this table are the whole correctness argument, and both were verified differentially:

- **`maxAgeRaw` is the raw string, not a parsed number.** Parsing inside the fold would emit one warning
  per occurrence; today's map-based code parses once, after last-wins has already chosen. Carrying the
  raw value and parsing after the fold reproduces "exactly one warning, naming the last occurrence".
- **Flags are set structurally, never derived from the attribute's value.** Today `contains` decides
  them, so `Secure` and `secure=false` both set the flag. Deriving from the value would change that.

Private to the parser, so it is not a compatibility surface.

## 4. Frozen reference oracles (test-only, new)

The equivalence suite needs today's behaviour as a value it can call. Each oracle is a **verbatim,
unmodified copy** of a region of today's source, wrapped in an object.

| Oracle | Copied from | Adaptation permitted |
|---|---|---|
| escaping + body assembly | `templates/Syntax.scala` private region | import the public ADT from the live object; nothing else |
| cookie parsing | `storage/CookieParser.scala` parse region | attach the logging mixin to the wrapper; nothing else |

**Package placement is load-bearing.** The cookie oracle must live in its own package, not alongside
the live parser. The logging framework derives the logger name from the class name, so an oracle in the
storage package would log under a child of that package's logger — and the test helper that captures
warnings by logger prefix would capture the oracle's warnings too, making warning-parity vacuously
true. This is the kind of green-but-meaningless test the constitution's "no mock asserted against a
mock" rule exists to prevent.

**Drift is the failure mode.** These files have no automated guard — a checksum test is just another
thing someone regenerates. Each carries, in its header, the exact `git show <sha>:<path>` command that
reproduces it, and any later diff to one is a hard review stop. They must land in the first commit,
before any production file is touched, or they freeze the wrong thing.

## 5. Input population (test-only, new)

Generated inputs plus enumerated boundary tables. Enumeration matters more than generation in two
places, and both were found the hard way:

| Population | Must enumerate, not sample | Because |
|---|---|---|
| field names and values | the **complete** escape set for JSON and for XML, plus both ends of the control-character range | the fast path is guarded by a predicate that must agree with the branch list; sampling can miss the one character where they disagree |
| raw cookie headers | attribute names in every letter case, **including the dotless and dotted Turkish letters** | three separate 20k-input corpora reported zero differences for behaviour-changing variants until those two characters entered the alphabet |

Also generated: strings with nothing escapable (the dominant real case), empty strings, strings whose
only escapable character is first or last, and surrogate pairs. For cookies: multiple lines, duplicate
attributes, empty-valued attributes, valueless attributes, trailing and repeated separators, a
separator-only line, values containing the separator, and lines with no separator.

## 6. Comparison outcome (test-only, new)

Equality is over the **full observable outcome**, not the return value alone. Anything less would let a
rewrite change an error or a warning and still pass:

| Component | Why it is part of the comparison |
|---|---|
| produced value | the obvious half |
| thrown exception class **and** message | a separator-only cookie line raises today; a defensive rewrite silently returns empty instead |
| ordered list of emitted warnings | the Max-Age warning's count and chosen occurrence are behaviour, and nothing else pins them |

On a difference, the failure names the offending input and both outcomes, so the divergence is
diagnosable without re-deriving it.

## 7. Measurement pair (record, not a type)

Not code. For each performance commit: a before recording and an after recording on the path it
targets, each reporting per-operation allocation and throughput, kept in the commit or PR body. Where
the pair shows no improvement the change does not ship and the issue closes citing the recording.

Per-operation allocation comes from the GC profiler — the only profiler available on a stock
development machine here, which is why no acceptance criterion may be written against the others.
