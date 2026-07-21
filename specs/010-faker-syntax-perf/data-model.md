# Data Model: Faker & Feeder-Transform Hot-Path Allocation Reduction

**Feature**: `010-faker-syntax-perf` | **Date**: 2026-07-21

No persistent data, no serialized formats, no new entities. The feature reshapes the
*runtime construction* of existing values without changing any observable shape.
This file records the value shapes that act as parity contracts.

## Frozen output shapes (parity invariants)

| Producer | Shape (unchanged) | Invariants |
|----------|-------------------|------------|
| `lorem.words(count)` | single string, `count` catalog words joined by one space | words ∈ `FakerData.loremWords`; `count > 0` enforced at construction (0 rejected) |
| `internet.ipv6()` | 8 groups of 4 hex chars joined by `:` | `split(":").length == 8`; charset = lowercase `0-9a-f` (explicit alphabet; no longer via legacy `RandomDataGenerators`) |
| `br.cpf(formatted)` | 11 digits; formatted: `ddd.ddd.ddd-dd` | stella-valid mod-11 check digits in both forms |
| `de.steueridentifikationsnummer()` | 11 digits | first digit 1–9, remaining ten 0–9 |
| `person.companyEmailName()` / `internet.email(...)` | normalized local part (+ `.suffix@domain` for email) | lowercase; runs of non-`[a-z0-9]` collapsed to single `.`; no leading/trailing `.`; empty local part → `user` (email path) |
| `number.long(min, max)` | uniform Long in inclusive `[min, max]` | both endpoints producible; narrow/wide strategy boundary at mathematical width = `Long.MaxValue` (exclusive) — must not shift |
| `selectKeys(keys…)` | record ∩ selected keys | map equality with today's output; absent keys silently absent |
| `prefixKeys(p)` / `suffixKeys(s)` | same values, every key renamed `p + k` / `k + s` | map equality; empty prefix/suffix = identity on key content |
| `withDefaults(d…)` | `defaults ++ record` | record value wins on collision; duplicate default keys last-wins (varargs order) |

## Internal state transitions

| Site | Before | After | Observable delta |
|------|--------|-------|------------------|
| Generator sampling (lorem/ipv6/TIN) | per-call intermediate collection → join | per-call single builder → result | none (same RNG draw order) |
| CPF `formatted` branch | per-call `Pattern` compile | construction-time compiled `Pattern`, per-call match only | none |
| Email normalization | per-call `Pattern` + intermediates ×2 sites | shared private helper, lowered string + builder | none (helper output ≡ old chain, property-tested) |
| `nextLongInclusive` classification | 3+ `BigInt` per call | pure `Long` arithmetic, zero allocation | none (equivalence proof, research R2) |
| Transform closures | static config rebuilt per record | static config captured at construction (closure `val`) | none (map equality) |

## Relationships

- `nextLongInclusive` is the shared substrate of `long`, `positiveLong`,
  `negativeLong`, and `date.offset` (feature 009) — one change, four generator
  families affected; parity tests exercise the substrate directly plus one derived
  generator.
- Email helper serves two public call paths (`companyEmailName`, both `email`
  overloads); single shared implementation removes the current duplication.
