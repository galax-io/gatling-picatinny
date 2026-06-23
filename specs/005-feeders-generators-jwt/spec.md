# Feature Specification: Feeders, Generators & JWT Correctness

**Feature Branch**: `005-feeders-generators-jwt`

**Created**: 2026-06-23

**Status**: Draft

**Input**: User description: "https://github.com/galax-io/gatling-picatinny/milestone/5"

**Milestone**: v1.21.0 — Feeders, Generators & JWT (#205, #206, #223)

## Clarifications

### Session 2026-06-23

- Q: How should numeric/boolean JWT claims sourced from the session be produced (FR-005, #223)? → A: Auto-detect by default with explicit override — `claimFromSession` infers the JSON type from the genuine session value type (numeric → JSON number, boolean → JSON boolean, everything else incl. numeric-looking strings → JSON string), AND typed override methods (e.g. `claimFromSessionLong`/`claimFromSessionBool`/`claimFromSessionString`) let the author force a specific JSON type. (This is a **bug fix** — #223: `claimFromSession` was meant to preserve the session value's type; the always-string output was an undocumented defect, never specified or test-pinned. The fix restores intended typing; `claimFromSessionString` lets anyone who actually wanted a string keep it.)
- Q: What valid encoding replaces the codiceFiscale day field (FR-004, #205)? → A: Model gender — randomly emit day `01..31` (male) or day+40 `41..71` (female); the `32..40` gap never appears.
- Q: What should `**`/`separateBy` do for a non-positive count `n <= 0` (FR-003, #205)? → A: Throw `IllegalArgumentException` naming the invalid count (fail-loud, project convention).

## User Scenarios & Testing *(mandatory)*

<!--
  Stories are framed from the library consumer's perspective: teams writing Gatling
  load-test simulations on top of gatling-picatinny. "System" = the library. Each fix
  was confirmed against current source (file:line in Requirements) before this spec.
-->

> **Note**: Every fix ships with ≥1 negative and ≥1 positive test (constitution §III). Each Acceptance Scenarios block below carries at least one negative/boundary case and at least one positive case; no test pins a known-buggy behavior.

### User Story 1 - Numeric generators honor their declared bounds (Priority: P1)

A simulation author uses the random data generator `long(min, max)` to produce identifiers
or amounts spanning the full 64-bit range (e.g. `long(-3_000_000_000L, 3_000_000_000L)`).
Today the generator gates a Long range with `Int` limits, so any bound outside the `Int`
range silently falls through to a fully **unbounded** value — the test data ignores the
requested range entirely, with no error. The generator must respect the full Long range.

**Why this priority**: Silent wrong data is the highest-severity defect in the milestone
(severity:high). A feeder that ignores its bounds invalidates any simulation that relies on
constrained identifiers/amounts, and the failure is invisible — no exception, just wrong values.

**Independent Test**: Draw a large sample from `long()` with bounds outside the `Int` range
and assert every value lands within `[min, max]`; assert a fully-unbounded call still spans
the Long range. No other module involved.

**Acceptance Scenarios**:

1. **Given** `long(-3_000_000_000L, 3_000_000_000L)`, **When** many values are drawn, **Then** every value is within the inclusive range and never falls outside it.
2. **Given** `long(0L, Long.MaxValue)`, **When** values are drawn, **Then** they stay within `[0, Long.MaxValue]` (no negative values).
3. **Given** `long(Long.MinValue, Long.MaxValue)`, **When** values are drawn, **Then** the generator spans the full Long range (intended unbounded behavior preserved).
4. **Given** a fixed seed, **When** the same `long()` generator is drawn twice, **Then** it produces the identical sequence (determinism preserved).

---

### User Story 2 - Generator package has regression-proof test coverage (Priority: P1)

A maintainer changes a generator and needs confidence the change did not silently corrupt
output. Today the entire `feeders/generators` package (~50 public functions and combinators)
has **zero** tests — which is exactly why the `long()` defect shipped. The package must gain a
unit suite that pins numeric, string, character, and collection generators, the `~` / `**` /
`separateBy` combinators, seeded determinism, and case-class derivation.

**Why this priority**: The constitution mandates test-first. The `long()` fix (US1) and the
combinator fix (US5) cannot be considered done without a suite that proves them and guards
against the next silent regression. This is the foundation the other generator fixes ship on.

**Independent Test**: Run the new generator suite in isolation (`Test` scope, no container);
it asserts exact values and boundaries for each public generator and fails if any is broken.

**Acceptance Scenarios**:

1. **Given** the new suite, **When** unit tests run, **Then** numeric (`int`/`long`/`double`/`float`), string-length, character-class, and `oneOf`/`listOfN`/`randomList` generators each have at least one exact-value and one boundary assertion.
2. **Given** the combinators `~`, `**`, and `repeat().separateBy()`, **When** tested at representative counts (e.g. n=3), **Then** the produced strings match an exact expected shape.
3. **Given** a seeded `GeneratorContext`, **When** a generator is drawn, **Then** the suite asserts deterministic output for that seed.
4. **Given** the patched package, **When** coverage is measured, **Then** statement/branch coverage remains at or above the project floor (65/60).

---

### User Story 3 - JWT claims preserve numeric and boolean session values (Priority: P2)

A simulation author sources a JWT claim from the Gatling session via
`claimFromSession("user_id", "#{user_id}")` where `user_id` is numeric (e.g. fed as a `Long`).
Today every session-resolved claim is serialized as a JSON **string**, so the payload contains
`{"user_id":"42"}` instead of `{"user_id":42}`. Backends that strictly type-check JWT claims
reject the token (401). By default the library must infer the JSON type from the genuine session
value (numeric → number, boolean → boolean, everything else → string), and must also offer typed
override methods so an author can force a specific JSON type regardless of the inferred one.

**Why this priority**: Confirmed bug (issue #223) that breaks real auth flows, but narrower than
US1: it affects only custom numeric/boolean claims, and registered string claims (`sub`/`iss`/`aud`)
remain strings. Restoring type preservation is a **bug fix** (the always-string output was a defect,
not intended behavior); string output (including numeric-looking strings) stays unchanged.

**Independent Test**: Resolve a JWT payload from a session whose attribute is numeric/boolean and
assert the decoded claim is a JSON number/boolean; assert a string attribute still yields a JSON
string; assert a typed override forces the requested JSON type.

**Acceptance Scenarios**:

1. **Given** a session with a genuinely numeric `user_id` (e.g. `Long`), **When** `claimFromSession` resolves it, **Then** the JWT payload contains a JSON number (`{"user_id":42}`), not a quoted string.
2. **Given** a session with a genuinely boolean attribute, **When** `claimFromSession` resolves it, **Then** the payload contains a JSON boolean (`true`/`false`), not `"true"`.
3. **Given** a session whose attribute is a String (including a numeric-looking string like `"42"`), **When** `claimFromSession` resolves it, **Then** the payload contains a JSON string (`"42"`) — no silent retyping of string data.
4. **Given** an author who calls a typed override (e.g. force-string or force-long), **When** the payload is produced, **Then** the claim is emitted as the explicitly requested JSON type regardless of the session value's inferred type.
5. **Given** a registered claim such as `sub` resolved via EL from a string attribute, **When** the payload is produced, **Then** it remains a JSON string (spec-correct, unchanged).

---

### User Story 4 - JWT fails clearly on algorithm/key mismatch (Priority: P3)

A simulation author configures JWT generation with an HMAC algorithm name (e.g. `HS256`) but
supplies an asymmetric private key (or the reverse). Today the code performs an unchecked cast
and throws a raw `ClassCastException` with no guidance. The library must validate the
algorithm/key pairing and fail with a clear, actionable error.

**Why this priority**: Confirmed defect (issue #206). A raw `ClassCastException` is opaque and
slow to diagnose; the fix matches the project's loud-failure convention. Medium severity —
misconfiguration, not data corruption.

**Independent Test**: Attempt JWT generation with a mismatched algorithm/key pair and assert an
`IllegalArgumentException` whose message names both the algorithm and the key kind; assert a
matching pair still succeeds.

**Acceptance Scenarios**:

1. **Given** an `HS*` algorithm name paired with an asymmetric private key, **When** JWT generation runs, **Then** an `IllegalArgumentException` is thrown naming the algorithm and the key kind (not `ClassCastException`).
2. **Given** an asymmetric algorithm (`RS*`/`ES*`/`PS*`) paired with a string secret, **When** generation runs, **Then** an `IllegalArgumentException` is thrown with a clear message.
3. **Given** a correctly matched algorithm and key, **When** generation runs, **Then** a valid token is produced (happy path unchanged).

---

### User Story 5 - JWT key loading and generation report actionable errors (Priority: P3)

A simulation author points JWT key loading at a malformed PEM (invalid Base64, truncated DER,
or a key whose algorithm doesn't match), or supplies an EL expression that cannot be resolved
from the session at generation time. Today malformed-key parsing leaks an unwrapped
`InvalidKeySpecException`/`IllegalArgumentException`/`NoSuchAlgorithmException` with no context,
and the generation-failure path (`Failure → IllegalStateException`) is entirely untested. The
library must surface a contextual error for bad keys and have its failure contract pinned by tests.

**Why this priority**: Confirmed gap (issue #206). Improves diagnosability and locks the failure
contract so future refactors can't silently swallow errors. Medium severity, test-heavy.

**Independent Test**: Feed malformed key material and assert a contextual exception (naming
algorithm/source/failure stage); drive `setJwt`/`setJwtAsBearer` with an unresolvable EL claim
and assert `IllegalStateException` carrying the underlying failure message.

**Acceptance Scenarios**:

1. **Given** invalid Base64 key material, **When** the key is loaded, **Then** a contextual exception is thrown that names the algorithm and the source, with the underlying cause attached.
2. **Given** a truncated/invalid DER key, **When** loaded, **Then** a contextual exception is thrown (not a bare `InvalidKeySpecException`).
3. **Given** a JWT generator whose claim EL cannot be resolved from the session, **When** `setJwt` runs, **Then** an `IllegalStateException` is thrown whose message includes the resolution-failure detail.
4. **Given** the same unresolvable-EL scenario, **When** `setJwtAsBearer` runs, **Then** the same `IllegalStateException` contract holds.

---

### User Story 6 - Generator combinators reject invalid repetition counts (Priority: P4)

A simulation author calls a repetition/separator combinator (`**` or `repeat().separateBy()`)
with a non-positive count (`n <= 0`). Today this reduces over an empty sequence and throws a
cryptic `UnsupportedOperationException: empty.reduceLeft`. The combinator must reject `n <= 0`
with a clear, intentional error.

**Why this priority**: Confirmed defect (issue #205, low severity). Cryptic crash → clear error
is a usability improvement; affects only callers passing an invalid count.

**Independent Test**: Invoke `**` and `separateBy` with `n = 0` and `n = -1` and assert a clear
`IllegalArgumentException`; assert `n = 3` produces the expected combined string.

**Acceptance Scenarios**:

1. **Given** `** 0` (or `separateBy` with `n = 0`), **When** the generator is built, **Then** an `IllegalArgumentException` naming the invalid count is thrown (not `UnsupportedOperationException`).
2. **Given** a negative count, **When** the generator is built, **Then** the same clear rejection occurs.
3. **Given** a valid count (n ≥ 1), **When** the generator is built and drawn, **Then** it produces the expected repeated/separated value.

---

### User Story 7 - Italian codice fiscale emits valid day codes (Priority: P4)

A simulation author uses the Faker `codiceFiscale` generator to produce realistic Italian tax
codes. Today the day field is drawn from `1..71`, emitting impossible codes in the `32..40` gap
(valid codes are `01..31` for males and `41..71` for females, the `+40` female offset). The
generator must only emit valid day codes.

**Why this priority**: Confirmed defect (issue #205, low severity). Affects only consumers of
`codiceFiscale` whose downstream validates the standard; structurally-permissive tests miss it today.

**Independent Test**: Draw many `codiceFiscale` values and assert the day-code portion is always
in `01..31` or `41..71`, never `32..40`.

**Acceptance Scenarios**:

1. **Given** many generated `codiceFiscale` values, **When** the day code is inspected, **Then** it is always within `01..31` or `41..71`.
2. **Given** many generated values, **When** inspected, **Then** no value has a day code in `32..40`.
3. **Given** the fixed code structure, **When** generated, **Then** the overall codice fiscale length and field layout are unchanged.

---

### Edge Cases

- `long(min, max)` where `min == max` → returns that single value (degenerate range).
- `long(min, max)` where `max == Long.MaxValue` but `min > Long.MinValue` → bounded path, no overflow on `max + 1`.
- JWT session claim whose attribute is a String that *looks* numeric (e.g. `"42"`) → MUST stay a JSON string under auto-detection (only genuinely numeric/boolean session values become JSON numbers/booleans), so string data is never silently retyped; a typed override can still force a number if the author intends it.
- JWT session claim whose attribute is missing/unresolvable → existing `Failure → IllegalStateException` path (covered by US5).
- codiceFiscale gender boundary: female day 31 → code `71` (max valid); female day 1 → `41`; male day 1 → `01`.
- `**` / `separateBy` with `n = 1` → returns the single underlying value (no separator), not an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `long(min, max)` generator MUST respect the full `Long` range — bounded calls whose limits exceed the `Int` range MUST stay within `[min, max]` and MUST NOT fall through to an unbounded draw. (`feeders/generators/GeneratorInstances.scala:24-31`: today gated by `Int.MaxValue`/`Int.MinValue`.) Implementation note: when `max == Long.MaxValue`, use `ThreadLocalRandom.current().nextLong()` (unbounded form) combined with clamping to `[min, max]`; otherwise use `nextLong(min, bound)` where `bound = max + 1L`.
- **FR-002**: The `feeders/generators` package MUST gain a unit test suite (`Test` scope, no container) covering numeric/string/character/collection generators, the `~`/`**`/`separateBy` combinators, seeded determinism, and case-class derivation (derivation via `Generator`'s `Functor`-like `map`/`flatMap` — any product type whose fields have `Generator` instances can be sequenced with a for-comprehension and `yield CaseClass(...)`), with exact-value and boundary assertions. (Package currently has zero tests.)
- **FR-003**: The `**` and `separateBy` combinators MUST reject a non-positive repetition count with an `IllegalArgumentException` naming the invalid count, instead of `UnsupportedOperationException: empty.reduceLeft`. (`feeders/generators/Syntax.scala`: `separateBy` L24-25, `**` L46, `repeat` L49.)
- **FR-004**: The Faker `codiceFiscale` generator MUST emit only valid day codes by modeling gender — randomly producing day `01..31` (male) or day+40 `41..71` (female); the `32..40` gap MUST never appear. (`feeders/faker/Faker.scala:577`: today draws `int(1, 71)`.)
- **FR-005**: `claimFromSession` MUST infer the JSON claim type from the genuine session value: a numeric value → JSON number, a boolean value → JSON boolean, any other value (including a numeric-looking String) → JSON string. The library MUST additionally provide typed override entry points (e.g. force long/double/boolean/string via `claimFromSessionLong`/`claimFromSessionDouble`/`as[Double]`/`claimFromSessionBoolean`/`claimFromSessionString`) so an author can pin a specific JSON type regardless of the inferred one. (`utils/jwt/ClaimsBuilder.scala:107-118`: today every EL claim is wrapped in `JString`.)
- **FR-006**: JWT generation MUST validate the algorithm/key pairing and throw an `IllegalArgumentException` naming the algorithm and the key kind when an HMAC algorithm is paired with an asymmetric key (or vice versa), instead of a raw `ClassCastException`. (`utils/jwt/jwt.scala:108-112`.)
- **FR-007**: JWT key loading MUST wrap malformed-key failures (invalid Base64, truncated DER, wrong-algorithm key) in a contextual exception that names the algorithm and source and attaches the underlying cause, instead of leaking a bare `InvalidKeySpecException`/`NoSuchAlgorithmException`. (`utils/jwt/JwtKeys.scala:59-77`.) FR-007 targets parse-time mismatch (e.g., loading an EC-formatted key bytes as RSA); FR-006 targets encode-time mismatch (e.g., pairing an HMAC algorithm with an asymmetric key object at JWT generation).
- **FR-008**: The JWT generation failure paths (`setJwt` / `setJwtAsBearer`, `Failure → IllegalStateException`) MUST be covered by negative-path tests that pin the exception type and that the underlying failure message is preserved. (`utils/jwt/jwt.scala:64-78`.)
- **FR-010**: No public Scala or Java/Kotlin facade API signature MAY be removed or changed incompatibly; the typed-override entry points (FR-005) MUST be additive. Serialized JWT output for String session values (including numeric-looking strings) MUST be byte-for-byte unchanged. The numeric/boolean typing of genuinely numeric/boolean session values (FR-005) is a **bug fix** (#223) and MUST be noted in the changelog (so anyone who relied on the buggy quoted output can switch to `claimFromSessionString`); it is not a breaking API change.

### Key Entities

- **Generator[A]**: Composable random-value producer over a seeded `GeneratorContext`; underlies feeders. Includes numeric (`int`/`long`/`double`/`float`), string, character, collection generators and the `~`/`**`/`separateBy` combinators.
- **codiceFiscale generator**: Faker generator that assembles an Italian tax code from name, date, and place fields; the day field encodes day-of-month with a `+40` female offset.
- **ClaimsBuilder**: Builder for JWT claims; holds static claims (typed `JValue`) and EL/session claims (resolved per virtual user). Source of the numeric-claim defect.
- **SigningKey / JWT algorithm**: Pairing of a signing key (string secret vs asymmetric key) with a JWT algorithm (HMAC vs asymmetric); mismatch is the source of the `ClassCastException`.
- **JwtKeys**: Loads RSA/EC keys from PEM resources/files; source of the unwrapped key-parse exceptions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across a large sample (≥100,000 draws) from `long()` with any in-`Long`-range bounds, 100% of values fall within the inclusive `[min, max]` range; zero out-of-bounds values.
- **SC-002**: The `feeders/generators` package goes from zero to a suite covering every public generator family and combinator (numeric, string-length, character-class, collection, `~`/`**`/`separateBy`, derivation), with project statement/branch coverage at or above the 65/60 floor after the change.
- **SC-003**: A genuinely numeric or boolean session value sourced via `claimFromSession` round-trips as a JSON number/boolean (verified by decoding the payload); a String session value (including `"42"`) still serializes identically to today's quoted output; a typed override emits the explicitly requested JSON type.
- **SC-004**: An algorithm/key mismatch produces an `IllegalArgumentException` naming both the algorithm and key kind in 100% of mismatch combinations; zero `ClassCastException` from this path.
- **SC-005**: Malformed key material produces a contextual exception naming the algorithm and source in 100% of malformed-input cases tested; zero unwrapped `InvalidKeySpecException` escapes.
- **SC-006**: `**` and `separateBy` with `n ≤ 0` produce an `IllegalArgumentException` in 100% of cases; zero `UnsupportedOperationException` from this path.
- **SC-007**: Across a sample of ≥10,000 `codiceFiscale` values, zero day codes fall in `32..40`; 100% fall in `01..31` or `41..71`.
- **SC-008**: All pre-existing feeder, generator, and JWT unit/integration tests pass unchanged after the feature; no public API signature is removed or altered incompatibly.

## Assumptions

- The milestone sub-item "SeparatedValues `Seq[Map]` overload does not trim" (#205) was found **already resolved** — all three overloads call `.trim` (`SeparatedValuesFeeder.scala:43,75,125`). It is therefore out of scope; no change is made.
- The milestone's `#97` reference (rescope — "match already exhaustive") is a no-op and out of scope.
- FR-005 default is **auto-detection by genuine session value type** (numeric → number, boolean → boolean, else → string), plus **additive typed override** entry points for forcing a JSON type. Auto-detection is keyed on the session value's actual type, not on string content, so numeric-looking strings (`"42"`) stay strings. This is a **bug fix** (#223): the always-string output was an undocumented defect, not intended behavior, so restoring type preservation is a correctness change appropriate for a MINOR release (v1.21.0), not a breaking redefinition. Consumers who actually relied on the quoted output can adopt `claimFromSessionString`; noted in the changelog. (Decision recorded in Clarifications, 2026-06-23.)
- FR-003 chooses a loud `IllegalArgumentException` over returning an empty/default generator, consistent with the project's "fail loud on invalid input" convention established in prior milestones. (Decision recorded in Clarifications, 2026-06-23.)
- FR-004 keeps the codice fiscale field layout and length unchanged; only the day-code value is corrected by modeling gender to produce `01..31` (male) or `41..71` (female). (Decision recorded in Clarifications, 2026-06-23.)
- All new tests run in `Test` scope without Docker/containers (unit/functional and compile-guard layers), consistent with TESTING.md.
- Generators are constructed at simulation-author/setup time; added validation guards (FR-001, FR-003, FR-006) are not on a per-request hot path and have negligible runtime cost.
- Existing JWT key fixtures under test resources are sufficient to author malformed-key variants (truncated/garbled) without new external dependencies.
