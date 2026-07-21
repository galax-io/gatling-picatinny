# Contract: Behavior Parity & Build Gates

**Feature**: `010-faker-syntax-perf` | **Date**: 2026-07-21

This feature exposes NO new interface. The contract is the frozen existing surface.

## Public surface freeze (FR-005 / SC-005)

Touched files may not change any public signature. Methods whose *bodies* change:

| Public member | Signature (frozen) |
|---------------|--------------------|
| `Faker.lorem.words` | `(count: Int): Generator[String]` |
| `Faker.internet.ipv6` | `(): Generator[String]` |
| `Faker.br.cpf` | `(formatted: Boolean = false): Generator[String]` |
| `Faker.de.steueridentifikationsnummer` | `(): Generator[String]` |
| `Faker.person.companyEmailName` | `(): Generator[String]` |
| `Faker.internet.email` | `(domain: String = "example.com")` / `(name: String, domain: String)` |
| `Faker.number.long` (+ `positiveLong`, `negativeLong`) | `(min: Long, max: Long): Generator[Long]` |
| `FeederOps.selectKeys` | `(keys: String*): Feeder[Any]` |
| `FeederOps.prefixKeys` / `suffixKeys` | `(prefix: String)` / `(suffix: String): Feeder[Any]` |
| `FeederOps.withDefaults` | `(defaults: (String, Any)*): Feeder[Any]` |

`private` helpers (`nextLongInclusive`, `emailFromName`, new normalization helper,
compiled patterns) are exempt from compatibility rules (Constitution II).
Java/Kotlin facade: untouched — it delegates to these methods and sees no difference.

## Gates (all must pass per commit)

| Gate | Command | Pass condition |
|------|---------|----------------|
| Format | `sbt scalafmtCheckAll scalafmtSbtCheck` | clean |
| Lint | `sbt "scalafixAll --check"` | clean |
| Compile + unit | `sbt compile test` | green, including all pre-existing `GeneratedFeederSpec` cases unchanged |
| Coverage | scoverage floor | statement ≥75 / branch ≥66 (benchmark sources excluded) |
| Binary compat | `sbt mimaFindBinaryIssues` | zero new findings |
| Benchmark evidence (feature-level, FR-007) | `sbt 'Jmh/run -prof gc -f 1 -wi 3 -i 5 .*FakerBenchmark.*'` before vs after (JMH regex is case-sensitive; method names are camelCase — match the whole class) | `gc.alloc.rate.norm` strictly lower on benchmarked paths (lorem, ipv6, narrow-long, email); numbers recorded in PR description |

## Per-issue regression contract (FR-006)

Every issue commit carries its parity test(s) in `GeneratedFeederSpec` (layer 1),
each with ≥1 negative/boundary case, as sketched in the plan's Test Model table.
Issue mapping: #123 lorem, #124 ipv6, #125 CPF+TIN, #139 email, #304 long substrate,
#129 selectKeys, #130 prefix/suffix, #131 withDefaults.
