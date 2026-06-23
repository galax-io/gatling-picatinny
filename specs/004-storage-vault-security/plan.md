# Implementation Plan: Storage, JDBC & Vault Security Hardening

**Branch**: `004-storage-vault-security` | **Date**: 2026-06-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-storage-vault-security/spec.md`

**Milestone**: [v1.19.0 — Storage, JDBC & Vault security](https://github.com/galax-io/gatling-picatinny/milestone/4) | Issues: [#204](https://github.com/galax-io/gatling-picatinny/issues/204), [#209](https://github.com/galax-io/gatling-picatinny/issues/209), [#94](https://github.com/galax-io/gatling-picatinny/issues/94)

## Summary

Five targeted security/correctness fixes across four files, plus two internal cleanups,
with no public API signature changes. Each fix adds a pre-condition guard or replaces
a partial match with exhaustive handling, making failures loud and actionable instead of
silent or opaque. All new tests run without containers.

## Technical Context

**Language/Version**: Scala 2.13.18

**Primary Dependencies**: json4s (DefaultFormats, native), java.net.URI (stdlib), java.nio.file.Paths (stdlib), Scala Logging — no new deps required

**Storage**: JDBC (PostgreSQL / H2 in tests via `JdbcTestSupport.RecordingJdbcDriver`)

**Testing**: ScalaTest (AnyWordSpec + Matchers), ScalaMock for `THttpClient`, `JdbcTestSupport.RecordingJdbcDriver` for JDBC unit layer

**Target Platform**: JVM 17 (compile target); CI Temurin 21

**Project Type**: Published library (Maven Central)

**Performance Goals**: Validation guards run at simulation setup time (not hot path) — negligible overhead; no performance goal applies

**Constraints**: No new dependencies; no public API signature changes; all new tests in `Test` scope (no Docker for new tests)

**Scale/Scope**: 5 source-file edits (4 production + 1 refactor), 4 test-file extensions

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | `JdbcStorageBackend` constructed with `tableName = "bad; DROP TABLE x; --"` | Unit/Functional | Assert `IllegalArgumentException` thrown before any JDBC call (verify `RecordingJdbcDriver` connection count stays 0); exact message includes "Invalid tableName" and the offending value. Negative: `tableName = "gatling_session_storage"` constructs successfully. |
| FR-001 | `JdbcStorageBackend` with `tableName = ""` (empty) | Unit/Functional | Assert `IllegalArgumentException`; negative: `tableName = "a"` (single letter) succeeds. |
| FR-002 | `VaultFeeder.apply` called with `vaultUrl = "http://vault.prod.internal"` | Unit/Functional | ScalaMock `THttpClient` receives `postOrThrow` call (proceeds); assert logger WARN captured with URL in message before the HTTP call. |
| FR-002 | `VaultFeeder.apply` called with `vaultUrl = "http://localhost:8200"` | Unit/Functional | No warning logged; ScalaMock `THttpClient` receives `postOrThrow` call (localhost exemption — no log). |
| FR-002 | `VaultFeeder.withToken` called with `vaultUrl = "http://10.0.0.1"` | Unit/Functional | Assert WARN logged with URL; ScalaMock `getOrThrow` still called (warning only, no block). |
| FR-003 | `ProfileBuilderNew.buildFromYaml("../../etc/passwd")` | Unit/Functional | Assert `ProfileBuilderException` thrown before `Source.fromFile` is called; message contains "traversal" or "containment" and the supplied path. |
| FR-003 | `ProfileBuilderNew.buildFromYaml("src/test/resources/profileTemplates/profile1.yml")` | Unit/Functional | No traversal error; existing happy-path test still passes. |
| FR-004 | `Request.toRequest` with header `"malformed-no-colon"` in `requestHeaders` | Unit/Functional | Assert `ProfileBuilderException` (not `MatchError`) with message naming `"malformed-no-colon"`. Negative: header `"Content-Type: application/json"` extracts key `"Content-Type"` and value `"application/json"` correctly. |
| FR-004 | `Request.toRequest` with empty string header `""` | Unit/Functional | Assert `ProfileBuilderException` (not `MatchError`). |
| FR-005 | `InjectionProfileParser` closed `other` step with `productArity = 0` | Unit/Functional | Minimal `Product` stub with `productArity = 0`; assert `IllegalArgumentException` with arity and type in message before any `productElement` call. |
| FR-005 | `InjectionProfileParser` open `other` step with `productArity = 0` | Unit/Functional | Same stub pattern for `openSegments`; assert `IllegalArgumentException`. |
| FR-005 | Well-formed `RampRateOpenInjection(1.0, 5.0, 10.seconds)` | Unit/Functional | Parsed via `fromOpen`; assert `WorkloadSettings.intensityRps > 0` and `stagesNumber` correct exact value. |
| FR-006 | `StorageBackend.scala` compiles with single `formats` definition | Compile Guard | `sbt compile` succeeds; grep for `private implicit val formats` returns exactly one occurrence (in `StorageFormats` object). |
| FR-007 | `mergeWithStrategy(FailOnDuplicate, pairs with duplicate key "user")` | Unit/Functional | Pure function call (no mock needed); assert `IllegalArgumentException` listing `"user"` as duplicate. |
| FR-007 | `mergeWithStrategy(LastWins, [("k","v1"),("k","v2")])` | Unit/Functional | Returns `Map("k" -> "v2")`; assert log warning captured (using `LazyLogging` test intercept or checking logger output). |
| FR-007 | `mergeWithStrategy(FirstWins, [("k","v1"),("k","v2")])` | Unit/Functional | Returns `Map("k" -> "v1")`; assert log warning captured. |
| FR-007 | `login` called with mock returning malformed JSON body | Unit/Functional | ScalaMock `THttpClient.postOrThrow` returns `HttpResult(200, "not-json")`; assert `RuntimeException` with message "Failed to parse Vault login response as JSON". |
| FR-007 | `readSecret` called with mock returning JSON missing `"data"` field | Unit/Functional | ScalaMock `THttpClient.getOrThrow` returns `HttpResult(200, "{}")`; assert `RuntimeException` with message referencing the secret path. |
| FR-008 | All rejection paths fire on invalid input without false positives | Unit/Functional | Cross-cut: one negative test per FR-001–FR-005 (covered by the rows above); one positive case per fix confirming valid input is not rejected. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — No Java facade changes in this feature. `InjectionProfileParser` is `private[gatling]`; all other changes are in Scala core modules. Java facade (`javaapi/internal/ProfileBuilderNew.scala`) is not touched — it delegates to Scala `ProfileBuilderNew` which is being hardened.
- [x] **II. Backward Compatibility** — No public API signatures change. All changes tighten pre-conditions on existing public methods for invalid inputs that were already broken (SQL injection, cleartext credentials, path traversal, `MatchError`). The valid-input happy path is unchanged. MINOR version bump appropriate (security hardening with no removal). No serialized config/profile format changes.
- [x] **III. Test Discipline** — Test Model filled above: one row per FR, real case named, all layers are Unit/Functional (pure functions or ScalaMock boundary) or Compile Guard. No DSL/Action Component layer needed (no Gatling runtime behavior). No Testcontainers for new tests (FR-007 explicitly avoids Docker). `InjectionProfileParser` is `private[gatling]` — tests go in the `diagnostics` package test scope. ScalaMock for `THttpClient` (existing pattern in `VaultFeederSpec`). `JdbcTestSupport.RecordingJdbcDriver` for JDBC unit tests (existing pattern in `JdbcStorageBackendSpec`). ≥1 negative/boundary case per FR confirmed in test sketches.
- [x] **IV. Small, Focused Changes** — No opportunistic refactors. FR-006 (`DefaultFormats` dedup) is explicitly authorized by issue #204. No new dependencies. `InjectionProfileParser` is internal — no API signature concern. Changes are isolated to exactly the lines cited in the issues.
- [ ] **V. Release Integrity** *(release PRs only)* — Not applicable to this feature PR. Will apply when cutting the release.

## Project Structure

### Documentation (this feature)

```text
specs/004-storage-vault-security/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0: decisions per FR
├── data-model.md        # Phase 1: modified type contracts
├── quickstart.md        # Phase 1: validation guide
├── contracts/
│   └── api-contracts.md # Phase 1: behavior contract tables
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code

