# Curated Set

Cap: 25 entries. Importance: critical | high | medium | low.
When full, evict per `lowest-importance-first` and log the eviction in
observations.md. Findings are ≤ 2 sentences; details live behind the evidence link.

| ID | Importance | Finding | Source candidate | Evidence |
|----|------------|---------|------------------|----------|
| K001 | critical | Branch predates PR #300 (merged to main mid-session): wide-range fix + its regression test are absent here, and #300 rewrote `nextLongInclusive` (full-range branch first, both-bounds retry). Rebase onto origin/main is a hard prerequisite for #304 work. | C001 | E010 |
| K002 | critical | Issue #130 premise refuted: `s"$prefix$key"` already compiles to one `makeConcatWithConstants` indy — `prefix + key` is bytecode-identical, there is no interpolation machinery to remove. | C002 | E002 |
| K003 | critical | #304 Long-span classification proven equivalent to BigInt reference: 2,000,078 pairs, 0 mismatches, bounds exact. | C003 | E001 |
| K004 | high | Email fix must keep `String.toLowerCase` (special casing: U+0130 → 2 codepoints; tr-locale 'I' → ı); only the regex chain is replaceable — per-char lowering breaks parity. | C004 | E003 |
| K005 | high | Facade is pure delegation for every touched generator — no second normalization site to fix. | C005 | E004 |
| K006 | high | CPF digit-Vector claim (#125) stale: stella lib generates; residual = Pattern compile per formatted call. TIN half of #125 still real. | C006 | E006 |
| K007 | high | `selectKeys` keySet already hoisted; only per-record `view.filterKeys(...).toMap` remains (#129 half-superseded). | C007 | E008 |
| K008 | medium | `nextLongInclusive` feeds long/positiveLong/negativeLong + both date.offset overloads — one change, four families. | C008 | E005 |
| K009 | medium | Gates all real: JmhPlugin + FakerBenchmark, coverage 75/66 with benchmark exclusion, MiMa baseline 1.23.0. | C009 | E011 |
| K010 | medium | Rebase also picks up scalafmt-core 3.11.4 + sbt-scalafmt 2.6.2 bumps (4aa5592, 54c58da) — reformat drift possible; run new formatter before committing. | C001 | E010 |
| K011 | high | ipv6 rewrite must NOT call `RandomDataGenerators.hexString`: legacy pre-Faker surface, shared `scala.util.Random` (contention) + Iterator-chain allocations per call; generate 32 hex chars inline via ThreadLocalRandom (user directive). Not `@deprecated` itself — only its ID generators are. | C013 | E014 |
