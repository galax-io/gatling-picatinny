# Quickstart: Validate the v1.22.0 Config & Cookie Fixes

Prerequisites: repo root, sbt, Java 17+. Docker NOT required (no Testcontainers in scope).

Run formatting + the standard CI gate before committing any change:

```bash
sbt scalafmtAll scalafmtSbt
sbt scalafmtCheckAll scalafmtSbtCheck compile test
```

## FR-001/002/003 — Decimal intensity (#93)

Unit layer. The regression guard the bug lacked:

```bash
sbt "testOnly org.galaxio.gatling.utils.IntensityConverterTest"
```

Expected after the fix: `"0.25 rps"` → `0.25`, `"123.55 rph"` → `123.55/3600`, whole numbers and units unchanged; `".5"`, `"1.2.3"`, `"abc"`, `""` throw `IllegalArgumentException`. (Before the fix, the new decimal cases fail — write them first, watch them go red.)

## FR-006/007 — Max-Age WARN (#111)

Unit layer (value + WARN capture via the repo's Logback `ListAppender` idiom):

```bash
sbt "testOnly org.galaxio.gatling.storage.CookieParserSpec"
```

Expected: `Max-Age=abc` → `maxAge = None` AND exactly one WARN containing `abc`; `Max-Age=3600` → `Some(3600L)`, zero WARNs; absent `Max-Age` → `None`, zero WARNs.

## FR-004/005 — Cookie restored into the jar (#207)

Component layer (session.set + multi-cookie + no-op via `transactions/Mocks`):

```bash
sbt "testOnly org.galaxio.gatling.storage.SessionStorageSpec"
```

End-to-end two-role switching (proves the cookie auto-sends AND that re-restoring swaps the active role):

```bash
sbt publishLocal            # from repo root first, OR set PICATINNY_VERSION to a published build
cd examples/scala-sbt-example
sbt "Gatling/testOnly org.galaxio.performance.picatinny.Debug"   # switching scenario added to Debug; or: sbt Gatling/test
```

Scenario (single VU, no explicit `Cookie` header on any protected call — relies purely on the jar):

| Step | Action | Expected status |
|------|--------|-----------------|
| 1 | login user → save body cookie; `restoreCookies(..,"localhost")` | 200 |
| 2 | `GET /admin/data` | **403** (user ≠ admin) |
| 3 | `GET /user/data` | **200** |
| 4 | login admin → `restoreCookies` (overwrites `sid`) | 200 |
| 5 | `GET /admin/data` | **200** |
| 6 | `GET /user/data` | **403** (user revoked by overwrite) |
| 7 | login user again → `restoreCookies` | 200 |
| 8 | `GET /user/data` | **200** (back to user) |

All assertions are `check(status.is(...))` on the response; `global.failedRequests.count.is(0)` gates the build. Login stubs return the cookie in the response **body** (not a `Set-Cookie` header) so only `restoreCookies` injects it. No `WireMock.verify`, no request re-decode.

## FR-008 — randomValue scaladoc (#207)

Doc-only; no behavior change. The existing property tests stand as the guard:

```bash
sbt "testOnly org.galaxio.gatling.utils.RandomDataGeneratorsTest"
```

Confirm the Scaladoc on `randomValue(min, max)` and `randomValue(max)` now reads "exclusive" for the maximum.

## FR-009 — Backward compatibility

```bash
sbt compile test    # compile guard + facade delegation specs stay green
```

Confirm no public signature changed: `getIntensityFromString: Double`, `restoreCookies(String, String): ChainBuilder`, `ParsedCookie` shape, `SimulationConfig.intensity: Double`.

## Coverage gate

```bash
sbt clean coverage test coverageReport
```

Statement ≥ 65%, branch ≥ 60% (must not regress).
