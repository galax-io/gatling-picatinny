# Feature Specification: Faker & Feeder-Transform Hot-Path Allocation Reduction

**Feature Branch**: `010-faker-syntax-perf`

**Created**: 2026-07-21

**Status**: Draft

**Input**: User description: "https://github.com/galax-io/gatling-picatinny/milestone/12 — v1.25.0 — Perf: Faker (open issues #123, #124, #125, #129, #130, #131, #139, #304)"

## User Scenarios & Testing *(mandatory)*

<!--
  TEST-MODEL HOOK (Constitution III): for each acceptance scenario below, keep in mind
  the REAL case it exercises and the test LAYER it maps to (see TESTING.md: unit/functional,
  DSL/action component, external integration, full Gatling e2e, compile guard, facade).
  `/speckit-plan` will expand these into the plan's mandatory code-free "Test Model" table.
-->

### User Story 1 - Fake-value generators produce identical values with less garbage (Priority: P1)

A load-test author drives thousands of virtual users, each sampling fake values —
lorem-ipsum text, IPv6 addresses, Brazilian CPF numbers, German tax identification
numbers, e-mail addresses — once per iteration, often once per request. Today each of
those samples creates short-lived intermediate objects (temporary collections and
strings) that exist only to be joined into the final value and discarded. At scale
this churn becomes garbage-collection pressure inside the load generator itself,
distorting the very latency measurements the tool exists to take. The author needs
the same values — byte-for-byte the same formats, value sets, and distributions —
produced without the throwaway intermediates.

