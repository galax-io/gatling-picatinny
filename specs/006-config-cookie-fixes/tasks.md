---
description: "Task list for Config & Cookie Correctness Fixes (v1.22.0)"
---

# Tasks: Config & Cookie Correctness Fixes (v1.22.0)

**Input**: Design documents from `specs/006-config-cookie-fixes/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/public-api.md](contracts/public-api.md), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. Constitution III mandates test-first (TDD): write the failing test before the code (red → green → refactor); assert exact values + ≥1 negative/boundary case; ScalaMock only for leaf deps (none needed here); never mock the Gatling runtime where a real path exists.

**Organization**: Grouped by user story. The four fixes touch independent files (`IntensityConverter`, `SessionStorage`, `CookieParser`, `RandomDataGenerators`) and are independently deliverable. The cookie e2e lives in the existing `examples/.../Debug.scala` (no separate simulation file).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1=decimal intensity, US2=cookie restore/switch, US3=Max-Age WARN, US4=randomValue doc

## Path Conventions

Published Scala library, single project. Core: `src/main/scala/org/galaxio/gatling/`. Tests: `src/test/scala/org/galaxio/gatling/`. E2e overlay: `examples/scala-sbt-example/src/test/scala/org/galaxio/performance/picatinny/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm a green baseline before changing anything (so later red tests are meaningful).

- [x] T001 Verify clean baseline from repo root: `sbt scalafmtCheckAll scalafmtSbtCheck compile test` passes on branch `006-config-cookie-fixes` before any change.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None. The four fixes are independent (separate source files, no shared new infrastructure, no new dependencies — `scalalogging` is already a main dep, `logback`/`scalacheck` already on the test classpath). All user stories may begin immediately after Phase 1.

**Checkpoint**: No blocking work — proceed to any user story.

---

## Phase 3: User Story 1 - Decimal request intensities honored exactly (Priority: P1) 🎯 MVP

