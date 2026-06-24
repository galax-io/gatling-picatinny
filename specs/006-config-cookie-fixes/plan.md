# Implementation Plan: Config & Cookie Correctness Fixes (v1.22.0)

**Branch**: `006-config-cookie-fixes` | **Date**: 2026-06-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-config-cookie-fixes/spec.md`

## Summary

Four independent correctness fixes for milestone [v1.22.0](https://github.com/galax-io/gatling-picatinny/milestone/7). Note: issue [#207](https://github.com/galax-io/gatling-picatinny/issues/207) bundles TWO sub-fixes — the cookie-discard fix (item 2) and the randomValue doc fix (item 4):

1. **#93 — decimal intensity truncation**: `IntensityConverter` regex `(\d+\.?\d?)` captures only one fractional digit; replace with an anchored full-match `^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)?$` so `0.25 rps` → `0.25` and malformed input throws cleanly instead of silently truncating.
2. **#207 cookie discard — restore into the jar**: `SessionStorage.restoreCookies` parses cookies then only `session.set(name, value)`, so cookies never auto-send. Add jar registration via the **supported public** `addCookie`/`Cookie` DSL (name/value at runtime, scoped to the `domain` arg, default path `/`), iterating the runtime-parsed list with `foreach`. Keep the existing `session.set` (additive). Per-cookie `path`/`max-age`/`secure`/`httpOnly` are not propagated — the public DSL fixes them at build time, and reflection into Gatling internals is rejected (see Constitution IV note).
3. **#111 — silent Max-Age**: `CookieParser` drops a non-numeric `Max-Age` with no log; add a WARN naming the offending value (`StrictLogging`, matching `TransactionTracker`).
4. **#207 (same issue as item 2) randomValue doc**: Scaladoc for `randomValue(min, max)` / `randomValue(max)` says the max is inclusive; the implementation is exclusive (`ThreadLocalRandom.next*(min, max)`). Fix the docs only; no behavior change.

## Technical Context

**Language/Version**: Scala 2.13.18

**Primary Dependencies**: Gatling 3.13.5 (`Provided`), Scala Logging (`StrictLogging`), netty cookie types (transitive via gatling-http, 4.1.119.Final). No new dependencies.

**Storage**: N/A (cookie jar lives in the Gatling `Session`, managed by Gatling).

**Testing**: ScalaTest (`AnyWordSpec` + `Matchers` + `ScalaCheckDrivenPropertyChecks`); Logback `ListAppender` for WARN capture (already on the test classpath, idiom from `AssertionsBuilderSpec`); full Gatling e2e in `examples/scala-sbt-example` against WireMock (`sbt Gatling/test`).

**Target Platform**: JVM (compile target Java 17; CI Temurin 21).

**Project Type**: Published Scala library with thin Java/Kotlin facade.

**Performance Goals**: N/A (correctness fixes; no hot-path change. `foreach` over a per-VU cookie list is O(cookies), negligible).

**Constraints**: Backward compatibility (Constitution II) — no public Scala/Java signature change; `restoreCookies(setCookieField: String, domain: String): ChainBuilder` unchanged; `ParsedCookie` shape unchanged; intensity stays `Double`. Coverage floor 65%/60%.

**Scale/Scope**: 4 source files + 2–3 test files + 1 examples overlay e2e. ~Small.

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | `"0.25 rps"` and `"123.55 rph"` configured intensities | Unit / Functional | In `IntensityConverterTest`, assert `getIntensityFromString("0.25 rps")` equals exactly `0.25` and `"123.55 rph"` equals `123.55/3600` (exact-value). Negative/boundary: `"1.2.3 rps"` and `".5"` MUST throw `IllegalArgumentException` (no silent truncation). These are the regression guards #93 lacked. |
| FR-002 | Whole numbers, case-insensitive unit, default unit, extra whitespace | Unit / Functional | Assert `"50 rpm"` = `50/60`, `"10"` (no unit) = `10` rps, `"100 RPS"` = `100`, `"1.5   rps"` = `1.5` (extra spaces tolerated). Boundary: `"12rps"` (no separator) still parses to `12` rps. |
| FR-003 | Garbage and partial inputs | Unit / Functional | Assert `"abc"`, `""`, `"1."`, `"-5"` each throw `IllegalArgumentException` with the existing message; assert a valid value still parses (positive case) so the throw is specific to malformed input, not blanket. |
| FR-004 | Two-role cookie switching: restore user cookie → admin endpoint fails / user succeeds; restore admin (same `sid`, overwrites) → admin succeeds / user fails; restore user again → user succeeds | Full Gatling e2e (examples) | Added to the existing single-VU `Debug.scala` in `examples/scala-sbt-example`. Two login stubs return the raw Set-Cookie in the response **body** (so Gatling does NOT auto-capture — only `restoreCookies` injects). One cookie name `sid`, two values (`user-secret`/`admin-secret`); same domain/path so re-restore overwrites and revokes the prior role. Protected stubs match on `withCookie("sid", equalTo(...))` → 200 else 403. Each protected request carries NO explicit `Cookie` header; assert the 8-step status sequence (5 role-gated checks) via `check(status.is(200|403))` (403 = NOT success, 200 = success), gated by `global.failedRequests.count.is(0)`. Boundary: step 6 (`/user/data` → 403 after admin overwrite) proves the switch revokes. No `WireMock.verify` / request re-decode. Full flow in research.md Decision 5. |
| FR-004 | `restoreCookies` also still sets the session attribute (backward compat) | DSL / Action Component | Drive `restoreCookies` via the `transactions/Mocks` ActorSystem harness against a real `Session` seeded with a raw `Set-Cookie`; assert the resulting session has the cookie name→value attribute set (exact value) — the additive behavior is preserved. |
| FR-005 | Multi-line `Set-Cookie` (2 cookies) and a missing source attribute | DSL / Action Component + Unit | Component: seed a two-cookie multi-line value; assert both name→value pairs are set and the chain completes. No-op boundary: when the source attribute is absent, assert the session passes through unchanged with no error. (Pure parse of the multi-line string is already covered in `CookieParserSpec`.) |
| FR-006 | `Set-Cookie: sid=x; Max-Age=abc` | Unit / Functional | In `CookieParserSpec`, assert the returned `ParsedCookie.maxAge` is `None` AND (via the repo's `captureWarns` Logback `ListAppender` idiom, logger `org.galaxio.gatling.storage`) exactly one WARN is emitted whose message contains `abc`. Negative pairing with FR-007 below. |
| FR-007 | Valid `Max-Age=3600` and entirely absent `Max-Age` | Unit / Functional | Assert `maxAge == Some(3600L)` with zero WARNs for the valid case, and `maxAge == None` with zero WARNs for the absent case (absence is not an error — the key boundary that prevents log noise). |
| FR-008 | `randomValue(min, max)` documented bound vs runtime bound | Unit / Functional (existing) + doc | Doc-only change; no new behavior test required. The existing `RandomDataGeneratorsTest` property check already asserts generated values fall in `[min, max)` — it stands as the guard that the doc now matches (value `>= min` and `< max`). |
| FR-009 | Public Scala/Java signatures unchanged | Compile Guard + Facade Delegation | Existing compile-guard / facade tests must stay green: `restoreCookies` signature, `IntensityConverter.getIntensityFromString: Double`, `SimulationConfig.intensity: Double`, and the Java facade delegations compile and delegate unchanged. |
| FR-010 | Re-restoring `sid` with a new value revokes the prior value (role switch) | Full Gatling e2e (examples) | The two-role flow in `Debug.scala` is the verifier: after restoring `sid=user-secret` then `sid=admin-secret` (same domain/path), the boundary assertion is step 6 `GET /user/data` → **403** (old user value gone) while step 5 `GET /admin/data` → 200, then step 7-8 restores user and `/user/data` → 200 again. Exact-status checks; gated by `global.failedRequests.count.is(0)`. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — All four fixes are in Scala core. The Java facade (`IntensityConverter.java`, `SimulationConfig.java`) already delegates; no facade logic added. ✓
- [x] **II. Backward Compatibility** — No public signature change: `restoreCookies(String, String): ChainBuilder`, `getIntensityFromString: Double`, `ParsedCookie` shape all unchanged. The cookie-jar registration is an **additive** DSL behavior addition (authorized in `/speckit-clarify`), warranting the v1.22.0 MINOR bump. randomValue is doc-only. Intensity regex fix changes only previously-wrong output (truncated → correct), which is a bug fix, not a compat break. ✓
- [x] **III. Test Discipline** — Test Model above is filled per FR with real case + layer + code-free sketch and ≥1 negative/boundary each; work is test-first; layers follow TESTING.md (HTTP-free unit for parser/converter; DSL-component via `transactions/Mocks` for `restoreCookies` session behavior; full e2e via WireMock overlay for auto-send; no Gatling-runtime mocking; ScalaMock only if a leaf needs mocking — none required here). Coverage stays ≥ floor. ✓
- [x] **IV. Small, Focused Changes** — No new deps (`scalalogging` already main; `logback`/`scalacheck` already test). No API signature change. **Note (principle bent, justified in Complexity Tracking):** `restoreCookies` is restructured from a single `exec{}` to `exec{} + foreach{ addCookie }` — minimal restructure required to populate the jar; the rejected fuller alternative (reflection) is documented. No opportunistic refactors. ✓
- [x] **V. Release Integrity** *(release PRs only)* — Fixes land on `main` via PR first; v1.22.0 is a MINOR release cut as `release/1.22.0` from `main` per the release process. Not exercised at plan stage. ✓

## Project Structure

### Documentation (this feature)

```text
specs/006-config-cookie-fixes/
├── plan.md              # This file
├── research.md          # Phase 0 output (Gatling cookie API + repo patterns)
├── data-model.md        # Phase 1 output (ParsedCookie, Intensity)
├── quickstart.md        # Phase 1 output (how to validate each fix)
├── contracts/
│   └── public-api.md    # Phase 1 output (unchanged public surfaces this touches)
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (repository root)

