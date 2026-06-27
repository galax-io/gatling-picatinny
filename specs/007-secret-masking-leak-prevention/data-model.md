# Phase 1 Data Model: Secret Masking & Leak Prevention

No persistence. These are the in-code entities the feature introduces or changes. Masking is a **presentation-layer** concern — no in-memory config object is mutated.

## RedactionSettings (new, internal)

Resolved once at config-load from the optional `picatinny.redaction` block; passed into the masking helper.

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `additionalSensitiveKeys` | `List[String]` | `Nil` | User-supplied extra terms; lowercased, merged with built-ins (never replaces). |
| `replacement` | `String` | `"******"` | Sentinel substituted for sensitive values. Fixed-width; reveals no entropy. |

**Validation / rules**
- Absent `picatinny.redaction` block → all defaults (FR-012 boundary). Guarded by `config.hasPath`.
- Effective term set = `BuiltinSensitiveTerms ++ additionalSensitiveKeys`, normalized (lowercase, de-duplicated). **Merge-not-replace**: a user cannot remove a built-in term.

## Sensitive Term (built-in set, hardened)

The curated floor. Compared against a path's **last segment**, split into camelCase/snake/kebab words; a term matches when it equals a whole word (or forms a recognized `…Password/Secret/Token/Key` compound).

- Existing (kept): `password`, `passwd`, `pwd`, `secret`, `token`, `apikey`/`api-key`/`api_key`, `credential`, `privatekey`/`private_key`, `clientsecret`/`client_secret`, `accesskey`/`access_key`, `secretkey`/`secret_key`.
- Added (FR-003): `authorization`, `bearer`, `passphrase`, and `key` only as a recognized compound suffix (NOT a bare word — avoids `publicKey`/`keyboard` over-mask).

**Negative invariants (must NOT match):** `roleId`, `roleIdPrefix`, `tokenBucketSize`, `apiKeyboard`, `secretariat`, `baseUrl`.

## ConfigValueMasking (changed, `private[config]` → `private[gatling]`)

The single central helper. Public-within-library surface:

| Member | Signature | Purpose |
|--------|-----------|---------|
| `isSensitive` | `(path: String): Boolean` | Last-segment word-boundary sensitivity test (kept name; hardened logic). Reused by D4 JVM-arg redaction. |
| `displayValue` | `(path: String, value: Any): String` | Scalar masking (kept). |
| `displayConfig` | `(cfg: com.typesafe.config.Config): String` | **New.** Leaf-walk a nested block via `entrySet()`, mask per-leaf (FR-004). |
| `redactUserInfo` | `(raw: String): String` | **New.** Fail-safe URL userinfo stripper (FR-007). |

State: effective term set + replacement, resolved once from `RedactionSettings`.

## SigningKey subtypes (changed — toString only)

| Type | Field (unchanged) | `toString` (new) |
|------|-------------------|------------------|
| `StringSecret` | `value: String` | `"StringSecret(******)"` |
| `AsymmetricKey` | `value: PrivateKey` | `"AsymmetricKey(******)"` |

**Invariant:** `value` accessor, `apply`/`unapply`/`copy`, and case-class status unchanged — only the rendered string changes.

## Redacted JVM Argument (transient, FR-006)

Not a stored type — a transform over `mxBean.getInputArguments`:
- `-Dkey=value` with `isSensitive(key)` → `-Dkey=******`.
- Non-`-D`, value-less, or non-sensitive args → unchanged.

## Banner Log Event (FR-008/009)

The full multi-line banner/diagnostics block emitted as ONE `logger.info` event under category `org.galaxio.gatling.diagnostics`. Embedded `\n` preserved; ASCII-alignment chars (`|`, `/`, `_`) intact. Rendered prefix-free under the recommended `BANNER` appender (`%msg%n`, `additivity="false"`).