**Why this priority**: These are the widest per-request hot paths in the fake-data
API (issues #123, #124, #125, #139) and the load generator's own overhead directly
contaminates measurement quality — the library's core promise.

**Independent Test**: Can be fully tested by sampling each affected generator before
and after the change, verifying every produced value still matches the exact expected
format and value set, and measuring that per-value memory allocation strictly
decreased.

**Acceptance Scenarios**:

1. **Given** the existing unit tests asserting exact formats for lorem-ipsum, IPv6, CPF, German TIN, and e-mail generation, **When** the optimized generators run, **Then** every pre-existing test passes unchanged — zero behavioral drift.
2. **Given** a large sample from the IPv6 generator, **When** outputs are inspected, **Then** every value is eight colon-separated hexadecimal groups, exactly as before.
3. **Given** a large sample from the CPF and German TIN generators, **When** check digits are recomputed independently, **Then** every value remains a valid, correctly check-digited identifier of the same length and shape as before.
4. **Given** e-mail local-parts containing uppercase, accented, and disallowed characters (boundary case), **When** normalization runs, **Then** the normalized output is identical, character for character, to the previous normalization result.
5. **Given** allocation measurement of one generated value on each of these paths, **When** compared to the previous implementation, **Then** measured per-value allocation is strictly lower.

---

### User Story 2 - Bounded whole-number sampling stops paying arbitrary-precision overhead (Priority: P2)

A load-test author uses generators that draw a whole number from an inclusive range —
directly (random longs, positive/negative longs) or indirectly (the discrete date-time
offset generator added in feature 009). Today every single sample first classifies the
range as narrow or wide using arbitrary-precision arithmetic, allocating several
arbitrary-precision numbers per drawn value even for the common narrow case. The
author needs the same uniformly distributed values from the same inclusive bounds,
with the range classification done in plain fixed-width arithmetic at no per-sample
allocation cost.

**Why this priority**: One shared path feeds every whole-number-derived generator
(issue #304), so the blast radius is broad — but the values themselves are already
correct, making this pure overhead removal, slightly less user-visible than Story 1's
text paths.

**Independent Test**: Can be fully tested by sampling ranges on both sides of the
narrow/wide classification boundary and at the extreme representable values,
verifying bounds inclusivity and identical narrow-vs-wide classification, with zero
arbitrary-precision objects created on the narrow path.

**Acceptance Scenarios**:

1. **Given** any inclusive range whose width fits the standard fixed-width integer, **When** values are sampled repeatedly, **Then** every value lies inside the inclusive bounds and both endpoints are producible, exactly as before.
2. **Given** ranges straddling the narrow/wide classification boundary (width just below, at, and just above the maximum fixed-width span — boundary case), **When** each range is classified, **Then** the chosen strategy is identical to the previous arbitrary-precision classification for every input, including the extreme representable minimum and maximum.
3. **Given** the wide-range sampling regression tests introduced by the previous fix (PR #300), **When** the optimized code runs, **Then** those tests pass unchanged — wide-range behavior is untouched.
4. **Given** allocation measurement of one narrow-range sample, **When** compared to the previous implementation, **Then** no arbitrary-precision number objects are created.

---

### User Story 3 - Feeder record transforms do static work once, not per record (Priority: P3)

A load-test author chains record transforms onto feeders — selecting a subset of
keys, prefixing or suffixing key names, filling in default values. The
configuration of such a transform (which keys, which prefix, which defaults) is
fixed when the chain is built, yet today parts of it are recomputed for every record
flowing through: the defaults map is rebuilt per record and key selection builds
intermediate structures per record. *(Amended 2026-07-21: key renaming was also
suspected of per-record waste, but verification showed the compiled code is already
minimal there — that part closes on evidence, not on a code change.)* The author
needs transformed records identical to today's — same keys, same values, same
override semantics — with configuration-derived work done once when the transform is
constructed.

**Why this priority**: Also a per-record hot path (issues #129, #130, #131), but
transforms are optional stages an author opts into, whereas Stories 1–2 hit everyone
using the affected generators.

**Independent Test**: Can be fully tested by running each transform over a stream of
records and verifying output records are equal to the previous implementation's
output for the same input, including boundary records (empty, missing keys,
key collisions).

**Acceptance Scenarios**:

1. **Given** a key-selection transform and records containing a mix of selected, unselected, and absent keys (boundary case), **When** records are transformed, **Then** each output record contains exactly the selected keys present in the input, identical to previous behavior.
2. **Given** a prefixing or suffixing transform, **When** records are transformed, **Then** output key names and values equal the previous implementation's output for the same input.
3. **Given** a defaults-filling transform where some default keys are present in the record and some are absent, **When** records are transformed, **Then** record values win over defaults for present keys and defaults appear for absent keys — exact previous override semantics.
4. **Given** any of these transforms processing N records, **When** work is measured, **Then** static configuration (selected-key set, defaults map, prefix/suffix strings) is captured once at transform construction, and per-record work is a single pass over the record — at most one plain string concatenation per renamed key, with no per-record rebuilding of configuration and no repeated string-interpolation machinery. *(Amended 2026-07-21 during planning: renamed key names are record-derived — records carry no homogeneity guarantee — so they cannot be precomputed at construction; see research R5.)*

---

### Edge Cases

- E-mail normalization boundary inputs — uppercase, accented characters, characters the normalization strips, consecutive strippable characters — must normalize to outputs identical to the previous implementation.
- Whole-number ranges at classification extremes: equal bounds; width exactly at the narrow/wide boundary; bounds at the representable minimum/maximum — classification and produced values must match previous behavior exactly (the boundary must not shift by one).
- Wide-range whole-number sampling (rejection loop from PR #300) is explicitly out of optimization scope: its regression tests must pass unchanged.
- Empty record, empty key selection, and prefix/suffix producing colliding key names — transform output must equal previous output (whatever collision semantics existed are preserved, not redefined).
- Zero- and one-word lorem-ipsum requests — degenerate assembly must produce the same output as before.
- Distribution shape everywhere: uniformity and randomness source are unchanged — only allocation behavior may differ.
- Check-digit algorithms (CPF, German TIN) must remain correct for every generated value, not just format-shaped.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every optimized generator and transform MUST produce observably identical output to the current implementation — same value sets, formats, check-digit validity, inclusive-bounds behavior, override semantics, and distributions — such that all pre-existing tests pass unchanged.
- **FR-002**: The lorem-ipsum, IPv6, CPF, German TIN, and e-mail-normalization generation paths MUST assemble their output values without creating intermediate collections or intermediate strings that are discarded immediately after producing the final value.
- **FR-003**: Bounded whole-number sampling MUST classify ranges as narrow or wide, and compute the narrow-path sampling bound, using only fixed-width arithmetic — no arbitrary-precision number objects per sample — while leaving wide-range sampling behavior (including its regression tests from PR #300) unchanged.
- **FR-004**: Record transforms whose configuration is fixed at construction (key selection, key prefixing/suffixing, defaults filling) MUST perform configuration-derived computation once at construction time, not per record.
- **FR-005**: The change MUST be purely internal: no public API signature, DSL behavior, feeder output shape, session variable name, or serialized format may change (Constitution II); the binary-compatibility check MUST report zero new issues.
- **FR-006**: Each of the eight tracked issues MUST land with regression coverage asserting exact output parity, including at least one negative or boundary case per issue, at the unit/functional layer.
- **FR-007**: The improvement MUST be evidenced by before/after benchmark measurements of representative optimized paths (at minimum: one text-assembly generator and the narrow-range whole-number path), demonstrating strictly reduced per-value allocation.
- **FR-008**: Where an optimization would change any observable behavior, behavior parity wins and the optimization is narrowed or dropped — measurement fidelity is never traded for generator speed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of pre-existing tests pass unchanged — zero behavioral drift across all optimized paths.
- **SC-002**: Measured per-value allocation on every optimized path is strictly lower than the pre-change baseline, and the narrow-range whole-number path allocates zero arbitrary-precision number objects.
- **SC-003**: 100% of sampled outputs from every optimized generator match the previously expected formats and value sets (format shapes, check-digit validation, inclusive bounds, override semantics).
- **SC-004**: All eight open milestone issues (#123, #124, #125, #129, #130, #131, #139, #304) are closed — seven by changes carrying their own regression tests, and #130 by documented refutation evidence (verification showed its premise no longer holds), completing the open performance scope of milestone v1.25.0.
- **SC-005**: The published-API compatibility report shows zero new findings — consumers can upgrade with no source or binary change.

## Assumptions

- Scope is exactly the eight open issues in milestone 12 ("v1.25.0 — Perf: Faker"); the milestone's twenty closed issues are already delivered and out of scope.
- Issue #123 cites a Java source path from the original audit; the lorem-ipsum generator actually lives in the Scala core (the audit pointer is stale). The planning phase resolves exact locations; behavior scope is unaffected.
- Issues #129–#131 were refiled from the templating module to the feeder-transform module (per the milestone description); the affected transforms are the feeder record transforms (key selection, prefix/suffix, defaults), and the templating module is out of scope.
- Distribution shape of every generator is unchanged. The randomness source is also unchanged, with one deliberate exception: IPv6 generation moves off the legacy shared-`Random` utility (`RandomDataGenerators.hexString`) onto the thread-local RNG the rest of the fake-data API already uses — same uniform distribution over the same lowercase-hex alphabet, no user-visible determinism existed to preserve. *(Amended 2026-07-21: new hot-path code must not deepen dependence on the legacy pre-Faker utility surface.)*
- Benchmark measurements are evidence attached to the work, not a CI-enforced performance gate; no new benchmark infrastructure beyond the project's existing benchmark practice is introduced.
- No user-facing documentation or changelog entries are expected — the changes are internal optimizations with no API surface; each issue's "docs updated if user-facing" acceptance item resolves to not-applicable unless planning discovers otherwise.
- House delivery rules apply: one issue = one semantic commit, each independently green.
- This feature belongs to the active release milestone (v1.25.0) and closes issues [#123](https://github.com/galax-io/gatling-picatinny/issues/123), [#124](https://github.com/galax-io/gatling-picatinny/issues/124), [#125](https://github.com/galax-io/gatling-picatinny/issues/125), [#129](https://github.com/galax-io/gatling-picatinny/issues/129), [#130](https://github.com/galax-io/gatling-picatinny/issues/130), [#131](https://github.com/galax-io/gatling-picatinny/issues/131), [#139](https://github.com/galax-io/gatling-picatinny/issues/139), and [#304](https://github.com/galax-io/gatling-picatinny/issues/304).