```text
src/main/scala/org/galaxio/gatling/
├── storage/
│   └── StorageBackend.scala        # FR-001 (tableName require), FR-006 (DefaultFormats dedup)
├── feeders/
│   └── VaultFeeder.scala           # FR-002 (requireHttps helper + call sites)
├── profile/
│   └── ProfileBuilderNew.scala     # FR-003 (path containment), FR-004 (exhaustive header match)
└── diagnostics/
    └── InjectionProfileParser.scala # FR-005 (arity guards in closedSegments + openSegments)

src/test/scala/org/galaxio/gatling/
├── storage/
│   └── JdbcStorageBackendSpec.scala # FR-001: new negative tests for invalid tableName
├── feeders/
│   └── VaultFeederSpec.scala        # FR-002: HTTPS enforcement tests; FR-007: merge/parse unit tests
├── profile/
│   └── ProfileBuilderTest.scala     # FR-003: traversal rejection; FR-004: malformed header
└── diagnostics/
    └── InjectionProfileParserSpec.scala  # FR-005: arity guard (new file or extend existing)
```

**Structure Decision**: Single-project layout (existing); no new modules or directories in source. Test files extend existing specs where the class already has coverage (`JdbcStorageBackendSpec`, `VaultFeederSpec`, `ProfileBuilderTest`). `InjectionProfileParserSpec` may be a new file if no existing diagnostics spec exists.

## Complexity Tracking

> No Constitution violations. Table not needed.
