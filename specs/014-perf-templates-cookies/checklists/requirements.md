# Specification Quality Checklist: Perf — Templates & Cookies, plus Locale Correctness and the OpenNFR v0.8.0 Metric Axis

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-01 · **Re-scoped**: 2026-09-01 (JMH before/after gate; #328 pulled in; both locale defects and the packaging defect pulled in)
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

**Scope is four issues plus a tracking issue.** At the maintainer's direction the two locale defects
and the packaging gap are fixed in this feature rather than deferred. They are tracked by
[#333](https://github.com/galax-io/gatling-picatinny/issues/333) on milestone v1.27.0, which the
linkage gate needs and which enumerates all four unfiled items with acceptance criteria.

**The benchmark dependency is resolved.** #143 (`Templates.makeJson`) and #144 (`CookieParser`) were
moved from v1.29.0 into **v1.27.0** at the maintainer's direction: under the new before/after policy
they are prerequisites for #126 and #137, not later baseline work. Milestone v1.27.0 now holds seven
issues — #126, #127, #137, #143, #144, #328, #333.

**Both moved issues are partly stale and planning must not take them at face value.** #143 says "no
benchmark for nested or many-field JSON payloads"; `SyntaxBenchmark` already covers nested (3 levels),
large-array (100 items), flat, mixed and interpolated payloads for both JSON and XML. The real gap is
**escaping-heavy fixtures** (FR-003) — the thing this milestone actually needs, and not what either
issue asks for. Both also give `src/test/scala` as their location, which is wrong for this project:
benchmarks live on the production classpath under `src/main/scala`, which is what makes them
discoverable and what makes the `*Benchmark*` naming rule load-bearing (FR-004). #144's premise
("nothing benchmarks CookieParser") is accurate.

**The benchmark gate is a blocker, not a formality.** `sbt Jmh/run` does not work at HEAD. It dies with
`ClassNotFoundException: org.openjdk.jmh.Main` before executing a single benchmark, because
`build.sbt:123-125` rewrites every `org.openjdk.jmh` module to `Provided` and `Jmh / run` forks against
`fullClasspathAsJars`, which drops Provided-only modules. Reproduced first-hand. A session-only
`set Jmh / dependencyClasspathAsJars ++= (Compile / dependencyClasspath).value` was verified to fix it
and keep the POM clean; whether that is the preferred remedy is a planning decision. Until it lands,
"confirm every perf change with JMH before and after" cannot be met at all — hence User Story 1 at P1.

Two further measurement findings that decide whether the gate is worth anything:

- **`SyntaxBenchmark` cannot see an escaping regression.** Not one fixture string in
  `SyntaxBenchmark.scala:9-51` contains any character `escapeJson` or `escapeXml` branches on. A #126
  rewrite that fast-paths escape-free strings would post a large win while an escaping-heavy
  regression stayed invisible. Escaping-heavy fixtures are mandatory (FR-003).
- **Benchmark class naming is load-bearing.** Exclusion from the coverage denominator and the
  published jar runs off the *file* pattern `.*Benchmark.*`, not the package pattern — the latter
  excludes nothing the former does not, since all four existing benchmarks live in other packages. A
  class missing the marker silently enters the coverage denominator (floor 75/66) **and** ships (FR-004).

**The packaging gap, now in scope.** `build.sbt:117-120` filters `Compile / packageBin / mappings` on
`path.matches(".*Benchmark.*\.class") || path.startsWith("org/galaxio/gatling/jmh/")`. Because
`Jmh / classDirectory` is the *Compile* class directory, a local `Jmh/compile` writes generator output
there — and `META-INF/BenchmarkList`, `META-INF/CompilerHints` and bare `jmh_generated` directory
entries match neither clause, so they survive into the package. Whether this has ever shipped was not
established. Making benchmark runs routine is what raises the exposure, which is why it belongs with
User Story 1 rather than in a separate feature (FR-005, SC-002).

**The masking defect is the most severe item in this milestone**, which is why its story outranks every
performance concern. `ConfigValueMasking.splitWords` (`:174`) ends `.map(_.toLowerCase)` — default
locale. Under `tr`/`az` a capital `I` maps to U+0131, so a capitalised key misses the secret words that
contain `i`: `credential`, `credentials`, `authorization` in `StrongTerms`, and `apikey`, `privatekey`,
`clientsecret` in `CompoundTerms`. The `suffixFloor` safety net (`:73`) compares the same corrupted
string, so it does not catch the miss, and `normalizedTerm` (`:194`) routes user-supplied
`picatinny.redaction.additionalSensitiveKeys` through the same lowering. Result: the value is logged in
full by the feature whose purpose is to prevent exactly that. Note the direction of the fix is
one-way — masking may widen, never narrow (FR-008), so it cannot itself introduce a disclosure.

**Both locale defects need only a capital `I`.** Lower-case text is unaffected: `apiKey` is safe,
`API_KEY` is not; `Domain=` is safe, `DOMAIN=` is not. Tests must therefore use the capitalised
spellings — a mixed-case test passes before the fix and proves nothing. On the cookie side, of the five
recognised attribute names only `DOMAIN` contains an ASCII `I`, so it is the only reachable one:
measured on this JVM, `"DOMAIN".toLowerCase()` under `tr-TR` yields `domaın`, the `attrs.get("domain")`
lookup at `CookieParser.scala:35` misses, and the cookie silently falls back to `defaultDomain` with
**no warning**. Other default-locale `toLowerCase` sites were checked and found unreachable —
`IntensityConverter:36` (`rps`/`rpm`/`rph`), `ClaimsBuilder:178` (`true`/`false`), `VaultFeeder:199,202`
(`http`, loopback names) — none of those tokens carries an affected letter.

**Three issue premises were amended on evidence.** All three are in the spec's Assumptions:

- **#126** — the issue's own suggested fix ("single-pass char loop into a pre-sized buffer") already
  landed in `f5446fb`. Its other suggestion, a thread-local buffer, is rejected (FR-017). What survives
  is worse than stated: `escapeJson`/`escapeXml` run on the field **name** on every branch as well as
  the value, so a plain five-field payload pays ~20 short-lived objects.
- **#127** — **premise false, and the suggested fix does not compile.** `HttpRequestBuilder` is an
  `ActionBuilder` consumed once at scenario construction; `StringBody(String)` is strict and
  `.el[String]` eager, so body text is built once per DSL declaration, never per request. The proposed
  `Seq[Field]` overload was compiled against Scala 2.13.18 and fails — same erasure as `Field*`.
  **#127 closes as invalid with no code change**, superseding an earlier draft of this spec that
  proposed a `List[Field]` entry point: under the before/after gate an API addition with no measurable
  effect cannot be justified.
- **#137** — premise holds, but the cost is array/map allocation, not pattern compilation: the `split`
  calls all take `java.lang.String`'s single-non-metachar fast path.

**#328 was re-scoped from the released README and differs from the issue text in two ways.** Upstream
`v0.8.0` shipped 2026-09-01T02:01:51Z, clearing the block. But (1) the retired metric name has **no
deprecation window** — it is retired outright and its reach row reads "cannot", where the issue
anticipated a window; and (2) the metric/selection pairing is **bidirectional**. The issue directs
working from the released artefacts, so the released text governs.

Four #328 facts for planning, read from the pinned artefacts (Gatling 3.13.5, gatling-shared-model 0.0.11):

- **No new Gatling call is needed.** `TimeMetric` is sealed with exactly one case, `ResponseTime`, and
  `AssertionWithPath` exposes `responseTime` as its only time entry point; `AssertionValidator` picks
  `groupCumulatedResponseTimeGeneralStats` purely from path resolution. The repo already proves it —
  `ParitySpec.scala:35` builds the group oracle as
  `details(grp("myGroup")).responseTime.percentile(95).lt(1600)`. #328 removes a refusal.
- **The largest real code change is invisible in the parity claim.** `Reach.resolve` composes
  `scope(selector)` and `measure(predicate)` as independent `Either`s, so neither sees the other. The
  bidirectional rule needs the scope threaded into `measure` or a joint post-check.
- **A wording trap upstream.** The Aggregations section writes `groupCumulatedResponseTime.percentile(n)`,
  which reads as a DSL call. No such builder exists — it is the *statistic*'s name. Transcribed
  literally into `contracts/reach.md` it would describe code that does not compile.
- **Two consequences to document, not fix.** A group assertion prints as "response time" in Gatling's
  own report, so the distinction lives only in the document; and post-#328 a group requirement and the
  old "call the group a request" workaround render to the *identical* assertion value, so that
  prohibition survives only as documentation.

**Status**: all items pass. Ready for `/speckit-plan`. `/speckit-clarify` is optional — no open question
remains in the spec, and the benchmark-scheduling decision is settled.
