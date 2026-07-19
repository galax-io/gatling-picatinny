# Feature Specification: Discrete Date-Time Offset Generation

**Feature Branch**: `009-faker-datetime-offset`

**Created**: 2026-07-19

**Status**: Draft

**Input**: User description: "https://github.com/galax-io/gatling-picatinny/issues/294 — Add discrete LocalDateTime offset generator to Faker date API"

## User Scenarios & Testing *(mandatory)*

<!--
  TEST-MODEL HOOK (Constitution III): for each acceptance scenario below, keep in mind
  the REAL case it exercises and the test LAYER it maps to (see TESTING.md: unit/functional,
  DSL/action component, external integration, full Gatling e2e, compile guard, facade).
  `/speckit-plan` will expand these into the plan's mandatory code-free "Test Model" table.
-->

### User Story 1 - Generate a date-time a whole number of units away from a base (Priority: P1)

A load-test author needs a random date-time that is a **whole number** of time units
(days, weeks, hours, …) away from a base date-time — for example "0 to 5 whole days
from now" — with both bounds inclusive. Today the fake-data API can only produce a
random point *anywhere inside* a date-time range (any second within it), which is a
different distribution: same boundaries, different values. An author migrating from
the deprecated random-date feeder must reproduce the old discrete behavior exactly,
without hand-rolling the composition.

