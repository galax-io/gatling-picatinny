# Feature Specification: Secret Masking & Leak Prevention

**Feature Branch**: `007-secret-masking-leak-prevention`

**Created**: 2026-06-24

**Status**: Draft

**Input**: GitHub Milestone 8 — v1.23.0: Secret-masking & leak prevention (issues #208, #87, #88)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Secrets Never Appear in Logs (Priority: P1)

A load test developer configures a simulation with sensitive credentials: Vault tokens, HMAC signing keys, bearer tokens, role IDs, and passwords embedded in connection URLs. When the simulation runs, none of those values appear in plaintext in any log output, console output, or exception message — regardless of log level.

**Why this priority**: Credential leaks in CI/CD logs are a direct security risk. This is the core purpose of the milestone. Every other story depends on a working central masking foundation.

**Independent Test**: Run a simulation configured with known secret values; capture all log/stdout output at the most verbose level; assert none of the secret values appear in plaintext. Delivers the core security guarantee on its own.

**Acceptance Scenarios**:

1. **Given** a config key whose name matches a known sensitive pattern (token, password, secret, key, authorization, bearer, passphrase), **When** the library logs or prints config state, **Then** the value is replaced with a redacted placeholder and the original value is absent from all output.
2. **Given** a config block containing a nested child key matching a sensitive pattern, **When** the entire parent block is logged as a string, **Then** only sensitive leaf values are redacted while non-sensitive sibling keys remain visible.
3. **Given** a cryptographic signing object created with an HMAC secret, **When** that object is converted to a string (in an exception, log, or interpolation), **Then** the secret material is not present in the resulting string.
4. **Given** a JVM started with sensitive `-Dkey=value` system arguments (e.g. `-DvaultToken=…`), **When** startup diagnostics print the input arguments, **Then** values for sensitive keys are redacted.
5. **Given** a key name that is NOT in the masking list, **When** the library logs config, **Then** the value appears unredacted (masking is not over-broad).

---

### User Story 2 - URL Credentials Stripped from Output (Priority: P2)

A CI/CD operator reviews build logs after a load test. A simulation base URL contains embedded credentials (`http://user:secret@host/path`). When picatinny prints the startup banner or logs any URL, the userinfo credential component is stripped, so passwords never appear in build logs.

**Why this priority**: Embedded URL credentials are a common, distinct leak path. URL userinfo is structurally different from key-value config and needs its own handling.

**Independent Test**: Configure a URL with userinfo; capture startup banner output; assert the password portion is absent while the host remains.

**Acceptance Scenarios**:

1. **Given** a base URL of the form `http://user:pass@host`, **When** the startup banner is printed, **Then** the output contains the host but not the password.
2. **Given** a base URL with no userinfo, **When** the startup banner is printed, **Then** the URL appears unchanged.
3. **Given** a malformed URL that cannot be parsed, **When** it would be printed, **Then** no exception is raised and no credential is exposed.

---

### User Story 3 - Diagnostic Output Routable via Standard Logging (Priority: P3)

A load test developer wants to suppress or redirect verbose startup diagnostic output in CI. Because diagnostic and banner output flows through the standard logging framework rather than directly to stdout, they can set a log level to quiet, redirect, or structure this output — without modifying picatinny source.

**Why this priority**: Controllability is a correctness property. Direct stdout writes cannot be silenced or captured by a logging sink; framework writes can. Also enables structured log collection in enterprise environments.

**Independent Test**: Capture stdout and the logging framework output separately. With a suppressing log config, stdout has zero diagnostic lines. With a permissive config, the same messages appear via the logging framework.

**Acceptance Scenarios**:

1. **Given** a logging configuration that sets the diagnostics category to OFF, **When** a simulation starts, **Then** no startup banner or diagnostic messages appear on stdout.
2. **Given** a logging configuration that sets the diagnostics category to INFO, **When** a simulation starts, **Then** banner and diagnostic messages are emitted through the logging framework (not raw stdout).

---

### User Story 4 - Recommended Logging Config Without Classpath Pollution (Priority: P4)

A developer adds picatinny to a new project and wants sensible, quiet logging. Picatinny does NOT force a logging configuration onto their classpath (which would silently override their own config). Instead, picatinny documents a recommended minimal logging configuration and ships a working reference in its runnable example overlay, so the developer copies it into their own project and stays in full control.

**Why this priority**: Good out-of-box guidance without the classpath-conflict anti-pattern. A library that ships an auto-discovered logging config on its main classpath collides with the consumer's own config (non-deterministic first-match wins) and steals their control — forbidden by the constitution's non-runnable/Provided posture and by SLF4J's own library guidance. Lowest priority because it is consumer guidance, not a security guarantee.

**Independent Test**: Inspect the published library artifact and assert it contains no `logback.xml`/`logback-test.xml` on the main classpath; confirm a consumer that supplies the documented snippet gets quiet, configurable output with no "occurs multiple times on the classpath" warning attributable to picatinny.

**Acceptance Scenarios**:

1. **Given** the published library artifact, **When** its contents are inspected, **Then** no auto-discovered logging configuration (`logback.xml` / `logback-test.xml`) is present on the main/compile classpath.
2. **Given** a consumer project that already has its own logging configuration, **When** picatinny is on the classpath, **Then** the consumer's configuration is the only one applied — picatinny contributes no competing config and no duplicate output or classpath-conflict warning.
3. **Given** a consumer following picatinny's documented logging snippet, **When** a simulation runs, **Then** output is quiet and configurable (root level no noisier than necessary, no uncontrolled DEBUG/TRACE flood) and the startup banner renders with correct alignment.

---

### Edge Cases

- A config key name partially matches a sensitive token (e.g., `roleIdPrefix`) — masking applies to clearly sensitive matches, not arbitrary substrings, and never leaks the underlying value.
- A sensitive value is `null` or empty — redaction handles it gracefully with no error.
- A URL is malformed and cannot be parsed for userinfo stripping — the print path fails safe (no exception, no credential exposure).
- A consumer already ships their own logging configuration — picatinny contributes none of its own on the main classpath, so the consumer's config is the sole authority (no "occurs multiple times on the classpath" conflict, no first-match ambiguity).
- The multi-line ASCII banner is routed through the logging framework — it MUST be emitted as a single log event (never line-by-line) so a per-line prefix cannot break box-drawing alignment; the recommended config renders the banner category without a per-line prefix.
- Masking is applied to a deeply nested config structure — all matching leaf values are redacted at every depth.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The library MUST apply secret masking consistently at every output channel: log statements, console/diagnostic output, startup banner, and string representations of configuration or cryptographic objects.
- **FR-002**: The library MUST consult a single centralized, authoritative list of sensitive key-name patterns; masking MUST be applied via this one list rather than scattered ad-hoc checks.
- **FR-003**: Sensitive key-name patterns MUST include at minimum: `token`, `password`, `secret`, `key`, `authorization`, `bearer`, `passphrase`, and their common variants. Matching MUST be by whole-word on a key's last path segment (NOT raw substring), so non-secret identifiers like `roleId`/`roleIdPrefix` and benign compounds like `tokenBucketSize`/`apiKeyboard` are NOT masked.
- **FR-004**: When logging or printing a nested configuration block, the library MUST walk all leaf entries and redact any whose key name matches a sensitive pattern — not only top-level keys.
- **FR-005**: Cryptographic objects (e.g., signing keys) MUST NOT expose their secret material in any string representation.
- **FR-006**: JVM input system arguments printed by startup diagnostics MUST have values redacted for keys matching sensitive patterns.
- **FR-007**: URL values logged or printed by the library MUST have any userinfo (`user:password@`) component stripped or redacted before output, failing safe on unparseable URLs.
- **FR-008**: All startup banner and diagnostic messages MUST be emitted through the standard logging framework and MUST NOT write directly to stdout or stderr.
- **FR-009**: The multi-line ASCII banner and diagnostics block MUST each be emitted as a single log event (not one event per line) under a dedicated logger category, so that a per-line layout prefix cannot break the box-drawing alignment.
- **FR-010**: The library MUST NOT place any auto-discovered logging configuration (`logback.xml` or `logback-test.xml`) on its main/compile classpath (`src/main/resources/`); it MUST NOT override or conflict with a consumer's logging configuration. The library MUST depend only on a logging API (`scala-logging`/SLF4J) at compile scope and MUST NOT force a concrete logging backend on consumers. (A `logback.xml` in the library's own `src/test/resources/`, which is excluded from the published artifact, is permitted for the library's test runs only.)
- **FR-011**: The project MUST provide a recommended logging configuration to consumers via (a) documentation containing a copy-pasteable minimal configuration and the override path, and (b) a working reference configuration in the runnable `examples/scala-sbt-example` overlay test scope.
- **FR-012**: The masking key-pattern list MUST be extensible via configuration so project-specific sensitive key names can be added without modifying library code.

### Key Entities

- **Sensitive Key Pattern**: A name or name fragment designating a config key whose value must be redacted (e.g., `token`, `password`, `bearer`).
- **Redacted Placeholder**: The string substituted for a masked secret value in all output.
- **Config Leaf**: A terminal key-value pair in a possibly-nested configuration structure; the atomic unit of a masking decision.
- **URL Userinfo**: The `user:password@` component of a URL that must be stripped before any URL is logged or displayed.
- **Recommended Logging Configuration**: A documented, copy-pasteable logging configuration snippet plus a working reference in the runnable example overlay — provided to consumers, NOT auto-loaded from the library's classpath.
- **Banner Log Event**: The full multi-line ASCII banner/diagnostics block emitted as one logging event under a dedicated category, so layout prefixes cannot break alignment.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero known secret-bearing values (matching the defined sensitive patterns) appear in plaintext across all log output, console output, banner, and exception messages during a test simulation run — verified by scanning captured output for the known test secret strings.
- **SC-002**: URL password components are absent from startup banner output in 100% of cases where a userinfo-bearing URL is configured.
- **SC-003**: Startup and diagnostic output can be fully suppressed by setting the relevant log category to OFF — zero diagnostic lines on stdout when suppressed.
- **SC-004**: The published library artifact contains NO `logback.xml` or `logback-test.xml` on the main classpath (verified by inspecting the packaged JAR); a consumer with its own logging config sees no "Resource [logback.xml] occurs multiple times on the classpath" warning attributable to picatinny.
- **SC-005**: A consumer following the documented logging snippet achieves quiet, configurable output (no uncontrolled DEBUG/TRACE flood) with picatinny dictating nothing; the snippet is present in docs and exercised by the `scala-sbt-example` overlay, and the banner renders with correct alignment under it.

## Assumptions

- The existing `ConfigValueMasking` infrastructure is the intended home for the centralized masking list; this spec assumes it is extended, not replaced.
- Masking is a presentation-layer concern — output is redacted; in-memory config objects are not mutated.
- The sensitive-pattern list is a curated set; fuzzy/regex matching of arbitrary key names is out of scope beyond what the authoritative list defines.
- Logging defaults are delivered as consumer-facing guidance (docs + example overlay), NOT as a config file packaged on the library's main classpath — a published library that ships an auto-discovered `logback.xml` collides with the consumer's own config (SLF4J/logback guidance + constitution non-runnable/Provided posture). The library's existing `src/test/resources/logback.xml` stays test-scoped (excluded from the artifact).
- The `examples/java-maven-example` and `examples/kotlin-gradle-example` overlays are currently non-runnable stubs (no build file); shipping a reference logging config to them is out of scope until they become runnable builds.
- URL userinfo stripping applies only to URLs picatinny itself prints or logs; it does not scan arbitrary free-text strings for URL patterns.
- This milestone does not change the public DSL or existing serialized config/profile keys — but it ADDS an optional `picatinny.redaction.*` config surface, which is a MINOR version bump (v1.23.0) per Constitution II.
- Related issues #87 (println → SLF4J) and #88 (recommended logging config, NOT a packaged `logback.xml`) are in-scope sub-tasks; #87 is a prerequisite for #88 (documented config is only meaningful once diagnostics route through the logging framework).
