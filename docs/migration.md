# Migration Guide

[← Back to README](../README.md)

Upgrade notes for the **spec-kit-driven releases** (v1.16.0 onward — the features tracked under
[`specs/`](../specs)). Older releases predate this workflow; for those see the
[GitHub Releases](https://github.com/galax-io/gatling-picatinny/releases) notes.

Each entry states **what changed and why**, then shows the **`Before:`** and **`After:`** code. Pure bug fixes that
need no code change are grouped under [Correctness releases](#correctness-releases-re-baseline-only).

## Contents

- [Matrix](#matrix) — one-line action per version
- [v1.23.0 — Secret masking & leak prevention](#v1230--secret-masking--leak-prevention)
- [v1.22.0 — Config & cookie fixes](#v1220--config--cookie-fixes)
- [Correctness releases (re-baseline only)](#correctness-releases-re-baseline-only)
- [Deprecations](#deprecations)

## Matrix

| Version | Focus | Kind | Action on upgrade |
|---------|-------|------|-------------------|
| **v1.23.0** | Secret masking & leak prevention | ⚠️ Behavioral | Banner/diagnostics now via SLF4J (not `stdout`); library ships no `logback.xml`; masking is now whole-word. |
| **v1.22.0** | Config & cookie fixes | ⚠️ Behavioral | `restoreCookies` now puts cookies in the jar so they **auto-send**; decimal `intensity` parses exactly. |
| **v1.21.0** | Feeders, Generators & JWT correctness | 🔁 Correctness | Re-baseline tests pinned to the old (wrong) output. No API change. |
| **v1.20.0** | Storage, JDBC & Vault security | 🔁 Correctness | Backward-compatible hardening; no code change. |
| **v1.19.0** | Template output fixes | 🔁 Correctness | Re-baseline snapshots of previously-malformed rendered bodies. |
| **v1.18.0** | Assertions correctness (NFR YAML) | 🔁 Correctness | Re-check assertions if you worked around the old behavior. |
| **v1.16.0** | Transactions reliability | 🔁 Correctness | Re-baseline transaction stats if you asserted on the prior behavior. |

_Internal: spec `002-test-model` (test model & regression coverage) was a process change with no user-facing release._

**Legend** — ⚠️ Behavioral: observable runtime behavior changed, you must act. 🔁 Correctness: a bug was fixed; no API break, but re-baseline tests pinned to the old output. All rows are semver MINOR/PATCH (no source/binary break).

---

## v1.23.0 — Secret masking & leak prevention

### Banner & diagnostics now go through SLF4J

`Utility.banner(...)` and `Utility.diagnostics()` previously wrote to `stdout` with `println` — always on, unfilterable.
They now emit through the SLF4J logger `org.galaxio.gatling.diagnostics`. The simulation code does not change. There are
**two separate concerns, one knob each — do not mix them:**

**1. Whether the output is produced — the Picatinny flags. This is the only on/off you need.**

| Setting | Default | Controls |
|---------|---------|----------|
| `picatinny.startup.banner.enabled` | `true` | the startup banner |
| `picatinny.diagnostics.enabled` | `false` | the JVM / runtime diagnostics block |

Set them in `simulation.conf`, or override per run with `-D`:

```hocon
# simulation.conf
picatinny.startup.banner.enabled = true
picatinny.diagnostics.enabled    = false
```

```bash
sbt Gatling/test -Dpicatinny.startup.banner.enabled=false   # turn the banner off
```

**2. How produced output renders — one `logback.xml`, set up once and left alone.**

Add this so the produced banner isn't swallowed by your root level and the ASCII box has no per-line prefix. Do **not**
toggle the banner here — use the flag from concern #1.

```xml
<configuration>
    <appender name="BANNER" class="ch.qos.logback.core.ConsoleAppender">
        <encoder><pattern>%msg%n</pattern></encoder>
    </appender>
    <logger name="org.galaxio.gatling.diagnostics" level="INFO" additivity="false">
        <appender-ref ref="BANNER"/>
    </logger>
    <root level="ERROR"/>
</configuration>
```

If the banner vanished after upgrading, that is concern #2 — add the snippet once. To turn it on/off afterwards, use the
flag (concern #1), never the logback level.

### Secret masking is now whole-word, not substring

Masking previously matched a key if its path **contained** a sensitive substring, which both over-masked benign keys
(`tokenBucketSize`, `secretariat`) and under-masked real secrets (`passwordHash`, `tokenValue`). It now matches the
key's **last path segment** by whole word, and the term list is extensible. If a project-specific key now prints in
clear, declare it (the built-in floor can only be extended, never weakened).

Before — `secretariat` was masked only by the substring accident "secret"; nothing was declared:

```hocon
# (no redaction config)
tenant.secretariat = "value"   # masked by luck
db.passwordHash    = "value"   # NOT masked — leaked
```

After — declare the keys you actually want masked:

```hocon
picatinny.redaction {
  additionalSensitiveKeys = ["secretariat"]   # passwordHash is now masked automatically
  replacement = "******"                       # optional
}
```

### Also changed (no action needed)

These touched-feature changes are informational — returned values, signatures, and JWT signing are unchanged; only logged/string output differs.

- **`SigningKey.toString` redacted.** `StringSecret`/`AsymmetricKey` now render `StringSecret(******)` / `AsymmetricKey(******)` instead of the raw secret/key. The `value` accessor is unchanged, so JWT signing is byte-for-byte identical. *Why:* key material was reaching logs/exceptions via interpolation.
- **Nested config logged leaf-by-leaf.** When a config value is read as a `Config` block, it is now logged per leaf with per-leaf masking (previously the whole block was stringified and masked only by the parent path). Returned config values are unchanged. *Why:* a benignly-named block could hide secret children in clear (#208).

## v1.22.0 — Config & cookie fixes

### `restoreCookies` now puts cookies in the jar (they auto-send)

`restoreCookies(field, domain)` previously only set cookie name→value as **session attributes**; they were not attached
to later requests, so you sent them by hand. They are now also registered in Gatling's cookie jar and sent automatically
— the prior "saved but not sent" split was a bug.

Before — cookies were NOT sent automatically; a manual `Cookie` header was required:

```scala
import org.galaxio.gatling.storage.SessionStorage.restoreCookies

exec(restoreCookies("savedSetCookie", "example.com"))
  .exec(http("api").get("/data").header("Cookie", "#{sid}"))
```

After — restored cookies are in the jar; drop the manual header:

```scala
import org.galaxio.gatling.storage.SessionStorage.restoreCookies

exec(restoreCookies("savedSetCookie", "example.com"))
  .exec(http("api").get("/data"))
```

If you relied on cookies NOT being sent, remove the `restoreCookies` call or scope `domain` so it does not match the host.

### Decimal `intensity` parses exactly

The intensity regex captured only one fractional digit, so `"0.25 rps"` silently became `0.2`. It now parses the full
value; malformed input (e.g. `"1.2.3 rps"`) fails fast instead of truncating.

Before:

```hocon
intensity = "0.25 rps"   # actually injected 0.2 rps
```

After:

```hocon
intensity = "0.25 rps"   # injects 0.25 rps
```

### `randomValue(min, max)` upper bound is exclusive

The scaladoc claimed `max` was inclusive; the implementation was always exclusive. The doc is now correct — if you
trusted the wrong doc, pass an exclusive bound.

Before — expecting `10` to be reachable (it never was):

```scala
import org.galaxio.gatling.utils.RandomDataGenerators.randomValue

randomValue(1, 10)   // yields 1..9
```

After — pass `max + 1` for an inclusive upper value:

```scala
import org.galaxio.gatling.utils.RandomDataGenerators.randomValue

randomValue(1, 11)   // yields 1..10
```

> The `RandomDataGenerators` value helpers (`randomValue`, `randomString`, `randomDate`, …) are slated for deprecation in favour of the Faker API (`Faker.number.*` / `Faker.string.*` / `Faker.date.*`) — see [Deprecations](#deprecations) and [#239](https://github.com/galax-io/gatling-picatinny/issues/239). They still work today.

## Correctness releases (re-baseline only)

**v1.16.0** (transactions reliability), **v1.18.0** (NFR-YAML assertions), **v1.19.0** (template output), **v1.20.0**
(storage/JDBC/Vault) and **v1.21.0** (feeders/generators/JWT) are bug fixes with **no API or source change**. They
change output from *wrong* to *right*: corrected feeder/generator/JWT values, well-formed template bodies,
de-duplicated NFR assertions, accurate transaction stats. Nothing changes in production code; the only thing that can
break is a test that hard-codes the previously-incorrect output.

Before — assertion frozen to the old, buggy value:

```scala
result shouldBe "old-wrong-value"
```

After — assert the corrected value, or a shape that survives future fixes:

```scala
result should fullyMatch regex "[A-Z][0-9]{3}"
```

## Deprecations

> **v1.23.0 deprecates and removes nothing.** No public API was added, changed, or removed; the masking/banner changes are behavioral (log output only). The list below is the project's standing deprecation set (pre-existing) — kept here so upgraders see what to migrate off and why.

Per the project's versioning policy, **removal of any deprecated API happens only in a MAJOR release** (`2.0.0`+). All entries below still work today; none has a fixed removal date — treat "next major" as the earliest.

| Deprecated API | Since | Reason | Replacement |
|----------------|-------|--------|-------------|
| All legacy feeders & data generators — the `Random*Feeder` family, `RegexFeeder`, and the `RandomDataGenerators` helpers | `faker-api` | Superseded by the composable, domain-oriented Faker API | **Migrate to the Faker API** — `Faker.*` + `GeneratedFeeder`. See [feeders.md](feeders.md) / [faker-api.md](faker-api.md) |
| `RedisSaddAction`, `RedisSremAction`, `RedisDelAction` | `picatinny 0.x` | Folded into one generic builder | `GenericRedisActionBuilder` + `RedisCommand.Sets.SAdd` / `.Sets.SRem` / `.Keys.Del` — see [redis.md](redis.md) |
| NFR-YAML assertions: `assertionFromYaml` (Scala + Java), `AssertionBuilderException` | `1.18.0` | NFR-YAML loading to be replaced by a new assertions API | Forthcoming assertions functionality — still works; watch the CHANGELOG. See [assertions.md](assertions.md) |

Compiler note: deprecated members emit `@deprecated`/`@Deprecated` warnings (with the same message/replacement) at build time, so usages surface in your own compile logs.
