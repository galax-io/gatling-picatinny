# Implementation Plan: Assertions Correctness (NFR YAML → Gatling assertions)

**Branch**: `claude/festive-einstein-efd50e` (spec dir `003-assertions-correctness`) | **Date**: 2026-06-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-assertions-correctness/spec.md`

## Summary

Fix six correctness defects in the NFR-YAML → Gatling assertions load path
(`assertionFromYaml`) on both the Scala builder and the Java facade, then deprecate
the whole NFR-YAML mechanism (generic "replacement coming" message). The defects:
Java exponential duplication (2^n assertions), silent drop of unknown keys (now WARN
+ skip), opaque crash on non-numeric thresholds (now a clear key+value error),
`AssertionBuilderException` returning null message/cause (call `super`),
charset-dependent matching of Cyrillic keys (remove the broken `toUtf` normalization),
and per-call reflection-heavy Jackson init in the Java facade (hoist to `static final`).
Ships as **v1.18.0** (next available minor — `v1.17.0`/`v1.17.1` already published; the
milestone title is stale). Test-first per `/tdd-workflow`; the existing Java test only
exercises error paths (via `.msg()`), so the happy-path/parity/exception gaps are why
these bugs survive — they get real value assertions. No public signature changes; the
only behavior delta is the added WARN on unknown keys (backward-compatible).

## Technical Context

**Language/Version**: Scala 2.13.18, Java 17 facade (JUnit 5 tests)

**Primary Dependencies**: Gatling 3.13.5 (`Provided`), PureConfig + `pureconfig.module.yaml`
(Scala YAML), Jackson `YAMLFactory` (Java facade YAML), scala-logging 3.9.5 (wraps
SLF4J — SLF4J API thus transitively on the classpath for the Java facade WARN),
ScalaTest + ScalaMock (unit), JUnit 5 + AssertJ (facade). **No new dependency.**

**Storage**: N/A (NFR data is a YAML file read at load).

**Testing**: sbt, **test-first (TDD)**. Library unit (`Test`, no Docker): Scala
`AssertionsBuilderSpec` (extend); Java facade `JavaAssertionFromYamlTest` /
`JavaAssertionsCompileTest` (extend). No integration/Testcontainers layer (no external
backend). No e2e overlay change (the NFR-YAML loader is not exercised by the
`HttpIntegrationCoverage` sim). Coverage gate `sbt-scoverage` floor unchanged (65/60);
these fixes add covered branches, only raising measured coverage.

**Target Platform**: JVM (published library + Java facade).

**Project Type**: Single project (published Scala/Java DSL library).

**Performance Goals**: N/A. FR-007 is a polish — remove per-call `findAndRegisterModules`
reflection; no throughput target, no JMH benchmark added.

**Constraints**: No public API / DSL / serialized-format change (Constitution II) — the
only serialized surface is the NFR YAML schema, which is **unchanged** (no new keys, no
strict flag). Version is tag-derived (`GitVersioning`) — v1.18.0 is the release tag, not
a `build.sbt` literal. Java facade cannot delegate to Scala core (the core→Java
`Assertion` wrap constructor is package-private — documented in `Assertions.java`), so
fixes are applied in BOTH places and **parity is enforced by test** (FR-002).

**Scale/Scope**: 3 production files (`AssertionsBuilder.scala`, `Assertions.java`,
`AssertionBuilderException.java`) + their tests + 1–2 new YAML fixtures. Tightly bounded.

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

> One row per functional requirement. Real case → layer → code-free prose sketch with
> ≥1 negative/boundary or exact-value assertion. The NFR-YAML loader emits no HTTP and
> drives no Gatling runtime, so there is **no DSL-component, no Testcontainers, and no
> e2e/WireMock layer** here — those layers do not fit this change (TESTING.md: apply
> only the layers that fit). ScalaMock is not needed either: there is no leaf
> collaborator to fake; the real YAML fixtures + real builder are the test.

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | Loading `nfr.yml` via the Java facade returns one assertion per entry, not 2^n | Facade Delegation (JUnit 5) | Java `assertionFromYaml("nfr.yml")` returns exactly 11 assertions (the recognized-entry count: 99p=3 + 95p=4 + errors=2 + max=2); a single-entry fixture returns exactly 1. Break: restore the `addAll(self)` duplication → size jumps to a power → assertion fails. |
| FR-002 | Scala builder and Java facade build the SAME set for the same `nfr.yml` | Facade Delegation (JUnit 5) | Normalize each side to a comparable form (count + per-assertion path/scope/threshold string) and assert the Java set equals the Scala set (size 11, same global/detail scopes, same `lt` thresholds). Break: flip one expected threshold → parity assertion fails. |
| FR-003 | `nfr.yml`'s unknown keys (APDEX, RPS) are skipped AND logged at WARN, in both paths | Unit (Scala) + Facade (Java) | Result size stays 11 (unknown keys produce no assertion); a captured log event at WARN names the skipped key ("APDEX", "RPS"). Negative: assert no assertion has a path derived from the unknown key. Break: remove the WARN → log-capture assertion fails; restore silent-only `None`/empty → still skipped but the WARN assertion fails. |
| FR-004 | A non-numeric threshold value yields an error naming the metric key + value, both paths; AND a fractional error-rate (`5.5`) parses (error-rate is Double) | Unit (Scala) + Facade (Java) | Load a fixture whose `value` is `"abc"`; assert the thrown error's message contains both the offending **metric record key** (not the scope key `all`) and `"abc"`. Scala and Java asserted independently (message-content parity). Also load `nfrSingle.yml` (`Процент ошибок` = `5.5`) → one Double-threshold assertion (proves the Scala `toInt`→`toDoubleOption` fix; was a crash). Break: revert error-rate to `.toInt` → `"5.5"` crashes / message lacks key+value → assertion fails. |
| FR-005 | `AssertionBuilderException(msg, cause)` exposes msg + cause via standard accessors | Facade (JUnit 5) | Construct with a known message and a known cause; assert `getMessage()` equals the message and `getCause()` is the same cause instance (both non-null), in addition to the existing `.msg()`/`.cause()`. Break: drop the `super(msg, cause)` call → `getMessage()`/`getCause()` return null → assertion fails. |
| FR-006 | A Cyrillic recognized key matches independent of the platform default charset | Unit (Scala) + Facade (Java) | After removing the `toUtf` round-trip, assert the Cyrillic-keyed `nfr.yml` still builds all 11 assertions and the detail paths retain their text (`myGroup`, `GET /test/uuid`); add a targeted assertion that a key round-tripped through a non-UTF-8 charset is NOT equal to the parsed key (documents why the lossy step is removed). Break: reinstate `Source.fromBytes(getBytes(), "UTF-8")` / no-op `toUtf` → on a non-UTF-8 default the key fails to match → fewer than 11 assertions. |
| FR-007 | Repeated Java-facade loads do not re-run one-time Jackson module init | Facade (JUnit 5) | Call `assertionFromYaml` twice; assert both return identical (size + content) results and complete without re-initialization side effects; structural check: the `ObjectMapper` is a `static final` initialized once (no `findAndRegisterModules()` inside `getNfr`). Break: identical-result assertion still passes, so this row's guard is the structural/no-per-call-init check + review (perf polish, low rigor by design). |
| FR-008 | Public load signatures are unchanged | Compile Guard (Test) | The existing `JavaAssertionsCompileTest` (and a Scala compile reference) still compile calls to `assertionFromYaml(String)` returning the same type on both sides. Break: change the signature → compile guard fails to compile. |
| FR-009 | Recognized-key mapping + scopes are unchanged for valid files | Unit (Scala) | The existing `AssertionsBuilderSpec` stays green: 11 assertions, distinct, detail paths preserve `myGroup` / `GET /test/uuid`, `all` → global. Break: drop a recognized `case` → size < 11 fails. |
| FR-010 | Every fix has a real-value test incl. a negative case | (meta) | Satisfied by the FR-001…FR-007 rows above — each names an exact-value or negative/boundary assertion and a deliberate-break that makes ≥1 test fail (constitution Principle III negative-test discipline). No empty bodies; no mock-vs-mock (no mocks used). |
| FR-011 | The release is v1.18.0, not a reused version | Release/meta | No automated test (version is the git tag). Verified at release: tag `v1.18.0` on a `release/1.18.0` branch; changelog notes the WARN + deprecation. Break (process): reusing `v1.17.x` → Sonatype rejects + `release.yml` branch/tag validation. |
| FR-012 | Referencing `assertionFromYaml` emits a generic deprecation warning; call still works | Compile Guard + Facade | Compile-guard reference shows the deprecation warning is emitted (symbol carries `@deprecated`/`@Deprecated`); a meta assertion checks the message text is generic (mentions "deprecated" + "future release", contains no version/date/issue link); a functional assertion confirms the deprecated call still returns the correct 11-assertion set. Break: remove the annotation → compile-guard/meta assertion fails. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — The Java facade does NOT newly duplicate
  logic: the duplication (facade re-implements the NFR→Assertion mapping via the Java
  DSL) is **pre-existing and unavoidable**, documented in `Assertions.java` — the
  core→Java `Assertion` wrap constructor is package-private, so the facade cannot
  delegate. This feature keeps both in lock-step and adds a **parity test** (FR-002) as
  the guard. No NEW facade-only logic is introduced; the fixes make the facade match the
  Scala source of truth. Recorded in Complexity Tracking.
- [x] **II. Backward Compatibility** — No public signature change (FR-008). The NFR YAML
  serialized format is unchanged (no new keys, no strict flag — the unknown-key decision
  is warn+skip only). Observable deltas are additive/bugfix: a WARN on unknown keys;
  clearer errors where values already crashed; and the Scala error-rate parse widened
  `Int`→`Double` (FR-004/F1) — integer percents produce the identical threshold (`5`→`5.0`,
  Gatling stores percent as Double) and previously-crashing fractional percents now work,
  so it is a strictly-additive bugfix, not a redefinition. Ships v1.18.0 MINOR.
- [x] **III. Test Discipline** — Test Model section above is filled (real case + layer +
  code-free sketch per FR). Test-first. Layers chosen that FIT: Unit + Facade Delegation
  + Compile Guard only — no Testcontainers (no backend), no DSL-component (no runtime
  action), no e2e/WireMock (loader emits no HTTP). Gatling runtime not mocked; no mocks
  at all (real fixtures). Coverage floor unchanged at 65/60 (fixes add covered branches).
- [x] **IV. Small, Focused Changes** — 3 production files + tests + fixtures. No new
  dependency (SLF4J already transitive via scala-logging). No opportunistic refactor.
  No API/config-format change. Deprecation is additive. The one bent principle
  (pre-existing facade duplication) is grandfathered and tracked below.
- [x] **V. Release Integrity** — v1.18.0 on a `release/1.18.0` branch cut from `main`;
  no version reuse (`v1.17.0`/`v1.17.1` already published); tag only on the release
  branch. Patch discipline N/A (minor). The fix lands on `main` first via PR, then is
  released per the constitution's branch/tag rules.

**Gate result**: PASS. No unjustified violations. The facade-duplication note is a
pre-existing, documented constraint (not introduced here).

## Project Structure

### Documentation (this feature)

```text
specs/003-assertions-correctness/
├── plan.md              # This file
├── research.md          # Phase 0 — fix-approach decisions (logging, toUtf, error type, perf, parity)
├── data-model.md        # Phase 1 — NFR entities + the assertion-count contract
├── quickstart.md        # Phase 1 — how to run + deliberate-break validation
├── contracts/
│   └── assertions-api.md # Public behavior contract for assertionFromYaml (Scala + Java) + exception + deprecation
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (repository root)

