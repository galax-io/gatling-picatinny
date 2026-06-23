---
description: "Task list for Feeders, Generators & JWT Correctness (v1.21.0)"
---

# Tasks: Feeders, Generators & JWT Correctness

**Input**: Design documents from `specs/005-feeders-generators-jwt/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: INCLUDED — Constitution §III mandates test-first (red → green). Each behavioral fix has a failing test written before the production change.

**Organization**: Grouped by user story (US1–US7, priority order). Shared test files (`GeneratorsSpec.scala`, `JwtSpec.scala`) force sequential test-writing within their cluster; production files differ and parallelize.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete dependencies)
- **[Story]**: US1–US7 maps to spec.md user stories
- All paths are repository-relative.

## Path Conventions

- Scala main: `src/main/scala/org/galaxio/gatling/`
- Scala test: `src/test/scala/org/galaxio/gatling/`
- Java facade main: `src/main/java/org/galaxio/gatling/javaapi/`
- Java facade test: `src/test/java/org/galaxio/gatling/javaapi/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Make new test locations exist; confirm no build changes needed.

- [X] T001 Create new test source dirs `src/test/scala/org/galaxio/gatling/feeders/generators/` and `src/test/java/org/galaxio/gatling/javaapi/utils/`; confirm `build.sbt`/`project/` need NO change (scoverage already includes `feeders.generators`; `sbt-jupiter-interface` already runs Java tests).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared generator test scaffold used by US1, US2, and US6.

**⚠️ CRITICAL**: Generator-cluster stories (US1/US2/US6) depend on this.

- [X] T002 Create `GeneratorsSpec.scala` shell (ScalaTest `AnyWordSpec` + `Matchers`) with a fixed-seed `GeneratorContext` helper for deterministic draws, in `src/test/scala/org/galaxio/gatling/feeders/generators/GeneratorsSpec.scala`. Compiles, no assertions yet.

**Checkpoint**: Generator test harness ready; JWT/Faker stories have no foundational blocker.

---

## Phase 3: User Story 1 - Numeric generators honor declared bounds (Priority: P1) 🎯 MVP

**Goal**: `long()` respects the full `Long` range; out-of-`Int` bounds no longer fall through to unbounded.

**Independent Test**: Draw a large seeded sample from `long()` with out-of-`Int` bounds; every value within `[min, max]`; full-range call spans both signs.

- [X] T003 [US1] Add failing tests to `src/test/scala/org/galaxio/gatling/feeders/generators/GeneratorsSpec.scala`: `long(-3_000_000_000L, 3_000_000_000L)` all-in-range over a large seeded sample (negative: zero out-of-bounds); `long(0L, Long.MaxValue)` never negative; `long(Long.MinValue, Long.MaxValue)` produces both signs; fixed seed → reproducible first value.
- [X] T004 [US1] Fix `long(min, max)` to gate with `Long.MaxValue`/`Long.MinValue` instead of `Int.*` in `src/main/scala/org/galaxio/gatling/feeders/generators/GeneratorInstances.scala` (lines 24-31) → T003 green.

**Checkpoint**: `long()` correct and regression-guarded.

---

## Phase 4: User Story 2 - Generator package test coverage (Priority: P1)

**Goal**: First-ever suite covering the `feeders/generators` public surface.

**Independent Test**: `GeneratorsSpec` runs in isolation, asserting exact seeded values + boundaries for each generator family and combinator.

- [X] T005 [US2] Extend `GeneratorsSpec.scala` with numeric (`int`/`double`/`float`, bounded variants, `positiveInt`/`positiveLong`), string-length (`alphaStringN`/`numberStringN`/`alphanumericStringN`/`printableStringN`), and character-class (`alphaChar`/`lowerChar`/`upperChar`/`digitChar`/`alphanumericChar`/`printableChar`) cases — each with ≥1 exact-value and ≥1 boundary assertion.
- [X] T006 [US2] Extend `GeneratorsSpec.scala` with collections (`oneOf` ×3 overloads, `listOfN`, `randomList`), the `~` concatenation combinator, case-class derivation, and seeded-determinism (same seed → identical output) cases.

