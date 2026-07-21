# Harness Budget Ledger

## Mission
1. (not set — pass a question to /speckit.harness.init or /speckit.harness.explore)

## Budget
| Resource | Budget | Spent | Remaining |
|----------|-------:|------:|----------:|
| searches | 30 | 8 | 22 |
| inspections | 40 | 9 | 31 |
| verifications | 20 | 14 | 6 |

Context render cap: 4000 tokens per iteration.

## Stop conditions
- Budget exhausted in any resource required for the next action.
- Marginal gain: 3 consecutive actions produced no new curated evidence.
- Mission answered AND every `critical` claim has a `verified` record.

## Action log
| # | Action | Target | Cost | New evidence? |
|---|--------|--------|------|---------------|
| 1 | jshell equivalence sweep (V001) | #304 Long-span vs BigInt reference | 1 verification | E001 |
| 2 | jshell locale checks (V003) | toLowerCase special casing | 1 verification | E003 |
| 3 | grep facade delegates (V004) | javaapi/FakerApi.scala | 1 search + 1 verification | E004 |
| 4 | re-read offset/long routing (V005) | Faker.scala:44-47,86-89,360-394 | 1 inspection + 1 verification | E005 |
| 5 | re-read CPF + JDK javadoc (V006) | Faker.scala:628-632 | 1 inspection + 1 verification | E006 |
| 6 | find Faker.java (V007) | src + examples | 1 search + 1 verification | E007 |
| 7 | re-read selectKeys (V008) | Syntax.scala:96-99 | 1 inspection + 1 verification | E008 |
| 8 | test lock withDefaults (V009) | GeneratedFeederSpec:213-222 | 1 inspection + 1 verification | E009 |
| 9 | grep #300 test name (→ refute path) | GeneratedFeederSpec | 1 search | — |
| 10 | git ancestry + show 23f2243 (V010) | origin/main vs HEAD | 2 searches + 1 verification | E010 (refuted) |
| 11 | build.sbt gates (V011, V012) | build.sbt:9-15,41-45,77,86 | 1 inspection + 2 verifications | E011, E012 |
| 12 | javap FeederOps bytecode (V002) | Syntax$FeederOps$.class | 2 searches + 2 inspections + 1 verification | E002 (refuted) |
| 13 | re-read assembly sites (V013) | Faker.scala:805,:234,:709-714 | 1 inspection + 1 verification | E013 |
| 14 | read RandomDataGenerators impl (V014, user challenge) | RandomDataGenerators.scala:41-71,163-184 | 1 search + 1 inspection + 1 verification | E014 (refuted plan R4 detail) |
