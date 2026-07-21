# Research: Faker & Feeder-Transform Hot-Path Allocation Reduction

**Feature**: `010-faker-syntax-perf` | **Date**: 2026-07-21

All eight issue targets were located and read in the current codebase before planning.
Line numbers below are current `main` (verified 2026-07-21). No NEEDS CLARIFICATION
markers remain.

## R1. Issue ground truth vs. audit pointers

**Decision**: Plan against the code as it exists today, not the audit-era line numbers.

| Issue | Audit claim | Verified current state |
|-------|-------------|------------------------|
| #123 lorem | `Faker.java:662` | Scala only: `Faker.scala:805` — `Vector.fill(count)(data(...)).mkString(" ")` inside `lorem.words` |
| #124 IPv6 | `Faker.scala:201` | `Faker.scala:234` — `Vector.fill(8)(RandomDataGenerators.hexString(4)).mkString(":")` |
| #125 CPF | `Faker.scala:500-503` Vector of digits | **Stale**: CPF now delegates to caelum-stella `generateRandomValid()` (`Faker.scala:628-632`); no digit Vector remains. Residual per-call allocation: `raw.replaceFirst(regex, …)` compiles a fresh `Pattern` on every `formatted = true` call |
| #125 German TIN | `Faker.scala:570` | `Faker.scala:709-714` — `(1 to 10).map(…)` builds an `IndexedSeq`, then `(first +: rest).mkString` prepends (second collection) |
| #139 email | `Faker.scala:163,205-206` | Two identical chains: `person.companyEmailName` (`Faker.scala:196`) and `internet.emailFromName` (`Faker.scala:238`) — `toLowerCase.replaceAll("[^a-z0-9]+", ".").stripPrefix(".").stripSuffix(".")`; `String.replaceAll` compiles a `Pattern` per call |
| #304 long | `nextLongInclusive` | `Faker.scala:92-105` — narrow/wide classification allocates 3+ `BigInt` per call. **Post-branch-cut drift (V010/E010)**: PR #300 merged to main after this branch was cut and reshaped the function — explicit full-range branch (`min == Long.MinValue && max == Long.MaxValue`) FIRST, rejection loop checks BOTH bounds; the `BigInt` narrow classification survives unchanged. Rebase (foundational task) required before touching this |
| #129 selectKeys | `templates/Syntax.scala:98` | Refiled: `feeders/faker/Syntax.scala:96-99`. `keySet` already hoisted (`:97`); remaining per-record cost is `.view.filterKeys(keySet.contains).toMap` (MapView wrapper + function wrapper + rebuild) |
| #130 prefix/suffix | `templates/Syntax.scala:83,87` | Refiled: `feeders/faker/Syntax.scala:82-87` — `s"$prefix$key"` interpolation (StringBuilder machinery) per key per record |
| #131 withDefaults | `templates/Syntax.scala:103` | Refiled: `feeders/faker/Syntax.scala:102-103` — `defaults.toMap` rebuilt per record |

**Rationale**: Issues #129–#131 were explicitly refiled to `faker/Syntax` per the
milestone description; #123/#125 pointers predate the stella adoption and the Scala
consolidation. The `templates/Syntax.scala` module has its own benchmark
(`templates/SyntaxBenchmark.scala`) and is out of scope.

## R2. #304 — Long-only narrow/wide classification (equivalence proof)

**Decision**: Replace the `BigInt` classification with wraparound `Long` arithmetic:
`span = max - min`; narrow iff `span >= 0 && span < Long.MaxValue`; then
`bound = span + 1` feeds the existing `ThreadLocalRandom.nextLong(bound)`. `min == max`
short-circuit and both wide branches — post-#300 shape: the explicit full-range
branch (`min == Long.MinValue && max == Long.MaxValue` → direct draw) stays FIRST,
and the rejection loop keeps its both-bounds check — stay byte-identical.

**Rationale** (branch equivalence, for `min <= max`, mathematical distance
`d = max − min ∈ [0, 2^64 − 1)`):

- Old narrow condition: `d + 1 <= Long.MaxValue` ⟺ `d < Long.MaxValue`.
- Two's-complement: `span = max - min` equals `d` when `d < 2^63`, else wraps negative.
- If `d < 2^63`: `span = d >= 0`, and `span < Long.MaxValue ⟺ d < Long.MaxValue` — same predicate. Boundary check: `d = Long.MaxValue` → `span = Long.MaxValue`, fails `< Long.MaxValue` → wide (old: `d + 1 = 2^63 > Long.MaxValue` → wide ✓); `d = Long.MaxValue − 1` → narrow with `bound = span + 1 = Long.MaxValue`, no overflow ✓.
- If `d >= 2^63`: `span < 0` → wide (old: `d + 1 > 2^63 > Long.MaxValue` → wide ✓).