**Checkpoint**: Generators package covered; coverage floor protected.

---

## Phase 5: User Story 3 - JWT claims preserve numeric/boolean session values (Priority: P2)

**Goal**: `claimFromSession` infers JSON type from the genuine session value (bug fix #223); typed overrides force a kind.

**Independent Test**: Resolve a claim from a numeric/boolean/string session value; decoded payload has the matching JSON type; String (incl. `"42"`) stays a string; override forces the requested type.

- [X] T007 [US3] Add failing claim-typing tests to `src/test/scala/org/galaxio/gatling/utils/jwt/JwtSpec.scala`: `Long` → `{"uid":42}`, `Boolean` → `true`, `String "x"` → `"x"`, numeric-string `"42"` → `"42"` (negative against retyping); `claimFromSessionString` on a `Long` → `"42"`; `claimFromSessionLong` on `"42"` → `42`. Decode payload and compare exact JSON.
- [X] T008 [US3] In `src/main/scala/org/galaxio/gatling/utils/jwt/ClaimsBuilder.scala`: add `private[jwt] sealed trait ClaimType` (`AsString`/`AsLong`/`AsDouble`/`AsBoolean`), add defaulted field `forcedTypes: Map[String, ClaimType]`, change `resolve` to resolve EL as `Any` and map by type (auto-detect) or coerce per `forcedTypes`, and add `claimFromSessionString`/`Long`/`Double`/`Boolean(name, el)` → T007 green.
- [X] T009 [US3] Update `Jwt.claims()` in `src/main/java/org/galaxio/gatling/javaapi/utils/Jwt.java` to pass the additional empty `forcedTypes` map to the 6-arg `ClaimsBuilder` constructor (facade stays delegation-only).
- [X] T010 [US3] Add facade delegation test `src/test/java/org/galaxio/gatling/javaapi/utils/JwtClaimsDelegationTest.java` (JUnit 5): `Jwt.claims().claimFromSessionLong(...)` claim JSON equals the Scala-core result for the same session inputs (depends on T009).

**Checkpoint**: Numeric/boolean JWT claims correct from both Scala and Java; string output unchanged.

---

## Phase 6: User Story 4 - JWT fails clearly on algorithm/key mismatch (Priority: P3)

**Goal**: Mismatched algorithm/key throws `IllegalArgumentException`, not `ClassCastException`.

**Independent Test**: `HS*`+asymmetric-key and asymmetric-alg+secret each throw `IllegalArgumentException` naming both; matched pairs produce a verifiable token.

- [X] T011 [US4] Add failing tests to `src/test/scala/org/galaxio/gatling/utils/jwt/JwtSpec.scala`: `jwt("HS256", rsaPrivateKey)` and `jwt("RS256", "secret")` token generation each throw `IllegalArgumentException` naming algorithm + key kind (negative, not `ClassCastException`); matched `HS256`+secret and `RS256`+RSA key produce verifiable tokens.
- [X] T012 [US4] Replace `algorithm.asInstanceOf[JwtAsymmetricAlgorithm]` with an explicit `JwtHmacAlgorithm`/`JwtAsymmetricAlgorithm` match-and-validate in `encode` in `src/main/scala/org/galaxio/gatling/utils/jwt/jwt.scala` (lines 108-112) → T011 green.

**Checkpoint**: Algorithm/key mismatch is a clear, typed error.

---

## Phase 7: User Story 5 - JWT key loading & generation report actionable errors (Priority: P3)

**Goal**: Malformed keys throw contextual errors; `setJwt` failure path is pinned.

**Independent Test**: Invalid Base64 / truncated DER → contextual `IllegalArgumentException` with cause; unresolvable claim EL via `setJwt` → `IllegalStateException` with detail.

- [X] T013 [P] [US5] Add malformed-key PEM fixtures (invalid Base64, truncated DER, wrong-algorithm key) under `src/test/resources/keys/`.
- [X] T014 [US5] Add failing tests to `src/test/scala/org/galaxio/gatling/utils/jwt/JwtKeysSpec.scala`: each malformed fixture loaded via `JwtKeys.rsaPrivateKeyFromResource`/`ecPrivateKeyFromResource` throws `IllegalArgumentException` naming algorithm + stage with cause attached (negative); a valid PEM still loads (positive).
- [X] T015 [US5] Wrap `privateKeyFromPem`/`publicKeyFromPem` bodies in a contextual `try`/`catch` (algorithm + stage + cause) in `src/main/scala/org/galaxio/gatling/utils/jwt/JwtKeys.scala` (lines 59-77) → T014 green.
- [X] T016 [US5] Add tests confirming existing `IllegalStateException` throw contract for `setJwt`/`setJwtAsBearer` with unresolvable EL (production code already correct — test-coverage only, no fix needed) in `src/test/scala/org/galaxio/gatling/utils/jwt/JwtSpec.scala`: assert `IllegalStateException` carrying the underlying `Failure` detail for a claim EL referencing a missing session key (FR-008 is test-only; existing throw at `jwt.scala:67,77` already satisfies — confirm green).

**Checkpoint**: Key-load and generation failures are actionable and pinned.

---

## Phase 8: User Story 6 - Generator combinators reject invalid counts (Priority: P4)

**Goal**: `**`/`separateBy` reject `n <= 0` loudly.

**Independent Test**: `** 0` / `** -1` / `repeat(0).separateBy(",")` throw `IllegalArgumentException`; valid counts produce the exact combined string.

- [X] T017 [US6] Add failing tests to `src/test/scala/org/galaxio/gatling/feeders/generators/GeneratorsSpec.scala`: `** 0`, `** -1`, `repeat(0).separateBy(",")` throw `IllegalArgumentException` naming the count (negative); `** 3` → `"xxx"` and `repeat(3).separateBy("-")` → `"x-x-x"` for a `const("x")` generator (exact).
- [X] T018 [US6] Add `require(n >= 1, ...)` guards to `**` (L46), `repeat` (L49), and `SeparatorStep.separateBy` (L24-25) in `src/main/scala/org/galaxio/gatling/feeders/generators/Syntax.scala` → T017 green.

**Checkpoint**: Combinators fail loud on bad counts.

---

## Phase 9: User Story 7 - codiceFiscale emits valid day codes (Priority: P4)

**Goal**: Day code is gender-modeled (`01..31` / `41..71`); `32..40` never appears.

**Independent Test**: Over a large sample, every day code ∈ `01..31` ∪ `41..71`, none in `32..40`, both halves appear, length stays 16.

- [X] T019 [US7] Add failing tests to `src/test/scala/org/galaxio/gatling/feeders/faker/GeneratedFeederSpec.scala`: 10k `Faker.it.codiceFiscale()` draws — every day code ∈ `01..31` ∪ `41..71` (positive), none ∈ `32..40` (negative), both a `<=31` and a `>=41` code appear, total length 16.
- [X] T020 [US7] Replace `number.int(1, 71)` with a gender-modeled day (`01..31` male / day+40 `41..71` female), keeping `f"%02d"` and layout, in `src/main/scala/org/galaxio/gatling/feeders/faker/Faker.scala` (line 577) → T019 green.

**Checkpoint**: codiceFiscale day codes always valid.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: FR-009/FR-010 guards, docs, coverage, release gate.

- [X] T021 [P] FR-010 byte-stability: add a test asserting a `String` session claim serializes byte-for-byte identical to the pre-change quoted output, in `src/test/scala/org/galaxio/gatling/utils/jwt/JwtSpec.scala`.
- [X] T022 [P] FR-010 compile guard: lock the new public generator/JWT method signatures (additive, none removed) — extend the existing compile-guard spec or add a minimal one under `src/test/scala/...` (target: `src/test/scala/org/galaxio/gatling/utils/jwt/JwtApiCompileGuardSpec.scala`).
- [X] T023 [P] Update scaladoc on `ClaimsBuilder.claimFromSession` and the new `claimFromSession*` overrides documenting JSON-type inference + override behavior, in `src/main/scala/org/galaxio/gatling/utils/jwt/ClaimsBuilder.scala`.
- [X] T024 Run `sbt scalafmtAll scalafmtSbt` (format) then `sbt scalafmtCheckAll scalafmtSbtCheck compile test` (full unit gate) — all green. FR-009 check: confirm each behavioral fix (T003/T005-T006/T011/T014/T017/T019) carries ≥1 negative/boundary AND ≥1 positive assertion.
- [X] T025 Verify coverage ≥ 65% stmt / 60% branch (`sbt coverage test coverageReport`); confirm `feeders.generators` is now covered.
- [X] T026 Run [quickstart.md](quickstart.md) scenarios 1-6 and confirm expected outcomes; ensure the conventional-commit message records the #223 fix + `claimFromSessionString` migration note for git-cliff release notes.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (P1)**: none — start immediately.
- **Foundational (P2)**: after Setup. Blocks US1/US2/US6 (generator cluster) only.
- **User Stories (P3+)**: JWT/Faker stories (US3/US4/US5/US7) depend only on Setup; generator stories (US1/US2/US6) depend on Foundational.
- **Polish (P10)**: after all targeted stories complete.

### User Story Dependencies

- **US1, US2, US6** share `GeneratorsSpec.scala` (created in T002) → their test-writing tasks (T003, T005/T006, T017) are sequential, not parallel. Production fixes (T004, T018) touch different files.
- **US3, US4, US5** share `JwtSpec.scala` → test-writing tasks (T007, T011, T016) sequential. Production files differ (`ClaimsBuilder.scala`, `jwt.scala`, `JwtKeys.scala`).
- **US7** is fully independent (`Faker.scala` + `GeneratedFeederSpec.scala`).
- No story depends on another story's production code.

### Within Each User Story

- Failing test → production fix → green (TDD).
- Commit after each story's checkpoint, build green.

### Parallel Opportunities

- Production fixes across different files run in parallel once their tests are written: **T004 (GeneratorInstances), T018 (Syntax), T020 (Faker), T012 (jwt), T015 (JwtKeys), T008 (ClaimsBuilder)** — all distinct files.
- **T013** (key fixtures) is [P] with any non-resource task.
- Polish **T021/T022/T023** are [P] (different files).
- Whole stories US3, US4, US5, US7 can proceed in parallel by different developers after Setup.

---

## Parallel Example: production fixes after tests are red

```bash
# Each touches a distinct file — safe to run together once their failing tests exist:
Task: "Fix long() bounds in GeneratorInstances.scala (T004)"
Task: "Guard ** / separateBy in Syntax.scala (T018)"
Task: "Gender-model codiceFiscale day in Faker.scala (T020)"
Task: "Alg/key validation in jwt.scala (T012)"
Task: "Wrap key parse in JwtKeys.scala (T015)"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup → Phase 2 Foundational.
2. Phase 3 US1 (`long()` fix) — the highest-severity defect. **STOP & VALIDATE**.
3. Phase 4 US2 (suite) — locks the generators against regression.

### Incremental Delivery

US1 → US2 (P1 generators) → US3 (P2 JWT typing, the #223 fix) → US4/US5 (P3 JWT robustness) → US6/US7 (P4 polish defects) → Polish. Each story green and committable on its own.

### Test-First (mandatory)

Every behavioral story writes its failing test before the fix; FR-008 (T016) is test-only (production already throws). Verify red before green.

---

## Notes

- [P] = different files, no incomplete dependency.
- Shared spec files (`GeneratorsSpec.scala`, `JwtSpec.scala`) serialize their test tasks — do not parallelize edits to the same file.
- No new dependencies; Gatling stays `Provided`; no Docker for any task here.
- Commit semantically (conventional commits), build green after each.
