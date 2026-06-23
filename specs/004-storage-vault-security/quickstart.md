# Quickstart Validation Guide: Storage, JDBC & Vault Security Hardening

Proves all five security/correctness fixes work end-to-end via the standard unit test suite.
No containers or network required.

## Prerequisites

- JDK 17+ on PATH
- `sbt` available
- Working directory: repository root

## Run All Unit Tests

```bash
sbt compile test
```

Expected: all tests pass, including the new ones listed below.

## Fix-by-Fix Validation

### FR-001 — SQLi: `JdbcStorageBackend` tableName validation

**Test class**: `JdbcStorageBackendSpec`  
New test: `"reject tableName containing SQL injection payload"`

Verify manually:
```scala
// Should throw IllegalArgumentException before any DB connection:
JdbcStorageBackend(jdbcUrl = "jdbc:h2:mem:test", tableName = "bad; DROP TABLE x; --")
// Should construct successfully:
JdbcStorageBackend(jdbcUrl = "jdbc:h2:mem:test", tableName = "valid_table_name")
```

---

### FR-002 — Vault HTTPS: `VaultFeeder` URL enforcement

**Test class**: `VaultFeederSpec`  
New tests: `"reject http:// vaultUrl for non-localhost"`, `"allow http:// for localhost"`, `"allow http:// for 127.0.0.1"`

Verify manually:
```scala
// Should throw IllegalArgumentException before THttpClient creation:
VaultFeeder("http://vault.prod.internal", "secret/path", "role", "secret", List("key"))
// Should proceed (localhost exemption):
VaultFeeder("http://localhost:8200", "secret/path", "role", "secret", List("key"))
```

---

### FR-003 — Path traversal: `ProfileBuilderNew` containment check

**Test class**: `ProfileBuilderTest`  
New test: `"reject paths that escape the working directory"`

Verify manually:
```scala
// Should throw ProfileBuilderException "Path traversal detected":
ProfileBuilderNew.buildFromYaml("../../etc/passwd")
```

---

### FR-004 — Malformed header: `Request.toRequest` typed exception

**Test class**: `ProfileBuilderTest` (or a new `RequestSpec`)  
New test: `"throw ProfileBuilderException on malformed header string"`

Verify manually:
```scala
val r = Request("r", "100 rph", None, Params("GET", "/", Some(List("bad-header")), None))
// Should throw ProfileBuilderException "Malformed header: 'bad-header'":
r.toRequest
```

---

### FR-005 — Arity guard: `InjectionProfileParser` explicit failure

**Test class**: new `InjectionProfileParserSpec` (or extend existing diagnostics spec)  
New test: `"throw on closed injection step with arity < 2"`

Verify by running the new unit test — the class is `private[gatling]` and only accessible from within the same package scope (`diagnostics`).

---

### FR-006 — DefaultFormats dedup (compile verification)

```bash
sbt compile
```

`StorageBackend.scala` must compile with a single `formats` definition shared across all three backends. No behavioral test needed; failure mode is a compile error.

---

### FR-007 — Vault unit tests for merge/parse (no Docker)

```bash
sbt test
```

New tests in `VaultFeederSpec`:
- `"mergeWithStrategy(FailOnDuplicate) throws on duplicate keys"`
- `"mergeWithStrategy(LastWins) keeps last value and logs warning"`
- `"mergeWithStrategy(FirstWins) keeps first value and logs warning"`
- `"login throws RuntimeException on malformed JSON response"`
- `"readSecret throws RuntimeException when JSON parse fails"`

All run without containers and complete in under 5 seconds total.

---

## Integration Smoke Test (optional, requires Docker)

```bash
sbt "IntegrationTest / test"
```

Verifies `JdbcStorageIntegrationSpec` still passes against a real PostgreSQL container
with the default `tableName = "gatling_session_storage"` (which satisfies the new validation).

## Acceptance Criteria Summary

| Fix | Validation method | Expected outcome |
|-----|-------------------|-----------------|
| SQLi tableName | `JdbcStorageBackendSpec` new negative test | `IllegalArgumentException` thrown |
| Vault HTTPS | `VaultFeederSpec` new tests | `IllegalArgumentException` for http non-localhost |
| Path traversal | `ProfileBuilderTest` new test | `ProfileBuilderException` thrown |
| Malformed header | `ProfileBuilderTest` / `RequestSpec` new test | `ProfileBuilderException` thrown |
| Arity guard | `InjectionProfileParserSpec` new test | `IllegalArgumentException` thrown |
| DefaultFormats dedup | `sbt compile` | compiles without error |
| Vault unit tests | `sbt test` (no Docker) | all new tests green |
