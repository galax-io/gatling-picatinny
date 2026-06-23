# Implementation Plan: Feeders, Generators & JWT Correctness

**Branch**: `005-feeders-generators-jwt` | **Date**: 2026-06-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-feeders-generators-jwt/spec.md`

**Milestone**: [v1.21.0 — Feeders, Generators & JWT](https://github.com/galax-io/gatling-picatinny/milestone/5) | Issues: [#205](https://github.com/galax-io/gatling-picatinny/issues/205), [#206](https://github.com/galax-io/gatling-picatinny/issues/206), [#223](https://github.com/galax-io/gatling-picatinny/issues/223) | **Released**: [v1.21.1](https://github.com/galax-io/gatling-picatinny/releases/tag/v1.21.1) (patch: Belfiore regex broadened to `[A-Z][0-9]{3}`)

## Summary

Seven correctness fixes plus the first-ever test suite for the `feeders/generators` package,
spanning three modules (feeders/generators, feeders/faker, utils/jwt). Each fix replaces a silent
wrong result or an opaque exception with correct output or a loud, typed, actionable error. JWT
`claimFromSession` auto-detecting numeric/boolean session values (FR-005) is a **bug fix** (#223):
the always-string output was an undocumented defect — `claimFromSession` was meant to preserve the
session value's type. The fix restores intended typing; the pre-fix quoted output was never
specified, so this is a MINOR-appropriate correctness change (typed override methods are added so
any caller who genuinely wanted a string can pin it). All new tests run in `Test` scope without
Docker.

## Technical Context

**Language/Version**: Scala 2.13.18

**Primary Dependencies**: cats (`ReaderT`/`Eval` generator monad), json4s (`JValue`/`JsonDSL`/jackson), Gatling EL (`io.gatling.core.session.el`), pdi-jwt (`JwtAlgorithm`, `JwtHmacAlgorithm`, `JwtAsymmetricAlgorithm`), java.security (`KeyFactory`, key specs) — no new dependencies

**Storage**: N/A (no persistence in scope)

**Testing**: ScalaTest (`AnyWordSpec` + `Matchers`), seeded `GeneratorContext` for determinism, real Gatling `Session` (with fake `EventLoop`) for JWT claim resolution, JUnit 5 for facade delegation; no Testcontainers (JWT is the non-container `it`/`Test` layer)

**Target Platform**: JVM 17 (compile target); CI Temurin 21

**Project Type**: Published library (Maven Central) with a thin Java/Kotlin facade

**Performance Goals**: Generators are drawn per virtual user; the `long()` fix and combinator guards add no per-draw allocation. Claim auto-detection adds one type-dispatch per EL claim at token-generation time (not a feeder hot path). No performance regression target beyond "no new per-draw allocation".

**Constraints**: No new dependencies; no public API signature **removal**; Gatling stays `Provided`; coverage floor 65/60. FR-005 is a bug fix (#223) restoring intended type preservation — MINOR-appropriate. String session-claim output must stay byte-for-byte identical (only genuinely numeric/boolean values change, which is the fix).

**Scale/Scope**: 6 production files edited (`GeneratorInstances.scala`, `Syntax.scala`, `Faker.scala`, `ClaimsBuilder.scala`, `jwt.scala`, `JwtKeys.scala`) + 1 facade (`Jwt.java`); 1 new generator test suite + extensions to `JwtSpec`, `JwtKeysSpec`, `GeneratedFeederSpec`, plus a facade delegation test.

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | `long(-3_000_000_000L, 3_000_000_000L)` drawn 100k times with a seeded context | Unit/Functional | Assert every drawn value is within `[min, max]` inclusive (negative: zero values fall outside). Boundary: `long(0L, Long.MaxValue)` never returns negative; `long(Long.MinValue, Long.MaxValue)` produces both signs (unbounded preserved). Exact: a fixed seed yields a reproducible first value. |
| FR-002 | The whole `feeders/generators` public surface (numeric, string, char, collection, `~`/`**`/`separateBy`, derivation) | Unit/Functional | New suite asserts exact seeded values and boundaries for each generator family; e.g. `digitChar` only emits `0-9`, `alphaStringN(5)` has length 5, `oneOf(a,b,c)` only returns members. ≥1 boundary per family. Determinism: same seed → identical output. |
| FR-003 | `gStr ** 0`, `gStr ** -1`, and `gStr.repeat(0).separateBy(",")` | Unit/Functional | Assert `IllegalArgumentException` whose message names the invalid count (negative case). Positive: `** 3` yields the value repeated 3×; `repeat(3).separateBy("-")` yields three segments joined by `-` (exact string for a `const` generator). |
| FR-004 | 10k `Faker.it.codiceFiscale()` draws, day-code substring inspected | Unit/Functional | Assert every day code ∈ `01..31` ∪ `41..71`; assert none ∈ `32..40` (negative). Boundary: across the sample both a male code (≤31) and a female code (≥41) appear. Structure: total length stays 16. |
| FR-005 | `ClaimsBuilder().claimFromSession("uid","#{uid}")` resolved against a real `Session` with `uid` set to a `Long`, a `Boolean`, a `String`, and a numeric-looking `String`; plus typed-override calls | Unit/Functional | Assert resolved JSON: `Long` → `{"uid":42}` (number), `Boolean` → `true`, `String` → `"x"`, `"42"` → `"42"` (string, negative against retyping). Override: force-string on a `Long` → `"42"`; force-long on a `"42"` → `42`. Exact JSON compared. |
| FR-005 | `Jwt.claims().claimFromSessionLong("uid","#{uid}")` from Java facade | Facade Delegation | JUnit 5: facade-produced claim JSON equals the Scala-core result for the same inputs; confirms the facade delegates and the 6-arg constructor wiring is correct. No facade-only logic. |
| FR-006 | `jwt("HS256", asymmetricPrivateKey)` and `jwt("RS256", "secret")` token generation | Unit/Functional | Assert `IllegalArgumentException` naming the algorithm and the key kind on each mismatch (negative, replaces `ClassCastException`). Positive: matched `HS256`+secret and `RS256`+RSA key each produce a verifiable token (real crypto, no container). |
| FR-007 | `JwtKeys.rsaPrivateKeyFromResource` fed invalid Base64, truncated DER, and a wrong-algorithm key | Unit/Functional | Assert a contextual exception naming the algorithm and source with the underlying cause attached (negative; not a bare `InvalidKeySpecException`). Positive: a valid PEM still loads and signs. |
| FR-008 | `session.setJwt(gen,…)` and `setJwtAsBearer` where a claim EL references a missing session key | Unit/Functional | Assert `IllegalStateException` whose message carries the underlying resolution-failure detail (negative path, previously untested). Positive: a resolvable generator stores the token. |
| FR-009 | Every behavioral fix's invalid-input path | Unit/Functional | Cross-cut: each FR row above carries ≥1 negative/boundary assertion and ≥1 positive case; no new test pins a known-buggy behavior. |
| FR-010 | Public Scala/Java JWT and generator signatures; serialized output for String claims | Compile Guard + Facade Delegation | Compile-guard spec locks the public DSL/method signatures (new methods additive, none removed). A round-trip test asserts a String session claim serializes identically to the pre-change output (byte-for-byte), isolating the intentional numeric/boolean change. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — All logic lives in Scala (`ClaimsBuilder`, `jwt`, `JwtKeys`, generators, `Faker`). The Java facade `Jwt.claims()` returns the Scala `ClaimsBuilder`, so new typed methods are inherited with zero facade logic; only the constructor wiring (one extra empty-map argument) changes. No business logic added to the facade.
- [x] **II. Backward Compatibility (NON-NEGOTIABLE)** — **PASS (all changes are bug fixes or additive).**
  1. **FR-005 typing** — `claimFromSession` was meant to preserve the session value's type; the always-string output is the confirmed defect (#223), never documented or test-pinned. Correcting defective behavior to the intended one is a **bug fix**, not a behavioral redefinition → MINOR-appropriate (v1.21.0). String output (incl. numeric-looking strings) is unchanged. Typed override methods are additive. Changelog notes the fix so anyone who relied on the buggy quoted output can switch to `claimFromSessionString`.
  2. **`ClaimsBuilder` constructor arity** grows by one defaulted field (`forcedTypes`) — source-compatible for `ClaimsBuilder()`; the facade (which we own) updates its positional construction in the same change. Additive → MINOR.
  3. **Exception-type changes** (FR-003 `UnsupportedOperationException`→`IllegalArgumentException`; FR-006 `ClassCastException`→`IllegalArgumentException`; FR-007 wrapped key-parse exception; FR-004 invalid day-code removed) — all replace defective/invalid behavior; bug fixes, noted in changelog.
- [x] **III. Test Discipline** — Test Model above filled (real case + fitting layer + code-free sketch per FR), test-first, ≥1 negative/boundary per FR. Layers used: Unit/Functional (pure generators, real `Session`, real JWT crypto — no container, correct per TESTING.md JWT-is-non-container rule), Facade Delegation (JUnit 5), Compile Guard. No Gatling runtime mocked; ScalaMock not needed here (no leaf HTTP collaborator in scope). Coverage rises (generators 0 → covered); floor 65/60 preserved.
- [x] **IV. Small, Focused Changes** — No opportunistic refactors. One new internal `sealed trait ClaimType` and one new defaulted field justify FR-005's override capability; the generator suite is authorized by #205. No new dependencies. No changes outside the cited files.
- [ ] **V. Release Integrity** *(release PRs only)* — Not applicable to this feature PR. Ships in the v1.21.0 line (all changes are bug fixes / additive); standard `release/1.21.0` branch + `v1.21.0` tag at release time.

## Project Structure

### Documentation (this feature)

```text
specs/005-feeders-generators-jwt/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0: decision per FR
├── data-model.md        # Phase 1: modified type contracts (ClaimsBuilder, ClaimType, generators)
├── contracts/           # Phase 1: public API contracts
│   ├── jwt-claims.md     # claimFromSession auto-detect + typed overrides
│   └── generators.md     # long() bounds + combinator guards + codiceFiscale
├── quickstart.md        # Phase 1: validation guide
└── checklists/
    └── requirements.md  # Spec quality checklist (from /speckit-specify)
