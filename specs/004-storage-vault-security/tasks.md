---
description: "Task list for Storage, JDBC & Vault Security Hardening"
---

# Tasks: Storage, JDBC & Vault Security Hardening

**Input**: Design documents from `specs/004-storage-vault-security/`

**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓

**TDD**: Tests written first (red → green) per Constitution §III. Test tasks precede impl tasks within each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no cross-dependency)
- **[Story]**: Maps to user story from spec.md (US1–US5)

---

## Phase 1: Setup

**Purpose**: Establish clean baseline before any changes.

- [X] T001 Run `sbt compile` on `main` branch; confirm zero errors in `StorageBackend.scala`, `VaultFeeder.scala`, `ProfileBuilderNew.scala`, `InjectionProfileParser.scala`

**Checkpoint**: Clean compile baseline confirmed — no build.sbt changes required.

---

## Phase 2: Foundational — DefaultFormats Deduplication (FR-006)

**Purpose**: Internal refactor touching `StorageBackend.scala` — must complete before US1 to avoid merge conflict in the same file.

- [X] T002 In `src/main/scala/org/galaxio/gatling/storage/StorageBackend.scala`: add `private object StorageFormats { implicit val formats: Formats = DefaultFormats }` before the backend class definitions; remove the three `private implicit val formats: Formats = DefaultFormats` lines from `JsonFileBackend` (line ~38), `RedisBackend` (line ~60), and `JdbcStorageBackend` (line ~116) class bodies; add `import StorageFormats.formats` at the top of each class body; run `sbt compile` to confirm zero errors

**Checkpoint**: Single `DefaultFormats` definition — `sbt compile` green.

---

## Phase 3: User Story 1 — SQL Injection Protection (Priority: P1) 🎯 MVP

**Goal**: `JdbcStorageBackend` rejects invalid `tableName` at construction before any DB connection.

**Independent Test**: `sbt testOnly *JdbcStorageBackendSpec` — all tests pass including 4 new ones.

### Tests for User Story 1

> **Write these FIRST. Run `sbt testOnly *JdbcStorageBackendSpec` and confirm 3 new tests FAIL.**

- [X] T003 [US1] In `src/test/scala/org/galaxio/gatling/storage/JdbcStorageBackendSpec.scala`: add 4 new test cases inside the existing `"JdbcStorageBackend" should` block — (a) `"reject tableName with SQL injection payload"` → `intercept[IllegalArgumentException] { JdbcStorageBackend("jdbc:recording:", tableName = "bad; DROP TABLE x; --") }` and assert message contains `"Invalid tableName"`; (b) `"reject empty tableName"` → same intercept for `tableName = ""`; (c) `"reject tableName starting with a digit"` → same for `tableName = "123bad"`; (d) `"accept a valid tableName"` → `noException should be thrownBy JdbcStorageBackend("jdbc:recording:", tableName = "my_results_2024")` using the `RecordingJdbcDriver` pattern already in the file; run `sbt testOnly *JdbcStorageBackendSpec` and confirm (a)(b)(c) fail, (d) passes

### Implementation for User Story 1

- [X] T004 [US1] In `src/main/scala/org/galaxio/gatling/storage/StorageBackend.scala`, `JdbcStorageBackend` class body (after the field declarations): add `require(tableName.matches("[A-Za-z_][A-Za-z0-9_]*"), s"Invalid tableName: '$tableName'. Must be a valid SQL identifier (letters, digits, underscores, starting with a letter or underscore)")` as the first statement; run `sbt testOnly *JdbcStorageBackendSpec` and confirm all 4 new tests plus all existing tests pass

**Checkpoint**: `JdbcStorageBackendSpec` fully green — US1 independently testable.

---

## Phase 4: User Story 2 — Vault HTTP Warning + Unit Tests (Priority: P2)

**Goal**: `VaultFeeder` logs WARN before any network call when URL is non-HTTPS non-localhost; `mergeWithStrategy` and JSON-parse error paths covered by fast unit tests.

