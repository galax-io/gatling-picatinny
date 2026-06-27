# Quickstart: Validate Secret Masking & Leak Prevention

How to prove each leak path is closed. Run from repo root.

## Prerequisites

```bash
sbt scalafmtAll scalafmtSbt          # format
sbt scalafmtCheckAll compile         # green build before validating
```

## Unit / functional (the bulk of FR coverage)

```bash
sbt "testOnly org.galaxio.gatling.config.SimulationConfigUtilsSpec"   # FR-001/002/003/004/012
sbt "testOnly org.galaxio.gatling.utils.jwt.SigningKeySpec"           # FR-005
sbt "testOnly org.galaxio.gatling.diagnostics.DiagnosticsSpec"        # FR-006/008/009
```

Expected:
- **FR-003 over-match guard**: `isSensitive("roleIdPrefix") == false`, `isSensitive("tokenBucketSize") == false`, while `isSensitive("bearerToken") == true`. (The #208 regression guard.)
- **FR-004 nested**: `displayConfig` of `http { url=…, token="abc" }` shows `http.url` but `http.token = ******` and never `abc`.
- **FR-005**: `StringSecret("topsecret").toString` contains `******`, not `topsecret`; `.value` still returns `topsecret`.
- **FR-006**: `-DvaultToken=hunter2` → `-DvaultToken=******`; `-Xmx2g` untouched.
- **FR-007**: `redactUserInfo("https://user:pass@host/p") == "https://******@host/p"`; malformed input does not throw.
- **FR-008/009**: banner is exactly ONE captured `ILoggingEvent` under `org.galaxio.gatling.diagnostics`; stdout is empty (no `println`).

## Compile / classpath guard (FR-010)

```bash
sbt compile        # library compiles with scala-logging only; no logback at compile scope
```

Confirm `project/Dependencies.scala` declares no first-party logback; the library main classpath ships no `logback.xml`.

## Full Gatling e2e (FR-011) — recommended overlay config

```bash
cd examples/scala-sbt-example
sbt Gatling/test
```

Expected: run completes green; the startup banner line carries NO date/level prefix (rendered via the `BANNER` `%msg%n` appender), while ordinary log lines keep the normal prefixed pattern — proving the dedicated `org.galaxio.gatling.diagnostics` category with `additivity="false"`.

## Manual smoke (optional)

Launch any example with a secret-bearing `-D` and a userinfo base URL, then scan output:

```bash
# none of the secret values should appear in captured output
sbt -DvaultToken=hunter2 Gatling/test 2>&1 | grep -c 'hunter2'   # expect 0
```

## Reference

- Decisions: [research.md](research.md) (D1–D8)
- Entities: [data-model.md](data-model.md)
- Stable surface: [contracts/public-api.md](contracts/public-api.md)
