# Phase 0 — Research: OpenNFR assertions

**Feature**: `013-opennfr-assertions` · **Date**: 2026-08-30

One item in the plan's Technical Context was marked NEEDS CLARIFICATION (R1). It is resolved below,
with the evidence, and it remains the one item needing authorization before implementation. R2–R6
record decisions that were already settled by evidence and are written down so the implementation
does not re-open them.

---

## R1 — What validates a document against the OpenNFR JSON Schema (FR-012)

**Decision**: `com.networknt:json-schema-validator:1.5.8`, Test **and** Compile scope, subject to the
AGENTS.md rule that new dependencies are agreed first. **This is the one open authorization in the
feature.**

**Rationale.**

- The format's authority is a JSON Schema (Draft 2020-12). Without a validator this library enforces
  a hand-copied subset of the schema's rules (FR-004) which drifts from the schema on every upstream
  release — the drift this requirement exists to prevent. Six audits of that upstream showed the
  published text and the schema disagreeing repeatedly; a hand copy here would be a seventh place to
  disagree.
- Nothing on the compile classpath validates JSON Schema today. Checked:
  `show Compile/dependencyClasspath` carries `circe-*`, `json4s-*`, `jackson-*` and
  `gatling-jsonpath`, and no schema validator.
- **The version matters, and the obvious choice is wrong.** `json-schema-validator:3.0.7`, the current
  release, depends on **Jackson 3** (`tools.jackson.*`, groupId `tools.jackson.core`) while this
  project is on Jackson 2.22.2 (`com.fasterxml.jackson.*`). The two are different packages and would
  coexist, but it means carrying a second Jackson major — and `Dependencies.scala` already records an
  incident where a Jackson mediation slip produced a `NoClassDefFoundError` at runtime. Resolved with
  coursier: `1.5.8` stays on Jackson 2 (`2.18.3`, which mediates up to the project's `2.22.2`, same
  major), so **the net addition is two jars**: `json-schema-validator` and `com.ethlo.time:itu`
  (RFC-3339 parsing for `format` keywords). `slf4j-api` and every Jackson artifact are already present.

**Alternatives considered.**

| Alternative | Rejected because |
|---|---|
| **No validator; keep FR-004's hand-copied rules only** | The decoder plus a hand copy accepts documents the format rejects — a misspelled key, an empty `criteria`, a malformed `name` — and the hand copy must be re-checked against the schema on every upstream release by a human who will eventually not. It is the drift FR-012 exists to prevent. Acceptable only as the *interim* state while R1 is authorized, which is why FR-004 is a requirement in its own right and not a stand-in. |
| **`json-schema-validator:3.x`** | Pulls a second Jackson major into a build whose dependency file already documents a Jackson mediation failure. |
| **`org.everit.json.schema`** | Draft 2020-12 support is not its focus, and it is built on `org.json`, a third JSON tree in a build that already carries circe, json4s and Jackson. |
| **Validate with circe by hand-writing the schema's rules as decoders** | This is the hand copy above, wearing a type. It also loses the schema's own error messages, which are the ones upstream's documentation teaches. |
| **Test-scope only** | Would prove the corpus valid at build time and leave a user's own document unvalidated at run time, which is exactly backwards: the build's documents are the ones already known good. |

**What happens if R1 is not authorized**: the feature ships without FR-012 and FR-013, with FR-004
carrying the schema rules that change what renders. That is a real reduction — a document the format
rejects can still produce assertions — and it must then be stated in FR-017's documentation rather
than left for a user to discover.

---

## R2 — How the Java facade delegates when it cannot (FR-016, Principle I)

**Decision**: the Scala core resolves the *decision* — scope, statistic, condition, canonicalised
target, or a refusal — and exposes it as a value. The facade re-issues that resolved decision through
the Java DSL. No rule, no table and no refusal wording lives in the facade.