**Independent Test**: `sbt testOnly *VaultFeederSpec` — all tests pass including 8 new ones.

> US2 touches different files than US1. Implementation of T005–T007 can overlap with US1 once T002 is done.

### Refactor for Testability (prerequisite within US2)

- [X] T005 [P] [US2] In `src/main/scala/org/galaxio/gatling/feeders/VaultFeeder.scala`: (a) change `mergeWithStrategy` from `private` to `private[feeders]`; (b) extract the `Try(parse(...)).fold(...)` expression from `login` into a new `private[feeders] def parseLoginResponse(body: String, loginUrl: String): String` method — same logic, just a separate method; (c) extract the `Try(parse(...)).fold(...)` expression from `readSecret` into a new `private[feeders] def parseSecretResponse(body: String, secretPath: String): Record[String]` method; (d) add `private[feeders] def isUnsafeVaultUrl(vaultUrl: String): Boolean` that parses the URL with `java.net.URI`, returns `true` if scheme is `"http"` (case-insensitive) AND host is neither `"localhost"` nor `"127.0.0.1"`, returns `false` otherwise; update `login` and `readSecret` to delegate to the new extracted methods; run `sbt compile` to confirm

### Tests for User Story 2

> **Write these AFTER T005 but BEFORE T007. Run `sbt testOnly *VaultFeederSpec` and confirm new tests FAIL.**

- [X] T006 [US2] In `src/test/scala/org/galaxio/gatling/feeders/VaultFeederSpec.scala`: add 8 test cases — `"VaultFeeder.mergeWithStrategy"` block: (a) `"FailOnDuplicate throws IllegalArgumentException naming the duplicate key"` — call `VaultFeeder.mergeWithStrategy(List(("k","v1"),("k","v2")), DuplicateKeyStrategy.FailOnDuplicate)` and assert `IllegalArgumentException` with `"k"` in message; (b) `"LastWins returns map with last value"` — call with same pairs, `LastWins`, assert result `== Map("k" -> "v2")`; (c) `"FirstWins returns map with first value"` — call same, `FirstWins`, assert `Map("k" -> "v1")`; `"VaultFeeder.parseLoginResponse"` block: (d) `"throws RuntimeException on malformed JSON"` — call `VaultFeeder.parseLoginResponse("not-json", "http://vault/v1/auth/approle/login")` and assert `RuntimeException` with `"Failed to parse Vault login response"` in message; `"VaultFeeder.parseSecretResponse"` block: (e) `"throws RuntimeException when data field is absent"` — call `VaultFeeder.parseSecretResponse("{}", "secret/mypath")` and assert `RuntimeException` with `"Failed to extract secret data"` and `"mypath"` in message; `"VaultFeeder.isUnsafeVaultUrl"` block: (f) `"returns true for http non-localhost"` — assert `VaultFeeder.isUnsafeVaultUrl("http://vault.prod.internal") == true`; (g) `"returns false for http localhost"` — `VaultFeeder.isUnsafeVaultUrl("http://localhost:8200") == false`; (h) `"returns false for https"` — `VaultFeeder.isUnsafeVaultUrl("https://vault.prod.internal") == false`; run `sbt testOnly *VaultFeederSpec` and confirm (a)(d)(e)(f) fail

### Implementation for User Story 2

- [X] T007 [US2] In `src/main/scala/org/galaxio/gatling/feeders/VaultFeeder.scala`: add `private def warnIfNotHttps(vaultUrl: String): Unit = if (isUnsafeVaultUrl(vaultUrl)) logger.warn(s"VaultFeeder: vaultUrl '$vaultUrl' uses plaintext HTTP — credentials will be sent unencrypted. Use HTTPS for non-local hosts.")` ; insert `warnIfNotHttps(vaultUrl)` as the first statement (before `Using.resource(THttpClient(...))`) in `apply`, in all three `fromPaths` overloads (only the one that creates THttpClient — the other two delegate to it), and in `withToken`; run `sbt testOnly *VaultFeederSpec` — all 8 new tests plus all existing tests pass

