---
description: "Task list for Secret Masking & Leak Prevention (v1.23.0)"
---

# Tasks: Secret Masking & Leak Prevention (v1.23.0)

**Input**: Design documents from `specs/007-secret-masking-leak-prevention/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/public-api.md](contracts/public-api.md), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. Constitution III mandates test-first (TDD): write the failing test before the code (red → green → refactor); assert exact values + ≥1 negative/boundary case; logback `ListAppender` for log capture (the `captureWarns` idiom in `AssertionsBuilderSpec`); `Console.withOut(ByteArrayOutputStream)` to assert stdout is empty; ScalaMock only for leaf deps (none needed here); never mock the Gatling runtime where a real path exists.

**Organization**: Grouped by user story (P1→P4). The central masking engine (`ConfigValueMasking`) is the shared prerequisite (Phase 2), realizing the single-central-list / term-coverage / extensibility requirements every sink consumes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1=secrets-never-in-logs (P1), US2=URL-userinfo (P2), US3=diagnostics-via-SLF4J (P3), US4=recommended-config-no-pollution (P4)

## Path Conventions

Published Scala library, single project. Core: `src/main/scala/org/galaxio/gatling/`. Tests: `src/test/scala/org/galaxio/gatling/`. E2e overlay: `examples/scala-sbt-example/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm a green baseline so later red tests are meaningful.

- [X] T001 Verify clean baseline from repo root: `sbt scalafmtCheckAll scalafmtSbtCheck compile test` passes on branch `007-secret-masking-leak-prevention` before any change.

---

## Phase 2: Foundational (Central Masking Engine — Blocking Prerequisites)

**Purpose**: The single central helper every sink consumes. Realizes FR-002 (single list), FR-003 (term coverage), FR-012 (config-extensible) and the #208 over-match fix. **No user story can be correctly implemented until this is complete** — US1 sinks, US2's `redactUserInfo`, and US3/US4 all build on `ConfigValueMasking`.

**⚠️ CRITICAL**: BLOCKS all user stories.

- [X] T002 [P] Write FAILING unit tests for the hardened matching in `src/test/scala/org/galaxio/gatling/config/SimulationConfigUtilsSpec.scala` (extend `ConfigValueMaskingSpec`): assert FR-003 positives (`authorizationHeader`, `bearerToken`, `passphrase`, `apiKey`, `clientSecret` → `isSensitive == true` / `displayValue == "******"`) AND the #208 negatives (`roleId`, `roleIdPrefix`, `tokenBucketSize`, `apiKeyboard`, `secretariat`, `baseUrl` → `isSensitive == false`). Keep existing masking assertions green.
- [X] T003 [P] Write FAILING unit tests for config-extensibility (FR-012) in the same spec: build `ConfigValueMasking` from a `Config` with `picatinny.redaction.additionalSensitiveKeys=["mycorptoken"]` → `displayValue("mycorptoken","v") == "******"`; ABSENT `picatinny.redaction` → built-ins still mask (defaults); merge-not-replace boundary → a user list omitting `password` does NOT un-mask `password`.
- [X] T004 Harden `src/main/scala/org/galaxio/gatling/config/ConfigValueMasking.scala`: replace raw `contains` with **last-path-segment + word-boundary** matching (split on `.`/`/`, normalize camelCase/snake/kebab words, whole-word term match + `…Password/Secret/Token/Key` compound rule); add FR-003 terms (`authorization`, `bearer`, `passphrase`, `key`-as-compound); keep `"******"` sentinel; **widen visibility `private[config]` → `private[gatling]`** (internal, compat-exempt). Makes T002 green.
- [X] T005 Add `RedactionSettings(additionalSensitiveKeys: List[String] = Nil, replacement: String = "******")` + config-extensible term load to `ConfigValueMasking` and wire its resolution ONCE from the optional `picatinny.redaction` block (guarded by `config.hasPath`) in `src/main/scala/org/galaxio/gatling/config/SimulationConfigUtils.scala`; effective terms = built-in ++ user (lowercased, de-duplicated, **merge-not-replace**). Makes T003 green. (Depends on T004.)

**Checkpoint**: Central masking engine hardened, extensible, reusable from any package. User stories can begin.

---

## Phase 3: User Story 1 - Secrets Never Appear in Logs (Priority: P1) 🎯 MVP

**Goal**: No secret value (config leaves, nested blocks, signing keys, `-D` args) appears in plaintext in any sink, at any log level.

**Independent Test**: Run a simulation configured with known secret values; capture all log output; assert none appear in plaintext; assert non-sensitive values still visible.

### Tests for User Story 1 (write FIRST, ensure they FAIL)