```text
src/main/scala/org/galaxio/gatling/assertions/
└── AssertionsBuilder.scala            # FIX: Try-parse (FR-004), WARN+skip unknown keys (FR-003),
                                       #      remove lossy toUtf (FR-006). Public signature UNCHANGED.
                                       #      ADD @deprecated (FR-012). scala-logging LazyLogging for WARN.

src/main/java/org/galaxio/gatling/javaapi/
├── Assertions.java                    # FIX: drop getListAssertions(self) duplication → add single (FR-001),
│                                      #      static-final ObjectMapper, modules once (FR-007),
│                                      #      checked numeric parse w/ key+value message (FR-004),
│                                      #      WARN+skip unknown keys via SLF4J (FR-003), remove no-op toUtf (FR-006).
│                                      #      ADD @Deprecated (FR-012). Signature UNCHANGED.
└── AssertionBuilderException.java     # FIX: call super(msg, cause) so getMessage()/getCause() non-null (FR-005).
                                       #      ADD @Deprecated (FR-012). Keep .msg()/.cause() + equals/hashCode.

src/test/scala/org/galaxio/gatling/assertions/
└── AssertionsBuilderSpec.scala        # EXTEND: WARN-capture on unknown key (FR-003), non-numeric error msg (FR-004),
                                       #         Cyrillic/charset-independence (FR-006). Keep the 11-assertion baseline (FR-009).

src/test/java/org/galaxio/gatling/javaapi/assertions/
├── JavaAssertionFromYamlTest.java     # EXTEND: happy-path size=11 + single-entry=1 (FR-001), parity vs Scala (FR-002),
│                                      #         getMessage()/getCause() non-null (FR-005), repeated-load identical (FR-007),
│                                      #         non-numeric key+value message (FR-004), WARN on unknown key (FR-003).
└── JavaAssertionsCompileTest.java     # EXTEND: deprecation-warning compile reference + signature lock (FR-008, FR-012).

src/test/resources/
├── nfr.yml                            # EXISTING fixture (APDEX+RPS unknown, 11 recognized) — reused as-is.
├── nfrSingle.yml                      # ADD: one recognized entry → expect exactly 1 assertion (FR-001 boundary).
└── nfrNonNumeric.yml                  # ADD: a recognized key with a non-numeric value (FR-004 negative).

CHANGELOG / git-cliff                  # NOTE: WARN-on-unknown-key + NFR-YAML deprecation (FR-011/FR-012).
```