```

### Source Code (repository root)

```text
src/main/scala/org/galaxio/gatling/
├── feeders/generators/
│   ├── GeneratorInstances.scala   # FR-001: long() Long-range bounds
│   └── Syntax.scala               # FR-003: ** and separateBy reject n<=0
├── feeders/faker/
│   └── Faker.scala                # FR-004: codiceFiscale gender-modeled day code
└── utils/jwt/
    ├── ClaimsBuilder.scala        # FR-005: auto-detect + typed override + ClaimType
    ├── jwt.scala                  # FR-006: alg/key match validation in encode
    └── JwtKeys.scala              # FR-007: wrap key-parse failures with context

src/main/java/org/galaxio/gatling/javaapi/utils/
└── Jwt.java                       # FR-005: claims() 6-arg constructor wiring (facade stays thin)

src/test/scala/org/galaxio/gatling/
├── feeders/generators/
│   └── GeneratorsSpec.scala       # FR-002: NEW suite (numeric/string/char/collection/combinators/derivation)
├── feeders/faker/
│   └── GeneratedFeederSpec.scala  # FR-004: codiceFiscale day-code range assertions
└── utils/jwt/
    ├── JwtSpec.scala              # FR-005/006/008: claim typing, alg/key mismatch, setJwt failure paths
    └── JwtKeysSpec.scala          # FR-007: malformed-key contextual errors

