# Implementation Plan: Secret Masking & Leak Prevention (v1.23.0)

**Branch**: `007-secret-masking-leak-prevention` | **Date**: 2026-06-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-secret-masking-leak-prevention/spec.md`

## Summary

Close credential-leak paths for milestone [v1.23.0](https://github.com/galax-io/gatling-picatinny/milestone/8) (issues [#208](https://github.com/galax-io/gatling-picatinny/issues/208), [#87](https://github.com/galax-io/gatling-picatinny/issues/87), [#88](https://github.com/galax-io/gatling-picatinny/issues/88)). One central redaction helper (`ConfigValueMasking`) applied at every sink, plus routing diagnostics through SLF4J and delivering recommended logging config without polluting the consumer classpath.

1. **#208 central masking — harden + extend `ConfigValueMasking`**: today it matches a 17-token list by raw lowercase `contains` against the full path (over-matches `roleIdPrefix`/`tokenBucketSize`/`apiKeyboard`), masks only scalar `(path, value)` calls, and is hardcoded/`private[config]`. Harden to **last-path-segment + word-boundary** matching; add FR-003 terms (`authorization`, `bearer`, `passphrase`, `key`-as-compound); make the term list **config-extensible** (merge-not-replace via optional `picatinny.redaction`); widen to `private[gatling]` (internal — compat-exempt) so `diagnostics`/`feeders` can reuse it.
2. **#208 nested-config leak — leaf-walk masking**: `SimulationConfigUtils.getValueByType` has ONE log site (line 51) that calls `displayValue(path, value)` for every read type. When the read value is a nested `Config` (the `case ConfigTag` value-read at line 68), `displayValue` checks only the **parent** path and stringifies the whole block — a benign block name (`http`) leaks secret child leaves (`http.token`) at INFO. Add `displayConfig(cfg)` walking `Config.entrySet()` (already-flattened leaves), masking each leaf by its own last segment; at line 51, when the value is a `Config`, render via `displayConfig` instead of `displayValue`.
3. **#208 SigningKey leak — redact toString**: `StringSecret(value: String)` and `AsymmetricKey(value: PrivateKey)` are case classes; derived `toString` leaks the HMAC secret / provider-dependent key bytes. Override `toString` on both to `…(******)`. Keep types/fields/`apply`/`unapply`/`copy`/`value` intact (published API).
4. **#208 `-D` args leak — redact JVM args**: `Diagnostics.scala:21` joins `mxBean.getInputArguments` raw → `-DvaultToken=…` echoed. Split each `-Dkey=value`, run the key through `ConfigValueMasking.isSensitive`, replace sensitive values with `******` keeping `-Dkey=******`.
5. **#208 URL userinfo leak — strip credentials**: add a fail-safe `redactUserInfo(raw)` (java.net.URI + string surgery; never throws; regex+constant fallback) and apply at the `StartupBanner` base-URL sink (line 54).
6. **#87 println → SLF4J**: replace the 4 `println` sites (`StartupBanner` 15/27/30, `Diagnostics` 13) with `StrictLogging`, emitting each multi-line block as a **single** `logger.info` event under the `org.galaxio.gatling.diagnostics` category (alignment-safe). Keep existing `isEnabled` flags gating emission.
7. **#88 recommended logging config — NOT on the library main classpath**: ship no logback in library main (compile dep stays `scala-logging` only); deliver the recommended config via docs + the runnable `examples/scala-sbt-example` overlay `logback.xml` (dedicated `BANNER` `%msg%n` appender, `additivity="false"`, suppressible by category/level).

## Technical Context

**Language/Version**: Scala 2.13.18

**Primary Dependencies**: Gatling 3.13.5 (`Provided`), `com.typesafe.scala-logging` 3.9.6 (compile, SLF4J-backed), Typesafe Config (transitive via PureConfig/Gatling), PureConfig 0.17.10. **No new dependencies.** logback stays Test-scope only (transitive via Gatling).

**Storage**: N/A (config lives in Typesafe `Config`; no persistence).

**Testing**: ScalaTest (`AnyWordSpec` + `Matchers`); logback `ListAppender` for log capture (the `captureWarns` idiom in `AssertionsBuilderSpec`, retargeted to `org.galaxio.gatling.diagnostics` and the config logger); `Console.withOut(ByteArrayOutputStream)` to assert stdout is now empty; full Gatling e2e in `examples/scala-sbt-example` (`sbt Gatling/test`) for the overlay config.

**Target Platform**: JVM (compile target Java 17; CI Temurin 21).

**Project Type**: Published Scala library with thin Java/Kotlin facade.

**Performance Goals**: N/A (security/observability hardening). The config-extensible term list is resolved ONCE at config-load and passed in — no per-log-line config read. `entrySet()` leaf-walk runs only on nested-block display (rare), O(leaves).

**Constraints**: Backward compatibility (Constitution II) — no public Scala/Java signature change. `SigningKey`/`StringSecret`/`AsymmetricKey` types, fields, `value` accessors unchanged (only `toString` overridden). `ConfigValueMasking` is `private[config]` → widened to `private[gatling]` (internal, compat-exempt). New `picatinny.redaction` config keys are **additive and optional** (absent = built-in defaults). Coverage floor 65%/60%.

**Scale/Scope**: 5 source files + 3–4 test files + 1 overlay `logback.xml` + docs. ~Small–Medium.

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | A sensitive param (`db.password=s3cr3t`) read via `SimulationConfigUtils.getValueByType` is logged masked | Unit / Functional | Extend `ConfigValueMaskingSpec`: assert `displayValue("db.password","s3cr3t") == "******"` (exact). Capture the INFO line emitted by `getValueByType` for a sensitive path via `ListAppender` on the config logger; assert it contains `******` and NOT `s3cr3t`. Negative: a non-sensitive path (`baseUrl`) logs the literal value unchanged. |
| FR-002 | The same one term-set drives config masking, JVM-arg redaction, and (when keyed) URL handling | Unit / Functional | Add an extension term via `picatinny.redaction` (D8) and assert it now masks in BOTH `displayValue` AND the `-Dcustomsecret=…` JVM-arg redactor — proving a single shared `ConfigValueMasking` decision. Negative: a term absent from built-ins + config stays visible in both. |
| FR-003 | Each required term masks: `token`/`password`/`secret`/`key`/`authorization`/`bearer`/`passphrase` (+ `roleId` must NOT) | Unit / Functional | Parameterized `isSensitive`/`displayValue`: `authorizationHeader`, `bearerToken`, `passphrase`, `apiKey`, `clientSecret` → `******`. Boundary/negative (the core of #208 hardening): `roleId`, `roleIdPrefix`, `tokenBucketSize`, `apiKeyboard`, `secretariat` → `isSensitive == false` (word-boundary on last segment, not `contains`). |
| FR-004 | A benignly-named block `http { url=…, token="abc" }` displayed as a whole | Unit / Functional | Build a HOCON `Config`; assert `displayConfig(cfg)` contains `http.url = …` visibly and `http.token = ******`, and does NOT contain `abc`. Boundary: deeply nested `a.b.c.secret` leaf masked; a block with no sensitive leaf renders every value visibly (no over-mask). |
| FR-005 | `StringSecret`/`AsymmetricKey` interpolated into a log or exception message | Unit / Functional | New `SigningKeySpec`: assert `StringSecret("topsecret").toString == "StringSecret(******)"` and contains neither `topsecret`; assert `AsymmetricKey(key).toString == "AsymmetricKey(******)"`. Exact-value API guard: `StringSecret("topsecret").value == "topsecret"` still returns the raw secret (accessor intact). |
| FR-006 | A run launched with `-DvaultToken=hunter2` reaches diagnostics | Unit / Functional | Unit-test the per-arg redactor over `Seq("-Xmx2g","-DvaultToken=hunter2","-Dapp.name=foo")` → yields `-DvaultToken=******`, leaves `-Xmx2g` and `-Dapp.name=foo` untouched (exact list). Negative: `-DvaultToken` with no `=value` passes through; result never contains `hunter2`. |
| FR-007 | Base URL `https://user:pass@host:8080/p` printed in the banner | Unit / Functional | Assert `redactUserInfo("https://user:pass@host:8080/p") == "https://******@host:8080/p"`; `redactUserInfo("https://host/p")` returned unchanged (no userinfo). Fail-safe/boundary: malformed `"ht!tp://u:p@x"` does NOT throw and returns a string containing neither `:p@` nor the raw password; opaque `mailto:a@b` unchanged. |
| FR-008 | The startup banner goes through SLF4J, not stdout | Unit / Functional | Attach `ListAppender` to `org.galaxio.gatling.diagnostics`; emit banner with the flag enabled; assert exactly one captured event whose message contains `Picatinny Gatling Run`. Negative (proves no `println`): wrap the call in `Console.withOut(ByteArrayOutputStream)` and assert stdout is empty. |
| FR-009 | The multi-line ASCII banner stays aligned as a single event | Unit / Functional | With `ListAppender` on `org.galaxio.gatling.diagnostics`: assert `appender.list.size == 1` (one `ILoggingEvent`, NOT one-per-line), `getLoggerName` starts with `org.galaxio.gatling.diagnostics`, and `getMessage` contains embedded `\n` plus the ASCII-preview chars (`|`, `/`, `_`) intact. Boundary: assert it is NOT split into multiple events. |
| FR-010 | Library main classpath ships no logback config | Compile Guard | A `Test` guard asserting the library declares no first-party logback and ships no `logback.xml` on the main classpath: assert `getClass.getResource("/logback.xml")` resolves only to the **test** resource (not a main artifact entry) and that `project/Dependencies.scala` lists logback nowhere at compile scope (documented + asserted by scanning the resolved Compile classpath for absence of `ch.qos.logback`). Negative: the same scan finds logback present in the Test classpath (transitive), proving it is Test-only. |
| FR-011 | The example overlay ships the recommended banner config and runs prefix-free | Full Gatling e2e (examples) | In `examples/scala-sbt-example`, run the example (`sbt Gatling/test`) with the updated `logback.xml` (BANNER `%msg%n` appender + `additivity="false"` on `org.galaxio.gatling.diagnostics`); assert the run completes green and the captured banner line carries NO date/level prefix. Negative: a non-diagnostics log line in the same run still carries the normal prefixed pattern (proves the category scoping, not a global pattern change). |
| FR-012 | A user adds `myCorpToken` via `picatinny.redaction.additionalSensitiveKeys` | Unit / Functional | Construct `ConfigValueMasking` from a `Config` with `picatinny.redaction.additionalSensitiveKeys=["mycorptoken"]`; assert `displayValue("mycorptoken","v") == "******"` while built-ins still mask. Boundary: ABSENT `picatinny.redaction` → built-ins still mask (defaults applied); merge-not-replace — a user list that omits `password` does NOT un-mask `password`. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — All masking/logging logic lives in Scala core (`config`, `utils/jwt`, `diagnostics`). No Java/Kotlin facade change required (the facade does not log config); if any facade later logs, it must delegate to `ConfigValueMasking`, not reimplement. ✓
- [x] **II. Backward Compatibility** — No public signature change. `SigningKey`/`StringSecret`/`AsymmetricKey` types/fields/`value`/`apply`/`unapply`/`copy` unchanged (only `toString` overridden — observable string changes, but `toString` of a secret holder is not a compat contract). `ConfigValueMasking` is `private[config]` → `private[gatling]` (internal refactor, explicitly compat-exempt per Constitution II). New `picatinny.redaction.*` keys are additive + optional (absent = defaults), not a format change to existing keys. Justifies the v1.23.0 MINOR bump. ✓
- [x] **III. Test Discipline** — Test Model above is filled per FR (FR-001..FR-012) with real case + layer + code-free sketch and ≥1 negative/boundary each; work is test-first. Layers follow TESTING.md: pure-function + logging units (ScalaTest + `ListAppender`), one Full-Gatling-e2e overlay test (FR-011), one Compile Guard (FR-010). No Testcontainers needed (no Redis/Vault/JDBC container surface). No Gatling-runtime mocking. Coverage stays ≥ floor. ✓
- [x] **IV. Small, Focused Changes** — No new deps. **Note (principle bent, justified in Complexity Tracking):** (a) `ConfigValueMasking` matching is restructured (contains → last-segment word-boundary) — required to fix the #208 over-match, not opportunistic; (b) visibility widened `private[config]` → `private[gatling]` — minimal, internal. `VaultFeeder.scala:201`'s ad-hoc userinfo strip is NOT consolidated in this change (would be out-of-scope refactor) — noted as follow-up. ✓
- [x] **V. Release Integrity** *(release PRs only)* — Fixes land on `main` via PR first; v1.23.0 is a MINOR release cut as `release/1.23.0` from `main` per the release process. Not exercised at plan stage. ✓

