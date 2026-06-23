# Contract: JWT Session Claims (FR-005, FR-006, FR-007, FR-008)

Public API contract for JWT claim typing, key loading, and generation errors. Prose + signatures
only; behavior is authoritative, not the sketch syntax.

## ClaimsBuilder — session claim typing

### `claimFromSession(name: String, el: String): ClaimsBuilder` — behavior CHANGED

- Resolves `el` against the session and serializes the claim by the **genuine session value type**:
  - integral number → JSON number; fractional number → JSON number; boolean → JSON boolean;
  - any `String` (including `"42"`) and any multi-part EL (Gatling concatenates to a `String`) → JSON string.
- **Compatibility**: output for String session values is unchanged; output for genuinely
  numeric/boolean session values changes from quoted string to typed JSON — this is the **bug fix**
  (#223), MINOR-appropriate. `claimFromSessionString` preserves the old quoted output if needed.

### Typed overrides — NEW (additive)

```
def claimFromSessionString(name: String, el: String): ClaimsBuilder
def claimFromSessionLong(name: String, el: String): ClaimsBuilder
def claimFromSessionDouble(name: String, el: String): ClaimsBuilder
def claimFromSessionBoolean(name: String, el: String): ClaimsBuilder
```

- Force the named JSON kind regardless of the resolved value's inferred type.
- `claimFromSessionString` reproduces the pre-change always-string behavior for a caller who wants it.
- A value that cannot be coerced to the forced numeric/boolean kind produces a resolution
  `Failure`, surfaced as `IllegalStateException` by `setJwt`/`setJwtAsBearer`.

### Examples (illustrative)

| Call | Session value | Payload fragment |
|------|---------------|------------------|
| `claimFromSession("uid","#{uid}")` | `42L` | `"uid":42` |
| `claimFromSession("uid","#{uid}")` | `"42"` (String) | `"uid":"42"` |
| `claimFromSession("ok","#{ok}")` | `true` | `"ok":true` |
| `claimFromSessionString("uid","#{uid}")` | `42L` | `"uid":"42"` |
| `claimFromSessionLong("uid","#{uid}")` | `"42"` (String) | `"uid":42` |

### Java/Kotlin facade

`Jwt.claims()` returns the Scala `ClaimsBuilder`; the typed methods above are callable from Java
unchanged in semantics. Facade adds no logic (delegation only).

## JWT generation — algorithm/key validation (FR-006)

- Generating a token with a `StringSecret` and a non-HMAC algorithm, or an `AsymmetricKey` and a
  non-asymmetric algorithm, MUST throw `IllegalArgumentException` naming the algorithm and the key
  kind — never `ClassCastException`.
- A correctly matched algorithm/key pair produces a token verifiable with the corresponding key.

## JWT key loading — contextual errors (FR-007)

- `JwtKeys.{rsa,ec}{Private,Public}KeyFrom{Resource,File}` on malformed input (invalid Base64,
  truncated DER, wrong-algorithm key) MUST throw `IllegalArgumentException` naming the algorithm
  and failure stage, with the underlying cause attached — never a bare `InvalidKeySpecException`.
- Valid PEM continues to load and sign as before (signatures unchanged).

## JWT generation — failure path (FR-008)

- `session.setJwt(gen, name)` / `setJwtAsBearer(gen[, name])` MUST throw `IllegalStateException`
  carrying the underlying resolution-failure message when a header/payload/claim EL cannot be
  resolved from the session. Resolvable generators store the token (`setJwtAsBearer` stores
  `"Bearer <token>"`).
