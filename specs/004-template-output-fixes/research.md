# Phase 0 Research: Template Output Fixes (v1.19.0)

All five items are localized changes in `templates/Syntax.scala` and `templates/Templates.scala`.
The spec carried no open `[NEEDS CLARIFICATION]` markers; this document records the design
decision behind each fix, the rationale, and the alternatives considered. No new dependency
or public signature change is introduced by any decision below.

---

## D1 — EL detection for dotted/hyphenated names in `arr()` (FR-001, #74)

**Current code**: `Syntax.scala:70` — `private val interpolateRegExpr = "#\\{(\\w+)\\}".r`, used only at `Syntax.scala:83` inside `arr`.

**Observed reality** (verified against source): the regex's *only* use is to classify a
`String` element of `arr(...)`. Because `\w+` cannot match `user.id`, a dotted name like
`arr("#{user.id}")` finds **no** match and already falls through to `RawValString("#{user.id}")`,
which renders `"#{user.id}"` correctly. So #74's user-visible breakage is narrow — it is the
*classification* that is wrong (a valid EL string is labeled a plain literal), and it compounds
with the truncation bug (D2) for the mixed case.

**Decision**: Widen the EL-detection pattern to `#\{([\w.\-]+)\}` (word chars, dot, hyphen).
Keep it a single private regex used only at the `arr` call site (per #203's note to "narrow to
the one call site"). Detection now correctly identifies dotted/hyphenated EL names.

**Rationale**: `[\w.\-]+` covers the Gatling EL attribute-name grammar in practice (dotted
property access, hyphenated keys). Hyphen is placed last (or escaped) inside the class to avoid
a range. The regex stays private — no public surface change.

**Alternatives considered**:
- *Align with Gatling's full EL grammar (nested `#{a.b(c)}`, functions).* Rejected — out of
  scope, larger surface, and the `arr` helper only needs "does this string contain an EL ref".
- *Drop the regex and treat every string as potential EL.* Folded into D2 — see below.

---

## D2 — `arr()` must not truncate literal text around EL (FR-002, #203)

**Current code**: `Syntax.scala:82-83` —
`case s: String => interpolateRegExpr.findFirstMatchIn(s).fold(RawValString(s))(m => InterpolateStrVal(m.group(1)))`.
For `"hello #{name}!"` it keeps only `group(1)` (`name`), rendering `["#{name}"]` and silently
dropping `hello ` and `!`.

**Decision**: When an `arr` string element **contains** an EL expression (detected via the D1
regex), render the **entire original string** as a quoted, escaped JSON/XML string — do not
extract the variable name. Pure-literal strings (no `#{...}`) keep rendering as today.
Concretely: the string branch yields a value that, on emit, produces the full escaped string;
the extracted-`group(1)` path is removed.

**Why this is also correct for the pure case**: `InterpolateStrVal("name")` renders `"#{name}"`
and an escaped full-string render of `"#{name}"` is byte-identical (none of `#{}._-` are
JSON/XML-escaped). So pure `arr("#{name}")` is unchanged at the byte level; only the mixed case
is fixed, plus strings that *also* contain quotes/`<`/`&` are now correctly escaped.

**Behavioral consequence (the ⚠️ byte change)**:
- `arr("hello #{name}!")`: `["#{name}"]` → `["hello #{name}!"]` (fixed).
- An `arr` element's internal AST classification changes (an EL-bearing string is no longer
  surfaced as `InterpolateStrVal` inside the returned `ArrayVal`). This is an internal detail,
  not a rendered-output contract, but it **breaks `SyntaxSpec.scala:233-238`**, which asserts
  `arr(...)` contains `InterpolateStrVal("dynamic")`. That test is updated to assert on the
  rendered bytes (the real contract) rather than the internal node type.

**Alternatives considered**:
- *Add a new `FieldVal` variant for "full EL string".* Rejected — `RawValString`'s escape +
  quote behavior already produces correct bytes for an EL-bearing string (EL markers survive
  escaping); a new variant adds public surface for no rendering benefit.
