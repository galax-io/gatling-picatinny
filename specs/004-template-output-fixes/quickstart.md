# Quickstart / Validation Guide: Template Output Fixes (v1.19.0)

A run guide to prove the five fixes end-to-end. Implementation detail (the exact code edits and
full test bodies) belongs to `tasks.md` and the implementation phase — this file is the
validation script. Exact expected bytes are in [contracts/templates-dsl.md](contracts/templates-dsl.md).

## Prerequisites

- sbt; JDK 17 (CI runs Temurin 21)
- No new dependencies; no Docker required (all layers here are non-container)

## Touched files

- `src/main/scala/org/galaxio/gatling/templates/Syntax.scala` — FR-001, FR-002, FR-003, FR-004
- `src/main/scala/org/galaxio/gatling/templates/Templates.scala` — FR-005
- `src/test/scala/org/galaxio/gatling/templates/SyntaxSpec.scala` — update + add cases
- `src/test/scala/org/galaxio/gatling/templates/TemplatesSpec.scala` — add fast-fail + empty-dir cases
- `src/test/java/org/galaxio/gatling/javaapi/TemplateSyntaxDelegationTest.java` — facade parity (new if absent)

## Test-first order (red → green)

1. **FR-002 / FR-001 — `arr` truncation + EL detection** (`SyntaxSpec`)
   - Add failing assertions: `makeArrJson(arr("hello #{name}!").vs) shouldBe """["hello #{name}!"]"""`,
     and dotted/hyphenated EL cases.
   - **Update** the existing `arr() auto-detects EL string interpolation` test
     (`SyntaxSpec.scala:233-238`) to assert on **rendered bytes** rather than the internal
     `InterpolateStrVal` node — the classification of an EL-bearing string changes (see research D2).
2. **FR-004 — `RawValGen` escaping** (`SyntaxSpec`)
   - Add: stringy `RawValGen` is quoted+escaped in JSON and escaped in XML.
   - Keep all existing scalar tests (`42`, `true`, `3.14`, `9999999999L`) green — they lock the raw-scalar branch.
3. **FR-003 — XML element-name escaping** (`SyntaxSpec`)
   - Add: a `Field` with a name containing `<`/`&` is escaped in `makeXml`; plain-name tests stay byte-identical.
4. **FR-005 — missing templates dir fast-fail** (`TemplatesSpec`)
   - Add: with the thread context classloader pointed at a location lacking a `templates` resource,
     dereferencing the registry raises an explicit error naming the missing directory.
   - Add boundary: an existing-but-empty `templates` resource → empty registry, no error.
   - Keep the existing happy-path discovery tests green (`test_json`, `test_xml`).
5. **Facade delegation** (`TemplateSyntaxDelegationTest`, JUnit 5)
   - Assert `TemplateSyntax.makeXml(...)` / `makeJson(...)` / `fieldArr(...)` output equals the
     Scala core output for an escaped-value case.

## Run / verify

```bash
sbt scalafmtAll scalafmtSbt                         # format first (workflow rule)
sbt "testOnly org.galaxio.gatling.templates.SyntaxSpec"
sbt "testOnly org.galaxio.gatling.templates.TemplatesSpec"
sbt "testOnly org.galaxio.gatling.javaapi.*"        # facade-delegation JUnit test
sbt scalafmtCheckAll scalafmtSbtCheck compile test  # full CI gate (unit)
```

## Expected outcomes (acceptance → bytes)

| Scenario (spec) | Command surface | Expected |
|-----------------|-----------------|----------|
| US1 dotted/hyphenated EL (FR-001) | `arr("#{user.id}")`, `arr("#{tenant-name}")` | rendered `["#{user.id}"]`, `["#{tenant-name}"]` (full, classified EL) |
| US3 truncation (FR-002) | `arr("hello #{name}!")` | `["hello #{name}!"]` — no dropped text |
| US4 XML name (FR-003) | `makeXml(Field("a<b", …))` | element name escaped; plain names unchanged |
| US4 RawValGen escaping (FR-004) | stringy `RawValGen` in JSON/XML | quoted+escaped / escaped; scalars stay raw |
| US2 missing dir (FR-005) | deref `templates` with no `templates` resource | explicit error naming the directory, before any request |
| Facade parity (Principle I) | `TemplateSyntax.*` | byte-identical to Scala core |

## Release verification (FR-006 — not an automated test)

- Confirm the work ships as a conventional commit flagging the behavior change
  (`fix!:` + `BREAKING CHANGE:`/⚠️ body) so git-cliff renders the warning in the GitHub Release.
- **Target version: `v1.19.0`, MINOR** (maintainer-confirmed 2026-06-22). v1.18.0 is already
  released (`release/1.18.0` exists), so v1.18.0 is taken. The GitHub milestone *titled* "v1.18.0"
  and issue #203's body "v1.13.0" are stale tracker labels — actual target is v1.19.0. Cut a new
  `release/1.19.0` branch from `main` to host the `v1.19.0` tag.
- There is **no `CHANGELOG.md`** — do not create one; release notes are git-cliff-generated.
