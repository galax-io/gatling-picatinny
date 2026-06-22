---
description: "Task list for Template Output Fixes (v1.19.0)"
---

# Tasks: Template Output Fixes (v1.19.0)

**Input**: Design documents from `/specs/004-template-output-fixes/`

**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓, quickstart.md ✓

**Tests**: Test tasks ARE included — Constitution §III mandates test-first (red → green) for this repo.

**Organization**: Grouped by user story (spec.md). Priorities: US1 (P1), US2 (P1), US3 (P2), US4 (P2).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 / US4

## ⚠️ File-coupling constraint (drives [P] markers)

- `Syntax.scala` + `SyntaxSpec.scala` are touched by **US1, US3, US4** → those edits are **serialized** (no `[P]` among them, even though the stories are conceptually independent).
- `Templates.scala` + `TemplatesSpec.scala` (US2) and the new `TemplateSyntaxDelegationTest.java` are **separate files** → genuinely parallel with the Syntax work (marked `[P]`).

## Path conventions (single Scala library project, per plan.md)

- Core: `src/main/scala/org/galaxio/gatling/templates/`
- Facade: `src/main/java/org/galaxio/gatling/javaapi/`
- Scala tests: `src/test/scala/org/galaxio/gatling/templates/`
- Java tests: `src/test/java/org/galaxio/gatling/javaapi/`
- Test resources: `src/test/resources/templates/`

---

## Phase 1: Setup (Shared)

**Purpose**: Lock a known-green baseline before any change (so every later red→green is meaningful).

- [x] T001 Confirm green baseline: run `sbt scalafmtCheckAll scalafmtSbtCheck compile test` and record it passing before edits (no files changed in this task)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None required. There is no shared scaffolding to build — all fixes are localized edits to two existing files. The only cross-story coupling is the shared `Syntax.scala`/`SyntaxSpec.scala` file (handled by serialization, see Dependencies).

*(No tasks in this phase.)*

**Checkpoint**: Baseline green → user-story work can begin.

---

## Phase 3: User Story 1 — EL names with dots/hyphens are interpolated (Priority: P1) 🎯 MVP

