# API Contracts: Storage, JDBC & Vault Security Hardening

This file documents the **behavior contracts** added or tightened by this feature.
All changes are additive (stricter preconditions on existing APIs) — no signatures change.

---

## `JdbcStorageBackend` Constructor Contract

**Precondition** (new):
```
tableName must match [A-Za-z_][A-Za-z0-9_]*
```

| Input | Result |
|-------|--------|
| `"gatling_session_storage"` (default) | construction succeeds |
| `"my_results_2024"` | construction succeeds |
| `""` (empty) | `IllegalArgumentException`: "Invalid tableName" |
| `"results; DROP TABLE results; --"` | `IllegalArgumentException`: "Invalid tableName" |
| `"123bad"` (starts with digit) | `IllegalArgumentException`: "Invalid tableName" |
| `"tab le"` (space) | `IllegalArgumentException`: "Invalid tableName" |

**Guarantee**: if construction succeeds, all SQL operations on this instance use a
validated identifier — no SQL injection surface via `tableName`.

---

## `VaultFeeder` URL Contract

**Warning behavior** (new, applies to `apply`, all `fromPaths` overloads, `withToken`):
```
If vaultUrl scheme == "http" AND host is not "localhost" or "127.0.0.1":
  → WARN logged before any network call; call proceeds
Otherwise:
  → no warning
```

| Input | Result |
|-------|--------|
| `"https://vault.prod.internal"` | proceeds, no warning |
| `"http://localhost:8200"` | proceeds, no warning (localhost exemption) |
| `"http://127.0.0.1:8200"` | proceeds, no warning (loopback exemption) |
| `"http://[::1]:8200"` | proceeds, no warning (IPv6 loopback exemption) |
| `"http://vault.prod.internal"` | WARN logged with URL; call proceeds |
| `"http://[2001:db8::1]:8200"` | WARN logged with URL; call proceeds (non-loopback IPv6) |

**Guarantee**: if a non-HTTPS non-local URL is used, a WARN-level log entry naming the
URL is always emitted before the first network call.

---

## `ProfileBuilderNew.buildFromYaml` / `buildFromYamlJava` Path Contract

**Precondition** (new):
```
Paths.get(user.dir, path).normalize() must start with Paths.get(user.dir).toAbsolutePath
```

| Input `path` | Result |
|-------------|--------|
| `"src/test/resources/profile.yml"` | resolves, proceeds to file I/O |
| `"../../etc/passwd"` | `ProfileBuilderException`: "Path traversal detected" (escapes base) |
| `"/etc/passwd"` (absolute) | `ProfileBuilderException`: "Path traversal detected" (absolute paths rejected outright) |
| `"profiles/../profiles/p.yml"` (same dir after normalize) | proceeds normally |

**Guarantee**: if path validation passes, the resolved file is within the project's
working directory tree.

---

## `Request.toRequest` Header Contract

**Precondition** (tightened):
```
Every element of requestHeaders must match (.+?): (.+)
```

| Input header string | Result |
|--------------------|--------|
| `"Content-Type: application/json"` | key-value pair extracted correctly |
| `"greetings: Hello world!"` | key-value pair extracted correctly |
| `"malformed-no-colon"` | `ProfileBuilderException`: "Malformed header: 'malformed-no-colon'" |
| `""` (empty string) | `ProfileBuilderException`: "Malformed header: ''" |

**Change from before**: `MatchError` (opaque) → `ProfileBuilderException` (typed, actionable).
