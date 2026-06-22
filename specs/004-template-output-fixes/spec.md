# Feature Specification: Template Output Fixes (v1.19.0)

**Feature Branch**: `004-template-output-fixes`

**Created**: 2026-06-22

**Status**: Approved (planning chain complete; maintainer-authorized 2026-06-22 — MINOR, v1.19.0)

**Input**: Milestone 3 — v1.19.0: issues #73, #74, #203 (arr-truncation, XML-escape, RawVal-escape, EL-regex, missing-dir fast-fail)

## User Scenarios & Testing *(mandatory)*

### User Story 1 — EL Names With Dots and Hyphens Are Interpolated (Priority: P1)

A load-test author uses Gatling session variables named `user.id` or `tenant-name` inside a template expression (e.g., `#{user.id}`). Today, the template engine silently treats these as literal strings rather than interpolating them, producing wrong request bodies in production load tests with no warning.

**Why this priority**: Silent data corruption in generated request payloads is the highest-severity class of bug for a request-generation library. Any load test relying on dotted or hyphenated EL names produces incorrect traffic without feedback.

**Independent Test**: Can be fully tested by asserting that `templateWithEL("#{user.id}")` produces an interpolated EL reference rather than the literal string `#{user.id}`, verified with a unit test against the fixed regex.

**Acceptance Scenarios**:

1. **Given** a template string `"#{user.id}"`, **When** the template engine processes it, **Then** the output treats `user.id` as an EL variable name (not a literal)
2. **Given** a template string `"#{tenant-name}"`, **When** the template engine processes it, **Then** the output treats `tenant-name` as an EL variable name (not a literal)
3. **Given** a template string `"#{plain}"`, **When** the template engine processes it, **Then** behavior is unchanged (regression guard)
4. **Given** a template string `"literal-text"` (no EL), **When** the template engine processes it, **Then** output is the literal string unchanged

---

### User Story 2 — Missing Templates Directory Is Caught at Startup (Priority: P1)

A load-test author misconfigures the templates path (e.g., a typo in the directory name). Today, the library silently produces an empty template registry and fails later with `NoSuchElementException` deep in the scenario, far from the root cause. The author wastes time diagnosing a late crash.

**Why this priority**: Fail-fast diagnostics are critical for a published library. A misleading silent failure costs users significant debugging time on every misconfiguration.

**Independent Test**: Can be fully tested by invoking template loading with a path that does not exist and asserting that a clear, specific error is raised immediately rather than returning an empty collection.

**Acceptance Scenarios**:

1. **Given** a templates directory path that does not exist on the classpath, **When** the template registry is initialized, **Then** an explicit error is raised immediately with a message identifying the missing path
2. **Given** a templates directory path that exists and is correctly populated, **When** the template registry is initialized, **Then** templates load successfully (regression guard)
3. **Given** the `templates` resource exists on the classpath but contains no files, **When** the template registry is initialized, **Then** it yields an empty registry (NOT an error) — this is distinct from the missing-directory case. (Resolved: a dedicated opt-in for an intentionally-empty registry is deferred; missing = error. See research D5.)

---

### User Story 3 — `arr()` Correctly Handles Strings Containing EL Expressions (Priority: P2)

A load-test author writes `arr("#{name}")` expecting a JSON array element that references the session variable `name`. Today, `arr("hello #{name}!")` produces `["#{name}"]` — the surrounding literal text (`hello ` and `!`) is silently dropped. The output is wrong and the drop is invisible.

**Why this priority**: Incorrect array output silently corrupts generated JSON payloads. Lower priority than P1 because the pattern (mixed literal + EL in a single `arr()` argument) is less common, but still a correctness regression when hit.

**Independent Test**: Can be fully tested by asserting `arr("hello #{name}!")` renders with the full string treated as an EL expression (not just the extracted EL fragment), vs. `arr("literal")` renders as a plain string value.

**Acceptance Scenarios**:

1. **Given** `arr("#{name}")` (pure EL string), **When** rendered, **Then** produces a JSON array with an EL reference element
2. **Given** `arr("hello #{name}!")` (mixed literal + EL), **When** rendered, **Then** the entire string is treated as an EL expression (no silent truncation of surrounding text)
3. **Given** `arr("plainValue")` (no EL), **When** rendered, **Then** produces a JSON array with literal string element (regression guard)

---

### User Story 4 — Generated XML and JSON Output Is Safe From Markup Injection (Priority: P2)

A load-test author uses `RawValGen` with string values, or generates XML with dynamic element names. Today, string values emitted as raw and XML element names are not escaped, allowing structural characters (e.g., `<`, `>`, `"`) to corrupt the generated XML or JSON body.

