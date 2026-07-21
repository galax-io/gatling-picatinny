# Candidate Pool

Dedup key: source + topic. One row per candidate, append-only IDs (C001, C002…).
Status: `new` → `inspected` → `curated:<E-id>` | `discarded(<reason>)`.

| ID | Source | Type | Topic | Status | First seen |
|----|--------|------|-------|--------|------------|
| C001 | origin/main 23f2243 + git ancestry | git | PR #300 fix/test vs branch base | curated:E010 | 2026-07-21 |
| C002 | Syntax$FeederOps$.class bytecode | bytecode | s-interpolator lowering (#130) | curated:E002 | 2026-07-21 |
| C003 | jshell verify.jsh V1 | executable-check | #304 classification equivalence | curated:E001 | 2026-07-21 |
| C004 | jshell verify.jsh V3 | executable-check | toLowerCase special casing / locale | curated:E003 | 2026-07-21 |
| C005 | javaapi/FakerApi.scala | code | facade delegation purity | curated:E004 | 2026-07-21 |
| C006 | Faker.scala:628-632 + JDK javadoc | code+doc | CPF stella / replaceFirst Pattern | curated:E006 | 2026-07-21 |
| C007 | faker/Syntax.scala:96-99 | code | selectKeys hoist state | curated:E008 | 2026-07-21 |
| C008 | Faker.scala:44-47,86-89,370-384 | code | nextLongInclusive substrate fan-out | curated:E005 | 2026-07-21 |
| C009 | build.sbt:9-15,41-45,77,86 | build | JMH/coverage/MiMa gates | curated:E011 | 2026-07-21 |
| C010 | find src examples Faker.java | search | stale Java path (#123) | inspected | 2026-07-21 |
| C011 | GeneratedFeederSpec:218-221 | test | withDefaults right-bias lock | inspected | 2026-07-21 |
| C012 | Faker.scala:805,:234,:709-714 | code | string-assembly sites | inspected | 2026-07-21 |
| C013 | utils/RandomDataGenerators.scala | code | hexString legacy impl (shared Random, Iterator chain) | curated:E014 | 2026-07-21 |
