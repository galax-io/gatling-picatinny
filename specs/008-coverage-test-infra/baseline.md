# Pre-Feature Baseline (T004)

**Date**: 2026-07-04 | **Command**: `sbt clean coverage test "IntegrationTest / test" coverageReport`

| Metric | Value |
|--------|-------|
| Statement coverage (benchmarks still IN denominator) | **75.77%** |
| Branch coverage (benchmarks still IN denominator) | **68.29%** |
| Enforced floors at baseline | 65 / 60 |
| Full-chain wall-clock (clean, unit + IT, Docker warm) | **42.9 s** (107.0 s user, 263% cpu) |
| Report | `target/scala-2.13/scoverage-report/` |

Notes:
- Benchmark sources confirmed present in the report (`overview.html`, `…feeders.faker.html`, `…redis.html`, `…templates.html` list `*Benchmark.scala`) — the denominator is inflated exactly as #210 states.
- Feeds T006 (post-exclusion ratchet target) and T029 (≤ ~25% wall-clock budget: post-feature chain should stay ≲ 54 s under like-for-like conditions).

## Post-feature measurement (T029, 2026-07-04)

| Metric | Baseline | Post-feature | Delta |
|--------|----------|--------------|-------|
| Cold coverage chain (like-for-like) | 42.9 s | **42.3 s** | ~0% (budget ≤ +25% — SemanticDB overhead invisible on this machine) |
| Statement coverage (benchmarks excluded) | 75.77% (w/ benchmarks) | **78.69%** | floors 75/66 pass |
| Branch coverage | 68.29% | **69.22%** | |
| Warm full gate chain (fmt+lint+compile+MiMa+tests+IT) | n/a | 17.0 s | |