## Project Structure

### Documentation (this feature)

```text
specs/007-secret-masking-leak-prevention/
├── plan.md              # This file
├── research.md          # Phase 0 output (D1–D8 decisions)
├── data-model.md        # Phase 1 output (redaction entities)
├── quickstart.md        # Phase 1 output (how to validate each leak path)
├── contracts/
│   └── public-api.md    # Phase 1 output (unchanged public surface + new internal/config surface)
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (repository root)

```text
src/main/scala/org/galaxio/gatling/
├── config/
│   ├── ConfigValueMasking.scala       # FR-001/002/003/004/007/012 — central helper: word-boundary match,
│   │                                  #   displayConfig leaf-walk, redactUserInfo, config-extensible terms,
│   │                                  #   widen private[config] → private[gatling]
│   └── SimulationConfigUtils.scala    # FR-004/012 — at line-51 sink, render Config values via displayConfig;
│                                      #   build masking term-set from optional picatinny.redaction
├── utils/jwt/
│   └── SigningKey.scala               # FR-005 — override toString on StringSecret + AsymmetricKey
└── diagnostics/
    ├── Diagnostics.scala              # FR-006/008/009 — println → StrictLogging single event; -D arg redaction
    └── StartupBanner.scala            # FR-007/008/009 — println → StrictLogging single event; redactUserInfo on baseUrl