```text
src/main/scala/org/galaxio/gatling/
├── utils/
│   ├── IntensityConverter.scala        # FR-001/002/003 — regex fix
│   └── RandomDataGenerators.scala      # FR-008 — scaladoc fix (randomValue overloads)
└── storage/
    ├── CookieParser.scala              # FR-006/007 — WARN on bad Max-Age (StrictLogging)
    └── SessionStorage.scala            # FR-004/005 — restoreCookies: addCookie + foreach, keep session.set

src/test/scala/org/galaxio/gatling/
├── utils/
│   └── IntensityConverterTest.scala    # FR-001/002/003 — decimal + malformed cases
└── storage/
    ├── CookieParserSpec.scala          # FR-006/007 — maxAge None + WARN capture
    └── SessionStorageSpec.scala        # FR-004/005 — restoreCookies session.set + multi/no-op (component, transactions/Mocks)

examples/scala-sbt-example/src/test/scala/org/galaxio/performance/picatinny/
└── Debug.scala                         # FR-004/010 — add login/admin/user WireMock stubs + 8-step switch scenario + wire into setUp (no separate simulation file)
```

**Structure Decision**: Single published-library project (Option 1). Changes are confined to `utils/` and `storage/` in Scala core plus their existing test specs, with the cookie e2e added to the existing `Debug.scala` in the `scala-sbt-example` overlay (the authoritative copy CI overlays onto the template) — no separate simulation file. No facade files change (delegation already covers it).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `restoreCookies` restructured to `exec{} + foreach{ addCookie }` (Principle IV — minimal-change bent) | The jar can only be populated via the public `addCookie` action, which is build-time; a runtime-variable cookie list therefore requires `foreach` over a session-stored collection. | A single `exec{ session => ... }` cannot call `addCookie` (it is an action, not a `Session` transform). |
| Per-cookie `path`/`max-age`/`secure`/`httpOnly` NOT propagated to the jar (FR-004 scoped down from "take all") | The public `addCookie` DSL accepts only `name`/`value` as runtime values; the rest are build-time-fixed. | Reflection into `private[http] CookieSupport.storeCookie` would carry all attributes but couples a published library to Gatling internals across `Provided`-scope versions (silent breakage at the user's runtime). Rejected by user decision 2026-06-23; the dropped attributes do not affect what an outbound cookie transmits. |
