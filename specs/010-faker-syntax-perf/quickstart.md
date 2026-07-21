# Quickstart: Validating Faker & Feeder-Transform Allocation Reduction

**Feature**: `010-faker-syntax-perf` | **Date**: 2026-07-21

## Prerequisites

- JDK 17+, sbt (repo standard); no Docker needed — everything here is layer 1 + JMH.

## 1. Full parity gate (the core promise)

```bash
sbt scalafmtCheckAll scalafmtSbtCheck compile test
```

Expected: green with ZERO edits to pre-existing test cases. Pay attention to:

- `GeneratedFeederSpec` — email (:488+), ipv6 (:537+), lorem (:1264+ incl. `words(0)` rejection), TIN (:1162), CPF/CNPJ stella validation, long bounds incl. "handle Long.MaxValue as an inclusive upper bound" and the #300 wide-range test "correctly generate longs when range is wider than Long.MaxValue" (present after the foundational rebase onto origin/main — V010), transform cases (:194-231).
- New parity tests added by this feature (per-issue, see [contracts/parity-and-gates.md](contracts/parity-and-gates.md)).

## 2. Lint + binary surface

```bash
sbt "scalafixAll --check" mimaFindBinaryIssues
```

Expected: both clean — no public signature drift (frozen surface listed in the contract).

## 3. Allocation evidence (FR-007)

Capture BEFORE numbers on the pre-change commit, AFTER on the change:

```bash
sbt 'Jmh/run -prof gc -f 1 -wi 3 -i 5 .*FakerBenchmark.*'
```

Expected outcome: `gc.alloc.rate.norm` (B/op) strictly lower for the lorem-words,
ipv6, email, and narrow-range long benchmarks. Zero-`BigInt` on the narrow-long path
is established by the static property (T015: no `BigInt` anywhere in the method) and
corroborated by the B/op drop — `-prof gc` reports aggregate bytes, not per-class
allocation. Record both tables in the PR description.

## 4. Targeted spot-checks (optional, fast)

```bash
sbt 'testOnly org.galaxio.gatling.feeders.faker.GeneratedFeederSpec'
```

Manual REPL sanity (`sbt console`): sample `Faker.internet.ipv6()`,
`Faker.lorem.words(5)`, `Faker.number.long(Long.MinValue, Long.MaxValue)` — formats
per [data-model.md](data-model.md) frozen shapes.

## Success = all four sections pass

Maps to SC-001 (step 1), SC-005 (step 2), SC-002 (step 3), SC-003 (steps 1+4).
SC-004 (issues closed) is release hygiene at merge time.