src/test/java/org/galaxio/gatling/javaapi/utils/
└── JwtClaimsDelegationTest.java   # FR-005: NEW facade delegation (typed claim methods == Scala core)
```

**Structure Decision**: Single existing Scala library module plus its Java facade. No new sbt modules, no new dependencies. New code is confined to the three subsystems named in the milestone issues; tests extend existing specs where present and add one new generator suite and one facade delegation test.

## Complexity Tracking

> No constitution principle is bent — FR-005 is a bug fix (Principle II passes). The rows below
> record two design notes reviewers should be aware of; neither is a violation.

| Design note | Why Needed | Simpler Alternative Rejected Because |
|-------------|------------|-------------------------------------|
| **New field on public `ClaimsBuilder` case class** (`forcedTypes`, defaulted) | Typed overrides must remember the author's forced JSON type per claim without changing the existing `elClaims: Map[String,String]` field type. | Folding the type into `elClaims`' value type (`Map[String, ElClaim]`) was rejected: it changes an existing public field's type (worse compat) and complicates the facade's empty-map construction more than one extra defaulted map does. |
| **New internal `sealed trait ClaimType`** | Encodes the four forced JSON kinds (string/long/double/boolean) for overrides. | A bare `String`/`Int` tag was rejected as untyped and error-prone; the sealed trait is `private[jwt]`, no public surface. |
