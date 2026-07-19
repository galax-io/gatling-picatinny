# Data Model: Discrete Date-Time Offset Generation (009)

## Discrete Offset Generator (new)

- **Fields**: base date-time (`LocalDateTime`), inclusive lower offset (`Long`), inclusive upper offset (`Long`), time unit (`TemporalUnit`, default whole days).
- **Validation rules** (at construction, FR-004): lower ≤ upper; unit supported by date-time arithmetic (`base.isSupported(unit)`; rejects `FOREVER`). Overflow of the representable range surfaces at generation as the JDK arithmetic error (not clamped).
- **Value domain** (FR-001/002/003/005): `{ base + k·unit | k whole, lower ≤ k ≤ upper }`, k drawn uniformly; both endpoints producible; equal bounds → single deterministic value; range may span zero.
- **Relationships**: composes with the existing formatting combinators (`formatDateTime`) for string output (FR-007); exposed 1:1 through the Java/Kotlin facade by delegation (FR-006).

## Legacy Random-Date Feeder Configuration (existing, deprecated)

- **Fields**: param name, positive delta P (`Int`, **exclusive** upper), negative delta N (`Int`, inclusive lower), date pattern, base date-time, unit, timezone (formatting-only).
- **Relationships**: migration mapping (FR-009, research R5/R6):

| Legacy | Replacement |
|--------|-------------|
| offset set `[−N, P−1]` in `unit` from base | `offset(base, −N, P − 1, unit)` |
| `pattern` + `timezone` formatting | existing formatting combinator on the generator's output |

- **State transitions**: none (stateless generation); the legacy feeder itself is untouched except documentation (FR-008).

## Milestone Ledger

- Issue [#294](https://github.com/galax-io/gatling-picatinny/issues/294) ← milestone 12 (v1.25.0 — Perf: Faker), assigned 2026-07-19; feature PR carries the same milestone and closes #294; prerequisite for milestone 27 (v2.0.0 deprecated-API removal).