- [X] T006 [P] [US1] Write FAILING test for nested-config leaf-walk (FR-004) in `src/test/scala/org/galaxio/gatling/config/SimulationConfigUtilsSpec.scala`: build HOCON `http { url=…, token="abc" }`; `displayConfig(cfg)` contains `http.url = …` visibly, `http.token = ******`, never `abc`; boundary: deeply nested `a.b.c.secret` masked; block with no sensitive leaf renders all values visibly.
- [X] T007 [P] [US1] Write FAILING test for `SigningKey` toString redaction (FR-005) in a NEW `src/test/scala/org/galaxio/gatling/utils/jwt/SigningKeySpec.scala`: `StringSecret("topsecret").toString == "StringSecret(******)"` and excludes `topsecret`; `AsymmetricKey(key).toString == "AsymmetricKey(******)"`; exact-value API guard: `StringSecret("topsecret").value == "topsecret"`.
- [X] T008 [P] [US1] Write FAILING test for `-D` JVM-arg redaction (FR-006) in `src/test/scala/org/galaxio/gatling/diagnostics/DiagnosticsSpec.scala`: redactor over `Seq("-Xmx2g","-DvaultToken=hunter2","-Dapp.name=foo")` → `-DvaultToken=******`, others untouched; negative: `-DvaultToken` (no `=value`) passes through; output never contains `hunter2`.
- [X] T009 [US1] Write FAILING test for end-to-end masked param log line (FR-001) in `SimulationConfigUtilsSpec.scala`: attach `ListAppender` to the config logger, read a sensitive path via `getValueByType`, assert the INFO line contains `******` not the raw value; negative: a non-sensitive path logs the literal value. (Same file as T006 → after T006.)

### Implementation for User Story 1

- [X] T010 [US1] Add `displayConfig(cfg: com.typesafe.config.Config): String` to `src/main/scala/org/galaxio/gatling/config/ConfigValueMasking.scala`: walk `cfg.entrySet().asScala`, sort by key, mask each leaf via `displayValue(entry.getKey, entry.getValue.unwrapped)`, join `s"$key = $shown"` with `\n`; do NOT `resolve()`. Makes T006 green. (Depends on T004.)
- [X] T011 [US1] At the single line-51 log site of `getValueByType` in `src/main/scala/org/galaxio/gatling/config/SimulationConfigUtils.scala`, when the read value is a `com.typesafe.config.Config` (the `case ConfigTag` value-read at ~line 68), render it via `ConfigValueMasking.displayConfig(value)` instead of `displayValue(path, value)` (which stringifies the whole block checking only the parent path). Scalars still go through `displayValue`. Makes T009 green. (Depends on T010.)
- [X] T012 [P] [US1] Override `toString` on `StringSecret` and `AsymmetricKey` in `src/main/scala/org/galaxio/gatling/utils/jwt/SigningKey.scala` to `"StringSecret(******)"` / `"AsymmetricKey(******)"`; do NOT change types/fields/`value`/`apply`/`unapply`/`copy`. Makes T007 green.
- [X] T013 [US1] Add per-arg `-D` redaction in `src/main/scala/org/galaxio/gatling/diagnostics/Diagnostics.scala:21`: split each `-Dkey=value`/`-Dkey:value`, run the key through `ConfigValueMasking.isSensitive`, replace sensitive values with `******` keeping `-Dkey=******`; non-`-D`/value-less args pass through. Makes T008 green. (Reuses the Phase-2 engine; `private[gatling]` visibility from T004 required.)

**Checkpoint**: All secret sinks (config scalar + nested block, signing keys, `-D` args) masked. US1 fully testable independently.

---

## Phase 4: User Story 2 - URL Credentials Stripped from Output (Priority: P2)

**Goal**: Any URL the library prints/logs has its `user:password@` userinfo stripped, fail-safe on unparseable input.

**Independent Test**: Configure a userinfo-bearing base URL; capture banner output; assert the password is absent, host present.

### Tests for User Story 2 (write FIRST, ensure they FAIL)

- [X] T014 [P] [US2] Write FAILING tests for `redactUserInfo` (FR-007) in `src/test/scala/org/galaxio/gatling/config/SimulationConfigUtilsSpec.scala`: `redactUserInfo("https://user:pass@host:8080/p") == "https://******@host:8080/p"`; `redactUserInfo("https://host/p")` unchanged; fail-safe boundary: malformed `"ht!tp://u:p@x"` does NOT throw and returns a string containing neither `:p@` nor `pass`; opaque `mailto:a@b` unchanged.

### Implementation for User Story 2