src/test/scala/org/galaxio/gatling/
├── config/
│   └── SimulationConfigUtilsSpec.scala  # FR-001/002/003/004/012 — extend ConfigValueMaskingSpec:
│                                        #   over-match negatives, displayConfig nested, extensible terms, masked log line
├── utils/jwt/
│   └── SigningKeySpec.scala             # FR-005 — toString redaction + value accessor intact (NEW)
└── diagnostics/
    └── DiagnosticsSpec.scala            # FR-006/008/009 — -D redaction unit; banner single-event via ListAppender; stdout empty

src/test/scala/org/galaxio/gatling/          # FR-010 — compile/classpath guard (location TBD in tasks)

examples/scala-sbt-example/src/test/resources/
└── logback.xml                          # FR-011 — add BANNER %msg%n appender + diagnostics category (additivity=false)

docs/logging.md                          # FR-011 — recommended logback snippet + override path; references the overlay logback.xml as canonical
```

**Structure Decision**: Single published-library project (Option 1). Changes are confined to `config/`, `utils/jwt/`, `diagnostics/` in Scala core plus their existing/new test specs, with the recommended-config delivery in the `scala-sbt-example` overlay (the authoritative copy CI overlays onto the template) and docs. No facade files change (masking is core-side; delegation already covers the facade).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `ConfigValueMasking` matching restructured (raw `contains` → last-segment word-boundary) (Principle IV — minimal-change bent) | The #208 over-match (`roleIdPrefix`, `tokenBucketSize`, `apiKeyboard` falsely masked; future broad terms worsen it) cannot be fixed by adding tokens — the matching rule itself is the defect. | Adding the FR-003 terms to the existing `contains` list directly amplifies false positives (e.g. `passphrase` token would mask any `pass…`); regex-valued config pushes over-match onto users. |
| `ConfigValueMasking` visibility widened `private[config]` → `private[gatling]` (Principle IV — internal scope change) | `Diagnostics` (`-D` arg redaction) and `StartupBanner` (`redactUserInfo`) live in package `org.galaxio.gatling.diagnostics` and must reuse the single central helper (FR-002), which `private[config]` hides. | A duplicate token list / strip helper in `diagnostics` violates FR-002 (single central list) and the constitution's single-source-of-truth principle. Public API exposure is unnecessary and would be a compat surface. |
| `picatinny.redaction.*` config keys added (Principle IV / Constitution II — serialized config surface) | FR-012 requires user-extensible terms; merge-not-replace semantics keep it a security floor. | A compile-time-only list (status quo) fails FR-012; replace-semantics would let a user un-mask the library's known secrets. Keys are additive + optional (absent = defaults), so no existing config breaks. |
