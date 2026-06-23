# Phase 1 Data Model: Modified Type Contracts

Only types whose **shape or contract** changes are listed. Prose contracts, no implementation.

## ClaimsBuilder (`utils/jwt/ClaimsBuilder.scala`) — MODIFIED

Existing public case class. Today:

```
ClaimsBuilder(
  staticClaims: Map[String, JValue],   // typed literal claims (claim(name, Long/Boolean/String))
  elClaims:     Map[String, String],   // EL claims, currently always serialized as JString
  ttl:          Option[FiniteDuration],
  setIat:       Boolean,
  setNbf:       Boolean,
)
```

**Change**: add one field with a default.

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `forcedTypes` | `Map[String, ClaimType]` | `Map.empty` | Per-claim-name forced JSON kind set by typed-override methods. A name **absent** here is auto-detected; **present** is coerced to the named kind. |

- `elClaims` keeps its `Map[String, String]` type (the EL string per claim name).
- **Resolution contract** (`resolve`): for each `(name, el)` in `elClaims`, resolve the value as `Any` from the session, then:
  - if `name ∈ forcedTypes` → coerce to that `ClaimType`'s `JValue`;
  - else → auto-detect: integral → JSON number; fractional → JSON number; boolean → JSON boolean; anything else (incl. any `String`, incl. multi-part EL results) → JSON string.
- `staticClaims`, `ttl`, `setIat`, `setNbf`, and all existing methods are unchanged.

### New public methods (additive)

| Method | Stores | Effect on payload |
|--------|--------|-------------------|
| `claimFromSession(name, el)` | `elClaims += (name → el)` (unchanged signature) | **Auto-detect** the JSON type from the session value (behavior change vs. today's always-string). |
| `claimFromSessionString(name, el)` | `elClaims`, `forcedTypes += (name → AsString)` | Force JSON string (preserves pre-change behavior for a caller who wants it). |
| `claimFromSessionLong(name, el)` | `elClaims`, `forcedTypes += (name → AsLong)` | Force JSON number (integral). |
| `claimFromSessionDouble(name, el)` | `elClaims`, `forcedTypes += (name → AsDouble)` | Force JSON number (fractional). |
| `claimFromSessionBoolean(name, el)` | `elClaims`, `forcedTypes += (name → AsBoolean)` | Force JSON boolean. |

- `issuer`/`subject`/`audience` (which route EL values through `withClaim` → `elClaims`) inherit auto-detect; in practice they receive strings and stay JSON strings (spec-correct for registered claims).

### Coercion contract for forced types

| ClaimType | Input → output | On non-coercible input |
|-----------|----------------|------------------------|
| `AsString` | any → `JString(value.toString)` | never fails |
| `AsLong` | numeric or numeric string → `JLong` | resolution `Failure` with a clear message (flows to `IllegalStateException` via `setJwt`) |
| `AsDouble` | numeric or numeric string → `JDouble` | resolution `Failure` with a clear message |
| `AsBoolean` | `"true"`/`"false"`/boolean → `JBool` | resolution `Failure` with a clear message |

## ClaimType (`utils/jwt/ClaimsBuilder.scala`) — NEW (internal)

`private[jwt] sealed trait ClaimType` with case objects `AsString`, `AsLong`, `AsDouble`, `AsBoolean`. Not a public surface; exists only to tag forced overrides.

## SigningKey / JWT algorithm pairing (`utils/jwt/jwt.scala`) — CONTRACT TIGHTENED

No type changes. The `encode` function gains a validation contract:

| `SigningKey` | Required algorithm kind | Violation |
|--------------|------------------------|-----------|
| `StringSecret` | symmetric / HMAC (`JwtHmacAlgorithm`, e.g. `HS256/384/512`) | `IllegalArgumentException` naming algorithm + "string secret" |
| `AsymmetricKey` | `JwtAsymmetricAlgorithm` (e.g. `RS*/ES*/PS*`) | `IllegalArgumentException` naming algorithm + "asymmetric key" |

Replaces the unchecked `asInstanceOf[JwtAsymmetricAlgorithm]` (raw `ClassCastException`).

## JwtKeys parse methods (`utils/jwt/JwtKeys.scala`) — CONTRACT TIGHTENED

No signature change. `privateKeyFromPem`/`publicKeyFromPem` gain an error contract: any of Base64-decode / key-spec / key-factory failure is rethrown as `IllegalArgumentException` naming the algorithm, the failure stage, and the underlying cause (instead of a bare `InvalidKeySpecException`/`NoSuchAlgorithmException`).

## Generator combinators (`feeders/generators/Syntax.scala`) — CONTRACT TIGHTENED

No signature change. `**(n)`, `repeat(n)`, and `SeparatorStep.separateBy` gain a precondition: `n >= 1`, else `IllegalArgumentException` naming the invalid count (replaces `UnsupportedOperationException`).

## `long` generator (`feeders/generators/GeneratorInstances.scala`) — CONTRACT FIXED

No signature change. `long(min, max): Generator[Long]` now returns values within `[min, max]` for the full `Long` domain (previously fell through to unbounded for out-of-`Int` bounds).

## codiceFiscale day code (`feeders/faker/Faker.scala`) — OUTPUT CORRECTED

No signature change. `Faker.it.codiceFiscale()` day-code field now ranges over `01..31` (male) ∪ `41..71` (female); `32..40` no longer produced. Overall 16-char structure unchanged.

## Java facade (`javaapi/utils/Jwt.java`) — MODIFIED (thin)

`Jwt.claims()` constructs the Scala `ClaimsBuilder` via its positional constructor; the new `forcedTypes` field requires passing one additional empty `Map`. New typed `claimFromSession*` methods are inherited from the Scala builder — no facade-side logic. (Per Constitution I, facade only delegates.)