**Checkpoint**: `VaultFeederSpec` fully green — US2 independently testable.

---

## Phase 5: User Story 3 — Profile Path Traversal Prevention (Priority: P3)

**Goal**: `ProfileBuilderNew.buildFromYaml` and `buildFromYamlJava` reject traversal paths before any file I/O.

**Independent Test**: `sbt testOnly *ProfileBuilderTest` — all tests pass including new traversal tests.

> US3 and US5 touch different files — can be worked in parallel. US3 and US4 touch the same file — must be sequential.

### Tests for User Story 3

> **Write FIRST. Run `sbt testOnly *ProfileBuilderTest` and confirm new tests FAIL.**

- [X] T008 [US3] In `src/test/scala/org/galaxio/gatling/profile/ProfileBuilderTest.scala`: add 3 test cases inside the `"ProfileBuilderNew.buildFromYaml"` block — (a) `"reject path that escapes the working directory via traversal"` → assert the `ProfileBuilderException` is thrown for path `"../../etc/passwd"` and message contains `"traversal"` or `"containment"`; (b) `"reject absolute path outside working directory"` → assert `ProfileBuilderException` for `"/etc/passwd"`; (c) `"accept a path that normalizes within the project"` → assert `ProfileBuilderException` is NOT thrown for path `"src/test/resources/profileTemplates/../profileTemplates/profile1.yml"` (normalizes to a valid path inside project); add same 3 cases for `buildFromYamlJava` block; run `sbt testOnly *ProfileBuilderTest` — new tests fail

### Implementation for User Story 3

- [X] T009 [US3] In `src/main/scala/org/galaxio/gatling/profile/ProfileBuilderNew.scala`: in `buildFromYaml`, after the `fullPath <- ...map(Paths.get(_, path))` step in the for-comprehension, add a validation step `_ <- { val base = Paths.get(sys.props.getOrElse("user.dir","")).toAbsolutePath.normalize(); val resolved = fullPath.toAbsolutePath.normalize(); Either.cond(resolved.startsWith(base), (), new SecurityException(s"Path traversal detected: resolved path '$resolved' escapes project base '$base'")) }` and update `toProfileBuilderException` to handle `SecurityException`; apply the same guard in `buildFromYamlJava` inside the try block using `Paths.get(...).toAbsolutePath.normalize()` + `startsWith` check before `Source.fromFile`; run `sbt testOnly *ProfileBuilderTest` — all tests including new ones pass

**Checkpoint**: `ProfileBuilderTest` fully green for traversal cases — US3 independently testable.

---

## Phase 6: User Story 4 — Malformed CSV Header Handling (Priority: P4)

**Goal**: `Request.toRequest` throws `ProfileBuilderException` (not `MatchError`) on unrecognized header strings.

**Independent Test**: New tests in `ProfileBuilderTest` covering the malformed-header path.

> Must follow US3 (same source file `ProfileBuilderNew.scala`).

### Tests for User Story 4

> **Write FIRST. Run `sbt testOnly *ProfileBuilderTest` and confirm new tests FAIL.**

- [X] T010 [US4] In `src/test/scala/org/galaxio/gatling/profile/ProfileBuilderTest.scala`: add a new `"Request.toRequest"` block — (a) `"throw ProfileBuilderException on malformed header with no colon"` → construct `Request("r", "100 rph", None, Params("GET", "/", Some(List("bad-header-no-colon")), None))` and assert `intercept[ProfileBuilderException] { r.toRequest }` with message containing `"bad-header-no-colon"`; (b) `"throw ProfileBuilderException on empty header string"` → same for `Some(List(""))`; (c) `"correctly parse valid header Content-Type: application/json"` → `r.toRequest` does not throw and the resulting `HttpRequestBuilder` contains the header (assert via inspecting the builder or just assert no exception); run `sbt testOnly *ProfileBuilderTest` — (a)(b) fail with `MatchError`, (c) passes

