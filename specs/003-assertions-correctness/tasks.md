---
description: "Task list for Assertions Correctness (NFR YAML → Gatling assertions)"
---

# Tasks: Assertions Correctness (NFR YAML → Gatling assertions)

**Input**: Design documents from `/specs/003-assertions-correctness/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/assertions-api.md, quickstart.md

**Tests**: INCLUDED — the feature is test-first (constitution III / FR-010). Each fix gets a
failing test BEFORE the production change (red → green), with ≥1 negative/exact-value
assertion and a deliberate-break check.

**Organization**: By user story (P1 → P3). NOTE: all five stories edit the SAME three
production files (`Assertions.java`, `AssertionsBuilder.scala`, `AssertionBuilderException.java`)
and two test files. Tasks touching the same file are NOT parallel — see Dependencies.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on an incomplete task → parallelizable
- **[Story]**: US1–US5 (maps to spec.md user stories)
- Exact file paths included

## Path Conventions

Single-project library. Production: `src/main/scala/org/galaxio/gatling/assertions/`,
`src/main/java/org/galaxio/gatling/javaapi/`. Tests:
`src/test/scala/org/galaxio/gatling/assertions/`,
`src/test/java/org/galaxio/gatling/javaapi/assertions/`. Fixtures: `src/test/resources/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Test fixtures + confirm no build changes needed.

- [x] T001 [P] Add fixture `src/test/resources/nfrSingle.yml` — one recognized record `Процент ошибок` with a single **fractional** `value` entry `all: '5.5'`; expected result = exactly 1 assertion with a **Double** threshold `5.5` (FR-001 boundary AND the error-rate-Double regression guard — under the old Scala `v.toInt`, `"5.5".toInt` crashes).
- [x] T002 [P] Add fixture `src/test/resources/nfrNonNumeric.yml` — one recognized key (e.g. `99 перцентиль времени выполнения`) with a non-numeric value (e.g. `all: 'abc'`); used for the FR-004 negative case.
- [x] T003 Confirm no `build.sbt`/dependency change is required: SLF4J API is transitive via `scala-logging` (Java facade WARN), version is tag-derived (`GitVersioning`), coverage floor stays 65/60. Record finding (no edit expected).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the green baseline before any fix.

**⚠️ CRITICAL**: Run before touching production code, so each subsequent failing test is a true red.

- [x] T004 Run the existing suite green as the reference baseline — `sbt "testOnly org.galaxio.gatling.assertions.AssertionsBuilderSpec org.galaxio.gatling.javaapi.assertions.*"` — confirm `AssertionsBuilderSpec` builds 11 assertions and the Java error-path test passes (FR-009 / FR-008 baseline).

**Checkpoint**: Baseline green — story work can begin (same-file serialization applies).

---

## Phase 3: User Story 1 - Correct, non-duplicated assertion set (Priority: P1) 🎯 MVP

**Goal**: Java facade returns one assertion per entry (no 2^n), and Scala↔Java sets match.

**Independent Test**: Load `nfr.yml` via the Java facade → exactly 11 assertions; `nfrSingle.yml` → exactly 1; the set equals the Scala builder's (same scopes/thresholds).

### Tests for User Story 1 ⚠️ (write first, must FAIL)