- [X] T015 [US2] Add `private[gatling] redactUserInfo(raw: String): String` to `src/main/scala/org/galaxio/gatling/config/ConfigValueMasking.scala`: parse via `new java.net.URI(raw)` in try; userinfo = `Option(getRawUserInfo) orElse Option(getRawAuthority).filter(_.contains('@')).map(_.takeWhile(_!='@'))`; empty→return raw; present→string-replace `userinfo+"@"` with `"******@"`; on any `Throwable`→`raw.replaceFirst("://[^/@\\s]*@","://******@")` then conservative constant; never throw/leak. Makes T014 green.
- [X] T016 [US2] Apply `ConfigValueMasking.redactUserInfo` to the base-URL value in `src/main/scala/org/galaxio/gatling/diagnostics/StartupBanner.scala:54`.

**Checkpoint**: URL userinfo stripped at the banner sink, independent of US1/US3.

---

## Phase 5: User Story 3 - Diagnostic Output Routable via Standard Logging (Priority: P3)

**Goal**: Banner + diagnostics flow through SLF4J as a single event per block under a dedicated category — suppressible/redirectable, alignment-safe.

**Independent Test**: Capture stdout and the logging framework separately; suppress the category → stdout empty; enable → one event through the framework.

**⚠️ File-overlap note**: `Diagnostics.scala` also edited in T013 (US1), `StartupBanner.scala` in T016 (US2). Sequence US3 edits after those (not `[P]` with them).

### Tests for User Story 3 (write FIRST, ensure they FAIL)

- [X] T017 [P] [US3] Write FAILING tests for single-event banner via SLF4J (FR-008/009) in `src/test/scala/org/galaxio/gatling/diagnostics/DiagnosticsSpec.scala`: attach `ListAppender` to `org.galaxio.gatling.diagnostics`, emit banner with flag enabled → `appender.list.size == 1`, `getLoggerName` starts with `org.galaxio.gatling.diagnostics`, `getMessage` contains embedded `\n` + ASCII chars (`|`,`/`,`_`); boundary: NOT split into multiple events.
- [X] T018 [P] [US3] Write/Update FAILING test asserting stdout is empty (no `println`) in `src/test/scala/org/galaxio/gatling/diagnostics/UtilityIntegrationSpec.scala`: with `Console.withOut(ByteArrayOutputStream)` around banner+diagnostics emit, assert captured stdout is empty while the event is captured via `ListAppender`.

### Implementation for User Story 3

- [X] T019 [US3] Convert `src/main/scala/org/galaxio/gatling/diagnostics/StartupBanner.scala` from `println` to `extends StrictLogging`, converting ALL THREE emit sites (lines 15, 27, 30) so EACH emits its whole banner string as a single `logger.info(render(...))` call (one event per emission, never line-by-line); keep `isEnabled` gating. (After T016.)
- [X] T020 [US3] Convert `src/main/scala/org/galaxio/gatling/diagnostics/Diagnostics.scala` from `println` to `extends StrictLogging`, emitting the whole block in ONE `logger.info(render())` call (line 13); keep `isEnabled` gating. (After T013.)
- [X] T021 [US3] Update `Utility` / existing diagnostics specs that asserted stdout (`UtilityIntegrationSpec`, `DiagnosticsSpec`) to assert via `ListAppender` on `org.galaxio.gatling.diagnostics` instead of `Console.withOut`. Makes T017/T018 green.

**Checkpoint**: Banner/diagnostics fully routed through SLF4J, single event, suppressible by category.

---

## Phase 6: User Story 4 - Recommended Logging Config Without Classpath Pollution (Priority: P4)

**Goal**: Library ships no logback on its main classpath; recommended config delivered via docs + the runnable example overlay (prefix-free banner, suppressible).

**Independent Test**: Inspect the published artifact → no `logback.xml`/`logback-test.xml` on main classpath; consumer with own config sees no conflict; example overlay renders the banner prefix-free.

**⚠️ Depends on US3**: the overlay BANNER category only has effect once the banner routes through SLF4J (T019). Run US4 after US3.

### Tests for User Story 4 (write FIRST, ensure they FAIL)

- [X] T022 [P] [US4] Write a compile/classpath guard test (FR-010) in `src/test/scala/org/galaxio/gatling/` asserting the library main classpath ships no logback config: `getClass.getResource("/logback.xml")` resolves only to the test resource, and document/assert `project/Dependencies.scala` declares no compile-scope logback; negative: logback IS present on the Test classpath (transitive).

### Implementation for User Story 4