Classification is provably identical on every input pair; narrow-path `bound` is exact;
wide path untouched, so the #300 regression test ("correctly generate longs when range
is wider than Long.MaxValue", `GeneratedFeederSpec` — present after the foundational
rebase; absent on the pre-rebase branch, V010) and the inclusive-bound tests
(`GeneratedFeederSpec:301-304`) must pass unchanged. A property test will additionally
compare the new classifier against an inline `BigInt` reference on adversarial pairs
(`Long.MinValue/MaxValue` corners, distance exactly `Long.MaxValue ± 1`).

**Alternatives considered**: `Math.subtractExact` + catch — exception control flow,
rejected; keeping one cached `BigInt(Long.MaxValue)` — still 2 allocations per call,
rejected.

## R3. #139 — email normalization: keep `toLowerCase`, replace the regex chain

**Decision**: Keep the initial `name.toLowerCase` call, then replace
`replaceAll("[^a-z0-9]+", ".").stripPrefix(".").stripSuffix(".")` with a single
character loop over the lowered string writing into one `StringBuilder`
(collapse each run of non-`[a-z0-9]` chars to a single `.`, emit no leading dot, trim
one trailing dot). Extract as one shared private helper used by both call sites
(`companyEmailName`, `emailFromName`); `emailFromName` keeps its `"user"` fallback for
an empty result.

**Rationale**: `String.toLowerCase()` is locale-sensitive with special casing (e.g.
U+0130 lowers to a two-char sequence). A per-char `Character.toLowerCase` loop is NOT
equivalent for such inputs, and `email(name, domain)` accepts arbitrary caller
strings — so full-loop lowering would violate FR-001 parity. Keeping `toLowerCase`
preserves exact semantics; the loop still removes the per-call `Pattern` + `Matcher`
+ intermediate string(s) from `replaceAll`/`strip*`. Net: from ~4-5 transient objects
to the lowered string + builder + result.