### Implementation for User Story 4

- [X] T011 [US4] In `src/main/scala/org/galaxio/gatling/profile/ProfileBuilderNew.scala`, `Request.toRequest` method: replace `.headers(requestHeaders.map { case regexHeader(a, b) => (a, b) }.toMap)` with `.headers(requestHeaders.map { case regexHeader(a, b) => (a, b); case bad => throw ProfileBuilderException(s"Malformed header: '$bad'. Expected format is 'Name: Value'", new IllegalArgumentException(bad)) }.toMap)`; run `sbt testOnly *ProfileBuilderTest` — all tests including new ones pass

**Checkpoint**: `ProfileBuilderTest` fully green — US4 independently testable.

---

## Phase 7: User Story 5 — InjectionProfileParser Arity Validation (Priority: P5)

**Goal**: `InjectionProfileParser` throws an explicit `IllegalArgumentException` (not `IndexOutOfBoundsException`) on steps with insufficient arity.

**Independent Test**: New `InjectionProfileParserSpec` — all tests pass.

> US5 touches different files than US3/US4 — can be worked in parallel with Phase 5.

### Tests for User Story 5

> **Write FIRST. Run `sbt testOnly *InjectionProfileParserSpec` and confirm new tests FAIL.**

- [X] T012 [P] [US5] Create new file `src/test/scala/org/galaxio/gatling/diagnostics/InjectionProfileParserSpec.scala` in `package org.galaxio.gatling.diagnostics`; extend `AnyWordSpec with Matchers with OptionValues`; import `io.gatling.core.controller.inject.closed._`, `io.gatling.core.controller.inject.open._`, `scala.concurrent.duration._`; add 5 test cases — (a) `"fromClosed with a standard RampConcurrentUsersInjection produces correct WorkloadSettings"` → `InjectionProfileParser.fromClosed(List(RampConcurrentUsersInjection(10, 50, 60.seconds)))` asserts `isDefined`, `intensityRps > 0`, `unit == "users"`; (b) `"fromOpen with a standard RampRateOpenInjection produces correct WorkloadSettings"` → `InjectionProfileParser.fromOpen(List(RampRateOpenInjection(1.0, 10.0, 60.seconds)))` asserts `isDefined`; (c) `"fromClosed with empty step list returns None"` → `InjectionProfileParser.fromClosed(Nil) shouldBe None`; (d) `"fromClosed with a zero-arity Product other step throws IllegalArgumentException"` → create a minimal `case object ZeroArityStep extends ClosedInjectionStep` inside the test class (note: if `ClosedInjectionStep` is sealed and cannot be extended, use a reflective proxy or test `doubleField`/`longField` directly via `private[gatling]` access); (e) at minimum assert that calling `fromClosed` with a known-bad step shape results in either `IllegalArgumentException` or correct safe fallback per the implemented guard; run `sbt testOnly *InjectionProfileParserSpec` — standard injection tests pass, arity guard tests fail if guard not yet implemented

### Implementation for User Story 5

- [X] T013 [P] [US5] In `src/main/scala/org/galaxio/gatling/diagnostics/InjectionProfileParser.scala`: in `closedSegments`, inside the `case other =>` branch, add `require(other.productArity >= 2, s"Unsupported closed injection step '${other.productPrefix}' with productArity=${other.productArity}; expected ≥2 fields (start, end, [duration...])")` before `val start = doubleField(other, 0)` and before `val end = doubleField(other, math.max(0, other.productArity - 2))`; in `openSegments`, inside the `case other =>` branch, add `require(other.productArity >= 1, s"Unsupported open injection step '${other.productPrefix}' with productArity=${other.productArity}; expected ≥1 field")` before `val rate = rateFromUsers(longField(other, 0), duration)`; run `sbt testOnly *InjectionProfileParserSpec` — all tests pass