**Goal (FR-001, #74)**: `arr()` correctly recognizes dotted/hyphenated EL names (`#{user.id}`, `#{tenant-name}`) as EL expressions.

**Independent Test**: `makeArrJson(arr("#{user.id}").vs)` renders `["#{user.id}"]` and `arr("#{tenant-name}")` renders `["#{tenant-name}"]`; `arr("plain")` renders `["plain"]` (not classified as EL).

### Tests for User Story 1 (write first, must FAIL)

- [x] T002 [US1] Add failing cases to `src/test/scala/org/galaxio/gatling/templates/SyntaxSpec.scala`: `arr("#{user.id}")` and `arr("#{tenant-name}")` render the full EL string (`["#{user.id}"]`, `["#{tenant-name}"]`); negative `arr("plain")` renders `["plain"]`

### Implementation for User Story 1

- [x] T003 [US1] Widen `interpolateRegExpr` from `#\{(\w+)\}` to `#\{([\w.\-]+)\}` (hyphen last/escaped to avoid a range) at `src/main/scala/org/galaxio/gatling/templates/Syntax.scala:70` — keep it private and used only at the `arr` call site (research D1)

**Checkpoint**: US1 green. (Existing `arr() auto-detects…` test at `SyntaxSpec.scala:233-238` still passes here — it only changes in US3.)

---

## Phase 4: User Story 2 — Missing templates directory caught at startup (Priority: P1)

**Goal (FR-005, #73)**: A missing `templates` classpath resource fails fast with a clear, specific error instead of a silent empty map that later throws `NoSuchElementException` deep in the scenario. Independent of the Syntax work — fully parallelizable.

**Independent Test**: With the thread context classloader pointed at a location lacking a `templates` resource, first dereference of the registry raises an explicit error naming the missing `templates` directory; a present-with-files resource still loads; an existing-but-empty resource yields an empty map (not an error).

### Tests for User Story 2 (write first, must FAIL)

- [x] T004 [P] [US2] Add cases to `src/test/scala/org/galaxio/gatling/templates/TemplatesSpec.scala`: (a) missing `templates` resource → dereferencing the trait's registry raises an explicit error whose message names the missing directory; (b) boundary — an existing-but-empty `templates` location → empty registry, NO error; keep the existing happy-path discovery tests green (`test_json`, `test_xml`). (Drive the missing/empty cases via the thread context classloader, restoring it in a `finally`.)

### Implementation for User Story 2

- [x] T005 [P] [US2] In `src/main/scala/org/galaxio/gatling/templates/Templates.scala:33-49`, replace the `fold(Map.empty)` null-resource branch with an explicit `IllegalStateException` (or equivalent) whose message states the expected `templates` directory was not found on the classpath; leave the resource-present branch and the empty-directory→empty-map behavior unchanged; keep the registry `lazy` so the throw occurs only on first dereference (research D5)
- [x] T006 [US2] Update the `Templates` ScalaDoc (`Templates.scala:25-27`) so it no longer states the empty-map behavior — document the fast-fail-on-missing contract instead

**Checkpoint**: US2 green and independently demonstrable (a misconfigured path fails at startup, before any HTTP request).

---

## Phase 5: User Story 3 — `arr()` keeps literal text around EL (Priority: P2)

**Goal (FR-002, #203)**: `arr("hello #{name}!")` renders `["hello #{name}!"]` — surrounding literal text is no longer dropped. Same `arr` call site as US1, so serialized after US1.

**Independent Test**: `makeArrJson(arr("hello #{name}!").vs)` renders `["hello #{name}!"]`; `arr("#{a} and #{b}")` renders the full string intact; pure `arr("#{name}")` still renders `["#{name}"]`.

### Tests for User Story 3 (write first, must FAIL)

- [x] T007 [US3] In `src/test/scala/org/galaxio/gatling/templates/SyntaxSpec.scala`: add `arr("hello #{name}!")` → `["hello #{name}!"]`; boundary multi-EL `arr("#{a} #{b}")` → `["#{a} #{b}"]` (full string); regression `arr("#{name}")` → `["#{name}"]`; numeric-EL `arr("#{0}")` → `["#{0}"]` (spec edge `#{0}`); empty-string edge `arr("")` → `[""]`; escaping case `arr("say \"#{x}\"")` → `["say \"#{x}\""]`. **Update** the existing `arr() auto-detects EL string interpolation` test (`SyntaxSpec.scala:233-238`) to assert on rendered bytes rather than the internal `InterpolateStrVal` node (research D2)

### Implementation for User Story 3

- [x] T008 [US3] In `src/main/scala/org/galaxio/gatling/templates/Syntax.scala:82-83`, change the `arr` `String` branch so an EL-bearing string (detected by the widened regex) renders the **entire original string** escaped, instead of extracting `group(1)`; pure-literal strings still render as `RawValString(s)` (research D2). Depends on T003 (US1 regex)

**Checkpoint**: US3 green; US1 cases still green (full-string render of `"#{user.id}"` is byte-identical).

---

## Phase 6: User Story 4 — Generated XML/JSON safe from markup injection (Priority: P2)

**Goal (FR-003 + FR-004, #203)**: XML element names are escaped (parity with the JSON path), and `RawValGen` string values are escaped (only genuine scalars stay raw). Same `Syntax.scala`/`SyntaxSpec.scala` file as US1/US3 → serialized.

**Independent Test**: `makeXml(Field("a<b", RawValString("v")))` escapes the element name; a stringy `RawValGen` (`"<x>"`, `"a\"b"`) is escaped in XML and quoted+escaped in JSON; scalar `RawValGen(42)`/`true`/`3.14` stay raw.

### Tests for User Story 4 (write first, must FAIL)

- [x] T009 [US4] In `src/test/scala/org/galaxio/gatling/templates/SyntaxSpec.scala`: FR-003 — `makeXml` with a field name containing `<`/`&` escapes the name at open and close tags; regression `makeXml("name" - "foo")` stays `<name>foo</name>`
- [x] T010 [US4] In `src/test/scala/org/galaxio/gatling/templates/SyntaxSpec.scala`: FR-004 — stringy `RawValGen("a\"b")` renders quoted+escaped in JSON (`["a\"b"]`) and `RawValGen("<x>")` escaped in XML (`<item>&lt;x&gt;</item>`); empty-string edge `RawValGen("")` → `[""]` (JSON) / `<item></item>` (XML); null edge `RawValGen(null)` → well-formed output (no NPE — see T012); regression — `RawValGen(42)`→`42`, `RawValGen(true)`→`true`, `RawValGen(3.14)`→`3.14` stay raw

### Implementation for User Story 4

- [x] T011 [US4] In `src/main/scala/org/galaxio/gatling/templates/Syntax.scala` `appendXmlField` (lines ~266-281), apply the existing `escapeXml` to the element `name` at both the open `<name>` and close `</name>` tags (research D3); leave the fixed `<item>` wrappers untouched
- [x] T012 [US4] In `src/main/scala/org/galaxio/gatling/templates/Syntax.scala`, change every `RawValGen` emission point — JSON (`:196` `appendJsonField`, `:233` `appendJsonValue`) and XML (`:270` `appendXmlField`, `:286` `appendXmlArray`) — to branch on the wrapped value's runtime type: scalars (`Int, Long, Short, Byte, Double, Float, BigInt, BigDecimal, Boolean`) emit raw; **`null` → `null` in JSON / empty body in XML** (guard the `.toString`/escape path against NPE); everything else is escaped as stringy (JSON: quoted + `escapeJson`; XML: `escapeXml`) (research D4). Depends on T011 (same file)

**Checkpoint**: US4 green; all prior scalar assertions still green.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T013 [P] Add facade-delegation test `src/test/java/org/galaxio/gatling/javaapi/TemplateSyntaxDelegationTest.java` (JUnit 5): assert `TemplateSyntax.makeXml(...)`, `makeJson(...)`, `fieldArr(...)` output is byte-identical to the Scala core (`Syntax`) for an escaped-value case — confirms Principle I after the rendering change (no facade-side logic). Depends on T008, T011, T012
- [x] T014 Run `sbt scalafmtAll scalafmtSbt` to format all touched Scala/sbt files
- [x] T015 Run the full unit gate `sbt scalafmtCheckAll scalafmtSbtCheck compile test` and confirm coverage stays ≥ 65% statement / 60% branch (`coverageFailOnMinimum`)
- [x] T016 Validate against [quickstart.md](quickstart.md): run the per-story `testOnly` commands and confirm the acceptance-bytes table
- [x] T017 Prepare the conventional commit flagging the behavior change (`fix!:` + `BREAKING CHANGE:`/⚠️ body) so git-cliff renders the byte-change warning in the v1.19.0 GitHub Release (FR-006; no `CHANGELOG.md` is created — research D7)

---

## Dependencies & Execution Order

### Phase order

- **Setup (P1)** → no deps; do first.
- **Foundational (P2)** → empty.
- **User stories (P3–P6)** → after Setup.
- **Polish (P7)** → after the stories it depends on.

### File-coupling (the real constraint)

- **US2 (Templates.scala / TemplatesSpec.scala)** is independent of all Syntax work → can run fully in **parallel** with US1/US3/US4.
- **US1 → US3 → US4** all edit `Syntax.scala` + `SyntaxSpec.scala` → **serialize in this order** (priority order). Specifically:
  - T008 (US3 arr render) depends on T003 (US1 regex) — same `arr` branch.
  - T012 (US4 RawValGen) depends on T011 (US4 XML name) — same file.
  - The existing-test update lives in T007 (US3), because US1's regex-only change keeps it green; only US3 changes the `arr` classification.

### Cross-cutting

- T013 (facade delegation) depends on the core rendering changes (T008, T011, T012).
- T014–T017 (format, gate, quickstart, commit) run last, after all stories.

### Within each story

- Test task(s) FIRST and failing, then implementation (Constitution §III, red → green).

---

## Parallel Opportunities

```bash
# US2 is the only story that parallelizes with the Syntax work (different files):
# Track A (one dev):  T002 → T003   (US1, Syntax)  then  T007 → T008 (US3)  then  T009/T010 → T011 → T012 (US4)
# Track B (other dev): T004 → T005 → T006            (US2, Templates)  — fully parallel to Track A

# Tasks explicitly marked [P] (independent files, no incomplete-task dep within their phase):
#   T004 [US2], T005 [US2]   — Templates.* (parallel to Syntax tests/impl)
#   T013                     — new TemplateSyntaxDelegationTest.java (after core changes)
```

---

## Implementation Strategy

### MVP (the two P1 stories)

1. T001 baseline green.
2. **US1** (T002–T003) → dotted/hyphenated EL detection. Smallest valuable slice.
3. **US2** (T004–T006) → fail-fast on missing templates dir (parallel-capable).
4. **STOP & VALIDATE**: both P1 stories independently testable. This is a shippable correctness/diagnostics MVP.

### Incremental delivery (P2 follow-on)

5. **US3** (T007–T008) → arr truncation fix (builds on US1's regex).
6. **US4** (T009–T012) → XML name + RawValGen escaping.
7. **Polish** (T013–T017) → facade parity, format, full gate + coverage, quickstart, breaking-flagged commit for the v1.19.0 release.

---

## Notes

- `[P]` = different files, no incomplete-task dependency. Most Syntax tasks are intentionally **not** `[P]` because they share one file.
- Every story carries ≥1 negative/boundary assertion (Constitution §III); exact expected bytes are in [contracts/templates-dsl.md](contracts/templates-dsl.md).
- No new dependencies; no public method-signature changes. All five fixes change generated bytes → ship as MINOR **v1.19.0** (maintainer-authorized 2026-06-22; v1.18.0 already released).
- Commit after each green story (semantic commits, build stays green).

---

## Implementation Notes (actual — deviations from the plan above)

Recorded for honesty; all are byte-equivalent to the planned behavior and the full suite is green (685 tests).

1. **EL detection is anchored (T003/T008)** — instead of "EL-bearing string → `RawValString`" (research D2 collapse), the `arr` `String` branch uses `case interpolateRegExpr(name) => InterpolateStrVal(name)` (whole-string match) else `RawValString(s)`. A string that is *entirely* `#{...}` stays `InterpolateStrVal` (AST + bytes unchanged); any literal-bearing string is kept whole as `RawValString`. Output bytes are identical to the collapse; this is strictly more backward-compatible.
2. **Existing `arr() auto-detects…` test was NOT modified (T007)** — because anchored detection keeps `arr("#{dynamic}")` as `InterpolateStrVal("dynamic")`, `SyntaxSpec.scala:233-238` stays green as-is. The planned "update to assert rendered bytes" was unnecessary.
3. **Present-resource registry regression dropped from the trait-based block (T004)** — dereferencing the real `templates` builds `ElFileBody`, which requires the Gatling runtime (`"Simulations can't be instantiated directly"`). The present case is already covered by the existing `loadTemplateNames` discovery tests (no `ElFileBody`); the trait's present path is exercised in the examples/ e2e layer. The unit tests only hit paths that never construct a body (missing → throw; empty dir → empty map).
4. **Facade delegation tests extended, not a new file (T013)** — added to the existing `JavaTemplateSyntaxTest.java` (FR-002 truncation, FR-001 dotted name, FR-003 XML name escaping via the facade) rather than creating `TemplateSyntaxDelegationTest.java`.

**Verification**: `sbt scalafmtCheckAll scalafmtSbtCheck compile test` → 685 passed, 0 failed. Targeted: `testOnly org.galaxio.gatling.templates.* org.galaxio.gatling.javaapi.JavaTemplateSyntaxTest` → 102 passed.
