# Contract: Feeders & Generators (FR-001, FR-003, FR-004)

Public DSL contract for the random data generators. Signatures unchanged; behavior corrected.

## `long(min: Long, max: Long): Generator[Long]` (FR-001)

- Returns a value within the inclusive range `[min, max]` for the **entire `Long` domain**.
- Bounds outside the `Int` range (e.g. `long(-3_000_000_000L, 3_000_000_000L)`) MUST be respected —
  they MUST NOT fall through to an unbounded draw.
- `long(Long.MinValue, Long.MaxValue)` spans the full range (intended unbounded behavior).
- Determinism preserved: a fixed-seed context yields a reproducible sequence.

| Call | Guarantee |
|------|-----------|
| `long(-3e9.toLong, 3e9.toLong)` | every value in `[-3e9, 3e9]` |
| `long(0L, Long.MaxValue)` | never negative |
| `long(Long.MinValue, Long.MaxValue)` | full-range, both signs appear |

## `**(n: Int)` and `repeat(n: Int).separateBy(sep: String)` (FR-003)

- `n >= 1` required. `n <= 0` MUST throw `IllegalArgumentException` naming the invalid count —
  never `UnsupportedOperationException: empty.reduceLeft`.
- `n == 1` returns the single underlying value (no separator for `separateBy`).
- `n >= 2` repeats / joins as before.

| Call (on a `const("x")` generator) | Result |
|------|--------|
| `gen ** 3` | `"xxx"` |
| `gen.repeat(3).separateBy("-")` | `"x-x-x"` |
| `gen ** 0`, `gen ** -1`, `gen.repeat(0).separateBy(",")` | `IllegalArgumentException` |

## `Faker.it.codiceFiscale(): Generator[String]` (FR-004)

- 16-character structure unchanged (`6 letters + 2 digits + 1 letter + 2 digits + 1 letter + 3 digits + 1 letter`).
- The day-code field (positions 9-10) ranges over `01..31` (male) ∪ `41..71` (female).
- `32..40` MUST never appear.
- Over a large sample both a male (`<= 31`) and a female (`>= 41`) day code appear.