**Checkpoint**: `InjectionProfileParserSpec` fully green — US5 independently testable.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Format, full test run, integration smoke test.

- [X] T014 [P] Run `sbt scalafmtAll scalafmtSbt` to format all changed files; commit format-only change if any diffs produced
- [X] T015 Run `sbt scalafmtCheckAll scalafmtSbtCheck compile test` — full CI gate must pass with zero failures
- [X] T016 Run `sbt "IntegrationTest / test"` — `JdbcStorageIntegrationSpec` and `VaultIntegrationSpec` pass with default `tableName = "gatling_session_storage"` (satisfies the new validation rule)

**Checkpoint**: All gates green — feature ready for PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: No dependencies — start immediately
- **Foundational (T002)**: Depends on T001; BLOCKS US1 (same file)
- **US1 (T003–T004)**: Depends on T002
- **US2 (T005–T007)**: Depends on T001 only; can start in parallel with US1 after T001
- **US3 (T008–T009)**: Depends on T001 only; can start in parallel with US1/US2
- **US4 (T010–T011)**: Depends on T009 (same file as US3 — must follow)
- **US5 (T012–T013)**: Depends on T001 only; can run in parallel with US1/US2/US3
- **Polish (T014–T016)**: Depends on all story phases complete

### Within Each Story

```
Tests (red) → Implementation (green) → Story checkpoint
```

### Parallel Opportunities

- **T005 ∥ T008 ∥ T012**: US2 refactor / US3 tests / US5 tests can all start simultaneously after T001
- **T003 ∥ T005**: US1 tests and US2 refactor are independent files
- **T013 ∥ T009**: US5 impl and US3 impl touch different files — can overlap

---

## Parallel Example: After Foundational Phase

```
T003 (US1 tests, JdbcStorageBackendSpec)
  → T004 (US1 impl, StorageBackend.scala)

T005 (US2 refactor, VaultFeeder.scala)         ← in parallel with T003
  → T006 (US2 tests, VaultFeederSpec)
    → T007 (US2 impl, VaultFeeder.scala)

T008 (US3 tests, ProfileBuilderTest)            ← in parallel with T003/T005
  → T009 (US3 impl, ProfileBuilderNew.scala)
    → T010 (US4 tests, ProfileBuilderTest)
      → T011 (US4 impl, ProfileBuilderNew.scala)

T012 (US5 tests+file, InjectionProfileParserSpec)  ← in parallel with all above
  → T013 (US5 impl, InjectionProfileParser.scala)
```

---

## Implementation Strategy

### MVP First (US1 only)

1. T001 → T002 → T003 → T004
2. **STOP & VALIDATE**: `sbt testOnly *JdbcStorageBackendSpec` green
3. Ship US1 as a standalone security patch if needed

### Incremental Delivery

1. T001 → T002 (foundation)
2. T003–T004 (US1) + T005–T007 (US2) in parallel → both green
3. T008–T009 (US3) → T010–T011 (US4), in parallel with T012–T013 (US5)
4. T014–T016 (polish)

---

## Notes

- `[P]` = different files, no incomplete-task dependency — safe to run concurrently
- `[Story]` traces each task to its user story for traceability
- TDD: every story's tests run first (confirmed failing), then implementation makes them green
- `JdbcTestSupport.RecordingJdbcDriver` already available in `JdbcStorageBackendSpec` scope — reuse for T003
- `mergeWithStrategy`, `parseLoginResponse`, `parseSecretResponse`, `isUnsafeVaultUrl` all become `private[feeders]` in T005 — accessible from `VaultFeederSpec` in the same package
- `InjectionProfileParser` is `private[gatling]` — accessible from `package org.galaxio.gatling.diagnostics` (within the `gatling` scope)
- Commit after each checkpoint; keep build green at every commit