**Why this priority**: This is the core capability gap (issue #294). The deprecated
random-date feeder promises a replacement in the new fake-data API, but the promised
replacement generates different values — the migration path is broken until this
exists. It also blocks the planned removal of deprecated APIs in the next major
release.

**Independent Test**: Can be fully tested by requesting a generated date-time with a
given base, bounds, and unit, and verifying every produced value is the base plus a
whole number of the chosen unit, with that number always inside the inclusive bounds.

**Acceptance Scenarios**:

1. **Given** a base date-time and inclusive bounds 0..5 in days, **When** values are generated repeatedly, **Then** every value equals the base plus 0, 1, 2, 3, 4, or 5 whole days — never a fractional-day point in between.
2. **Given** bounds that span zero (e.g. −10..10 hours), **When** values are generated, **Then** values both before and after the base are produced, all on whole-hour steps from the base, none outside the bounds.
3. **Given** equal lower and upper bounds (e.g. 3..3 weeks), **When** a value is generated, **Then** the result is always exactly the base plus 3 weeks (deterministic).
4. **Given** a lower bound greater than the upper bound, **When** the generator is requested, **Then** the request is rejected immediately with a descriptive error (not at generation time).
5. **Given** a time unit that date-time arithmetic cannot support, **When** the generator is requested, **Then** the request is rejected immediately with a descriptive error naming the unit.

---

### User Story 2 - Same capability from Java and Kotlin (Priority: P2)

A Java or Kotlin load-test author needs the same discrete offset generation through
the published Java/Kotlin facade. Composing it manually from the number generator is
not practical from those languages (it requires constructing Scala function values),
so without a facade entry point these users have no reasonable migration path at all.

**Why this priority**: The library ships a first-class Java/Kotlin facade; a
capability that exists only for Scala users leaves facade users stranded on the
deprecated feeder. Depends on User Story 1 existing.

**Independent Test**: Can be fully tested by invoking the facade entry point from
Java test code and verifying it produces values identical in kind to the core
generator (whole-unit steps inside inclusive bounds).

**Acceptance Scenarios**:

1. **Given** the Java/Kotlin facade, **When** a discrete offset generator is requested with base, bounds, and unit, **Then** generated values behave exactly as in User Story 1 (facade delegates, adds no logic).
2. **Given** the facade without an explicit unit, **When** a discrete offset generator is requested with base and bounds only, **Then** whole days are used as the default unit.

---

### User Story 3 - Documented migration from the deprecated random-date feeder (Priority: P3)

An author still using the deprecated random-date feeder needs to know exactly how to
express an existing configuration (positive delta, negative delta, base, unit,
output pattern) with the new API, so the eventual removal of the deprecated feeder
is a mechanical replacement, not a re-design.

**Why this priority**: Documentation parity completes the migration story but has no
value until Stories 1–2 ship.

**Independent Test**: Can be fully tested by taking a representative deprecated
feeder configuration, following the documented mapping, and verifying the new
expression produces values with the same distribution shape (discrete steps, same
inclusive range) and the same formatted output.

**Acceptance Scenarios**:

1. **Given** a deprecated feeder configuration with positive delta P, negative delta N, base B and unit U, **When** the documented replacement mapping is applied, **Then** the new generator covers exactly B − N·U … B + (P−1)·U on whole-unit steps — the deprecated feeder's upper delta is exclusive (discovered during planning), while the new generator's bounds are inclusive — and the deprecation notice points to a replacement that truly reproduces the behavior.
2. **Given** a deprecated configuration that also formats output with a date pattern, **When** the documented mapping is applied, **Then** the formatted output shape matches the old feeder's output shape.

---

### Edge Cases

- Lower bound greater than upper bound → rejected at generator construction with a descriptive error (never a silently empty or inverted range).
- Lower bound equal to upper bound → deterministic value, still valid.
- Bounds spanning zero (past and future from the same base) → supported; both sides reachable.
- Time unit unsupported by date-time arithmetic → rejected at construction, naming the unit, not at first generation inside a running load test.
- Offsets large enough to overflow the representable date-time range → the underlying date-time arithmetic error surfaces at generation; bounds themselves are not silently clamped.
- Distribution boundary: both endpoint values must actually be producible (inclusive, not exclusive, bounds).
- The existing continuous "random point inside a range" generator keeps its current behavior untouched — the new capability is additive, not a redefinition.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The fake-data date API MUST offer a generator producing a date-time equal to a caller-supplied base plus a whole number of caller-chosen time units, where that whole number is drawn uniformly from a caller-supplied inclusive range.
- **FR-002**: Both range endpoints MUST be producible (inclusive bounds); an equal-bounds range MUST yield exactly the single corresponding value.
- **FR-003**: Negative offsets MUST be supported, including ranges spanning zero, so past-and-future generation from one base is expressible in a single generator.
- **FR-004**: An invalid request — lower bound above upper bound, or a time unit the date-time arithmetic does not support — MUST be rejected when the generator is constructed, with a descriptive error, rather than failing later during value generation.
- **FR-005**: Generated values MUST lie exactly on whole-unit steps from the base — no sub-unit remainder — making the distribution observably different from the existing continuous in-range generator, whose behavior MUST remain unchanged.
- **FR-006**: The Java/Kotlin facade MUST expose the same capability by pure delegation to the core implementation (no facade-local logic), including a whole-days default when no unit is given.
- **FR-007**: The new generator MUST compose with the existing date-time formatting capability so formatted string output (as the deprecated feeder produced) is expressible without new formatting machinery.
- **FR-008**: The change MUST be purely additive: no existing public signature, generator behavior, or serialized format changes; the deprecated random-date feeder itself is not modified beyond documentation.
- **FR-009**: Migration documentation MUST state the exact mapping from every deprecated random-date feeder parameter set (positive delta, negative delta, base, unit, pattern) to the new API.

### Key Entities

- **Discrete offset generator**: description of "base date-time + whole-unit random offset"; attributes: base date-time, inclusive lower offset, inclusive upper offset, time unit (whole-days default).
- **Deprecated random-date feeder configuration**: the legacy parameter set (positive delta, negative delta, base, unit, output pattern, timezone) whose behavior the new generator must be able to reproduce; related to the discrete offset generator by the documented migration mapping.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of sampled generated values fall inside the requested inclusive range and lie exactly on whole-unit steps from the base (zero sub-unit remainders across the sampled runs).
- **SC-002**: Every deprecated random-date feeder parameter combination (any positive/negative delta pair, any supported unit, any pattern) has a documented one-to-one replacement expression producing the same value set and output shape.
- **SC-003**: A Java or Kotlin author can obtain the capability through the facade with a single call — zero Scala-specific constructs required on the caller's side.
- **SC-004**: All pre-existing tests pass unchanged: zero behavioral drift in existing generators.
- **SC-005**: Invalid requests (inverted bounds, unsupported unit) are rejected at construction in 100% of cases with an error message naming the offending input.

## Assumptions

- Uniform distribution over the whole-number offset range matches the deprecated feeder's behavior and user expectation; no weighting options are in scope.
- The deprecated feeder's upper delta is **exclusive** (its random-value helper excludes the upper bound); the new generator uses **inclusive** bounds as issue #294 requests, and the migration mapping absorbs the difference (upper bound = positive delta − 1).
- Whole days are the correct default unit (mirrors the deprecated feeder's default).
- The offset range uses whole numbers of arbitrary magnitude within the platform's standard integer range; fractional offsets are explicitly out of scope (the continuous generator already covers them).
- Timezone handling stays as in the existing date API (the deprecated feeder's timezone parameter only affected formatting, which the existing formatting capability already covers).
- Scope is the library and its Java/Kotlin facade only; example overlay projects are unaffected.
- This feature belongs to the active release milestone (v1.25.0) and closes issue [#294](https://github.com/galax-io/gatling-picatinny/issues/294); it is a prerequisite for removing the deprecated feeder in the planned 2.0.0 cleanup (milestone "v2.0.0 — Remove deprecated APIs").