- *Concatenate literal + `InterpolateStrVal` fragments.* Rejected — needlessly complex; Gatling
  interpolates the whole emitted string at runtime, so a single escaped string is sufficient.

**Implemented as (refinement, byte-identical):** anchored whole-string detection —
`case interpolateRegExpr(name) => InterpolateStrVal(name)` (matches only when the *entire* string
is `#{...}`) else `RawValString(s)`. A pure single-EL string stays `InterpolateStrVal` (preserving
the AST and keeping the existing `SyntaxSpec` test green); any literal-bearing string is kept whole
as `RawValString`. Output bytes are identical to the collapse described above, but strictly more
backward-compatible (no AST change for the common pure-EL case).

---

## D3 — Escape XML element names (FR-003, #203)

**Current code**: `Syntax.scala:266-281` (`appendXmlField`) and `283-292` (`appendXmlArray`)
emit `<name>…</name>` with `name` **unescaped**, whereas JSON already escapes the field name
(`escapeJson(name)` at lines 194/196/198…). An element name carrying `<`, `>`, `&` can inject
structural markup into the outgoing XML body.

**Decision**: Apply the existing `escapeXml` to element names at every emission point in
`appendXmlField` (the `<name>` open tag and `</name>` close tag), matching the JSON path that
already escapes names. `<item>` wrappers in `appendXmlArray` are fixed literals and are
unaffected.

**Rationale**: Parity with the JSON path; closes the markup-injection gap with the escaper that
already exists in the file. For a normal identifier name the output is byte-identical to today
(regression-guarded). For a name containing structural characters, the characters are escaped so
no *new* element can be injected. (We do not attempt to make a structurally-invalid element name
*valid* — element names are author-controlled; the goal is injection-safety, per the spec's
low-severity security framing.)

**Alternatives considered**:
- *Reject/throw on illegal element names.* Rejected — heavier behavior change, and the spec
  asks for escaping, consistent with the JSON side.
- *Whitelist-validate names against the XML NCName grammar.* Rejected as scope creep for a
  low-severity item; escaping is the minimal injection-safe fix.

---

## D4 — `RawValGen` string values must be escaped; only scalars stay raw (FR-004, #203)

**Current code**: `RawValGen(s)` is appended **raw** in JSON (`Syntax.scala:196`, `233`) and XML
(`270`, `286`) via `sb.append(s)`. `RawValGen[+T]` wraps any `T` (`Syntax.scala:35`), so a stringy
value injects unescaped/unquoted text — invalid JSON and an XML/JSON injection vector.

**Decision**: At each `RawValGen` emission point, branch on the wrapped value's runtime type:
- **Genuine scalars** — `Int`, `Long`, `Short`, `Byte`, `Double`, `Float`, `BigInt`,
  `BigDecimal`, `Boolean` — emit raw (unquoted), exactly as today.
- **Anything else (stringy)** — in JSON, quote and `escapeJson`; in XML, `escapeXml`.

**Rationale**: Preserves the documented intent (`"count" - 42 → 42`, `"b" - true → true`) and
keeps every existing scalar assertion in `SyntaxSpec` green, while making stringy raw values
safe. The scalar set matches what the facade's typed `field(...)` overloads can produce
(`int`/`long`/`double`/`boolean`) plus Scala numeric types, so facade-produced `RawValGen`s stay
raw and delegation parity holds.

**Behavioral consequence (the ⚠️ byte change)**: a `RawValGen` wrapping a `String` or arbitrary
object now renders quoted+escaped (JSON) / escaped (XML) instead of raw. Previously such usage
produced invalid output, so this only changes already-broken cases.

**Alternatives considered**:
- *Match on a sealed numeric type.* Rejected — `RawValGen[+T]` is unconstrained public API;
  changing its type bound would be a signature/compat change (forbidden without authorization).
  A runtime type check is the minimal, signature-preserving fix.
- *Always escape `RawValGen`.* Rejected — would quote numbers/booleans and break the documented
  raw-scalar contract and existing tests.