**Why this priority**: Correctness and a low-severity security concern. Invalid XML/JSON bodies fail target API validation in unexpected ways; load tests exercising adversarial inputs may inadvertently inject markup rather than testing it deliberately.

**Independent Test**: Can be fully tested by asserting that a `RawValGen` value containing `<tag>` is escaped in the output, and that an XML element name containing `<` is escaped, producing well-formed output in both cases.

**Acceptance Scenarios**:

1. **Given** an XML template with a `RawValGen` value containing `<foo>`, **When** rendered, **Then** the output XML is well-formed (special characters escaped)
2. **Given** a JSON template with a `RawValGen` value containing `"quoted"`, **When** rendered, **Then** the output JSON is valid (quotes escaped)
3. **Given** an XML element name that is a plain identifier, **When** rendered, **Then** output is unchanged (regression guard)
4. **Given** a `RawValGen` value that is a genuine numeric or boolean scalar (e.g., `42`, `true`), **When** rendered, **Then** the value is emitted as-is without escaping

---

### Edge Cases

- What happens when a template string is `""` (empty)? Must not crash; must produce empty output.
- How does `arr()` handle a string with multiple EL expressions (`"#{a} #{b}"`)? Behavior must be defined and consistent with the "contains EL → treat as EL" rule.
- What happens when the templates directory exists but is empty? Must not be conflated with the missing-directory case.
- What happens when `RawValGen` wraps `null` or an empty string? Must produce well-formed output.
- How does the EL regex change interact with variable names that are purely numeric (e.g., `#{0}`)? Behavior must remain consistent.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The EL interpolation pattern MUST recognize variable names containing dots (`.`) and hyphens (`-`) in addition to word characters (`\w`)
- **FR-002**: `arr()` MUST treat any string that contains `#{...}` as a full EL expression; surrounding literal text MUST NOT be silently discarded
- **FR-003**: XML element names MUST be escaped when emitted into generated XML output to prevent structural markup injection
- **FR-004**: `RawValGen` string values MUST be escaped when emitted into JSON and XML output; only genuine scalar types (numbers, booleans) MUST be emitted raw
- **FR-005**: Templates directory initialization MUST fail fast with a clear diagnostic error when the requested directory is absent from the classpath
- **FR-006**: All behavior changes introduced by this milestone MUST be documented in CHANGELOG before release, with an explicit ⚠️ note that generated request bytes may differ from prior versions

### Key Entities

- **EL Expression**: A Gatling expression-language reference of the form `#{varName}`, where `varName` follows an extended character set (word chars, dots, hyphens)
- **Template String**: A string potentially containing zero or more EL expressions mixed with literal text, used as input to DSL builders (`arr`, XML/JSON constructors, `RawValGen`)
- **Template Registry**: The runtime map from template name to template content, loaded from the classpath at simulation startup
- **RawValGen**: A DSL construct that injects a pre-computed value directly into a generated payload without further transformation

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every output-correctness regression (EL truncation, XML injection, raw-value escaping) produces correct generated bytes in all covered scenarios; zero silent data-corruption cases remain after the fix
- **SC-002**: A simulation with a misconfigured templates path fails at startup with a human-readable error message; the failure occurs before any HTTP request is sent
- **SC-003**: All existing tests continue to pass; statement coverage stays at or above 65% and branch coverage at or above 60%
- **SC-004**: Load tests using session variables named with dots or hyphens produce correctly interpolated request bodies without any library-side configuration change
- **SC-005**: The v1.19.0 release notes (git-cliff generated; no `CHANGELOG.md` in this repo) explicitly warn that generated request bytes may differ from v1.18.x

## Assumptions

- The extended EL regex (`[\w.\-]+`) aligns with Gatling's own EL grammar; names containing only those characters are valid Gatling session variable names
- For `arr()`: the new rule is "if the string contains `#{...}`, treat the entire string as an EL expression" — mixed literal-plus-EL strings in `arr()` were previously unintentional and have no documented semantic
- For the missing-directory case: an explicit opt-in (e.g., empty-templates sentinel or flag) will be provided if users have legitimate use-cases for an intentionally empty template registry; absent such opt-in, missing = error
- `RawValGen` escaping applies only to string-typed raw values; numeric and boolean scalars are emitted as-is (they cannot inject markup)
- These fixes are backward-incompatible at the byte level (generated payloads change) but not at the API/signature level; this warrants a MINOR version bump (v1.19.0) per the constitution's compatibility rules
- Changes are confined to `templates/Syntax.scala` and `templates/Templates.scala`; no new dependencies are required
- The CHANGELOG update is part of the same PR scope as the code fixes
