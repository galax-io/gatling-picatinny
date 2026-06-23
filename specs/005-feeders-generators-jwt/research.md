# Phase 0 Research: Feeders, Generators & JWT Correctness

One decision block per functional requirement. All NEEDS CLARIFICATION from the spec were
resolved in `/speckit-clarify` (see spec Clarifications, 2026-06-23); this file records the
implementation-level decisions and the alternatives weighed.

## FR-001 — `long()` honors the full Long range

- **Decision**: In `GeneratorInstances.long` (`GeneratorInstances.scala:24-31`) replace the `Int.MaxValue`/`Int.MinValue` guards with `Long.MaxValue`/`Long.MinValue`, mirroring `int()`. The bounded branch `between(min, max + 1)` is guarded by `max < Long.MaxValue` (no `+1` overflow); the upper branch `between(min - 1, max) + 1` is guarded by `min > Long.MinValue` (no `-1` underflow); the fully-unbounded `nextLong()` fires only when `min == Long.MinValue && max == Long.MaxValue`.
- **Rationale**: Root cause confirmed (workflow + REPL): the `Int` constants make any out-of-`Int` bound fall through to unbounded `nextLong()`. Using `Long` constants makes the same three-branch logic correct for the declared `Long` domain. No allocation change.
- **Alternatives considered**: (a) Always call `between(min, max+1)` — rejected: overflows when `max == Long.MaxValue`. (b) `BigInt` arithmetic — rejected: needless allocation on a per-draw path.

## FR-002 — Generator package test suite

- **Decision**: New `GeneratorsSpec` (ScalaTest `AnyWordSpec` + `Matchers`) under `src/test/scala/org/galaxio/gatling/feeders/generators/`. Drive every generator through a **fixed-seed** `GeneratorContext` so assertions are exact and deterministic. Cover: numeric (`int`/`long`/`double`/`float`, bounded variants, `positiveInt/Long`), string-length (`alphaStringN`/`numberStringN`/`alphanumericStringN`/`printableStringN`), character classes (`alphaChar`/`lowerChar`/`upperChar`/`digitChar`/`alphanumericChar`/`printableChar`), collections (`oneOf` ×3 overloads, `listOfN`, `randomList`), combinators (`~`, `**`, `repeat().separateBy()`), and case-class derivation.
- **Rationale**: Constitution III is test-first; this suite is the red→green harness for FR-001 and FR-003 and the regression guard that prevents the next silent generator defect. Seeded determinism lets assertions check exact values, not just ranges.
- **Alternatives considered**: Property-based (ScalaCheck) only — rejected as the sole approach: the milestone needs exact-value pinning and ScalaCheck is not the house default here; targeted seeded cases are clearer and match `RandomFeedersSpec`/`GeneratedFeederSpec` style. (Property checks may augment, not replace.)

## FR-003 — `**` / `separateBy` reject non-positive counts

- **Decision**: Add `require(n >= 1, s"... requires n >= 1, but got $n")` (→ `IllegalArgumentException`) at the entry of `**` (`Syntax.scala:46`) and `separateBy` (`Syntax.scala:24-25`); also validate eagerly in `repeat(n)` (`Syntax.scala:49`) so the failure surfaces at builder-construction time, not only when `separateBy` runs.
- **Rationale**: Clarification chose fail-loud over a lenient empty default, consistent with the project's prior-milestone convention. `require` yields a clear `IllegalArgumentException` naming the bad count instead of the cryptic `UnsupportedOperationException: empty.reduceLeft`.
- **Alternatives considered**: Return an empty-string generator for `n <= 0` — rejected by clarification (hides author mistakes).

## FR-004 — `codiceFiscale` valid day codes (gender-modeled)

- **Decision**: Replace `number.int(1, 71)` (`Faker.scala:577`) with a gender-modeled draw: pick a base day in `1..31`, pick gender; female adds the `+40` offset → emit `01..31` (male) or `41..71` (female). Keep `f"$d%02d"` formatting and the surrounding 16-char layout unchanged.
- **Rationale**: Clarification chose gender modeling so both valid halves appear and the female-offset path is exercised; the `32..40` gap can never be produced.
- **Alternatives considered**: Male-only `01..31` (simpler, never female) and uniform-over-valid-set without a gender concept — both rejected by clarification in favor of explicit gender modeling.

## FR-005 — `claimFromSession` auto-detect + typed override

