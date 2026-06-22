# Quickstart: Validate Assertions Correctness

Runnable validation that the six fixes + deprecation work. Test-first: write each
assertion before the production fix, watch it fail (red), then fix (green).

## Prerequisites

- JDK 17, sbt. No Docker (no integration layer).
- Files under test: `AssertionsBuilder.scala`, `Assertions.java`,
  `AssertionBuilderException.java`. Fixtures in `src/test/resources/` (`nfr.yml` exists;
  add `nfrSingle.yml`, `nfrNonNumeric.yml`).

## Run

```bash
sbt scalafmtAll scalafmtSbt                 # format first (project rule)
sbt "testOnly org.galaxio.gatling.assertions.AssertionsBuilderSpec"
sbt "testOnly org.galaxio.gatling.javaapi.assertions.*"
sbt compile test                            # full unit gate (no Docker)
```

## Validation scenarios (each maps to an FR + a deliberate break)

| # | Scenario | Expected | Deliberate break → must fail |
|---|----------|----------|------------------------------|
| 1 | Java `assertionFromYaml("src/test/resources/nfr.yml")` | exactly **11** assertions (FR-001) | restore `assertionList.addAll(getListAssertions(self,…))` → size becomes a power of 2 |
| 2 | `nfrSingle.yml` (`Процент ошибок`=`5.5`) | exactly **1** assertion, **Double** threshold `5.5`, both paths (FR-001 boundary + error-rate-Double / F1) | revert Scala error-rate to `toInt` → `"5.5".toInt` crashes; or restore duplication → size 2 |
| 3 | Scala vs Java for `nfr.yml` | same normalized set: 11, same scopes/thresholds (FR-002) | flip one expected threshold in the test → parity fails |
| 4 | `nfr.yml` (APDEX, RPS unknown) | result 11; a captured WARN names `APDEX` and `RPS` (FR-003) | remove the WARN call → log-capture assertion fails |
| 5 | `nfrNonNumeric.yml` (recognized key, value `"abc"`) | error message contains the key **and** `abc`, both Scala + Java (FR-004) | revert to raw `.toInt`/`Integer.valueOf` → message lacks key/value |
| 6 | `new AssertionBuilderException("m", cause)` | `getMessage()=="m"`, `getCause()==cause` (FR-005) | drop `super(msg,cause)` → both null |
| 7 | Cyrillic-keyed `nfr.yml` | 11 assertions; detail paths keep `myGroup`, `GET /test/uuid` (FR-006) | reinstate lossy `toUtf` + simulate non-UTF-8 default → key mismatch → <11 |
| 8 | Java `assertionFromYaml` called twice | identical results; mapper init once (FR-007) | n/a (structural + review) |
| 9 | Compile reference to `assertionFromYaml` | compiles; emits a deprecation warning; message generic, no version/date/link (FR-008, FR-012) | remove `@deprecated`/`@Deprecated` → compile-guard/meta assertion fails |

## Coverage / release gate

```bash
sbt clean coverage test coverageReport      # statement ≥65% / branch ≥60% (unchanged floor)
```
- The fixes add covered branches (Try-parse, WARN path) → measured coverage rises.
- Release: cut `release/1.18.0` from `main`, tag `v1.18.0` (NOT v1.17.x — already
  published). Changelog notes: WARN on unknown NFR keys; NFR-YAML assertions deprecated.

## Definition of done

- All 9 scenarios green; each deliberate break shown to fail ≥1 test then restored.
- `sbt scalafmtCheckAll scalafmtSbtCheck compile test` passes.
- No public signature changed; no new dependency; no NFR YAML format change.
