# Research: Discrete Date-Time Offset Generation (009)

Phase 0 output. All Technical Context unknowns resolved; no NEEDS CLARIFICATION remain.

## R1. API shape — overload `offset` vs alternatives (from-scratch analysis)

- **Decision**: Add an overload `Faker.date.offset(from: LocalDateTime, minOffset: Long, maxOffset: Long, unit: TemporalUnit = ChronoUnit.DAYS): Generator[LocalDateTime]` next to the existing `offset(LocalDate, minDays, maxDays)`.
- **Rationale**: (1) Java/Kotlin facade ergonomics — manual composition (`number.long(…).map(base.plus(_, unit))`) requires Scala function values, unusable as an API from Java/Kotlin, so a core method is the only way to give facade users a migration path; (2) precedent/symmetry — `offset(LocalDate, …)` is already public, refusing the `LocalDateTime` twin is inconsistent; (3) direct migration mental model — the legacy feeder thinks in "base ± delta", exactly what `offset` expresses.
- **Alternatives considered**:
  - *Do nothing / wontfix + doc recipe* (composition one-liner): cheapest and Scala-idiomatic, but abandons facade users and leaves the deprecation notice pointing at a replacement that cannot reproduce the behavior; rejected.
  - *`between(from, to, step: TemporalUnit)` overload* (random node of a discrete grid inside a range): conceptually elegant — continuous `between` becomes the `step=SECONDS` special case — but the migration mapping becomes indirect (caller computes bounds from deltas) and it grows a second `between` family; rejected.
  - *Per-unit methods* (`offsetDays`, `offsetHours`, …): method explosion; rejected.
  - *`Duration`/`Period` bounds*: loses the discrete-grid property that is the entire point; rejected.

## R2. Bounds semantics — inclusive, `Long`, uniform

- **Decision**: Both bounds inclusive; `Long` offsets; uniform distribution via the existing `Faker.number.long(min, max)` (inclusive by construction, `ThreadLocalRandom`-backed).
- **Rationale**: issue #294 explicitly requests inclusive bounds; `number.long` already implements inclusive uniform sampling with the `min == max` degenerate case handled; `Long` matches both `number.long` and the existing `offset(LocalDate, minDays: Long, maxDays: Long)`.
- **Alternatives considered**: `Int` offsets (legacy feeder used `Int` deltas) — needless narrowing vs the sibling API; exclusive upper (legacy semantics) — rejected by the issue itself, handled in the migration mapping instead (R5).

## R3. Validation — fail-fast at construction

- **Decision**: `require(minOffset <= maxOffset)` and `require(from.isSupported(unit))` at generator construction; overflow of the representable date-time range surfaces naturally as the JDK arithmetic error at `sample()`.
- **Rationale**: matches every sibling (`between`, `past`, `future`, `offset`, `range` all `require(...)` eagerly with messages naming the inputs); an unsupported unit (`ChronoUnit.FOREVER` is the only `ChronoUnit` `LocalDateTime` rejects) must not detonate mid-load-test; clamping bounds silently would hide caller bugs.
- **Alternatives considered**: lazy validation at first sample — violates FR-004 and sibling convention; whitelisting units — `Temporal.isSupported(unit)` is the JDK-canonical check, no list to maintain.

## R4. Default unit and facade shape

- **Decision**: Scala default parameter `unit = ChronoUnit.DAYS`; facade exposes two explicit overloads `dateOffset(from, min, max)` and `dateOffset(from, min, max, unit)` — the 3-arg one delegating to the Scala default.
- **Rationale**: whole days mirror the legacy feeder's default (`unit: TemporalUnit = ChronoUnit.DAYS`); Java cannot see Scala default arguments, and the existing facade already uses the explicit-overload pattern (`pan()`/`pan(bins)`, `phoneMobile(country)`/`phoneMobile(country, format)`).
- **Alternatives considered**: single 4-arg facade method only — forces Java callers to spell `ChronoUnit.DAYS` for the common case; `@varargs`-style tricks — noise.

## R5. Legacy parity semantics — CRITICAL planning discovery

- **Decision**: Document the migration as `RandomDateFeeder(name, P, N, pattern, from, unit, tz)` → `date.formatDateTime(date.offset(from, −N, P − 1, unit), pattern)` and back it with an executable doc-parity test.
- **Rationale**: the legacy path is `dateFrom.plus(randomValue(-negativeDelta, positiveDelta), unit)` and `RandomDataGenerators.randomValue(min, max)` is **max-exclusive** (its own scaladoc: "strictly less than max"; `docs/migration.md` example: `randomValue(1, 11)` yields 1..10). So the legacy offset set is `[−N, P−1]`, while the new generator is inclusive — the mapping absorbs the difference. Issue #294's "0…29 weeks" example (from a delta of 30) confirms the exclusive reading.
- **Also discovered**: the existing `docs/faker-api.md` migration example is wrong today — it maps `RandomDateFeeder("createdAt", 30, 0)` (a FUTURE 0..29-day discrete offset, datetime-based) to `Faker.date.past(days = 30)` (a PAST range, `LocalDate`-based). FR-009 replaces it with the correct `offset` mapping.
- **Alternatives considered**: making the new API exclusive-upper for drop-in numeric parity — contradicts the issue's explicit "bounds inclusive" and the sibling `offset(LocalDate)`/`number.long` semantics; rejected.

## R6. Timezone parameter of the legacy feeder

- **Decision**: no timezone parameter on the new generator; the migration note states that the legacy `timezone` only affected the *formatting* step (`atZone(tz).format(pattern)`), which the existing formatting combinators cover; callers needing zone-shifted output apply it in their format/map step.
- **Rationale**: the generator's value domain is `LocalDateTime` (no zone); replicating a formatting-only parameter into the generator would smuggle presentation concerns into value generation.

## R7. Versioning / compatibility

- **Decision**: purely additive change shipped in MINOR v1.25.0 (milestone 12); MiMa advisory vs `1.24.0` expected clean; no `mimaBinaryIssueFilters` entries.
- **Rationale**: constitution II classifies a new overload as additive (PATCH-trivial at minimum); it rides the already-open v1.25.0 minor. New public method appears in the facade too — also additive.