- **Decision (default, auto-detect)**: In `ClaimsBuilder.resolve`, resolve each EL claim with `.el[Any]` instead of `.el[String]`, then map the raw session value to a `JValue` by runtime type: integral (`Long`/`Int`/`Short`/`Byte`/`BigInt`) → `JLong`/`JInt`; fractional (`Double`/`Float`/`BigDecimal`) → `JDouble`/`JDecimal`; `Boolean` → `JBool`; everything else (including any `String`, even `"42"`, and multi-part EL which Gatling concatenates to a `String`) → `JString(value.toString)`.
- **Decision (override)**: Add `claimFromSessionString`/`claimFromSessionLong`/`claimFromSessionDouble`/`claimFromSessionBoolean(name, el)`. Store the forced kind in a new `forcedTypes: Map[String, ClaimType] = Map.empty` field (a `private[jwt] sealed trait ClaimType` with `AsString`/`AsLong`/`AsDouble`/`AsBoolean`). During resolve, a claim name present in `forcedTypes` coerces the resolved value to that kind (e.g. `AsString` → `JString(v.toString)`, `AsLong` → `JLong(v.toString.trim.toLong)`); absent → auto-detect.
- **Rationale**: The single-attribute EL `#{x}` returns the raw typed attribute (`AttributePart` → `validate[Any]`), so `.el[Any]` recovers the genuine type that the current `.el[String]` was discarding. Keying auto-detect on the value's *type* (not string content) guarantees numeric-looking strings stay strings (spec Edge Case). A separate `forcedTypes` map leaves the existing `elClaims: Map[String,String]` field type unchanged, minimizing compat surface.
- **Backward-compat note**: Adding `forcedTypes` grows the `ClaimsBuilder` case-class constructor arity (source-compatible for `ClaimsBuilder()`, binary-incompatible for positional construction); the Java facade `Jwt.claims()` is updated to pass the extra empty map. The auto-detect default is a **bug fix** (#223 — the always-string output was an undocumented defect), not a behavioral redefinition; it ships in the MINOR v1.21.0 line. `claimFromSessionString` preserves the old quoted output for anyone who wants it.
- **Alternatives considered**: (a) Auto-detect by parsing string content (`"42"` → number) — rejected: silently retypes genuine string data, unsafe. (b) Single `claimFromSession(name, el, ClaimType)` method — rejected by clarification's "defaults auto-compute" intent and a wider new signature. (c) Replace `elClaims` value type with a richer `ElClaim` — rejected: changes an existing public field type (worse compat).

## FR-006 — Algorithm/key mismatch → `IllegalArgumentException`

- **Decision**: In `jwt.encode` (`jwt.scala:108-112`) replace `algorithm.asInstanceOf[JwtAsymmetricAlgorithm]` with an explicit match. For `SigningKey.AsymmetricKey`, require `algorithm` to be a `JwtAsymmetricAlgorithm`, else throw `IllegalArgumentException` naming the algorithm and "asymmetric key". For `SigningKey.StringSecret`, require a symmetric/HMAC algorithm (`JwtHmacAlgorithm`), else throw `IllegalArgumentException` naming the algorithm and "string secret".
- **Rationale**: pdi-jwt models HMAC vs asymmetric as distinct algorithm traits; matching on them replaces the raw `ClassCastException` with a validation error consistent with `JwtGeneratorBuilder.jwtAlgorithm`'s existing `IllegalArgumentException` pattern.
- **Alternatives considered**: Validate at builder-construction time only — rejected: the key and algorithm definitively meet at `encode`; validating there covers all construction paths with one guard (a builder-time check may still be added as defense-in-depth but is not required).

## FR-007 — Wrap malformed key-parse failures with context

- **Decision**: Wrap the body of `privateKeyFromPem`/`publicKeyFromPem` (`JwtKeys.scala:59-77`) in a `try`/`catch` that rethrows `IllegalArgumentException(s"Failed to load $algorithm key from PEM (stage: <decode|spec|factory>): <cause msg>", cause)` for `IllegalArgumentException` (bad Base64), `InvalidKeySpecException` (bad DER), and `NoSuchAlgorithmException`. Public method signatures unchanged.
- **Rationale**: Turns an opaque `InvalidKeySpecException: Could not parse key` into a message naming the algorithm and failure stage with the cause attached, so authors can tell a wrong-path from a corrupt-key from a wrong-algorithm error.
- **Alternatives considered**: A bespoke `JwtKeyException` type — rejected (new public type for marginal benefit); `IllegalArgumentException` matches the codebase convention.

## FR-008 — `setJwt`/`setJwtAsBearer` failure-path tests

- **Decision**: Extend `JwtSpec` with negative cases driving `setJwt` and `setJwtAsBearer` (`jwt.scala:64-78`) where a claim/header/payload EL references a missing session attribute, asserting `IllegalStateException` whose message contains the underlying `Failure` detail.
- **Rationale**: These `Failure → IllegalStateException` branches are currently 0% covered; pinning them locks the contract that EL resolution failure throws (not silently returns) with the cause surfaced.
- **Alternatives considered**: None — pure test addition.

## FR-009 / FR-010 — Negatives + compatibility guard

- **Decision**: Every behavioral FR ships ≥1 negative/boundary test (already itemized in the Test Model). A compile-guard spec locks public generator/JWT signatures (new methods additive, none removed); a round-trip test asserts a String session claim serializes byte-for-byte identically to pre-change output, isolating the intentional numeric/boolean change.
- **Rationale**: Constitution III + the FR-010 byte-stability requirement for String claims.
- **Alternatives considered**: None.
