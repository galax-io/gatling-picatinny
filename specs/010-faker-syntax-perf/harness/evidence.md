# Evidence Links

Pointers only. An entry records WHERE proof lives, not the proof itself.
Excerpts are capped at 25 words. IDs match curated.md (E001, E002…).

## E001
- Claim: Long-span narrow/wide classification equivalent to BigInt reference on all inputs.
- Source: scratchpad `verify.jsh` (jshell, session 2026-07-21), re-runnable anywhere
- Locator: V1 block — corners × corners + 2M seeded-random pairs vs BigInteger
- Excerpt: "V1 pairs checked=2000078 mismatches=0"
- Supports: research.md R2 / plan.md FR-003 row / spec FR-003

## E002
- Claim: s-interpolator already lowers to indy string concat; no StringBuilder machinery to remove.
- Source: `.bloop/.../org/galaxio/gatling/feeders/faker/Syntax$FeederOps$.class` via `javap -p -c`
- Locator: method `$anonfun$prefixKeys$2`, instruction 30
- Excerpt: "invokedynamic makeConcatWithConstants:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String"
- Supports: refutes research.md R5 (#130 leg) / issue #130 premise

## E003
- Claim: String vs Character lowercase diverge (special casing + locale).
- Source: scratchpad `verify.jsh` V3 block (jshell)
- Locator: U+0130 and tr-locale 'I' checks
- Excerpt: "U+0130 len=2 codepoints=69,307; Character→69; tr 'I'→131"
- Supports: research.md R3 / plan FR-008 row

## E004
- Claim: facade is pure delegation for all touched generators.
- Source: src/main/java/org/galaxio/gatling/javaapi/FakerApi.scala:63-69,156-158
- Locator: `email`/`ipv6`/`lorem` one-line delegates
- Excerpt: "= Faker.lorem.words(count) … = Faker.internet.ipv6()"
- Supports: plan Constitution I / spec FR-005

## E005
- Claim: one substrate feeds four generator families.
- Source: src/main/scala/org/galaxio/gatling/feeders/faker/Faker.scala:44-47,86-89,370-384
- Locator: `number.long`, `positiveLong/negativeLong`, both `date.offset` overloads
- Excerpt: "number.long(minOffset, maxOffset).map(from.plus(_, unit))"
- Supports: research R2/R7, data-model relationships

## E006
- Claim: CPF has no digit Vector; formatted branch compiles Pattern per call.
- Source: Faker.scala:628-632 + JDK 17 javadoc `java.lang.String#replaceFirst`
- Locator: `br.cpf` formatted branch
- Excerpt: "yields exactly the same result as Pattern.compile(regex).matcher(str).replaceFirst(repl)"
- Supports: research R1/R4 (#125)

## E007
- Claim: no Java Faker source exists in repo or examples.
- Source: command `find src examples -name "Faker.java"`
- Locator: empty result set; Scala lorem at Faker.scala:798-810
- Excerpt: "(no matches)"
- Supports: research R1 (#123), spec assumption on stale audit path

## E008
- Claim: selectKeys keySet hoisted; per-record view+toMap remains.
- Source: src/main/scala/org/galaxio/gatling/feeders/faker/Syntax.scala:96-99
- Locator: `def selectKeys`
- Excerpt: "keys.toSet … record.view.filterKeys(keySet.contains).toMap"
- Supports: research R1/R5 (#129)

## E009
- Claim: record-wins override semantics locked by existing test.
- Source: Syntax.scala:102-103; GeneratedFeederSpec:218-221
- Locator: "add defaults without overriding existing values"
- Excerpt: "withDefaults(currency->USD…) shouldBe Map(currency->EUR, active->true)"
- Supports: research R5 (#131), plan FR-004 row

## E010
- Claim: feature branch predates PR #300; wide-range fix + regression test absent until rebase.
- Source: `git merge-base --is-ancestor 23f2243 HEAD` (false); `git show 23f2243`
- Locator: origin/main commit 23f2243 vs branch base 6b9ab75
- Excerpt: "else { … if (value >= min && value <= max) value else retry() }"
- Supports: refutes plan FR-003 row "pre-existing #300 test", research R1/R2 current-state, quickstart §1

## E011
- Claim: JMH + coverage exclusion + 75/66 floor configured.
- Source: build.sbt:9-15,41-45
- Locator: `enablePlugins(… JmhPlugin)`, coverage settings block
- Excerpt: "= 75, = 66, benchmarkFilePattern, benchmarkPackagePattern"
- Supports: research R6, plan Constitution III, contracts gates

## E012
- Claim: MiMa baseline configured at 1.23.0.
- Source: build.sbt:77,86
- Locator: `mimaPreviousArtifacts` / `mimaBinaryIssueFilters`
- Excerpt: "Set(\"org.galaxio\" %% \"gatling-picatinny\" % \"1.23.0\")"
- Supports: contracts gates, spec SC-005

## E014
- Claim: hexString is legacy shared-Random Iterator-chain; unfit foundation for the ipv6 rewrite.
- Source: src/main/scala/org/galaxio/gatling/utils/RandomDataGenerators.scala:41-46,69-71,163-184
- Locator: `randomString` body; `hexString`; deprecation block
- Excerpt: "Iterator.continually(Random.nextInt(alphabet.length)).map(alphabet).take(length).mkString"
- Supports: research R4 (amended), spec randomness-source assumption (amended), plan deps line

## E013
- Claim: three string-assembly sites match plan line refs at branch HEAD.
- Source: Faker.scala:805 (lorem), :234 (ipv6), :709-714 (TIN)
- Locator: `lorem.words`, `internet.ipv6`, `de.steueridentifikationsnummer`
- Excerpt: "Vector.fill(count)(…).mkString(\" \") / Vector.fill(8)(…).mkString(\":\") / (1 to 10).map"
- Supports: research R1/R4, plan structure section
