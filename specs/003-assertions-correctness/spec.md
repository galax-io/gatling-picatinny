# Feature Specification: Assertions Correctness (NFR YAML → Gatling assertions)

**Feature Branch**: `003-assertions-correctness`

**Created**: 2026-06-22

**Status**: Draft

**Input**: Milestone [v1.17.0 — Assertions correctness](https://github.com/galax-io/gatling-picatinny/milestone/2): "Assertions DSL + Java facade. #71, #72, #89, exponential-dup, toUtf no-op, null-super." Bundled issues: [#71](https://github.com/galax-io/gatling-picatinny/issues/71), [#72](https://github.com/galax-io/gatling-picatinny/issues/72), [#89](https://github.com/galax-io/gatling-picatinny/issues/89), [#202](https://github.com/galax-io/gatling-picatinny/issues/202).

**Target version**: **v1.18.0** (MINOR). The milestone is titled "v1.17.0", but `v1.17.0` and `v1.17.1` are already tagged and published to Maven Central — version numbers MUST NOT be reused (constitution V; Sonatype permanently rejects duplicates). The fixes in this feature therefore ship in the next available minor, **v1.18.0**.

## Context: Current Defects

A user expresses Non-Functional Requirements (NFR) — error-rate and response-time
thresholds — in a YAML file and loads them as Gatling assertions via the public
`assertionFromYaml(path)` entry point, available in both the Scala builder and the
Java/Kotlin facade. The loaded assertions then pass/fail the simulation against the
NFR budget.

That load path currently misbehaves in six ways (the milestone bundle). Stated as
user-observable behavior, not implementation:

1. **Duplicated assertions (high, Java facade).** Loading an NFR file through the
   Java facade produces a number of assertions that grows roughly as 2^n with the
   number of threshold entries rather than one assertion per entry. Even a single
   entry is duplicated. The resulting simulation evaluates the same budget many
   times over — wasteful and misleading. (#202 exponential-dup)
2. **Typos silently disable a threshold (high, both).** An NFR key that is not one
   of the recognized metric names is silently dropped. A misspelled metric (e.g.
   `p_95` instead of the recognized key) produces no assertion and no warning, so
   the simulation reports success while validating nothing. (#71)
3. **Non-numeric threshold crashes opaquely (high, both).** A threshold value that
   is not a valid number aborts loading with a low-level number-format error that
   names neither the offending key nor the bad value, leaving the user to guess
   which line of YAML is wrong. (#72)
4. **Facade exception loses its detail (low, Java).** When loading fails, the
   facade's exception does not carry its message or underlying cause through the
   standard accessors, so callers (and logs/stack traces) see `null` instead of the
   reason. (#202 null-super)
5. **Non-ASCII keys depend on platform charset (low, both).** Recognized metric
   keys are non-ASCII (Cyrillic). The encoding-normalization step is a no-op on the
   Java side and a lossy default-charset round-trip on the Scala side, so key
   matching can break on a host whose default charset is not UTF-8. (#202 toUtf)
6. **Redundant per-call initialization (medium, Java perf).** The Java facade
   re-runs reflection-heavy parser initialization on every load call instead of
   once. (#89)

The recognized-key contract that DOES work today MUST be preserved: error-rate
percent, the 99/50/75/95 response-time percentiles, and max response time; the
`all` entry maps to a global assertion, any other entry maps to a per-detail
assertion addressed by `group / request`; the threshold is a strict "less-than"
(`lt`) bound.

**Direction**: this is the *final* correctness pass on the NFR-YAML assertions
feature. As of v1.18.0 the whole NFR-YAML mechanism (`assertionFromYaml`, Scala and
Java) is **deprecated** in favor of forthcoming replacement functionality. The fixes
here harden the path for its remaining life; the feature keeps working this release
and is removed only in a later major once the replacement ships.

## Clarifications

### Session 2026-06-22

- Q: How should an unrecognized NFR assertion key be handled (today both Scala and
  Java silently drop it)? → A: **Warn + skip only.** Log a WARN that names the
  unknown key and skip it (no assertion for that key); never throw, add no new
  config flag. Fully backward-compatible — the existing skip behavior is retained,
  only made visible. The existing `AssertionsBuilderSpec`/`nfr.yml` fixture (which
  relies on APDEX/RPS being skipped) stays green. This keeps the release a clean
  v1.18.0 MINOR (no behavioral redefinition).
- Q: How specific should the NFR-YAML deprecation message be, given no replacement
  is specced/scheduled yet? → A: **Generic, no commitment.** State the feature is
  deprecated and will be replaced by new assertions functionality in a future
  release, that it still works for now, and to watch the changelog — with NO issue
  link, version, or date promised. Avoids over-promising a vaporware replacement in a
  published library while honoring the "new functionality coming" intent.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Correct, non-duplicated assertion set from an NFR file (Priority: P1)

A load-test engineer loads an NFR YAML file through the Java facade (or the Scala
builder) and gets exactly one Gatling assertion per threshold entry — never
duplicates — so the simulation evaluates each budget once and the report reflects
the real NFR.

**Why this priority**: The exponential duplication is the highest-severity defect;
it silently inflates the assertion set on the most-used facade path and makes the
simulation's pass/fail accounting wrong. Fixing it is the minimum viable outcome of
this feature.

**Independent Test**: Load a fixture NFR file with a known number of threshold
entries via the Java facade and assert the returned assertion count equals that
number (not a power of it), and that each expected (metric, scope, threshold) is
present exactly once. Add one entry and confirm the count grows by one.

**Acceptance Scenarios**:

1. **Given** an NFR file with N total threshold entries across recognized metrics,
   **When** it is loaded via the Java facade, **Then** exactly N assertions are
   returned, each distinct and matching an entry.
2. **Given** an NFR file with a single threshold entry, **When** it is loaded via
   the Java facade, **Then** exactly one assertion is returned (no duplication).
3. **Given** the same NFR file, **When** it is loaded via both the Scala builder
   and the Java facade, **Then** both return an equivalent assertion set (same
   size, same thresholds, same global/detail scope).

---

### User Story 2 - Misconfigured NFR files fail loudly with actionable errors (Priority: P1)

A load-test engineer who misspells a metric key or writes a non-numeric threshold
is told exactly what is wrong — instead of the assertion silently disappearing or
the load crashing with an opaque parse error — so a bad NFR file never masquerades
as a passing run.

**Why this priority**: Silent drop (typo) and opaque crash (bad number) both
defeat the purpose of NFR assertions; a budget that silently validates nothing, or
a startup that fails without saying why, is worse than no assertion at all.

**Independent Test**: Load an NFR file containing an unrecognized key and confirm a
WARN naming that key is logged and no assertion is produced for it — not a silent
skip. Separately, load an NFR file whose threshold value is not a number and
confirm the resulting error names both the offending key and the bad value, in
both the Scala and Java paths.

**Acceptance Scenarios**:

1. **Given** an NFR file with an unrecognized assertion key, **When** it is loaded,
   **Then** the system logs a WARN naming the unknown key and skips it (no
   assertion produced for that key) rather than silently dropping it with no signal;
   loading otherwise succeeds.
2. **Given** an NFR file whose threshold value is not a valid number, **When** it
   is loaded via the Scala builder, **Then** loading fails with an error message
   that contains both the offending key and the offending value.
3. **Given** the same malformed value, **When** it is loaded via the Java facade,
   **Then** loading fails with an error message that likewise names the key and the
   bad value (parity with Scala).
4. **Given** a well-formed NFR file with only recognized keys and numeric values,
   **When** it is loaded, **Then** loading succeeds with no warnings or errors
   (the fixes do not regress valid files).

---

### User Story 3 - Failures are debuggable; non-ASCII keys are reliable (Priority: P2)

When loading fails, the facade exception carries its message and underlying cause
so the reason appears in logs and stack traces; and NFR files using the
(Cyrillic) recognized keys load correctly regardless of the host's default
character set.

**Why this priority**: Both are reliability/diagnosability fixes that round out
correctness. They are lower severity than the wrong-count and silent-skip defects,
but a `null` exception detail and charset-dependent key matching erode trust in the
loader.

**Independent Test**: Construct the facade exception with a message and a cause and
assert the standard accessors return them (non-null). Separately, load an NFR file
whose keys contain non-ASCII characters on a JVM configured with a non-UTF-8
default charset and confirm the keys still match and produce their assertions.

**Acceptance Scenarios**:

1. **Given** a facade load failure carrying a message and a cause, **When** a
   caller inspects the exception via the standard message/cause accessors, **Then**
   both are returned and neither is `null`.
2. **Given** an NFR file whose recognized keys contain non-ASCII (Cyrillic)
   characters, **When** it is loaded on a host whose default charset is not UTF-8,
   **Then** every key still matches its metric and produces the expected assertion.
3. **Given** identical non-ASCII-keyed input, **When** loaded via Scala and via
   Java, **Then** both match the keys identically (no charset-dependent divergence
   between the two paths).

---

### User Story 4 - Repeated loading stays efficient (Priority: P3)

A user who loads NFR assertions repeatedly (e.g. across many simulations in one
process) does not pay redundant one-time initialization cost on every call.

**Why this priority**: A performance polish (medium-perf issue). It does not change
results, only avoids needless repeated reflection-heavy setup; lowest priority of
the bundle.

**Independent Test**: Invoke the Java facade load path multiple times and confirm
the shared parser initialization happens once, not once per call (observable as a
single initialization rather than per-invocation repetition), with results
unchanged.

**Acceptance Scenarios**:

1. **Given** repeated NFR load calls through the Java facade, **When** the loads
   run, **Then** shared parser initialization is not repeated per call and the
   returned assertions are identical to a single-call result.

---

### User Story 5 - Users are told the NFR-YAML feature is deprecated (Priority: P2)

A load-test engineer who compiles or runs against the NFR-YAML assertions feature
(`assertionFromYaml`, Scala or Java) sees a deprecation notice telling them this
approach is being superseded and that replacement functionality is coming — so they
can plan a migration and know not to build new work on the old path, while their
existing simulations keep working unchanged this release.

**Why this priority**: The maintainer is steering users off the NFR-YAML mechanism
ahead of a replacement. Signaling the deprecation now (alongside the final
correctness pass) gives users lead time. It is higher than the perf polish because
it shapes adoption, but lower than the live correctness defects.

**Independent Test**: Compile code that calls `assertionFromYaml` (Scala and Java)
and confirm a deprecation warning is emitted naming the feature and pointing to the
forthcoming replacement; confirm the call still functions (returns the correct,
fixed assertion set) and is not removed.

**Acceptance Scenarios**:

1. **Given** the v1.18.0 library, **When** a user references `assertionFromYaml`
   (Scala builder or Java facade), **Then** a deprecation warning is emitted whose
   message states the NFR-YAML approach is deprecated and that new functionality is
   coming in a future release — generically, with no version, date, or issue link.
2. **Given** the deprecation, **When** an existing simulation that uses
   `assertionFromYaml` runs on v1.18.0, **Then** it still loads assertions correctly
   (with all the correctness fixes applied) — deprecation does not remove or break
   the feature.
3. **Given** the changelog for v1.18.0, **When** a user reads it, **Then** the
   NFR-YAML deprecation and the "replacement coming" note are recorded.

### Edge Cases

- **Empty NFR file / empty value map for a record**: loading yields zero assertions
  for that record without error (an empty budget is valid, not a failure).
- **`all` plus named entries under one metric**: the `all` entry maps to a global
  assertion and the named entries to per-detail assertions; both appear exactly
  once (no cross-contamination from the duplication fix).
- **Mixed valid and invalid entries**: behavior follows the resolved unknown-key
  and non-numeric policies — the file is not partially/silently accepted in a way
  that hides the bad entry.
- **A previously-accepted file containing extra/unknown keys**: still loads and
  still skips the unknown keys (no behavior change), but now emits a WARN per skipped
  key so the previously-silent skip is visible.
- **Negative or zero threshold values that still parse as numbers**: accepted as
  before (numeric validity, not range validity, is what this feature checks).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Loading an NFR file through the Java facade MUST return exactly one
  assertion per threshold entry across recognized metric records — the total equals
  the number of value entries summed over recognized records — with no duplication,
  for any number of entries (including a single entry). (#202 exponential-dup)
- **FR-002**: For identical NFR input, the Java facade and the Scala builder MUST
  produce equivalent assertion sets: the same number of assertions, the same
  thresholds, and the same scope mapping (`all` → global, otherwise per-detail by
  `group / request`). (parity; constitution: facade mirrors Scala source of truth)
- **FR-003**: When an NFR file contains an assertion key that is not one of the
  recognized metric names, the system MUST log a WARN that names the unknown key and
  skip it (produce no assertion for that key) rather than silently dropping it with
  no signal. It MUST NOT throw, and MUST NOT introduce a new config flag. The
  behavior MUST be identical across the Scala and Java paths, and remains
  backward-compatible (skip retained, only made visible). (#71)
- **FR-004**: When an NFR threshold value cannot be parsed as the number its metric
  requires, both the Scala builder and the Java facade MUST fail with a clear error
  whose message names the offending **metric key** (the record `key`, e.g. `99
  перцентиль времени выполнения` — not the inner scope key like `all`) and the
  offending value — not an opaque low-level number-format error. The number type is
  per metric: error-rate (`Процент ошибок`) is a **decimal** (fractional percents such
  as `5.5` MUST parse; the Scala path's current integer-only parse is a defect to fix),
  response-time percentile/max are **integers** (milliseconds). (#72)
- **FR-005**: The Java facade load-failure exception MUST expose its message and its
  underlying cause through the standard exception accessors (i.e. they MUST be
  non-`null` when a message/cause was provided), so failures are visible in logs and
  stack traces. (#202 null-super)
- **FR-006**: NFR keys containing non-ASCII (Cyrillic) characters MUST be matched to
  their metric correctly and identically in both the Scala and Java paths,
  independent of the host's or file's default character set; the
  encoding-normalization step MUST NOT corrupt keys, silently alter matching, or
  depend on a non-UTF-8 platform default. (#202 toUtf)
- **FR-007**: Repeated NFR loads through the Java facade MUST NOT redundantly repeat
  one-time, reflection-heavy parser initialization on every call; the initialization
  MUST happen once and be reused, with no change to the returned assertions. (#89)
- **FR-008**: The public load entry points (`assertionFromYaml(path)` in both the
  Scala builder and the Java facade) MUST keep their existing signatures and return
  types; only the correctness of their outputs and error behavior changes. (backward
  compatibility for published API)
- **FR-009**: The set of recognized NFR metric keys and their mapping MUST remain
  unchanged: error-rate percent, response-time 99/95/75/50 percentiles, and max
  response time; `all` → global assertion, otherwise per-detail assertion addressed
  by `group / request`; threshold compared with strict less-than (`lt`). Valid NFR
  files that load today MUST continue to load to the same assertions (subject only to
  the unknown-key decision in FR-003). (no regression for valid configs)
- **FR-010**: Every fix MUST be covered test-first per the project test model with
  exact-value assertions and at least one negative/boundary case each: a Java
  happy-path size+content test (FR-001), a Scala↔Java parity test (FR-002), an
  unknown-key test asserting a WARN is logged naming the key and no assertion is
  produced for it (FR-003), a non-numeric-value
  negative test for both paths asserting the message contains key+value (FR-004), an
  exception message/cause test (FR-005), a non-ASCII-key test independent of default
  charset (FR-006), and a repeated-load test confirming single initialization
  (FR-007). No test asserts a mock against a mock; no empty test bodies.
- **FR-011**: The fixes MUST ship as **v1.18.0** (MINOR) with changelog notes. The
  milestone's "v1.17.0" title is stale — `v1.17.0` and `v1.17.1` are already published
  and MUST NOT be reused (constitution V), so the next available minor is v1.18.0. None
  of the fixes is a backward-incompatible redefinition under the resolved decisions:
  unknown keys are still skipped (FR-003, only a WARN added); non-numeric values
  already crashed today, so a clear error replaces an opaque one (FR-004, pure
  bug-fix); the duplication, encoding, exception, and perf fixes correct defects
  without changing the contract for valid files. The added WARN logging on unknown keys
  MUST be recorded in the changelog as a visible-behavior note.
- **FR-012**: The NFR-YAML assertions feature — the public `assertionFromYaml(path)`
  entry point on both the Scala builder (`org.galaxio.gatling.assertions.AssertionsBuilder`)
  and the Java facade (`org.galaxio.gatling.javaapi.Assertions`), plus the `AssertionBuilderException`
  (and the public `AssertionBuilderException`) — MUST be marked **deprecated** as of
  v1.18.0 (Scala `@deprecated`, Java `@Deprecated`), following the project's existing
  deprecation convention. The deprecation message MUST be **generic and non-committal**:
  it states the NFR-YAML assertions approach is deprecated and will be replaced by new
  assertions functionality in a future release, that it still works for now, and to
  watch the changelog — with NO issue link, version number, or date promised (avoids
  over-promising a not-yet-scheduled replacement in a published library). Deprecation is
  additive and backward-compatible: the feature keeps working (with all the v1.18.0
  correctness fixes applied) and is NOT removed in this release. Removal is deferred to
  a future MAJOR once the replacement ships. The internal `NFR`/`Record` types are
  `private` and carry no user-visible deprecation. The deprecation MUST be recorded in
  the changelog.

### Key Entities *(include if data involved)*

- **NFR file**: a YAML document of records; each record has a metric `key` and a
  `value` map of scope → threshold. Scope is either `all` (global) or a
  `group / request` path (per-detail).
- **Recognized metric**: one of error-rate percent, response-time 99/95/75/50
  percentile, max response time. Drives which Gatling assertion is built and whether
  the threshold parses as integer or decimal.
- **Assertion (output)**: a Gatling assertion with a scope (global or detail path),
  a metric, and a strict less-than threshold. The load result is a collection of
  these, one per threshold entry.
- **Facade load exception** (`AssertionBuilderException`, Java): the error raised
  when loading fails; carries a message and an underlying cause that MUST be
  retrievable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For an NFR file with N threshold entries across recognized metrics,
  the Java facade returns exactly N assertions (previously up to ~2^N); adding one
  entry increases the count by exactly one. Verified by a size + content test.
- **SC-002**: The Scala builder and Java facade return equal-size, equal-content
  assertion sets for the same input; flipping one expected threshold makes the
  parity test fail.
- **SC-003**: Loading an NFR file with an unrecognized key emits a WARN naming the
  key and skips it 100% of the time (no silent no-op); the typo is visible in logs
  rather than vanishing without trace. Verified by a test capturing the WARN and
  asserting the key is named and produces no assertion.
- **SC-004**: Loading an NFR file with a non-numeric threshold produces an error
  whose message contains both the metric key (record `key`) and the offending value,
  in both the Scala and Java paths (verified by negative tests on both). A fractional
  error-rate (`5.5`) loads successfully as a decimal assertion on both paths.
- **SC-005**: The facade exception's message and cause accessors return the provided
  values (non-`null`) in 100% of constructions that supply them.
- **SC-006**: A non-ASCII (Cyrillic) NFR key matches its metric and produces its
  assertion when loaded on a JVM whose default charset is not UTF-8 (cross-charset
  test passes), with Scala and Java agreeing.
- **SC-007**: Repeated Java-facade loads perform one-time parser initialization once,
  not per call, with identical assertion output to a single load.
- **SC-008**: All recognized-key mappings and valid-file outcomes are unchanged from
  today (subject only to the FR-003 unknown-key decision), confirmed by the existing
  recognized-key tests staying green (updated only where the unknown-key decision
  requires).
- **SC-009**: Each fix is demonstrated to catch its regression: deliberately
  reverting the production fix makes at least one test fail; restoring it makes the
  suite green.
- **SC-010**: Referencing `assertionFromYaml` (Scala or Java) on v1.18.0 emits a
  deprecation warning at compile time whose message names the NFR-YAML feature and
  states replacement functionality is coming in a future release (generic — no
  version/date/issue link); the call still returns the correct fixed assertion set
  (not removed). The v1.18.0 changelog records the deprecation.
- **SC-011**: The released version is v1.18.0 (not the already-published v1.17.0 /
  v1.17.1); no published version number is reused.

## Assumptions

- The user-facing entry point is `assertionFromYaml(path)` on both the Scala builder
  (`org.galaxio.gatling.assertions.AssertionsBuilder`) and the Java facade
  (`org.galaxio.gatling.javaapi.Assertions`); both stay public and signature-stable,
  and are marked deprecated in v1.18.0 (FR-012) — deprecation does not change the
  signature, only attaches a compile-time notice.
- The target release is **v1.18.0** (next available minor), not the milestone's stale
  "v1.17.0" title: `v1.17.0`/`v1.17.1` are already published and cannot be reused
  (constitution V). Deprecating the NFR-YAML feature is additive/backward-compatible,
  so it fits a minor bump; actual removal waits for a future major and is out of scope
  here. The exact `@deprecated`/`@Deprecated` "since" token follows the project's
  existing convention (planner's choice — e.g. `"1.18.0"`).
- "Equivalent assertion set" (FR-002) means same count, same per-entry threshold,
  and same global/detail scope — not object identity (Scala core `Assertion` and
  Java `Assertion` are distinct types; the facade builds via the Java DSL because the
  core→Java conversion constructor is not public, per the existing in-code note).
- The non-numeric-value fix (FR-004) is a pure bug-fix: today's behavior is an
  uncaught crash, so adding a clear error is not a backward-incompatible change to a
  previously-successful load.
- The unknown-key decision (FR-003) is resolved to **warn + skip only**: unknown keys
  are still skipped (backward-compatible) but now emit a WARN naming the key. No
  config flag is added and no exception is thrown, so the release stays a clean
  v1.18.0 MINOR with no behavioral redefinition.
- The existing `AssertionsBuilderSpec` and its `nfr.yml` fixture (which rely on
  unknown keys APDEX/RPS being skipped) stay green unchanged; a new test additionally
  asserts that skipping an unknown key now logs a WARN naming it.
- This feature touches only the assertions load path (Scala builder + Java facade +
  facade exception) and their tests; it adds no new runtime dependency and changes no
  serialized config/profile format (the warn-only unknown-key decision adds no flag).
- Performance (FR-007) is verified behaviorally (single vs per-call initialization),
  not via a benchmark gate; no JMH benchmark is mandated by this feature.