**Rationale**: `io.gatling.javaapi.core.Assertion`'s wrapping constructor is package-private, so there
is no public way to turn a core `Assertion` into a Java one. `Assertions.java` already hit this and
documents it in a comment, duplicating the deprecated builder's key mapping as a result. Repeating
that duplication would repeat its cost — two places to change a rule. Resolving to a value first
keeps the *rules* single-homed even though the final DSL call happens twice.

**Alternatives considered**: returning the core Scala type from the facade (leaks a Scala type into
the Java surface); reflection into the package-private constructor (breaks on any Gatling upgrade and
is worse than the duplication it avoids); asking upstream Gatling to widen the constructor (right
answer, wrong timescale — record it, do not wait for it).

---

## R3 — Pinning the schema (FR-013)

**Decision**: vendor `requirementset.schema.json` under `src/main/resources/opennfr/`, record the
upstream release and commit beside it, and gate on byte-identity with the recorded release.

**Rationale**: FR-014 forbids fetching it at run time, and the format is pre-1.0 — its schema changed
materially between two consecutive releases (a scalar became an array), so "whatever upstream has
today" is not a stable input. A byte-identity gate is the cheapest check that catches both an
accidental local edit and an unnoticed upstream bump, and it must fail even when the edit makes the
schema *more* permissive — a widened schema silently widens what this library accepts.

**Alternatives considered**: a git submodule (drags the whole upstream repo into the build for one
file); fetching at build time (a network dependency in the build, and a build that breaks when
upstream moves); no pin at all (the library would claim to support "OpenNFR" without saying which).

---

## R4 — Threshold arithmetic (FR-008)

**Decision**: `BigDecimal` throughout — parse the literal as written, multiply by the unit factor
exactly, and require a whole number where the target is integral.

**Rationale**: this is a correctness requirement, not a preference. `1.001 s` is exactly `1001` ms;
computed in binary floating point it is `1000.9999999999999` and would be refused. Two renderers
disagreeing on an ordinary latency budget is the failure the whole-number rule exists to prevent, and
upstream states no arithmetic model — reported as `opennfr#59`, still open, so this library decides it
under FR-011 and records that it did.

**Alternatives considered**: `Double` (wrong, as above); a tolerance (reintroduces the rounding the
rule forbids); parsing to `Long` nanoseconds (works for time and not for shares or counts).

---

## R5 — What correctness is measured against (FR-015, User Story 3)

**Decision**: the deprecated builder is the oracle. The translated fixture and the original must build
**equal** assertion sets over the ten of eleven the format can state, compared as sets.

**Rationale**: no OpenNFR renderer exists anywhere, so there is no reference implementation. The
deprecated path is a known-good producer of the exact `Assertion` values Gatling evaluates, with an
existing suite behind it. Comparing sets rather than inspecting fields is what makes the test
falsifiable: a wrong scope, a wrong condition or a truncated target all fail it.

**The eleventh is excluded by name, with a test asserting why.** Silently shrinking the expected set
would turn the strongest test in the feature into one that proves nothing.

**Enumerated, so the claim is checkable**: [`contracts/parity.md`](contracts/parity.md) lists all six
recognised keys, all five scope forms and all eleven assertions. A spike on 2026-08-30 built both ways
and observed ten equal and one excluded; the spike was then deleted, so **that is a design input and
not a passing test**. Nothing may be treated as proven until User Story 3's test re-establishes it.

---

## R6 — The shape of a refusal (FR-003)

**Decision**: the pipeline is `Either[List[String], List[Assertion]]` end to end; the public entry
point folds a `Left` into one exception listing every reason. Each reason names its requirement and
its predicate identity — the predicate's `name` where set, its `aggregation` otherwise, which is the
format's own identity rule.

**Rationale**: FR-003 requires every violation at once. Failing at the first means an engineer fixes
one mistake per run of a build. Using `Either` internally and throwing once at the boundary keeps the
core a pure function — testable without exception handling — while giving simulation authors the
failure mode they already get from the deprecated path.

**Alternatives considered**: throwing on the first defect (fails FR-003); returning an empty list on
failure (a silent green, and the failure this format exists to prevent); logging and continuing
(same).
