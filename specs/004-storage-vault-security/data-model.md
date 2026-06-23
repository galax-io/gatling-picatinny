# Data Model: Storage, JDBC & Vault Security Hardening

No new persistent entities or schema changes. This feature hardens existing value-object
constructors and pure functions. The changes below document modified validation rules and
state invariants for each affected type.

---

## Modified Types

### `JdbcStorageBackend` (`storage/StorageBackend.scala`)

**Type**: `final case class`  
**Public constructor**: `JdbcStorageBackend(jdbcUrl, tableName, username, password)`

**New invariant** (post-construction):
- `tableName` matches `[A-Za-z_][A-Za-z0-9_]*`
- Violated → `IllegalArgumentException` thrown before any JDBC connection is made
- Default value `"gatling_session_storage"` satisfies the invariant

**Internal change** (no public impact):
- `private implicit val formats` removed from this class body; imported from shared `StorageFormats` object

---

### `JsonFileBackend` + `RedisBackend` (`storage/StorageBackend.scala`)

**Internal change only**:
- `private implicit val formats` removed from both class bodies; imported from shared `StorageFormats` object
- No public API change; no behavioral change

---

### `StorageFormats` (new private object, same file)

**Type**: `private object`  
**Scope**: `storage` package only

| Member | Type | Value |
|--------|------|-------|
| `formats` | `implicit Formats` | `DefaultFormats` |

---

### `VaultFeeder` (`feeders/VaultFeeder.scala`)

**Type**: Scala `object` (all-static methods)  
**Changed entry points**: `apply`, `fromPaths` (all three overloads), `withToken`

**New behavior** (before `THttpClient` is created):
- If `vaultUrl` scheme is `http` and host is not a loopback host → WARN logged naming the URL; call proceeds
- Loopback hosts (`localhost`, `127.0.0.1`, `[::1]`, `[0:0:0:0:0:0:0:1]`) → no warning (local dev exemption)
- HTTPS → no warning

**New private helper**:

| Member | Signature | Purpose |
|--------|-----------|---------|
| `warnIfNotHttps` | `(vaultUrl: String): Unit` | Parses URL via `java.net.URI`; calls `logger.warn` on HTTP non-localhost |

---

### `ProfileBuilderNew` (`profile/ProfileBuilderNew.scala`)

**Changed methods**: `buildFromYaml`, `buildFromYamlJava`

**New invariant** (post path resolution):
- Caller-supplied absolute paths (`Paths.get(path).isAbsolute`) are rejected outright
- Otherwise the resolved path must start with `Paths.get(user.dir).toAbsolutePath.normalize()`
- Violated → `ProfileBuilderException("Path traversal detected: ...", cause)` thrown before any file I/O
- Both methods apply the same check; `ProfileBuilderException` is the existing typed exception

---

### `Request` (`profile/ProfileBuilderNew.scala`)

**Changed**: `toRequest` method (in the `Request` case class)  
**Changed expression**: `requestHeaders.map { case regexHeader(a, b) => (a, b) }`

**New behavior**:
- Header parsing extracted to `private[gatling] def parsedHeaders`; `toRequest` delegates to it
- Malformed header string (one that doesn't match `(.+?): (.+)`) → `ProfileBuilderException(s"Malformed header: '$bad' ...")` thrown
- Valid headers → unchanged behavior
- Exception type: reuses existing `ProfileBuilderException`
- The Java/Kotlin facade `javaapi.internal.ProfileBuilderNew.toRequest` now delegates to `parsedHeaders` (was duplicating the unguarded match → `MatchError` for Java callers); `private[gatling]` visibility enables the delegation without exposing public API

---

### `InjectionProfileParser` (`diagnostics/InjectionProfileParser.scala`)

**Type**: `private[gatling] object` (internal only — no public API impact)  
**Changed methods**: `closedSegments`, `openSegments` (the `other` fallback branches)

**New preconditions**:

| Method | Guard | Thrown on violation |
|--------|-------|---------------------|
| `closedSegments` `other` branch | `other.productArity >= 2` | `IllegalArgumentException` with arity + step type in message |
| `openSegments` `other` branch | `other.productArity >= 1` | `IllegalArgumentException` with arity + step type in message |

Both guards use `require(...)` to match the existing validation style in this codebase.