**Goal**: `IntensityConverter` parses arbitrary-precision decimals exactly (`0.25 rps` → `0.25`) and throws clearly on malformed input instead of silently truncating (#93).

**Independent Test**: `sbt "testOnly org.galaxio.gatling.utils.IntensityConverterTest"` — decimals exact, units/defaults preserved, malformed throws. (Pure-parser fix → unit test only; no e2e.)

### Tests for User Story 1 (write first, must FAIL)

- [x] T002 [P] [US1] Add failing regression cases to `src/test/scala/org/galaxio/gatling/utils/IntensityConverterTest.scala`: assert exact values for `"0.25 rps"`→`0.25`, `"123.55 rph"`→`123.55/3600`, `"1.234567 rpm"`→`1.234567/60`, `"1.5   rps"` (extra inner whitespace)→`1.5`; lock the edge cases T003 implements: `" 0.25 rps "` (leading/trailing whitespace)→`0.25` and `"100 RPS"`/`"50 Rps"` (mixed-case unit)→rps; and negative/boundary cases `an[IllegalArgumentException] thrownBy` for `".5"`, `"1."`, `"1.2.3"`, `"abc"`, `""`. Keep existing `"3600.0 rps"`, `"30"`, `"3600.0 jpeg"` cases green. Also add a ScalaCheck property (SC-001 "100% of valid decimals"): for a generated non-negative decimal `d`, `getIntensityFromString(s"$d rps")` round-trips to exactly `d` (no truncation).

### Implementation for User Story 1

- [x] T003 [US1] Fix the regex in `src/main/scala/org/galaxio/gatling/utils/IntensityConverter.scala`: replace `"""(\d+\.?\d?)\s?(\w+)?"""` + `findAllIn`/`group` with an anchored full-match `"""^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)?$"""` applied to `intensity.trim` (the explicit `.trim` strips leading/trailing whitespace so `" 0.25 rps "` matches; the anchored `^…$` would otherwise reject it) via a `case pattern(value, unit) =>` extractor, default the unit to `rps` when absent, and make the unit `match` exhaustive with an explicit `case _ => throw new IllegalArgumentException("Simulation param for intensity incorrect")` (preserve that exact message/type from the outer `Try`).
- [x] T004 [US1] Run `sbt "testOnly org.galaxio.gatling.utils.IntensityConverterTest"` → green; confirm no behavior change for previously-correct inputs and that the malformed cases throw with the preserved message.

**Checkpoint**: US1 fully functional and independently testable (MVP).

---

## Phase 4: User Story 2 - Restored cookies are sent on later requests + role switching (Priority: P2)

**Goal**: `restoreCookies` registers cookies in the Gatling jar (public `addCookie` DSL, name/value at runtime, scoped to `domain`, default path `/`) so they auto-send; keeps the existing `session.set` (additive); re-restoring the same name/domain overwrites (revokes the prior role) (#207, FR-004/005/010).

**Independent Test**: `sbt "testOnly org.galaxio.gatling.storage.SessionStorageSpec"` (component) and, in `examples/scala-sbt-example`, `sbt "Gatling/testOnly org.galaxio.performance.picatinny.Debug"` (e2e auto-send + switching, added to the existing Debug simulation).

### Tests for User Story 2 (write first, must FAIL)

- [x] T005 [P] [US2] Add a DSL/action-component test to `src/test/scala/org/galaxio/gatling/storage/SessionStorageSpec.scala` driving `restoreCookies` via the `transactions/Mocks` ActorSystem harness against a real `Session`: assert (a) a single `Set-Cookie` sets the cookie name→value session attribute (exact value, backward compat / FR-005); (b) a multi-line two-cookie value sets both; (c) no-op when the source attribute is absent (session passes through, no error).
- [x] T006 [US2] Add the cookie WireMock stubs to the existing `examples/scala-sbt-example/src/test/scala/org/galaxio/performance/picatinny/Debug.scala` server setup: `GET /login/user` and `GET /login/admin` returning the raw Set-Cookie string in the response **body** (e.g. `{"cookie":"sid=user-secret; Path=/"}` / `...admin-secret...`), NOT as a `Set-Cookie` header (so Gatling does not auto-capture — only `restoreCookies` injects); and protected `GET /admin/data` / `GET /user/data` matched on cookie value (`withCookie("sid", equalTo("admin-secret"|"user-secret"))` → 200, lower-priority catch-all for the same path → 403).
- [x] T007 [US2] Add the 8-step cookie-switch scenario (5 role-gated status checks) to `Debug.scala` and wire it into the simulation's `setUp` (its own scenario at `atOnceUsers(1)`, or appended to the Debug flow): **(1)** login user → save body cookie → `restoreCookies(field, "localhost")`; **(2)** `GET /admin/data` `check(status.is(403))`; **(3)** `GET /user/data` `check(status.is(200))`; **(4)** login admin → `restoreCookies`; **(5)** `/admin/data`(200); **(6)** `/user/data`(403); **(7)** login user → `restoreCookies`; **(8)** `/user/data`(200). Each protected request carries NO explicit `Cookie` header. This must FAIL before T008 and is gated by the existing `.assertions(global.failedRequests.count.is(0))`. (Depends on T006.)

### Implementation for User Story 2

- [x] T008 [US2] Restructure `restoreCookies` in `src/main/scala/org/galaxio/gatling/storage/SessionStorage.scala`: in the `exec { session => }` block, parse the raw `Set-Cookie` via `CookieParser.parse(raw, domain)`, keep `session.set(name, value)` for each cookie (compat), and store the parsed name/value list under a collision-free private session attribute; then `.foreach("#{<thatKey>}", "cookie") { exec(addCookie(Cookie("#{cookie.name}", "#{cookie.value}").withDomain(domain))) }`. Keep the method signature `restoreCookies(setCookieField: String, domain: String): ChainBuilder` unchanged; import `io.gatling.http.Predef.{addCookie, Cookie}`. No-op path: empty/missing source attribute → empty list → `foreach` iterates nothing.

### Verification for User Story 2

- [x] T009 [US2] Run `sbt "testOnly org.galaxio.gatling.storage.SessionStorageSpec"` → green (FR-005, session.set retained, no-op).
- [x] T010 [US2] From `examples/scala-sbt-example` (after `sbt publishLocal` of the library or `PICATINNY_VERSION` set): run `sbt "Gatling/testOnly org.galaxio.performance.picatinny.Debug"` → green; confirm all 8 steps return their expected status — the 5 role-gated protected calls (steps 2,3,5,6,8) plus the 3 login 200s (steps 1,4,7) (FR-004 auto-send + FR-010 overwrite-revocation).

**Checkpoint**: US2 works independently — cookies auto-send and role switching revokes the prior cookie.

---

## Phase 5: User Story 3 - Malformed `Max-Age` is visible, not silent (Priority: P3)

**Goal**: `CookieParser` logs a WARN naming the offending value when `Max-Age` is present but non-numeric, while still returning `maxAge = None`; valid/absent `Max-Age` stays silent (#111, FR-006/007).

**Independent Test**: `sbt "testOnly org.galaxio.gatling.storage.CookieParserSpec"` — value + WARN assertions.

### Tests for User Story 3 (write first, must FAIL)

- [x] T011 [P] [US3] Add failing cases to `src/test/scala/org/galaxio/gatling/storage/CookieParserSpec.scala` using the repo's Logback `ListAppender` `captureWarns` idiom (from `AssertionsBuilderSpec.scala:46-59`, logger name `org.galaxio.gatling.storage`). Rule under test: **`max-age` present AND not parseable as `Long` → `maxAge = None` + exactly one WARN naming the value; absent → `None`, silent; parseable (incl. negative) → `Some`, silent.** Cases: (a) `Max-Age=abc` → `None` + 1 WARN containing `abc`; (b) `Max-Age=3600` → `Some(3600L)`, 0 WARNs; (c) absent `Max-Age` → `None`, 0 WARNs; (d) empty `Max-Age=` → `None` + 1 WARN (present but unparseable); (e) negative `Max-Age=-1` → `Some(-1L)`, 0 WARNs (valid `Long`, no throw); (f) overflow `Max-Age=99999999999999999999` → `None` + 1 WARN (unparseable, no throw).

### Implementation for User Story 3

- [x] T012 [US3] In `src/main/scala/org/galaxio/gatling/storage/CookieParser.scala`: `import com.typesafe.scalalogging.StrictLogging`, change `object CookieParser {` to `object CookieParser extends StrictLogging {`, and split the `max-age` handling (currently `attrs.get("max-age").flatMap(_.toLongOption)`) into: key absent → `None`, no log; key present and `toLongOption` succeeds (incl. negative) → `Some`, no log; key present and `toLongOption` is `None` (non-numeric, empty, or overflow) → `None` + `logger.warn(...)` naming the offending raw value. Never throw.
- [x] T013 [US3] Run `sbt "testOnly org.galaxio.gatling.storage.CookieParserSpec"` → green; confirm the existing attribute cases (`c.maxAge shouldBe Some(3600L)`) still pass.

**Checkpoint**: US3 works independently — bad Max-Age is observable, return shape unchanged.

---

## Phase 6: User Story 4 - `randomValue` range bounds documented correctly (Priority: P3)

**Goal**: Fix the Scaladoc for `randomValue(min, max)` and `randomValue(max)` to state the maximum is **exclusive** (matches the implementation). Doc-only; no behavior change (#207, FR-008).

**Independent Test**: existing `sbt "testOnly org.galaxio.gatling.utils.RandomDataGeneratorsTest"` stays green (behavior guard); doc reads "exclusive".

### Implementation for User Story 4

- [x] T014 [US4] In `src/main/scala/org/galaxio/gatling/utils/RandomDataGenerators.scala`, edit the Scaladoc only: change the `randomValue(min, max)` `@param max` / `@return` wording from "inclusive" to "exclusive" (returned value `>= min` and `< max`), and the single-bound `randomValue(max)` wording from "less than or equal to max" to "less than max (exclusive)". Do not touch any implementation.
- [x] T015 [US4] Run `sbt "testOnly org.galaxio.gatling.utils.RandomDataGeneratorsTest"` → green (confirms no behavior change; existing property checks already assert `[min, max)`).

**Checkpoint**: US4 complete — docs match runtime, zero behavior change.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Backward-compat guard, formatting, coverage, and full validation across all stories.

- [x] T016 [P] Release notes are generated by git-cliff from conventional-commit messages (per AGENTS.md) — no manual CHANGELOG file to edit. Ensure each commit is semantic (`fix(config):`, `fix(storage):`, `feat(storage):` for the additive cookie-jar behavior, `docs(utils):` for randomValue) so the generated notes are correct. Update any human-facing usage docs only if `restoreCookies` is documented outside Scaladoc.
- [x] T017 Confirm backward compatibility (FR-009): public signatures unchanged — `IntensityConverter.getIntensityFromString: Double`, `SessionStorage.restoreCookies(String, String): ChainBuilder`, `ParsedCookie` shape, `SimulationConfig.intensity: Double`; Java facade delegations (`javaapi/utils/IntensityConverter`, `javaapi/SimulationConfig`) still compile and delegate (no facade edits).
- [x] T018 Run `sbt scalafmtAll scalafmtSbt` then the full CI gate `sbt scalafmtCheckAll scalafmtSbtCheck compile test` → green.
- [x] T019 Run `sbt clean coverage test coverageReport` → statement ≥ 65%, branch ≥ 60% (no regression).
- [x] T020 Run the full [quickstart.md](quickstart.md) validation (all FRs incl. the `examples/scala-sbt-example` `Gatling/test` Debug e2e) end to end. Note (SC-005): no `it`/`IntegrationTest` sources (Redis/Vault/JDBC) are modified, so the integration suite is out of risk-scope; run `sbt "IntegrationTest / test"` only as an optional belt-and-suspenders pass.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: empty (no blocking work).
- **User Stories (Phase 3–6)**: each depends only on Phase 1; all four are mutually independent (separate files) and may run in parallel.
- **Polish (Phase 7)**: after the user stories targeted for the release are done.

### User Story Dependencies

- **US1 (P1)**, **US2 (P2)**, **US3 (P3)**, **US4 (P3)**: independent. US2's e2e uses `CookieParser.parse` (existing behavior) but does not depend on US3's WARN change. (US2's Debug.scala stubs/scenario and US3's `CookieParserSpec` touch different files.)

### Within Each User Story

- Tests written and FAILING before implementation (TDD, Constitution III).
- US1: T002 → T003 → T004. US2: (T005 [P], T006 → T007) → T008 → (T009, T010). US3: T011 → T012 → T013. US4: T014 → T015.

### Parallel Opportunities

- After T001, all four stories can proceed in parallel (different files).
- US2: the component test T005 is parallel with the Debug e2e tasks T006/T007 (different files); T011 (US3), T002 (US1), T014 (US4) are all parallel with each other and with US2.

---

## Parallel Example: kick off all stories after setup

```bash
# After T001 baseline is green, these failing-test tasks touch different files and can start together:
Task T002 [US1]: decimal/malformed cases in IntensityConverterTest.scala
Task T005 [US2]: restoreCookies component test in SessionStorageSpec.scala
Task T011 [US3]: Max-Age WARN cases in CookieParserSpec.scala
Task T014 [US4]: randomValue scaladoc fix in RandomDataGenerators.scala
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 (T001 baseline green).
2. Phase 3 (US1): T002 (red) → T003 (fix) → T004 (green). Ship #93 alone if desired.

### Incremental Delivery

US1 (#93) → US3 (#111) → US4 (randomValue doc) → US2 (#207 cookie restore/switch, the largest). Each is an independent, green, semantic commit. All land on `main` via PR, then v1.22.0 is cut as `release/1.22.0` (MINOR — additive cookie behavior).

---

## Notes

- [P] = different files, no dependencies.
- No new dependencies; Gatling stays `Provided`.
- US2 cookie e2e lives in the existing `Debug.scala` (per 2026-06-23 decision) — no separate simulation file; the `SessionStorageSpec` component unit test is kept.
- US2 uses the supported public `addCookie` DSL only — no reflection into Gatling internals; per-cookie `path`/`max-age`/`secure`/`httpOnly` intentionally not propagated.
- e2e assertions are Gatling `check` on responses only — never `WireMock.verify`, never re-decode the request (TESTING.md layer 4).
- Commit after each task or logical group; keep the build green at every commit.
