# Allocation Benchmarks: 010-faker-syntax-perf (FR-007 / SC-002)

Harness: JMH via `sbt Jmh/run`, flags `-bm avgt -prof gc -f 1 -wi 3 -i 5 -w 1 -r 1`,
regex `.*FakerBenchmark.faker(Email|LoremWords|NarrowLong|Ipv6).*`.
Metric of record: `gc.alloc.rate.norm` (B/op). Machine: local dev (Darwin, JDK 17 toolchain), same host for BEFORE/AFTER.

## BEFORE — commit 69553f0 (post-rebase base 23f2243, pre-optimization)

| Benchmark | avgt (us/op) | gc.alloc.rate.norm (B/op) |
|-----------|-------------:|--------------------------:|
| fakerEmailSample | 0.691 ± 0.067 | 2848.178 ± 0.051 |
| fakerIpv6Sample | 0.850 ± 0.037 | 3904.000 ± 0.001 |
| fakerLoremWordsSample (50 words) | 0.933 ± 0.169 | 2360.012 ± 0.053 |
| fakerNarrowLongSample | 0.015 ± 0.003 | 167.997 ± 0.001 |

Raw output: session scratchpad `jmh-before.txt` (full JMH log incl. gc.count/gc.time).

## AFTER — commits c0b4636/b305259/ac57994/dfd05a1/4ab0559 (#139 #124 #125 #123 #304)

| Benchmark | avgt (us/op) | gc.alloc.rate.norm (B/op) | B/op vs BEFORE |
|-----------|-------------:|--------------------------:|---------------:|
| fakerEmailSample | 0.464 ± 0.011 | 1725.406 ± 0.031 | −39.4% |
| fakerIpv6Sample | 0.101 ± 0.007 | 136.000 ± 0.001 | **−96.5%** (8.4× faster) |
| fakerLoremWordsSample (50 words) | 0.649 ± 0.009 | 800.960 ± 0.062 | −66.1% |
| fakerNarrowLongSample | 0.002 ± 0.001 | ≈ 10⁻⁶ (zero) | **−100%** (7.5× faster) |

Every benchmarked path strictly lower B/op (SC-002 ✓). Narrow-long allocates
nothing at all — the static no-BigInt guarantee (T015) plus escape analysis
eliminating the boxed sample. Raw output: session scratchpad `jmh-after.txt`.
Transform changes (#129/#131, Syntax.scala) are not JMH-covered by design
(SC-002 scope note in spec.md); their evidence is the hoisting property + parity tests.
