# Public API Contract: Faker date — discrete LocalDateTime offset (009)

The library's external interface is its published Scala API + Java/Kotlin facade. This contract is the compatibility-sensitive surface added by feature 009. Everything here is ADDITIVE; nothing existing changes.

## Scala core (`org.galaxio.gatling.feeders.faker.Faker.date`)

```scala
def offset(
    from: LocalDateTime,
    minOffset: Long,
    maxOffset: Long,
    unit: TemporalUnit = ChronoUnit.DAYS,
): Generator[LocalDateTime]
```

### Semantics

1. Each `sample()` returns `from.plus(k, unit)` with `k` drawn **uniformly** from the **inclusive** range `[minOffset, maxOffset]`.
2. Values lie exactly on the whole-`unit` grid anchored at `from` — no sub-unit remainder, ever (contrast: `between(from, to)` returns any whole second in range).
3. `minOffset == maxOffset` → deterministic single value.
4. Negative offsets and zero-spanning ranges are first-class.
5. Generation is lazy and stateless (standard `Generator` contract); cost per sample: one RNG draw + one date-arithmetic op.

### Errors

| Condition | When | Error |
|-----------|------|-------|
| `minOffset > maxOffset` | construction | `IllegalArgumentException` naming both values |
| `unit` unsupported by `LocalDateTime` (only `ChronoUnit.FOREVER`) | construction | `IllegalArgumentException` naming the unit |
| result outside representable date-time range | `sample()` | JDK `DateTimeException` (not clamped) |

## Java/Kotlin facade (`org.galaxio.gatling.javaapi.FakerApi`)

```scala
def dateOffset(from: LocalDateTime, minOffset: Long, maxOffset: Long): Generator[LocalDateTime]                     // unit = whole days
def dateOffset(from: LocalDateTime, minOffset: Long, maxOffset: Long, unit: TemporalUnit): Generator[LocalDateTime]
```

Pure delegation to the Scala core (constitution I); identical semantics and errors; the 3-arg overload is the whole-days default (Java cannot see Scala default arguments).

## Documented migration contract (FR-009)

`RandomDateFeeder(name, P, N, pattern, from, unit, tz)` ≡ feeder over
`Faker.date.formatDateTime(Faker.date.offset(from, −N, P − 1, unit), pattern)`
— upper bound `P − 1` because the legacy helper's upper delta is exclusive; `tz` affected only legacy formatting (research R5/R6). Backed by an executable doc-parity test.

## Compatibility

- Additive only: MiMa vs `1.24.0` reports zero incompatibilities; no `mimaBinaryIssueFilters`.
- Existing `between`/`offset(LocalDate)`/`past`/`future`/`range` signatures and behavior untouched.
- Ships in MINOR v1.25.0.