**Structure Decision**: Single-project library. Edits are confined to the three
assertions files and their existing test classes (extend, do not recreate). Two small
YAML fixtures are added for the single-entry and non-numeric cases; the existing
`nfr.yml` (which already carries APDEX/RPS unknown keys) covers the skip+WARN and the
11-assertion baseline. No build.sbt change (no dep, tag-derived version), no CI/workflow
change, no overlay/e2e change.

## Complexity Tracking

> Items where a Constitution principle is bent — require explicit authorization.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Java facade duplicates the NFR→Assertion mapping (Constitution I) | **Pre-existing**, documented in `Assertions.java`: the core→Java `Assertion` wrap constructor is package-private, so the facade cannot delegate to the Scala builder; it must rebuild via the Java DSL (`global()`/`details()`). This feature keeps both in lock-step and enforces equivalence with a parity test (FR-002) | True delegation is impossible without a public core→Java conversion (an upstream Gatling API gap, out of scope). Not fixing both sides would leave the high-severity Java exponential-dup bug live |
| Adds a WARN log on previously-silent unknown keys (touches observable behavior) | FR-003 resolved decision: surface typo'd NFR keys that today silently disable a threshold. Additive + backward-compatible (still skips); no new config | Pure silent-skip leaves the "tests validate nothing" defect; fail-fast/strict-flag would be a breaking change or new config surface (rejected per the clarification) |