**Alternatives considered**: precompiled static `Pattern` (issue's fallback option) —
keeps `Matcher` + intermediate string per call, strictly worse than the loop;
full char-loop with per-char lowering — behavior drift on locale-sensitive input,
rejected on FR-001/FR-008.

**Parity test**: property test comparing helper output against an inline reference
implementation (the old chain) over generated names plus adversarial fixtures
(uppercase, accents, `"--a--"`, `"..."`, all-symbols, digits-only, empty).

## R4. #123/#124/#125 — single-pass string assembly

**Decision**:

- `lorem.words` (`:805`): one `StringBuilder` loop appending word + space separator; `require(count > 0)` untouched (rejection of 0 stays — existing negative test `GeneratedFeederSpec:1283`).
- `internet.ipv6` (`:234`): one `StringBuilder` filled with 32 hex digits (8 groups × 4, `:` separators) drawn directly from `ThreadLocalRandom` over the lowercase `0123456789abcdef` alphabet — dropping the `RandomDataGenerators.hexString` calls entirely. Rationale: `hexString` delegates to `randomString`, whose body is `Iterator.continually(Random.nextInt(...)).map(alphabet).take(length).mkString` on the SHARED `scala.util.Random` singleton — an Iterator+closure+builder allocation chain per group plus cross-thread RNG contention under load. `RandomDataGenerators` is also the legacy pre-Faker utility surface (its ID generators already carry `@deprecated("…", "faker-api")`); new hot-path code must not deepen the dependency (user directive 2026-07-21). Distribution and format are unchanged (uniform over the same lowercase-hex alphabet, same 8×4 shape); the RNG *source* for ipv6 changes from shared `Random` to `ThreadLocalRandom` — covered by the amended spec assumption. `Faker.string.hex` and the other `RandomDataGenerators` delegates are OUT of scope (no issue covers them).
- `de.steueridentifikationsnummer` (`:709-714`): one `StringBuilder`; first digit 1-9 then 10 digits 0-9 appended directly — no Range `.map`, no prepend.
- `br.cpf` (`:628-632`): stella generation untouched; hoist the format regex to a private compiled `Pattern` used by the `formatted = true` branch (allocation per call drops from Pattern+Matcher+String to Matcher+String). CNPJ has the identical pattern-per-call shape but is NOT in issue scope — left untouched (Constitution IV, no opportunistic refactors); noted for a possible follow-up issue.

**Rationale**: matches each issue's own fix sketch; output shapes are identical.
Draw-order/source preservation: lorem and TIN keep their existing `ThreadLocalRandom`
consumption sequence (`Vector.fill`/Range-map evaluate left-to-right, as does
sequential append). ipv6 is the deliberate exception — its RNG *source* changes
(legacy shared `Random` → `ThreadLocalRandom`, see the decision above), so parity
there is distribution-level (same uniform draw over the same alphabet, same 8×4
shape), not stream-level.

**Alternatives considered**: `String.join`/`mkString` on an `Array` — still an
intermediate collection, rejected; `java.util.StringJoiner` — equivalent to
StringBuilder here, no advantage.

## R5. #129/#130/#131 — transform closures: hoist static, single-pass dynamic

**Decision**:

- `withDefaults` (`:103`): hoist `defaults.toMap` into a `val` outside the closure; per-record work stays `defaultsMap ++ record` (semantics: record wins on key collision; varargs duplicate-key last-wins folds into the hoisted map exactly as today).
- `selectKeys` (`:98`): keep the hoisted `keySet`; replace `.view.filterKeys(keySet.contains).toMap` with a direct single-pass `filter` on the map (`kv => keySet.contains(kv._1)`) — drops the MapView wrapper, the function wrapper, and the second rebuild pass.
- `prefixKeys`/`suffixKeys` (`:83,:87`): **NO code change — issue #130 premise refuted (V002/E002)**. Disassembly of the production classfile (`Syntax$FeederOps$.class`, `$anonfun$prefixKeys$2`) shows the s-interpolator already compiles to a single `invokedynamic makeConcatWithConstants` — Scala 2.13 lowers simple interpolations to indy string concat, so `prefix + key` would be bytecode-identical and there is no "interpolation machinery" to remove. Remaining allocations (result string, tuple, result map) are inherent to the operation. Disposition: close #130 with the bytecode evidence (maintainer confirmation required); a prefix/suffix output-parity regression test is still added under FR-004's chain test for safety.

**Rationale for NOT pre-building renamed keys** (deviation from #130's sketch): record
keys are record-derived, not construction-time configuration — a `Feeder[Any]` is an
iterator of arbitrary maps with no homogeneity guarantee. A first-record memo cache
would add mutable state plus an O(n) key-set comparison per record that costs about
what it saves, and risks behavior drift on heterogeneous streams. The construction-time
configuration for these two transforms is the prefix/suffix string itself; spec US3
acceptance scenario 4 is amended to say exactly that (static config captured once;
per-record work is a single pass with at most one plain concatenation per key).

**Alternatives considered**: first-record key-memo (rejected above); pre-sized
`Map.newBuilder` for `selectKeys` — measurable only for very wide records, `filter` is
already single-pass and allocation-minimal, keep simplest form (Constitution IV).

## R6. #304/#123 benchmark evidence (FR-007)

**Decision**: Extend the existing `feeders/faker/FakerBenchmark.scala` (JMH, runs via
`sbt Jmh/run`) with narrow-range `number.long` and `lorem.words` benchmark methods
(text-assembly + number-path representatives; ipv6 optional third). Evidence = before/
after `gc.alloc.rate.norm` from `-prof gc` runs recorded in the PR description.
Benchmarks live in main compile scope like the existing ones and are excluded from
coverage per TESTING.md.

**Rationale**: FR-007 requires one text-assembly path and the narrow-range number path
minimum; `FakerBenchmark` already establishes the harness pattern (extends
`JmhBenchmark`). No new infrastructure (Constitution IV / spec assumption).

**Alternatives considered**: new dedicated benchmark class — needless file churn;
JFR allocation profiling — heavier, JMH `-prof gc` is the established practice.

## R7. Test strategy summary

All parity work is TESTING.md layer 1 (Unit/Functional, `Test` config, ScalaTest +
ScalaCheck already in use in `GeneratedFeederSpec`). No DSL-component, integration,
e2e, or facade layer applies: no public signature changes (facades untouched — the
changed methods are `private` helpers or method bodies), no HTTP, no containers.
Existing guards that MUST stay green untouched: `GeneratedFeederSpec` (email :488-513,
ipv6 :537-540, lorem :1264-1284 incl. `words(0)` rejection, TIN :1162, CPF/CNPJ stella
validation :1137, long bounds :292-306 incl. Long.MaxValue inclusive, wide-range #300
test), `GovIdValidators` check-digit helpers, transform tests (:194-231). Binary
surface: `sbt mimaFindBinaryIssues` must stay clean (FR-005/SC-005).
