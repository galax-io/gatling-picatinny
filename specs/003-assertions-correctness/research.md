# Phase 0 Research: Assertions Correctness

Resolves the plan-level decisions deferred from `/speckit-clarify` (test/impl design,
not spec ambiguity). One entry per open question.

## R1 — Java facade WARN logging mechanism (FR-003)

- **Decision**: Use SLF4J in the Java facade (`org.slf4j.Logger` via
  `LoggerFactory.getLogger(Assertions.class)`); use scala-logging `LazyLogging` in the
  Scala builder. WARN message names the unknown key, e.g. `unknown NFR assertion key
  '<key>' — skipped`.
- **Rationale**: scala-logging 3.9.5 depends on `slf4j-api`, so the SLF4J API is already
  on the classpath transitively — **no new dependency** (Constitution IV satisfied). The
  Scala builder already lives in a scala-logging project; the Java facade has no logger
  today and SLF4J is the project's logging facade.
- **Alternatives considered**: `java.util.logging` (avoids SLF4J but diverges from the
  project's logging stack and won't honor the app's logback config) — rejected.
  System.err print — not a real log, untestable cleanly — rejected.

## R2 — Scala non-numeric failure: error type + message (FR-004)

- **Decision**: Parse **per metric** (Scala 2.13): error-rate (`Процент ошибок`) →
  `value.toDoubleOption` (**Double**); response-time percentile + max →
  `value.toIntOption` (**Int**). On `None`, throw `IllegalArgumentException` whose
  message names the offending **metric record key** and value, e.g. `NFR assertion
  '<metricKey>': value '<v>' is not a valid number` (the metric key like
  `99 перцентиль времени выполнения`, NOT the inner scope key `all` — F3). The Java
  facade keeps its already-correct per-metric types (percent = `double`, time = `int`)
  and throws `AssertionBuilderException` with the same message content. **Parity is on
  the message content (key + value), not the exception type** (different exception
  families by design — Scala core vs Java facade).
- **Canonical types (F1)**: error-rate is **Double** (matches Gatling
  `failedRequests.percent`, the Java `parseDouble`, and `data-model.md`); time metrics
  are **Int** (milliseconds). The current Scala `buildErrorAssertion` uses `v.toInt` for
  error-rate — a **latent bug**: `"5.5".toInt` throws, so a fractional error budget
  crashes on Scala while the Java facade accepts it. The FR-004 fix (T010) switches the
  error-rate branch to `toDoubleOption`, removing both the crash and the Scala↔Java
  divergence (and unblocking FR-002 parity). Integer values like `'5'` produce the same
  threshold (`5.0`) → backward-compatible.
- **Rationale**: Today Scala `.toInt` throws a bare `NumberFormatException` with no
  context (and cannot represent fractional percent); Java `Integer.valueOf`/`parseDouble`
  likewise give no context. The fix surfaces which YAML entry is wrong.
  `toIntOption`/`toDoubleOption` are the idiomatic checked parses (no `Try` import).
- **Alternatives**: Wrap in `Try` and rethrow — equivalent but heavier; `toIntOption`
  preferred. A shared custom Scala exception type — unnecessary; the message is the
  contract the test asserts.

## R3 — toUtf: fix vs remove (FR-006)

- **Decision**: **Remove** the `toUtf` normalization on both sides. Scala drops
  `Source.fromBytes(baseString.getBytes(), "UTF-8")` (a lossy default-charset round-trip:
  `getBytes()` uses the platform default, then re-decodes as UTF-8 — corrupting Cyrillic
  on a non-UTF-8 default). Java drops `new String(s.getBytes(UTF_8), UTF_8)` (a no-op).
  Match the parsed key string directly.
- **Rationale**: Both YAML readers already decode the file as UTF-8 regardless of the
  JVM `file.encoding`: Jackson's `YAMLFactory` (SnakeYAML) reads per the YAML spec
  (UTF-8/UTF-16 with BOM detection), and PureConfig's `YamlConfigSource.file` reads
  UTF-8. So the keys arrive correct; `toUtf` adds nothing on Java and actively corrupts
  on Scala. Removing it makes matching charset-independent.
- **Must verify in test**: FR-006 row asserts the Cyrillic-keyed `nfr.yml` still builds
  all 11 assertions and detail paths keep their text. If a platform-default round-trip is
  reintroduced (the deliberate break), Cyrillic keys mismatch and the count drops.
- **Alternatives**: Keep `toUtf` but force `getBytes(UTF_8)` — still a pointless
  round-trip; removal is simpler and equally correct. Rejected.

## R4 — Charset-independence test without forking the JVM (FR-006)

- **Decision**: Do NOT fork a JVM with `-Dfile.encoding`. Assert directly that the
  parsed Cyrillic keys match and produce the full assertion set, plus a targeted unit
  assertion that demonstrates the lossy round-trip corrupts the key (encode a Cyrillic
  string with a non-UTF-8 charset such as ISO-8859-1 and show it is NOT equal to the
  original) — documenting why the normalization was removed.
- **Rationale**: `file.encoding` is a JVM-start property; per-test mutation is brittle.
  The real correctness lever is "removed the default-charset dependency", which is
  provable with an in-process charset round-trip assertion + the end-to-end count check.
- **Alternatives**: A separate forked sbt test task with an explicit `-Dfile.encoding`
  — heavier CI surface for marginal extra confidence; rejected for this scope.

## R5 — Per-call Jackson init removal + verification (FR-007)

- **Decision**: Hoist the `ObjectMapper` to a `static final` field constructed once with
  modules registered at class-init: `static final ObjectMapper MAPPER = new
  ObjectMapper(new YAMLFactory()).findAndRegisterModules();`. Remove the per-call
  `mapper.findAndRegisterModules()` from `getNfr`. Verify behaviorally that two
  successive loads return identical results; the primary guard is the structural change
  (no per-call registration) confirmed in review.
- **Rationale**: `findAndRegisterModules()` does classpath scanning / reflection; running
  it on every `getNfr` is wasteful (the issue). Records deserialization still needs the
  parameter-names module, so registration is kept — just once.
- **Alternatives**: Drop module registration entirely — risky (record component-name
  binding may rely on the parameter-names module); keep it, register once. A timing/JMH
  assertion — over-engineered for a polish; rejected.

## R6 — Cross-layer parity test (FR-002)

- **Decision**: In the Java facade test, build the assertion set from `nfr.yml` via the
  Java `assertionFromYaml` and compare against the Scala builder's set obtained through
  its existing `assertionsFrom` test seam (`GatlingConfiguration.loadForTest()`).
  Compare on a **normalized structural form** — assertion count plus each assertion's
  scope (global vs detail path text) and `lt` threshold — since the Scala core
  `Assertion` and the Java `io.gatling.javaapi.core.Assertion` are distinct types and not
  directly equal.
- **Rationale**: FR-002 defines equivalence as same count + thresholds + scope, not
  object identity (the spec's Assumptions say so). The Scala seam is already public to
  the test (`private[assertions]`), so a cross-package test in `assertions` can read it,
  or expose a small test-only normalized view.
- **Open implementation detail (for tasks)**: the parity test is Java-side (JUnit); it
  needs a way to read the Scala set. Either (a) call the Scala object from Java (the seam
  is `private[assertions]` — not visible to Java; the public `assertionFromYaml` throws
  outside a simulation), or (b) assert both sides independently against the SAME expected
  normalized list (the 11 known assertions) — equivalent and simpler. **Prefer (b)**:
  both the Scala spec and the Java test assert against the identical expected normalized
  set, giving parity without cross-language calls.

## R7 — Deprecation `since` token + message (FR-012)

- **Decision**: Scala `@deprecated("<generic message>", "1.18.0")`, Java
  `@Deprecated(since = "1.18.0")` plus a Javadoc `@deprecated` note carrying the same
  generic message. Message (resolved in Clarifications): states the NFR-YAML assertion
  loading is deprecated and will be replaced by new assertions functionality in a future
  release, still works for now, watch the changelog — **no version/date/issue link**.
- **Rationale**: Matches the project's existing convention (`RegexFeeder` uses
  `@deprecated("…replacement guidance…", "faker-api")`; `Feeders.java` uses
  `@Deprecated(since = "faker-api")`). A version-style `since` ("1.18.0") is clearer than
  a label here. Deprecate the public surface only: `assertionFromYaml` (both) and the
  public `AssertionBuilderException`; the `private` `NFR`/`Record` types carry no
  user-visible deprecation.
- **Alternatives**: label `since` (e.g. "nfr-yaml") — less informative; rejected.

## Summary of decisions

| # | Topic | Decision |
|---|-------|----------|
| R1 | Java WARN logger | SLF4J (transitive via scala-logging) — no new dep |
| R2 | Non-numeric error + types | Per-metric: error-rate→Double (`toDoubleOption`, fixes latent Scala `toInt` crash on fractional %), time→Int (`toIntOption`); `IllegalArgumentException` (Scala) / `AssertionBuilderException` (Java) naming the metric key + value |
| R3 | toUtf | Remove on both sides (parsers already UTF-8) |
| R4 | Charset test | In-process round-trip assertion + count check; no JVM fork |
| R5 | Jackson init | `static final` mapper, modules registered once |
| R6 | Parity test | Both sides assert the SAME expected normalized 11-assertion set |
| R7 | Deprecation | `@deprecated(msg,"1.18.0")` / `@Deprecated(since="1.18.0")`, generic message, public surface only |

No remaining NEEDS CLARIFICATION.
