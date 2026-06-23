# Research: Storage, JDBC & Vault Security Hardening

No `NEEDS CLARIFICATION` markers from the spec. All decisions are straightforward given the fixed stack (Scala 2.13, existing code patterns).

---

## FR-001: SQL identifier validation for `tableName`

**Decision**: `require(tableName.matches("[A-Za-z_][A-Za-z0-9_]*"), s"Invalid tableName: '$tableName'. Must match [A-Za-z_][A-Za-z0-9_]*")`  
added in the `JdbcStorageBackend` constructor body (after field definitions).

**Rationale**: SQL identifiers cannot be parameterized via `PreparedStatement` — only values can. The minimal correct fix is to validate at construction using the standard SQL-identifier character class. Quoting identifiers (e.g., `"tableName"`) is dialect-specific and more complex than a strict allowlist; strict ASCII identifiers work across PostgreSQL, H2, MySQL, and MariaDB (all tested dialects).

**Alternatives considered**:
- Quoting identifiers with `"` — rejected: dialect-dependent quoting rules; adds complexity without benefit since Picatinny users control the table name at simulation setup time.
- Validating in each SQL method — rejected: validate-once-at-construction is safer and avoids repeated checks in hot paths.

---

## FR-002: Vault HTTP warning

**Decision**: extract a private `warnIfNotHttps(vaultUrl: String): Unit` helper that parses the URL scheme and host via `java.net.URI` and calls `logger.warn(...)` if `scheme == "http"` and host is not `localhost`/`127.0.0.1`. Call it at the top of each public entry point (`apply`, `fromPaths`, `withToken`) before `THttpClient` is created.

**Rationale**: Warning (not rejection) is the chosen severity — callers remain unblocked, which is important for environments where HTTP is intentional (internal networks, developer choice). The warning is emitted before any network call so the credential risk is surfaced even if the feeder subsequently fails. `java.net.URI` is stdlib, no new dep. Localhost/127.0.0.1 exemptions avoid noise in local dev.

**Alternatives considered**:
- Hard reject (`IllegalArgumentException`) — rejected by user preference: too strict for the intended use case.
- Checking inside `login`/`readSecret` private methods — rejected: warning must fire before any network call, and doing it at the public entry point is the correct placement.

---

## FR-003: Profile path traversal prevention

**Decision**: After `Paths.get(userDir, path)`, call `.normalize()` and assert that the result starts with `Paths.get(userDir).toAbsolutePath`. Throw `ProfileBuilderException` if the check fails.

**Rationale**: `java.nio.file.Path.normalize()` resolves `../` sequences without filesystem I/O. The post-normalize prefix check is the standard path-traversal defense. Already using `Paths`/`Source` from stdlib — no new dep.

**Applies to**: both `buildFromYaml` (functional chain) and `buildFromYamlJava` (imperative try/catch). Both paths must be hardened identically.

**Alternatives considered**:
- Blocking `../` via string replacement — rejected: fragile, URL-encoded bypasses exist.
- Restricting to relative paths only (reject absolute) — rejected: over-constrains valid usages; normalization + prefix check is the correct pattern.

---

## FR-004: Exhaustive CSV header matching in `Request.toRequest`

**Decision**: Replace the partial `case regexHeader(a, b)` with a `collect` that produces a typed `ProfileBuilderException` on any non-matching header, i.e. wrap the map in a try/catch or use `map` with an exhaustive match that throws a typed exception.

Preferred: keep `map` but add fallback case:
```
requestHeaders.map {
  case regexHeader(a, b) => (a, b)
  case bad               => throw ProfileBuilderException(s"Malformed header: '$bad' ...")
}
```

**Rationale**: `collect` would silently skip malformed headers, making the issue invisible. A typed exception with the offending value gives the user an actionable message. The exception type `ProfileBuilderException` is already defined in `ProfileBuilderNew` — reuse it for consistency.

**Alternatives considered**:
- `collect` (silent skip) — rejected: the spec requires a clear error.
- New exception type — rejected: `ProfileBuilderException` already covers "invalid profile content" semantics.

---

## FR-005: `InjectionProfileParser` arity validation

**Decision**: In the `other` fallback branch of `closedSegments`, add an arity guard before accessing `productElement`:
```
require(other.productArity >= 2, s"Unsupported closed injection step ...")
```
Similarly in `openSegments` `other` branch, guard `longField(other, 0)` with `require(other.productArity >= 1, ...)`.

**Rationale**: The existing `doubleField`/`longField` helpers return `0.0`/`0L` for unknown types but do NOT guard against empty products. A `require` placed before access throws `IllegalArgumentException` with a clear message. `InjectionProfileParser` is `private[gatling]` — no public API impact.

**Alternatives considered**:
- Returning a default `WorkloadSegment` for unknown steps — rejected: silent wrong behavior is worse than a loud failure.
- Pattern-matching exhaustively on all known step types — rejected: Gatling adds new step types over time; the `other` branch exists for forward compatibility; the correct fix is a guard, not exhaustive enumeration.

---

## FR-006: `DefaultFormats` deduplication

**Decision**: Define `private[storage] implicit val formats: Formats = DefaultFormats` in a `private object StorageFormats` (same file), and import it in each backend class body.

**Rationale**: All three backends (`JsonFileBackend`, `RedisBackend`, `JdbcStorageBackend`) are in the same file and package. A package-private object avoids repeating the definition without polluting the package namespace. No behavioral change.

**Alternatives considered**:
- `package object storage` — rejected: package objects are a Scala 2 legacy pattern; a named `private object` is cleaner.
- Hoisting to the `StorageBackend` trait — rejected: traits with implicit vals can cause implicit ambiguity in subclasses.

---

## FR-007: Vault unit tests (without container)

**Decision**: Add unit tests for `mergeWithStrategy` (all three strategy branches) and JSON parse error paths in `login`/`readSecret` to `VaultFeederSpec`. Both methods are `private[feeders]` — accessible from within the same package test scope.

**Rationale**: `mergeWithStrategy` is a pure function — no mock needed. `login`/`readSecret` use `THttpClient` which is already the ScalaMock boundary for VaultFeeder (pattern established in prior unit tests); mock `THttpClient` to return crafted responses and assert the `RuntimeException` is thrown with the correct message.

**New test file**: No new file needed; extend `VaultFeederSpec`.
