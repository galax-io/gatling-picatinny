# Feature Specification: Config & Cookie Correctness Fixes (v1.22.0)

**Feature Branch**: `006-config-cookie-fixes`

**Created**: 2026-06-23

**Status**: Draft

**Input**: Milestone [v1.22.0 — Config & cookies](https://github.com/galax-io/gatling-picatinny/milestone/7): `#93` intensity regex, `randomValue` doc-fix, `#111` Max-Age, cookie discard-attrs (`#207`).

## Overview

A bundle of four correctness defects in the **config**, **utils**, and **cookies/storage** areas of the published Gatling Picatinny library. Each defect causes a load test to either run with the *wrong* configured value, silently lose information, or be described inaccurately to the user. None add new capability — they make existing behavior match what users already expect from the DSL and its documentation.

The four defects are independent and each is shippable on its own; they are grouped only because they target the same v1.22.0 milestone and the same correctness theme.

## Clarifications

### Session 2026-06-23

- Q: US2 cookie restore — what happens to the existing `session.set(name, value)` behavior when wiring cookies into the engine cookie jar? → A: Additive — register the cookie in the Gatling cookie jar **and** keep setting the session attribute (backward-compatible, Constitution II).
- Q: US2 cookie restore — which parsed cookie attributes must be propagated into the cookie jar? → A: (initial intent) all of them. **Superseded below** by the API-feasibility clarification.
- Q: US2 cookie restore — given that Gatling's jar-store API (`CookieSupport`) is `private[http]` (uncallable from picatinny) and the public `addCookie` DSL only accepts runtime values for name/value (domain/path/max-age/secure are build-time-fixed), how should cookies be wired into the jar? → A: **Public DSL (safe, partial)** — use the supported `addCookie(Cookie(name, value).withDomain(<restoreCookies domain arg>))` route. Name and value flow at runtime; the cookie is scoped to the `domain` argument with default path `/`. Per-cookie parsed `path`/`max-age`/`secure`/`httpOnly` are **not** propagated. No reflection into Gatling internals; no public API signature change. Rationale: for an outbound (client-sent) cookie only `name=value` is transmitted; `max-age` affects only in-test jar expiry, `secure`/`path` affect attach-matching, and `httpOnly` is meaningless to a sending client — so the practical loss is negligible for load tests.
- Q: Should the cookie switch/overwrite behavior (re-restoring a cookie of the same name/domain replaces the prior value, revoking the previous role) be a stated requirement or only e2e coverage? → A: **Formalize as requirement** — overwrite-revocation is a guaranteed, published behavioral contract (FR-010), verified by the two-role switching e2e.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Decimal request intensities are honored exactly (Priority: P1)

A performance engineer configures a fractional request intensity (e.g. `0.25 rps`, `12.75 rpm`, `123.55 rph`) via the `intensity` simulation parameter. They expect the load generator to drive exactly the rate they typed.

**Why this priority**: This silently corrupts the *primary purpose* of a load test — the offered load. The number reported and applied differs from what the user configured, with no error. Wrong load → wrong results → invalid conclusions. Highest real-world blast radius of the four.

**Independent Test**: Feed a range of decimal intensity strings through the intensity parser and assert the returned requests-per-second value equals the exact configured value (within floating-point tolerance), including ≥2-digit decimals and a no-unit default case.

**Acceptance Scenarios**:

1. **Given** intensity `"0.25 rps"`, **When** the value is parsed, **Then** the resulting rate is `0.25` rps (not `0.2`).
2. **Given** intensity `"123.55 rph"`, **When** the value is parsed, **Then** the resulting rate equals `123.55 / 3600` rps (not `123.5 / 3600`).
3. **Given** intensity `"50 rpm"`, **When** the value is parsed, **Then** the resulting rate equals `50 / 60` rps (whole numbers still work).
4. **Given** intensity `"10"` with no unit, **When** the value is parsed, **Then** it defaults to `10` rps (default unit preserved).
5. **Given** a malformed intensity such as `"abc"` or `"1.2.3 rps"`, **When** the value is parsed, **Then** parsing fails with a clear error rather than silently truncating.

---

### User Story 2 - Restored cookies are actually sent on later requests (Priority: P2)

A user captures a `Set-Cookie` response header into a session attribute, then later restores it (via the cookie-restore DSL helper) so that subsequent requests in the same virtual-user flow carry the cookie — for example to resume an authenticated session loaded from storage.

**Why this priority**: Today the restore step parses the full cookie (name, value, domain, path, Max-Age, Secure, HttpOnly) but only copies name/value into a plain session attribute and never registers the cookie with the HTTP engine's cookie jar. As a result the cookie is **not** automatically attached to later requests — the feature does not deliver its core promise. High functional value; ranked below P1 only because intensity corrupts every test while this affects cookie-dependent flows.

**Independent Test**: Restore a cookie for a domain, then make a follow-up request to that domain carrying NO explicit `Cookie` header, and assert via Gatling `check` on the RESPONSE status that the cookie was auto-attached (a WireMock stub matches on the cookie value and returns 200, else 403). The two-role switching e2e (TESTING.md layer 4) further restores a different value for the same cookie name and asserts the prior role is revoked.

**Acceptance Scenarios**:

1. **Given** a session holding a `Set-Cookie` value with a name, value, and `Path`, **When** cookies are restored for the target domain, **Then** a subsequent request to that domain/path automatically includes the cookie.
2. **Given** a `Set-Cookie` carrying attributes, **When** cookies are restored for the target `domain`, **Then** the cookie is registered in the jar scoped to that domain with default path `/` (via the supported public `addCookie` DSL); per-cookie `path`/`max-age`/`secure`/`httpOnly` are not propagated, and this is acceptable because they do not affect what an outbound load-test cookie transmits.
3. **Given** a session that does not contain the named `Set-Cookie` attribute, **When** restore runs, **Then** the flow continues unchanged with no error.
4. **Given** a multi-line `Set-Cookie` value containing several cookies, **When** restore runs, **Then** every well-formed cookie is registered.
5. **Given** any restored cookie, **When** restore runs, **Then** the cookie's name→value is **also** set as a session attribute (existing behavior retained for backward compatibility), in addition to being registered with the cookie jar.
6. **Given** a cookie of name `sid` already restored with value A for a domain, **When** a cookie of the same name `sid` is later restored with value B for the same domain, **Then** the jar entry is replaced with value B (the prior value A is revoked), so subsequent requests carry only value B — enabling role switching (e.g. user → admin → user).

---

### User Story 3 - Malformed `Max-Age` is visible, not silent (Priority: P3)

An engineer debugging why a restored cookie has no expiry inspects the logs. A `Set-Cookie` header arrived with a non-numeric `Max-Age` (e.g. `Max-Age=abc`).

**Why this priority**: Observability fix. The cookie still parses (Max-Age simply omitted), so behavior is already safe; the gap is that the user has no signal explaining the missing TTL. Lower urgency than a wrong value, but cheap and prevents silent confusion.

**Independent Test**: Parse a `Set-Cookie` line whose `Max-Age` is non-numeric and assert (a) the cookie is still returned with no max-age set, and (b) a warning is emitted naming the offending value.

**Acceptance Scenarios**:

1. **Given** a `Set-Cookie` with `Max-Age=abc`, **When** it is parsed, **Then** the cookie is returned with no max-age **and** a warning is logged that includes the offending value `abc`.
2. **Given** a `Set-Cookie` with a valid numeric `Max-Age=3600`, **When** it is parsed, **Then** the max-age is set and **no** warning is logged.
3. **Given** a `Set-Cookie` with no `Max-Age` attribute at all, **When** it is parsed, **Then** the cookie is returned with no max-age and **no** warning is logged (absence is not an error).

---

### User Story 4 - `randomValue` range bounds are documented correctly (Priority: P3)

A developer reading the API documentation for the random-value range generator relies on the stated bounds to decide whether the maximum is a reachable value.

**Why this priority**: Documentation-only correctness. The implementation is correct and must not change (callers depend on current behavior); the published Scaladoc misstates the upper bound as inclusive when it is exclusive. Low risk, but for a published library inaccurate docs are a real defect.

**Independent Test**: Inspect the documentation of the `randomValue` overloads and confirm the upper-bound wording reads "exclusive"; behavior is unchanged and remains covered by existing tests.

**Acceptance Scenarios**:

1. **Given** the range overload `randomValue(min, max)`, **When** a user reads its documentation, **Then** the maximum is described as **exclusive** (returned value is `>= min` and `< max`), matching the implementation.
2. **Given** the single-bound overload `randomValue(max)`, **When** a user reads its documentation, **Then** the maximum is likewise described as **exclusive**.
3. **Given** the runtime behavior of `randomValue`, **When** the doc fix is applied, **Then** generated values are unchanged (no behavior change, only wording).

---

### Edge Cases

- **Intensity**: leading/trailing whitespace (`" 0.25 rps "`), unit case-insensitivity (`RPS`/`Rps`), missing unit (default rps), zero or negative values (existing positivity validation still rejects non-positive intensities), and `.5 rps` (leading-dot decimal) — define behavior explicitly rather than truncating.
- **Cookies**: cookie value containing `=`; empty/blank lines among multiple cookies; attribute keys in mixed case; cookie with no attributes at all; restore invoked when the source attribute is absent or not a string.
- **Max-Age**: must never throw. Present-but-unparseable values — non-numeric (`abc`), empty (`Max-Age=`), and overflow (beyond `Long`) — yield `maxAge=None` + exactly one WARN. A negative value (`-1`) parses to a valid `Long` (`Some(-1)`) and is silent. Absent is silent.
- **randomValue**: `min == max` behavior is unchanged (returns `max`); no documentation should imply otherwise.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The intensity parser MUST accept decimal request intensities with an arbitrary number of fractional digits and apply the exact value (no truncation of digits beyond the first decimal place).
- **FR-002**: The intensity parser MUST continue to support whole-number intensities, the `rps`/`rpm`/`rph` units (case-insensitive), optional surrounding whitespace, and the no-unit default of `rps`.
- **FR-003**: The intensity parser MUST reject clearly malformed intensity strings with a clear error rather than silently parsing a partial/incorrect value; existing positive-value validation MUST remain in force.
- **FR-004**: Cookie restoration MUST register restored cookies with the HTTP engine's cookie jar (via the supported public Gatling cookie DSL — `addCookie`/`Cookie`) so they are automatically attached to subsequent requests to the target `domain`. The cookie's `name` and `value` carry at runtime; the cookie is scoped to the `domain` argument with default path `/`. Per-cookie parsed `path`/`max-age`/`secure`/`httpOnly` are NOT propagated (the public DSL fixes these at build time and cannot accept per-cookie runtime values; reflection into Gatling internals is explicitly rejected). Restoration MUST **also** continue to set each cookie's name→value as a session attribute (additive, backward-compatible per Constitution II).
- **FR-005**: Cookie restoration MUST handle multiple cookies from a multi-line value and MUST be a no-op (no error, flow continues) when the source attribute is missing or not a string.
- **FR-006**: Cookie parsing MUST emit one warning, including the offending value, when a `Set-Cookie` carries a `Max-Age` that is present but not parseable as a `Long` (non-numeric, empty, or out-of-range/overflow), while still returning the cookie with no max-age set and never throwing.
- **FR-007**: Cookie parsing MUST NOT warn when `Max-Age` is a valid `Long` (including negative values, which parse successfully) or entirely absent.
- **FR-008**: The published documentation for the `randomValue` range and single-bound overloads MUST describe the maximum bound as exclusive, consistent with the implementation; the implementation MUST NOT change.
- **FR-009**: All four fixes MUST preserve backward compatibility of public Scala/Java API signatures and serialized config formats (Constitution II).
- **FR-010**: Restoring a cookie of the same name and domain as a previously-restored cookie MUST replace the prior jar entry (overwrite-revocation), so only the most recently restored value is sent on subsequent requests to that domain. This makes role switching (restore user → restore admin → restore user) a supported, guaranteed behavior.

### Key Entities *(include if feature involves data)*

- **Intensity value**: a user-supplied rate string (`<number>[ ]<unit?>`) converted to a requests-per-second number; the number may be fractional, the unit is one of rps/rpm/rph (default rps).
- **Parsed cookie**: name, value, and optional attributes (domain, path, max-age, secure, httpOnly) derived from a `Set-Cookie` line.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of valid decimal intensity inputs parse to their exact configured rate (verified across a parameterized set including ≥2-digit decimals); 0 cases of digit truncation.
- **SC-002**: A cookie restored via the DSL is present on 100% of subsequent in-flow requests to its target domain in an end-to-end run (auto-attached by the cookie jar, with no explicit `Cookie` header in the request code).
- **SC-006**: In the two-role switching e2e (8 steps; login+restore = steps 1/4/7, protected calls = steps 2/3/5/6/8), every step returns its expected status — the 5 role-gated protected calls (user phase: admin→403, user→200; admin phase: admin→200, user→403; back-to-user: user→200) plus the 3 login 200s — proving overwrite-revocation; the run passes `global.failedRequests.count.is(0)`.
- **SC-003**: Every present-but-unparseable `Max-Age` (non-numeric, empty, or overflow) produces exactly one warning naming the value; valid (incl. negative) and absent `Max-Age` produce zero warnings.
- **SC-004**: The `randomValue` overload documentation states "exclusive" for the maximum and matches observed runtime bounds, with no change to generated values.
- **SC-005**: All existing unit and example e2e suites remain green, and module coverage stays at or above the project floor (65% statement / 60% branch). No `it`/IntegrationTest sources (Redis/Vault/JDBC) are modified, so the integration suite is out of risk-scope (may be run as an optional full pass).

## Assumptions

- **Cookie restoration is an additive behavior change** (US2, confirmed 2026-06-23): cookies are newly registered with the engine cookie jar via the supported public `addCookie` DSL (name/value at runtime, scoped to the `domain` argument, default path `/`) **and** the existing session-attribute copy of name→value is retained for backward compatibility. This is a DSL behavior addition and warrants the v1.22.0 (minor) version bump.
- **Per-cookie `path`/`max-age`/`secure`/`httpOnly` are intentionally not propagated** (decided 2026-06-23): the public `addCookie` DSL accepts only `name`/`value` as runtime values; `domain`/`path`/`max-age`/`secure` are build-time-fixed and cannot vary per parsed cookie, and there is no `httpOnly` setter. Reflection into Gatling's `private[http]` `CookieSupport` (the only route to full per-cookie fidelity) is rejected to avoid coupling a published library to Gatling internals across `Provided`-scope versions. The dropped attributes do not affect what an outbound load-test cookie transmits (`max-age` = in-test jar expiry only; `httpOnly` = irrelevant to a sending client).
- `restoreCookies` keeps its current signature `restoreCookies(setCookieField: String, domain: String): ChainBuilder`; the variable-length runtime cookie list is iterated with Gatling's `foreach` over a session-stored collection so the per-VU cookie count is honored.
- The intensity fix targets the parser regex only; the public type (`Double`) and method signatures are already correct and unchanged.
- The `randomValue` fix is documentation-only across all affected overloads (range and single-bound); implementation and tests are untouched apart from any added doc-verifying assertions.
- The `Max-Age` warning uses the library's existing logging facility (Scala Logging) at WARN level; no new logging dependency is introduced.
- No Java/Kotlin facade changes are required beyond what delegation already provides; facades stay thin (Constitution I).
- E2e validation for cookie restoration uses the existing WireMock echo overlay in `examples/` (TESTING.md layer 4); no new test infrastructure is added.

## Out of Scope

- Redesigning the cookie model or parsing/supporting `Set-Cookie` attributes that `CookieParser` does not already extract (SameSite, Expires, Partitioned, etc.).
- Reflection into Gatling's `private[http]` `CookieSupport` to achieve full per-cookie jar fidelity (`path`/`max-age`/`secure`/`httpOnly`). Explicitly rejected for backward-compat/coupling reasons (see Assumptions).
- Changing `randomValue` runtime semantics to make the maximum inclusive.
- Any change to intensity units, the `Double` intensity type, or downstream injection-profile math.
- Broader cookie-jar/session-storage refactors beyond wiring restored cookies into the jar.