- [X] T023 [US4] Confirm `build.sbt` / `project/Dependencies.scala` declare no first-party logback at compile scope and the library has no `src/main/resources/logback.xml` (no-op if already absent — verify + document the FR-010 invariant). Makes T022 green.
- [X] T024 [P] [US4] Update `examples/scala-sbt-example/src/test/resources/logback.xml`: add a `BANNER` `ConsoleAppender` with bare `<pattern>%msg%n</pattern>`; bind `<logger name="org.galaxio.gatling.diagnostics" level="INFO" additivity="false"><appender-ref ref="BANNER"/></logger>` so the banner renders prefix-free, once, and stays suppressible (set OFF/WARN). (FR-011)
- [X] T025 [P] [US4] Add `docs/logging.md` (recommended logback snippet + override path: `-Dlogback.configurationFile`, logback-test.xml precedence, the diagnostics category) that references `examples/scala-sbt-example/src/test/resources/logback.xml` as the canonical example (single source — no divergent second copy of the config). (FR-011)
- [ ] T026 [US4] E2e validation (FR-011): from `examples/scala-sbt-example` run `sbt Gatling/test`; assert green and the banner line carries NO date/level prefix while ordinary log lines keep the prefixed pattern. (After T024, T019.) — **DEFERRED TO CI**: this is the overlay publish+WireMock flow (needs `PICATINNY_VERSION` published to mavenLocal). Overlay `logback.xml` verified well-formed + correct here; the prefix-free single-event behavior it exercises is proven green at the unit layer (T017/T018).

**Checkpoint**: All four stories independently functional; recommended config delivered without polluting consumer classpaths.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T027 Run `sbt scalafmtAll scalafmtSbt` then `sbt scalafmtCheckAll scalafmtSbtCheck compile test` — full green.
- [X] T028 Confirm coverage ≥ floor (65% stmt / 60% branch) via `sbt clean coverage test coverageReport`; no padding on generated code.
- [ ] T029 [P] Run [quickstart.md](quickstart.md) validation end-to-end, including the `grep -c 'hunter2'` smoke (expect 0). — **PARTIAL**: unit/functional + compile-guard + coverage steps all ran green here; the `grep` smoke runs a real sim and is **DEFERRED TO CI** with T026.
- [X] T030 [P] Update CHANGELOG / release notes for v1.23.0 (security: secret-masking + leak prevention; #208/#87/#88).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: after Setup. **BLOCKS US1–US4** (central engine).
- **US1 (Phase 3)**: after Foundational. MVP.
- **US2 (Phase 4)**: after Foundational. Independent of US1/US3 (adds `redactUserInfo` to the same helper file → sequence its `ConfigValueMasking` edit after T010/T015 ordering within the file).
- **US3 (Phase 5)**: after Foundational. Edits `Diagnostics.scala` (after T013) and `StartupBanner.scala` (after T016) — file overlap with US1/US2.
- **US4 (Phase 6)**: after **US3** (overlay BANNER category needs SLF4J routing from T019).
- **Polish (Phase 7)**: after all desired stories.

### Within Each User Story

- Tests written and FAILING before implementation (red → green → refactor).
- Engine (Phase 2) before any sink application.
- `displayConfig` (T010) before its sink wiring (T011); `redactUserInfo` (T015) before its sink (T016).

### Parallel Opportunities

- T002, T003 (Phase 2 tests) in parallel.
- US1 tests T006, T007, T008 in parallel (different files); T009 after T006 (same file).
- US1 impl: T012 (`SigningKey.scala`) parallel with config-side T010/T011 and diagnostics T013 (different files).
- Across stories after Foundational: US1 and US2 can proceed in parallel (different sink files), but both add methods to `ConfigValueMasking.scala` — serialize the edits to that one file.
- US4 T024 (overlay logback), T025 (docs) in parallel.

---

## Parallel Example: User Story 1

```bash
# Failing tests first (different files → parallel):
Task: "T006 nested-config leaf-walk test in SimulationConfigUtilsSpec.scala"
Task: "T007 SigningKey toString test in SigningKeySpec.scala"
Task: "T008 -D arg redaction test in DiagnosticsSpec.scala"

# Then implementation across different files:
Task: "T012 toString override in SigningKey.scala"
Task: "T010 displayConfig in ConfigValueMasking.scala"
Task: "T013 -D redaction in Diagnostics.scala"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational (central engine, CRITICAL) → 3. Phase 3 US1 → **STOP & VALIDATE** secrets masked at every sink → demo. This alone closes the core #208 security gap.

### Incremental Delivery

1. Setup + Foundational → engine ready.
2. US1 (P1) → secrets never in logs → MVP.
3. US2 (P2) → URL userinfo stripped.
4. US3 (P3) → diagnostics via SLF4J (single event).
5. US4 (P4) → recommended config + overlay (needs US3).

### Notes

- [P] = different files, no dependencies. Edits to the shared `ConfigValueMasking.scala` (T004/T005/T010/T015) are serialized.
- Backward compat: no public signature change; `SigningKey` `toString`-only; `private[config]`→`private[gatling]` internal; `picatinny.redaction.*` additive+optional. → v1.23.0 MINOR.
- Commit after each task or logical group; keep the build green.
