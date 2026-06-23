# Quickstart: Validating Feeders, Generators & JWT Correctness

Runnable validation guide. Implementation lives in `tasks.md` / the implementation phase; this
file proves the feature end-to-end once built.

## Prerequisites

- Scala 2.13.18, sbt, JDK 17+ (CI Temurin 21).
- No Docker required — every scenario here runs in `Test` scope (JWT is the non-container layer).

## Build & format gate

```bash
sbt scalafmtAll scalafmtSbt
sbt scalafmtCheckAll scalafmtSbtCheck compile test
```

Expected: clean format, compile, and a green unit suite including the new
`feeders/generators/GeneratorsSpec` and the extended JWT/Faker specs.

## Scenario 1 — `long()` honors bounds (FR-001)

Run the generators suite:

```bash
sbt "testOnly org.galaxio.gatling.feeders.generators.GeneratorsSpec"
```

Expected: a large seeded sample of `long(-3_000_000_000L, 3_000_000_000L)` stays within range;
`long(0L, Long.MaxValue)` never negative; `long(Long.MinValue, Long.MaxValue)` spans both signs.
See [contracts/generators.md](contracts/generators.md).

## Scenario 2 — combinators reject `n <= 0` (FR-003)

Within the same suite: `** 0` / `repeat(0).separateBy(",")` raise `IllegalArgumentException`
naming the count; `** 3` and `repeat(3).separateBy("-")` produce the exact combined string.

## Scenario 3 — codiceFiscale valid day codes (FR-004)

```bash
sbt "testOnly org.galaxio.gatling.feeders.faker.GeneratedFeederSpec"
```

Expected: across many draws, the day code is always `01..31` or `41..71`, never `32..40`, and both
halves appear.

## Scenario 4 — JWT claim typing (FR-005)

```bash
sbt "testOnly org.galaxio.gatling.utils.jwt.JwtSpec"
```

Expected (decode the generated payload): numeric session value → JSON number; boolean → JSON
boolean; String (incl. `"42"`) → JSON string; typed overrides force the requested kind. The
String-claim round-trip is byte-for-byte identical to the pre-change output. See
[contracts/jwt-claims.md](contracts/jwt-claims.md).

## Scenario 5 — JWT algorithm/key + key-load + failure path (FR-006/007/008)

Within `JwtSpec` / `JwtKeysSpec`:

- `jwt("HS256", asymmetricKey)` and `jwt("RS256", "secret")` → `IllegalArgumentException` naming
  algorithm + key kind (not `ClassCastException`).
- `JwtKeys.rsaPrivateKeyFromResource` on invalid Base64 / truncated DER → contextual
  `IllegalArgumentException` with cause.
- `setJwt`/`setJwtAsBearer` with an unresolvable claim EL → `IllegalStateException` carrying the
  failure detail.

```bash
sbt "testOnly org.galaxio.gatling.utils.jwt.*"
```

## Scenario 6 — Java facade delegation (FR-005, FR-010)

```bash
sbt "testOnly org.galaxio.gatling.javaapi.utils.JwtClaimsDelegationTest"
```

Expected: `Jwt.claims().claimFromSessionLong(...)` from Java yields claim JSON identical to the
Scala-core result — facade delegates, no facade-only logic.

## Done signal

- `sbt scalafmtCheckAll scalafmtSbtCheck compile test` green.
- Coverage at or above the 65/60 floor (generators package now covered).
- No public signature removed; String JWT claim output unchanged (verified in Scenario 4).
