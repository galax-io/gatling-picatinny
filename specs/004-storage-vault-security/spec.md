# Feature Specification: Storage, JDBC & Vault Security Hardening

**Feature Branch**: `004-storage-vault-security`

**Created**: 2026-06-22

**Status**: Draft

**Input**: User description: "https://github.com/galax-io/gatling-picatinny/milestone/4"

**Milestone**: v1.19.0 — Storage, JDBC & Vault security (#204, #209, #94)

## User Scenarios & Testing *(mandatory)*

<!--
  These stories are framed from the library consumer's perspective: teams writing
  load-test simulations that depend on gatling-picatinny. "System" = the library.
-->

### User Story 1 - SQL Injection Protection for Storage Backend (Priority: P1)

A load-test author configures `StorageBackend` with a `tableName` that comes from
an environment variable or CI parameter. Today, a malformed or adversarial name
(e.g. `"results; DROP TABLE results; --"`) is interpolated directly into DDL/DML,
creating a SQL injection path inside the test infrastructure. The library must reject
invalid names before any SQL is executed.

**Why this priority**: SQL injection in test infrastructure can destroy shared databases
used across teams. It is the only finding with direct data-destruction potential and affects
the public `StorageBackend` constructor, a compatibility-sensitive surface.

**Independent Test**: Can be fully tested by constructing a `StorageBackend` with an
invalid `tableName` and asserting the constructor fails with a clear message, plus a
separate test confirming that a valid name succeeds.

**Acceptance Scenarios**:

1. **Given** a `StorageBackend` is constructed with `tableName = "results; DROP TABLE results; --"`, **When** construction is attempted, **Then** an `IllegalArgumentException` is thrown before any database connection is made.
2. **Given** a `StorageBackend` is constructed with `tableName = "load_test_results"`, **When** construction is attempted, **Then** it succeeds without error.
3. **Given** a `StorageBackend` is constructed with `tableName = "123bad"` (starts with digit), **When** construction is attempted, **Then** construction fails with a descriptive message naming the offending value.
4. **Given** a valid `StorageBackend`, **When** any SQL operation (create/insert/select/delete) runs, **Then** only the pre-validated name appears in the query; `record_data` values remain parameterized.

---

### User Story 2 - Vault HTTPS Warning (Priority: P2)

A load-test author configures `VaultFeeder` with `vaultUrl = "http://vault.internal"`.
Today, the library silently sends token and AppRole `secret_id` credentials over plaintext
HTTP with no indication of the risk. The library must emit a clearly visible warning before
any network call when a non-HTTPS URL is used with a non-localhost host.

**Why this priority**: Credential exposure over plaintext HTTP is a security defect affecting
any team using Vault in a networked environment. Localhost (development) must remain allowed
to avoid breaking local workflows.

**Independent Test**: Can be fully tested by constructing a `VaultFeeder` with an HTTP URL
pointing to a non-localhost host and verifying a warning is logged before any network call.

**Acceptance Scenarios**:

1. **Given** `vaultUrl = "http://vault.prod.internal"`, **When** the feeder is initialized, **Then** a warning is logged before any network call citing cleartext credential risk; initialization proceeds.
2. **Given** `vaultUrl = "http://localhost:8200"`, **When** the feeder is initialized, **Then** no warning is logged (localhost exemption).
3. **Given** `vaultUrl = "http://127.0.0.1:8200"`, **When** the feeder is initialized, **Then** no warning is logged (loopback exemption).
4. **Given** `vaultUrl = "https://vault.prod.internal"`, **When** the feeder is initialized, **Then** initialization succeeds without warnings.

---

### User Story 3 - Profile Path Traversal Prevention (Priority: P3)

A load-test author passes a profile file path (e.g. from a CLI argument or config file)
to `ProfileBuilderNew`. Today, a path like `../../secrets/credentials.conf` is joined
onto the working directory without containment checks, allowing traversal outside the
project. The library must normalize and validate that the resolved path stays within
the expected base directory.

**Why this priority**: Path traversal can expose files outside the project on CI agents.
Medium severity; affects only teams using `ProfileBuilderNew` with externally supplied paths.

**Independent Test**: Can be fully tested by calling `ProfileBuilderNew` with a traversal
path and asserting rejection, independently of any CSV parsing logic.

**Acceptance Scenarios**:

1. **Given** a profile path of `"../../etc/passwd"`, **When** `ProfileBuilderNew` resolves it, **Then** the call fails with a clear path-containment error before any file I/O.
2. **Given** a profile path of `"profiles/my_profile.csv"` within the project, **When** `ProfileBuilderNew` resolves it, **Then** it resolves to the correct absolute path and proceeds normally.
3. **Given** a path that after normalization lands exactly at the base directory, **When** resolved, **Then** it is accepted (boundary case).

---

### User Story 4 - Malformed CSV Header Handling (Priority: P4)

A load-test author provides a profile CSV file with a header that doesn't match any
known injection step format. Today, `ProfileBuilderNew` throws a `MatchError` at
runtime (during simulation startup), giving no useful context. The library must produce
a clear, actionable error message naming the offending header value.

**Why this priority**: `MatchError` stack traces are opaque and slow diagnosis. A clear
error message saves debugging time for every team with a malformed profile file.

**Independent Test**: Can be fully tested by calling `ProfileBuilderNew` with a CSV
containing an unrecognized header and asserting a typed, message-bearing exception is
thrown — independently of Vault or JDBC.

**Acceptance Scenarios**:

1. **Given** a CSV header of `"unknownStep,users/sec"`, **When** `ProfileBuilderNew` parses it, **Then** a typed exception is thrown with a message that includes the unrecognized header token.
2. **Given** an empty CSV header row, **When** `ProfileBuilderNew` parses it, **Then** a typed exception is thrown (not `MatchError`).
3. **Given** a valid header like `"rampUsersPerSec,from,to,during"`, **When** parsed, **Then** parsing succeeds.

---

### User Story 5 - InjectionProfileParser Arity Validation (Priority: P5)

A load-test author writes a custom injection step class with a non-standard field
count. Today, `InjectionProfileParser` accesses `productElement(arity - 2)` without
checking arity, silently reading the wrong field and producing an incorrect ramp shape.
The library must validate arity (and field types) before extraction and fail loudly on
unsupported step shapes.

**Why this priority**: Silent mis-parsing produces wrong load shapes that invalidate
entire performance test results. A loud failure is far preferable to a silent wrong result.

**Independent Test**: Can be fully tested by constructing a minimal injection profile
object with unexpected arity and asserting the parser throws an explicit exception
before producing any output.

**Acceptance Scenarios**:

1. **Given** an injection profile step with `productArity < 2`, **When** `InjectionProfileParser` processes it, **Then** an exception is thrown with a message naming the arity and the step type.
2. **Given** a step with correct arity but a field of unexpected type at position `arity - 2`, **When** processed, **Then** an exception is thrown naming the unexpected type.
3. **Given** a well-formed standard injection step, **When** processed, **Then** parsing succeeds and produces the correct ramp shape values.

---

### Edge Cases

- What happens when `tableName` is an empty string? → Must be rejected (fails the identifier regex).
- What happens when `tableName` contains Unicode or emoji? → Rejected (ASCII identifier pattern only).
- What happens when `vaultUrl` scheme is `HTTP` (uppercase)? → Must be normalized and rejected for non-localhost.
- What happens when the profile CSV has a header but zero data rows? → Should produce an empty profile, not crash.
- What happens when `InjectionProfileParser` receives a `null` step? → Must fail explicitly, not NPE silently.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `StorageBackend` MUST validate `tableName` at construction time; valid names consist solely of letters, digits, and underscores and must start with a letter or underscore (i.e., a safe SQL identifier); invalid names MUST cause construction to fail before any database connection is attempted.
- **FR-002**: `VaultFeeder` MUST log a WARNING when `vaultUrl` uses a non-HTTPS scheme and the host is not `localhost` or a loopback address; the warning MUST be emitted before any network call and MUST name the URL.
- **FR-003**: `ProfileBuilderNew` MUST normalize caller-supplied file paths and verify they remain within the declared base directory; traversal attempts MUST produce a containment error naming the offending path.
- **FR-004**: `ProfileBuilderNew` MUST replace the non-exhaustive partial function over CSV headers with exhaustive matching that emits a typed, message-bearing exception on unrecognized headers.
- **FR-005**: `InjectionProfileParser` MUST validate `productArity` and field types before accessing `productElement`; unsupported step shapes MUST cause a typed exception with the arity and step type in the message.
- **FR-006**: `StorageBackend` shared `DefaultFormats` instance MUST be defined once and reused across all three backend implementations (no duplicate `private implicit val formats`).
- **FR-007**: `VaultFeeder` unit tests MUST cover `mergeWithStrategy` branches and login/readSecret JSON-parse error paths without requiring a running Vault container.
- **FR-008**: All rejection paths defined in FR-001–FR-005 MUST include at least one negative-case unit test verifying the rejection triggers correctly.

### Key Entities

- **StorageBackend**: Wrapper around a JDBC connection + table name; responsible for persisting and retrieving Gatling session records.
- **VaultFeeder**: Feeder that authenticates to HashiCorp Vault and reads secrets; holds `vaultUrl` + credentials.
- **ProfileBuilderNew**: Reads a CSV-format injection profile from disk and returns a Gatling `InjectionProfile`; responsible for path resolution and header parsing.
- **InjectionProfileParser**: Extracts numeric field values from Scala `Product` case-class instances representing injection steps.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All four rejection paths (FR-001, FR-003–FR-005) trigger on their respective invalid inputs in under 10 ms, with no database or network call made; FR-002 warning is emitted before any network call.
- **SC-002**: All existing `StorageBackend`, `VaultFeeder`, `ProfileBuilderNew`, and `InjectionProfileParser` unit and integration tests continue to pass without modification after the changes.
- **SC-003**: New unit tests for Vault merge/parse paths run without any container or network dependency, completing in under 5 seconds.
- **SC-004**: Zero `MatchError` or `ArrayIndexOutOfBoundsException` exceptions are thrown by the patched code paths under any valid or invalid input combination exercised in the test suite.
- **SC-005**: Security audit of the four changed files finds no new injection, traversal, or credential-exposure vectors introduced by the changes.

## Assumptions

- `tableName` is a developer-controlled value set at simulation author time, not a per-request runtime value; strict ASCII identifier validation is an acceptable constraint for this surface.
- Localhost exemption for Vault HTTP covers `localhost`, `127.0.0.1`, and IPv6 loopback (`[::1]`, `[0:0:0:0:0:0:0:1]`).
- `ProfileBuilderNew` base directory is `System.getProperty("user.dir")`; no new configuration knob is introduced to override it.
- `StorageBackend` constructor is called at simulation setup time, not in the hot path; a `require` check has negligible performance impact.
- The `DefaultFormats` deduplication (FR-006) is a non-breaking internal refactor with no public API surface change.
- Vault unit tests (FR-007) will mock the HTTP client, consistent with the existing unit test model for `HttpJsonFeeder`.