---

## D5 — Missing templates directory must fail fast (FR-005, #73)

**Current code**: `Templates.scala:33-49` — `templates` is a `lazy val`;
`Option(getContextClassLoader.getResource("templates")).fold(Map.empty)(…)`. A missing resource
silently yields an empty map; the failure only surfaces later as `NoSuchElementException` from
`resolveTemplate` (`Templates.scala:51-57`), far from the misconfiguration.

**Decision**: Replace the `fold(Map.empty)` null-branch with an explicit failure: when
`getResource("templates")` is `null`, throw a clear exception (e.g. `IllegalStateException`)
whose message states that the expected `templates` directory was not found on the classpath and
hints at the misconfiguration. The resource-present branch is unchanged. Because `templates` is
`lazy`, the throw occurs only on **first dereference** (i.e. when a `postTemplate`/`resolveTemplate`
is actually used) — a Simulation that mixes in the trait but never touches templates is
unaffected.

**Empty-directory boundary**: a `templates` resource that exists but contains no files yields an
**empty map** (not an error) — this is distinct from "missing" and must stay non-fatal (spec
edge case). Only the null-resource case fails fast.

**Opt-in for an intentionally-empty registry**: deferred. With the trait mixed in, intent is to
use templates, so missing = error is the safe default. If a legitimate empty-on-purpose use case
appears, an explicit opt-in (sentinel/override) can be added later without breaking this default.
Recorded as an assumption in the spec; not implemented now (Principle IV — minimal change).

**Rationale**: Fail-fast diagnostics for a published library; the error names the root cause at
the point of misconfiguration instead of a misleading late `NoSuchElementException`.

**Alternatives considered**:
- *Make the resource path a configurable parameter and validate it.* Rejected — adds public API
  surface; out of scope for the bug.
- *Log a warning and continue with an empty map.* Rejected — the spec requires fail-fast; a
  warning is easily missed and still produces wrong runtime behavior.

---

## D6 — Test layer selection (Constitution III / TESTING.md)

**Decision**: Unit/Functional (ScalaTest exact-byte assertions) is the authoritative layer for
D1–D5; add one Facade-Delegation (JUnit 5) test for parity.

**Rationale**:
- D1–D4 are pure `String` rendering — exact `shouldBe` byte assertions are the strongest possible
  check and need no harness.
- D5 is classpath-resource + trait behavior — unit-testable by swapping the thread context
  classloader; no Testcontainers (no Redis/Vault/JDBC).
- **No full Gatling e2e**: whether Gatling interpolates `#{user.id}` at runtime is the Gatling EL
  engine's behavior, not picatinny's (picatinny only emits the string). An overlay e2e would test
  Gatling, not this change — low value, so omitted (TESTING.md: apply only the layers that fit).
- **Facade delegation required** because `TemplateSyntax.java` exists and routes through the
  changed `makeJson`/`makeXml`/`arr`; one test asserts facade output == core output.

---

## D7 — Documentation / versioning (FR-006, Constitution II & V)

**Findings**:
- There is **no `CHANGELOG.md`** in the repo; release notes are generated by **git-cliff** from
  conventional-commit messages at tag time (per AGENTS.md release process).
- All five fixes change generated bytes → a behavior change that must be surfaced to consumers.

**Decision**: Land the work as a conventional commit that flags the breaking/behavior nature
(e.g. `fix!:` with a `BREAKING CHANGE:`/⚠️ body) so git-cliff renders the warning in the GitHub
Release notes. **Version resolved (maintainer, 2026-06-22): MINOR, target `v1.19.0`.** The latest
released tag is `v1.18.0` (with a `release/1.18.0` branch), so v1.18.0 is taken and the next MINOR
is v1.19.0. The stale tracker labels — the GitHub milestone *titled* "v1.18.0" and issue #203's
body "v1.13.0" — do not reflect the target; actual target is **v1.19.0**. No `CHANGELOG.md` file
is created (would diverge from the established git-cliff process).
