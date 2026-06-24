# Phase 1 Data Model: Config & Cookie Correctness Fixes (v1.22.0)

No new persisted entities. This feature touches two existing in-memory value types and one transient session-stored collection. All shapes are backward-compatible (no field added/removed/renamed).

## ParsedCookie (existing — unchanged)

`org.galaxio.gatling.storage.ParsedCookie` (`CookieParser.scala:3-11`).

| Field | Type | Notes |
|-------|------|-------|
| `name` | `String` | required |
| `value` | `String` | required |
| `domain` | `Option[String]` | defaults to `restoreCookies` domain arg when absent |
| `path` | `Option[String]` | parsed but **not** propagated to the jar (build-time-fixed in public DSL) |
| `maxAge` | `Option[Long]` | seconds; `None` when absent **or** present-but-unparseable (FR-006 adds a WARN for the present-but-unparseable case); not propagated to the jar |
| `secure` | `Boolean` | parsed; not propagated to the jar |
| `httpOnly` | `Boolean` | parsed; not propagated (irrelevant to an outbound cookie) |

**Validation rule change (FR-006/007)**: when the `max-age` attribute is present but `toLongOption` returns `None` (non-numeric `abc`, empty `Max-Age=`, or overflow beyond `Long`), `maxAge` stays `None` AND a single WARN is logged naming the offending value. Absent `max-age` → `None`, no log. Parseable value, **including negative** (e.g. `-1` → `Some(-1L)`) → no log. Never throws.

**Note**: the shape is intentionally unchanged — `path`/`maxAge`/`secure`/`httpOnly` remain on `ParsedCookie` (other callers and the parser tests use them) even though `restoreCookies` does not push them into the jar.

## Intensity value (conceptual — `IntensityConverter`)

Input grammar (anchored): `^<number>(\s*<unit>)?$` where `<number>` = `\d+(?:\.\d+)?` (arbitrary-precision decimal, no leading dot, no trailing dot, single dot) and `<unit>` ∈ {`rps`, `rpm`, `rph`} case-insensitive, default `rps`.

| Aspect | Rule |
|--------|------|
| Output type | `Double` (requests per second) — **unchanged** |
| Conversion | `rps` → value; `rpm` → value/60; `rph` → value/3600 |
| Default unit | `rps` when unit absent |
| Malformed | no match → `IllegalArgumentException("Simulation param for intensity incorrect")` |
| Unsupported unit | explicit `case _` throw (not a swallowed `MatchError`) |

Positivity/zero validation is the caller's concern (`SimulationConfig.requirePositive`), not this converter — unchanged.

## Transient restore collection (new, internal)

`restoreCookies` stores the runtime-parsed cookies in a **temporary session attribute** so Gatling's `foreach` can iterate them and emit one `addCookie` action per cookie. This is an internal, private session key used only within the returned `ChainBuilder`; it is not a public/serialized format. Each element exposes at least `name` and `value` for EL access (`#{cookie.name}`, `#{cookie.value}`). Choose a collision-free attribute name (e.g. a picatinny-namespaced key) and clean intent is local to the chain.

State flow within the returned `ChainBuilder`:

1. `exec { session => parse raw Set-Cookie → list; set each name→value session attr (compat); store list under the temp key }`
2. `foreach(<temp key>, "cookie") { exec(addCookie(Cookie("#{cookie.name}", "#{cookie.value}").withDomain(domain))) }`

No-op: if the source attribute is absent or not a `String`, step 1 leaves the session unchanged and the list is empty → `foreach` iterates nothing → flow continues without error.