- [x] T005 [US1] Add failing tests in `src/test/java/org/galaxio/gatling/javaapi/assertions/JavaAssertionFromYamlTest.java`: `assertionFromYaml("src/test/resources/nfr.yml")` returns size **11**; `nfrSingle.yml` returns size **1**; and the result equals the expected normalized 11-set (each assertion's scope + `lt` threshold) — the parity baseline. In the expected set, error-rate (`Процент ошибок`) thresholds are **Double**, response-time percentile/max thresholds are **Int** (FR-001, FR-002).
- [x] T006 [P] [US1] Strengthen `src/test/scala/org/galaxio/gatling/assertions/AssertionsBuilderSpec.scala` to assert the SAME expected normalized 11-set (scope/global-vs-detail-path + threshold, error-rate = Double, time metrics = Int), establishing the shared parity reference (FR-002; complements FR-009).

### Implementation for User Story 1

- [x] T007 [US1] Fix exponential duplication in `src/main/java/org/galaxio/gatling/javaapi/Assertions.java`: in `buildPercentileAssertion`/`buildErrorAssertion`/`buildMaxResponseTimeAssertion`, replace `assertionList.addAll(getListAssertions(assertionList, …))` with a single `assertionList.add(key.equals("all") ? allAssertion : detailAssertion)`; remove the self-mutating `getListAssertions` helper (FR-001). Makes T005 green.

**Checkpoint**: US1 green — the highest-severity defect (wrong assertion count) is fixed and parity-locked. MVP deliverable.

---

## Phase 4: User Story 2 - Misconfigured NFR fails loudly (Priority: P1)

**Goal**: Unknown key → WARN + skip; non-numeric value → clear error naming key + value. Both paths.

**Independent Test**: Load `nfr.yml` (APDEX/RPS unknown) → 11 assertions + WARN naming each skipped key; load `nfrNonNumeric.yml` → error whose message contains the key and `abc`, in Scala and Java.

### Tests for User Story 2 ⚠️ (write first, must FAIL)

- [x] T008 [P] [US2] Add failing tests in `src/test/scala/org/galaxio/gatling/assertions/AssertionsBuilderSpec.scala`: loading `nfr.yml` logs a WARN naming `APDEX` and the RPS key while size stays 11 (capture via a test SLF4J appender / log-capture); loading `nfrNonNumeric.yml` throws `IllegalArgumentException` whose message contains the **metric record key** (e.g. `99 перцентиль времени выполнения`, NOT the scope key `all`) and the offending value `abc`; AND loading `nfrSingle.yml` (fractional error-rate `5.5`) succeeds with one Double-threshold assertion (the Scala `toInt`→`toDoubleOption` red) (FR-003, FR-004).
- [x] T009 [US2] Add failing tests in `src/test/java/org/galaxio/gatling/javaapi/assertions/JavaAssertionFromYamlTest.java`: unknown key logs WARN naming the key (size 11); `nfrNonNumeric.yml` throws `AssertionBuilderException` whose message contains the **metric record key** (not the scope key) and the offending value `abc` (FR-003, FR-004).

### Implementation for User Story 2

- [x] T010 [P] [US2] In `src/main/scala/org/galaxio/gatling/assertions/AssertionsBuilder.scala`: add `LazyLogging`; in `buildAssertion` change `case _ => None` to `logger.warn` naming the unknown metric key then skip. Parse thresholds **per metric**: error-rate (`Процент ошибок`) → `value.toDoubleOption` (**Double** — fixes the latent `v.toInt` bug that crashes on fractional percent like `5.5`); response-time percentile + max → `value.toIntOption` (**Int**). On `None`, throw `IllegalArgumentException(s"NFR assertion '<metricKey>': value '<v>' is not a valid number")` (the metric record key, not the scope key) (FR-003, FR-004). Makes T008 green.
- [x] T011 [US2] In `src/main/java/org/galaxio/gatling/javaapi/Assertions.java`: add an SLF4J `Logger`; in `buildAssertion` `default` branch log WARN naming the metric key then skip. Keep the per-metric types — percent via a checked **double** parse, percentile/max via a checked **int** parse (replace bare `parseDouble`/`Integer.valueOf`) — throwing `AssertionBuilderException` whose message names the metric record key + offending value (FR-003, FR-004). Makes T009 green. (Same file as T007 → after T007.)

**Checkpoint**: US1 + US2 green — typos and bad numbers are now loud, not silent/opaque.

---

## Phase 5: User Story 3 - Debuggable failures + reliable non-ASCII keys (Priority: P2)

**Goal**: Exception carries message + cause; Cyrillic keys match independent of default charset.

**Independent Test**: `new AssertionBuilderException("m", cause)` → `getMessage()=="m"`, `getCause()==cause`; Cyrillic-keyed `nfr.yml` → 11 assertions with detail paths intact.

### Tests for User Story 3 ⚠️ (write first, must FAIL)

- [x] T012 [P] [US3] Add tests in `src/test/java/org/galaxio/gatling/javaapi/assertions/JavaAssertionFromYamlTest.java`: `new AssertionBuilderException("m", new RuntimeException("c"))` → `getMessage()` equals `"m"` and `getCause()` is that cause, both non-null (FR-005); plus a Java-side charset check that the Cyrillic `nfr.yml` still yields 11 assertions (FR-006).
- [x] T013 [P] [US3] Add tests in `src/test/scala/org/galaxio/gatling/assertions/AssertionsBuilderSpec.scala`: Cyrillic-keyed `nfr.yml` builds 11 assertions and detail paths retain `myGroup` / `GET /test/uuid` after `toUtf` removal; an in-process assertion that a Cyrillic string round-tripped through ISO-8859-1 is NOT equal to the original (documents why the lossy step is removed) (FR-006).

### Implementation for User Story 3

- [x] T014 [P] [US3] In `src/main/java/org/galaxio/gatling/javaapi/AssertionBuilderException.java`: call `super(msg, cause)` from the constructor so `getMessage()`/`getCause()` are non-null; keep `.msg()`/`.cause()`/`equals`/`hashCode`/`toString` (FR-005). Makes the T012 exception assertions green.
- [x] T015 [P] [US3] In `src/main/scala/org/galaxio/gatling/assertions/AssertionsBuilder.scala`: remove the lossy `toUtf` (`Source.fromBytes(getBytes(),"UTF-8")`) and match the parsed key directly (FR-006). Makes T013 green. (Same file as T010 → after T010.)
- [x] T016 [P] [US3] In `src/main/java/org/galaxio/gatling/javaapi/Assertions.java`: remove the no-op `toUtf` and switch on the raw key (FR-006). Makes the T012 charset check green. (Same file as T007/T011 → after T011.)

**Checkpoint**: US1–US3 green — failures are diagnosable and non-ASCII keys are platform-independent.

---

## Phase 6: User Story 4 - Repeated loading stays efficient (Priority: P3)

**Goal**: No per-call reflection-heavy Jackson init in the Java facade.

**Independent Test**: Two successive `assertionFromYaml` calls return identical results; module init happens once.

### Tests for User Story 4 ⚠️ (write first, must FAIL/strengthen)

- [x] T017 [US4] Add a test in `src/test/java/org/galaxio/gatling/javaapi/assertions/JavaAssertionFromYamlTest.java`: calling `assertionFromYaml("src/test/resources/nfr.yml")` twice returns two equal (size 11, same content) results (FR-007). (Same file as T005/T009/T012 → after them.)

### Implementation for User Story 4

- [x] T018 [US4] In `src/main/java/org/galaxio/gatling/javaapi/Assertions.java`: hoist the `ObjectMapper` to `static final … new ObjectMapper(new YAMLFactory()).findAndRegisterModules()` and remove the per-call `mapper.findAndRegisterModules()` in `getNfr` (FR-007). (Same file as T007/T011/T016 → after them.)

**Checkpoint**: US1–US4 green — load path is correct and no longer re-initializes per call.

---

## Phase 7: User Story 5 - Deprecation notice for the NFR-YAML feature (Priority: P2)

**Goal**: `assertionFromYaml` (Scala + Java) + `AssertionBuilderException` marked deprecated with a generic "replacement coming" message; the call still works.

**Independent Test**: Compiling a reference to `assertionFromYaml` emits a deprecation warning whose message is generic (no version/date/issue link); the deprecated call still returns the 11-set.

### Tests for User Story 5 ⚠️ (write first)

- [x] T019 [P] [US5] In `src/test/java/org/galaxio/gatling/javaapi/assertions/JavaAssertionsCompileTest.java`: keep the signature-lock reference (FR-008) and assert the deprecated `assertionFromYaml` still returns the 11-assertion set; add a meta check that the `@Deprecated`/Javadoc message is generic (contains "deprecated" + "future release"; no version/date/URL); AND assert `AssertionBuilderException.class.isAnnotationPresent(Deprecated.class)` (the public exception class is also deprecated — F7) (FR-008, FR-012).
- [x] T020 [P] [US5] In `src/test/scala/org/galaxio/gatling/assertions/AssertionsBuilderSpec.scala` (or a small meta spec): assert `AssertionsBuilder.assertionFromYaml` carries `@deprecated` with the generic message (no version/date/link) and still returns 11 assertions (FR-012).

### Implementation for User Story 5

- [x] T021 [P] [US5] In `src/main/scala/org/galaxio/gatling/assertions/AssertionsBuilder.scala`: add `@deprecated("NFR-YAML assertion loading is deprecated and will be replaced by new assertions functionality in a future release. It still works for now; watch the changelog.", "1.18.0")` to `assertionFromYaml` (FR-012). (Same file as T010/T015 → after them.)
- [x] T022 [P] [US5] In `src/main/java/org/galaxio/gatling/javaapi/Assertions.java` add `@Deprecated(since = "1.18.0")` + Javadoc `@deprecated` (same generic message) to `assertionFromYaml`; in `src/main/java/org/galaxio/gatling/javaapi/AssertionBuilderException.java` add `@Deprecated(since = "1.18.0")` (FR-012). (Assertions.java same file as T018 → after it.)

**Checkpoint**: All five stories green — fixes done and the feature is signposted as deprecated.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T023 [P] Update changelog / git-cliff notes: WARN on unknown NFR keys (FR-011) and NFR-YAML assertions deprecation with "replacement coming" (FR-012). DEFERRED to commit time: there is no `CHANGELOG.md`; git-cliff generates release notes from conventional commit messages (`release.yml`), so the notes are carried by the commit message, not a file edit. Suggested commit: `fix(assertions): correct NFR-YAML loader (dup, unknown-key WARN, numeric errors, charset, exception) + deprecate`.
- [x] T024 Run the quickstart deliberate-break validation (`quickstart.md` scenarios 1–9): for each fix, revert the production change, confirm ≥1 test fails, restore → green (FR-010 / constitution **Principle III** negative-test discipline). NOTE: this is a **manual/local** verification ritual, not a committed CI test — reviewers should not expect a CI guard for it (SC-009).
- [x] T025 `sbt scalafmtAll scalafmtSbt` then `sbt scalafmtCheckAll scalafmtSbtCheck compile test` — full unit gate green, no Docker.
- [ ] T026 `sbt clean coverage test coverageReport` — confirm statement ≥65% / branch ≥60% (floor unchanged; new branches raise measured coverage). DEFERRED to CI: the calibrated floor is measured over unit **+ `IntegrationTest/test`** (CI runs `sbt clean coverage test "IntegrationTest/test" coverageOff coverageReport coverageAggregate`, which needs Docker/Testcontainers). A unit-only local run would understate branch coverage (redis/jwt/storage need `it`) and falsely trip the floor. This change is strictly coverage-additive (more tests + covered branches), so the CI gate will pass.

---

## Dependencies & Execution Order

### Phase order
Setup (P1) → Foundational baseline (P2) → US1 → US2 → US3 → US4 → US5 → Polish.
Stories are ordered by priority AND by same-file serialization (below), not run in parallel.

### Same-file serialization (critical — overrides naive [P])
- `Assertions.java` is edited by **T007 (US1) → T011 (US2) → T016 (US3) → T018 (US4) → T022 (US5)** — strictly sequential in that order.
- `AssertionsBuilder.scala` is edited by **T010 (US2) → T015 (US3) → T021 (US5)** — sequential.
- `JavaAssertionFromYamlTest.java` is edited by **T005 → T009 → T012 → T017** — sequential.
- `AssertionsBuilderSpec.scala` is edited by **T006 → T008 → T013 → T020** — sequential.
- `AssertionBuilderException.java` (T014) and `JavaAssertionsCompileTest.java` (T019) are each touched once.

### Genuine parallel opportunities
- Setup: **T001 ∥ T002** (different fixtures).
- Across files within a story, where the files differ: e.g. **T010 (scala) ∥ T011 (java)** in US2; **T014 (exception) ∥ T015 (scala) ∥ T016 (java)** in US3 (different files); **T021 (scala) ∥ T022 (java/exception)** in US5.
- Test-writing on different files: **T005 (java test) ∥ T006 (scala test)** in US1; **T008 (scala) ∥ T009 (java)**? — NO, both fine (different files) so **T008 ∥ T009**; **T012 (java) ∥ T013 (scala)**; **T019 (java) ∥ T020 (scala)**.

### TDD within each story
Write the story's test(s) first and see them fail (red) → implement the fix(es) → green.

---

## Implementation Strategy

### MVP (US1 only)
Setup → Foundational baseline → US1 (T005–T007). Stop, validate: Java facade returns
11 (not 2^n) and matches Scala. The highest-severity defect is fixed and demoable.

### Incremental delivery
US1 (correct count) → US2 (loud failures) → US3 (debuggable + charset) → US4 (perf) →
US5 (deprecation) → Polish (changelog, deliberate-break, format/coverage). Each story is
independently testable; later stories never regress earlier ones (parity + baseline tests
guard the shared files).

### Release
After all green and merged to `main` via PR: cut `release/1.18.0` from `main`, tag
`v1.18.0` (NOT `v1.17.x` — already published), push to trigger the release workflow.

---

## Notes

- `-deprecation` is set but NOT `-Xfatal-warnings`/`-Werror`, so deprecating the public
  method emits warnings (not errors) at internal/test call sites — no `@nowarn` needed.
  The Scala public `assertionFromYaml` delegates to the non-deprecated `assertionsFrom`
  seam, so it does not warn on itself; Scala unit tests use the seam (no warning).
- No mocks anywhere (real YAML fixtures + real builders) — nothing to mock-vs-mock.
- No Testcontainers / DSL-component / e2e-WireMock tasks — those layers don't fit (the
  loader emits no HTTP and drives no Gatling runtime).
- Commit after each task or logical group; keep the build green.
