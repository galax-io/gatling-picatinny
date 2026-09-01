# Feature Specification: Perf — Templates & Cookies, Behaviour-Locked, plus the OpenNFR v0.8.0 Metric Axis

**Feature Branch**: `327-perf-templates-cookies`

**Created**: 2026-09-01

**Status**: Draft

**Input**: User description: "https://github.com/galax-io/gatling-picatinny/milestone/13 — v1.27.0 — Perf: Templates & cookies. Be rational; choose the algorithm in the best way, one that does not break old behaviour." Amended 2026-09-01: (1) every performance change must be confirmed by a benchmark run before **and** after; (2) #328 is in scope and is to be done now; (3) the latent cookie defect is to be fixed too, in its own commit; (4) the two defects found while scoping are to be fixed in this feature rather than filed for later.

## User Scenarios & Testing *(mandatory)*

<!--
  TEST-MODEL HOOK (Constitution III): for each acceptance scenario below, keep in mind
  the REAL case it exercises and the test LAYER it maps to (see TESTING.md: unit/functional,
  DSL/action component, external integration, full Gatling e2e, compile guard, facade).
  `/speckit-plan` will expand these into the plan's mandatory code-free "Test Model" table.
-->

### User Story 1 - The benchmark gate runs, measures the right thing, and never reaches consumers (Priority: P1)

Every performance change in this milestone must be confirmed by a benchmark run before and after it.
Today that is impossible: the benchmark harness does not start. It reports that it cannot find its own
entry point and exits before a single measurement is taken, because the benchmark tooling is declared
as a compile-only dependency and the run forks against a classpath that drops exactly those. So the
first thing the milestone needs is a harness that runs at all.

Running is necessary but not sufficient. The existing body-assembly benchmark exercises only strings
that contain **no character requiring escaping**, so it cannot see an escaping regression at all: a
change that speeds up the escape-free case while corrupting or slowing the escaping case would show a
clean win. And nothing benchmarks cookie parsing. A maintainer needs the harness to run, to report
per-operation allocation and not only throughput, and to exercise the cases the changes are actually
about — otherwise the before/after numbers are decoration.

**Why this priority**: It is a hard prerequisite. Every other performance story in this milestone is
gated on a measurement that currently cannot be produced. Delivered alone it is already valuable: the
project's benchmark suite becomes runnable again for everyone.

**Independent Test**: Can be fully tested by running the benchmark harness and observing that it lists
and executes the project's benchmarks, reports a per-operation allocation figure, and includes cases
covering escaping-heavy body assembly and multi-attribute cookie parsing.

**Acceptance Scenarios**:

1. **Given** the repository as it stands, **When** the benchmark harness is asked to list the available benchmarks, **Then** it fails to start — this is the recorded starting condition the story fixes.
2. **Given** the harness fix, **When** the benchmarks are listed, **Then** every benchmark the project defines is listed and the harness exits successfully.
3. **Given** the harness fix, **When** a benchmark is run with allocation profiling requested, **Then** a per-operation allocation figure is reported alongside throughput.
4. **Given** body-assembly benchmarks, **When** their inputs are inspected, **Then** at least one case contains characters that force escaping in JSON and at least one forces escaping in XML — including a control character — so an escaping regression is visible; the pre-existing escape-free cases remain, so the fast path is measured too.
5. **Given** cookie parsing, **When** the benchmark set is inspected, **Then** a benchmark exercises a realistic multi-cookie, multi-attribute header.
6. **Given** any benchmark class added by this milestone, **When** the coverage report and the published artifact are inspected, **Then** the benchmark appears in neither — benchmarks are excluded by a naming convention, and a class that does not follow it silently enters the coverage denominator and ships to consumers.
7. **Given** the harness fix, **When** the published artifact's dependency descriptor is inspected, **Then** the benchmark tooling is still declared only in the scope that keeps it off every consumer's classpath, exactly as before the fix. *(Corrected 2026-09-01 during planning: an earlier wording said the tooling must be "absent" from the descriptor. It is not absent today and never has been — it is declared, at a non-transitive scope, and the published 1.26.0 artifact is the same. The property that matters, and that the fix preserves byte-for-byte, is non-transitivity, not absence. Releases 1.23.0-1.25.0 did leak it transitively, which is what the current declaration exists to prevent.)*
8. **Given** a local benchmark run followed by packaging the artifact, **When** the artifact's contents are listed, **Then** it carries no benchmark metadata and no benchmark-generated directory entries. Today the exclusion matches only compiled classes, so metadata files and bare directory entries produced by a benchmark run survive into the package — and this milestone makes benchmark runs routine, which is what raises the exposure.
9. **Given** the artifact produced with no benchmark run beforehand, **When** it is compared to one produced after a benchmark run, **Then** the two have identical contents — packaging is no longer sensitive to whether someone benchmarked first.

---

### User Story 2 - The host's locale never decides what the library recognises (Priority: P2)

Two places in the library match text case-insensitively by lowering it with the **host's default
locale**, and both then compare against words spelled with a plain ASCII capital `I`. On a generator
whose default locale applies locale-specific letter casing, that capital maps to a dotless character,
the comparison misses, and the library quietly does the wrong thing. Neither failure announces itself.

The severe half is secret masking. Configuration keys are matched against secret-indicating words to
decide whether a value is printed or replaced. Several of those words carry the affected letter, so a
key written in capitals fails to be recognised as secret-bearing and **its value is written out in
full** — the exact disclosure the masking feature exists to prevent. The safety net that is supposed
to catch missed matches compares the same corrupted text, so it does not catch this. User-supplied
extra sensitive keys go through the same lowering and break the same way.

The milder half is cookie restoration. A server may spell the domain attribute in capitals, which the
cookie standard permits since attribute names are case-insensitive. The attribute is not found, and the
cookie silently takes the fallback domain instead of the one the server sent, with no warning.

An operator needs matching decided by the letters, not by where the load generator happens to run.

**Why this priority**: The masking half is a secret-disclosure defect in the feature whose whole
purpose is to prevent disclosure, which outranks every performance concern in this milestone. Both
halves are one root cause with one shape of fix, found together, so they are specified together — but
they are separate modules with separate blast radii and ship as separate commits.

**Independent Test**: Can be fully tested by forcing an affected host locale and asserting that a
capitalised secret-bearing configuration key is masked and a capitalised cookie domain attribute is
honoured — tests that fail before the fix and pass after — with the unaffected-locale behaviour
unchanged.

**Acceptance Scenarios**:

1. **Given** a host locale with locale-specific letter casing and a configuration key spelled in capitals whose name carries a secret-indicating word containing the affected letter, **When** the configuration is printed, **Then** the value is masked.
2. **Given** the same key under an unaffected host locale, **When** the configuration is printed, **Then** the outcome is unchanged from today — the fix corrects the affected case without disturbing the ordinary one.
3. **Given** an extra sensitive key supplied through configuration and spelled with the affected letter, **When** it is normalised for matching, **Then** it is recognised under every host locale.
4. **Given** the masking safety net that catches separator-less keys ending in a secret word, **When** it runs under an affected locale, **Then** it too matches correctly — the fix covers every place the decision is made, not only the first one found.
5. **Given** an affected host locale and a header spelling the cookie domain attribute in capitals, **When** it is parsed, **Then** the domain the server sent is used, not the fallback default.
6. **Given** every recognised cookie attribute name spelled in plain ASCII, in every letter case, **When** parsed under any host locale, **Then** it is recognised — matching becomes locale-independent for the whole attribute set, not patched for one attribute.
7. **Given** an attribute name spelled with a non-ASCII letter that a specific host locale happens to fold onto an ASCII name — the dotted capital `İ` under Turkish being the only such case here — **When** it is parsed after the fix, **Then** it is no longer recognised, and that is correct. Cookie attribute names are ASCII; such a spelling is only ever recognised by accident, on one family of hosts, and treating it as valid is itself the locale dependence being removed. This is the one place the fix narrows rather than widens, it is deliberate, and it must be pinned by a test so it cannot be mistaken later for a regression.
8. **Given** these changes, **When** they are delivered, **Then** the masking fix and the cookie fix are separate commits, and both are separate from the single-pass rewrite, so a reviewer sees each behavioural change in isolation.
9. **Given** masking behaviour as a whole, **When** the change lands, **Then** no key that is masked today becomes visible — the fix may only widen what is recognised as secret, never narrow it. (The narrowing in scenario 7 is confined to cookie attribute names and does not apply to masking, whose word list is plain ASCII throughout.)

---

### User Story 3 - Upgrading proves byte-identical output before anything gets faster (Priority: P3)

A load-test author upgrades for a performance fix and must not discover that a request body or a
restored cookie now differs from the one their system under test accepted yesterday. Body assembly and
cookie parsing are the two places where a "harmless" rewrite silently changes an escape sequence, drops
a trailing separator, or reorders which duplicate attribute wins — and the failure surfaces as a
mysterious rejection from the target, not as a library bug. Before any optimization is attempted, the
author needs today's exact output captured as the reference answer, and every candidate measured
against it over a wide spread of generated inputs, not just the handful someone thought to write a test
for.

**Why this priority**: This is the standing constraint on the whole milestone. The performance changes
have zero intended user-visible effect, so a demonstrated equivalence result is the only thing that
distinguishes a correct optimization from a regression. Delivered alone it protects both paths
permanently, whether or not any optimization ships.

**Independent Test**: Can be fully tested by capturing the current output of body assembly and cookie
parsing as a reference, then feeding both the reference and the live implementation a broad generated
input population plus the enumerated boundary set, and confirming zero differences — with no
optimization present yet.

**Acceptance Scenarios**:

1. **Given** a broad generated population of field names and string values — including characters that must be escaped, characters that must not, control characters, and strings with nothing escapable at all — **When** each is rendered through JSON and XML body assembly, **Then** the produced body is character-for-character identical to the pre-change reference for every input.
2. **Given** a broad generated population of raw cookie headers — multiple cookies, attributes in mixed letter case, duplicate attributes, attributes with empty values, attributes with no value, values containing the name/value separator, blank lines, and lines with no separator — **When** each is parsed, **Then** the resulting cookie list is equal, field for field and in the same order, to the pre-change reference.
3. **Given** the reference population, **When** an equivalence run reports any difference, **Then** the run fails naming the exact input and both differing outputs, so the divergence is diagnosable without re-deriving it.
4. **Given** the existing hand-written tests for both paths, **When** the reference suite is added, **Then** those tests are unchanged and still pass — the safety net is added around them, not in place of them.

---

### User Story 4 - Escaping stops allocating a private buffer for every name and every value (Priority: P4)

A load-test author builds JSON or XML bodies whose field names and values are escaped one at a time.
Every name and every value gets its own freshly sized scratch buffer, and the finished text is copied
out of it into a throwaway string that exists only to be appended into the body under construction —
two objects per name and two per value. Because the field **name** goes through escaping on every
branch as well as the value, an ordinary five-field payload of plain strings pays about twenty
short-lived objects, and the overwhelmingly common case — text with nothing to escape — pays the full
buffer-and-copy price to reproduce the string it was handed. The author needs the same escaped text,
written straight into the body being assembled, with the untouched case costing nothing.

**Why this priority**: The widest of the perf changes — charged per name and per value, so it scales
with payload width — and the one a user can genuinely put on a per-request path by calling body
assembly from inside a request-time expression. It ranks below the stories above because it cannot be
measured or trusted without them.

**Independent Test**: Can be fully tested by rendering the reference input population through the
optimized escaping and confirming identical output, then comparing before/after benchmark runs on both
an escaping-heavy and an escape-free case.

**Acceptance Scenarios**:

1. **Given** every character JSON escaping treats specially — quote, backslash, newline, carriage return, tab, backspace, form feed — **When** a value containing each is rendered, **Then** the emitted escape sequence is byte-identical to the previous output.
2. **Given** control characters with no dedicated short escape, **When** they are rendered, **Then** each emits the same fixed-width four-digit lowercase hexadecimal escape as before, for every control character in range including both ends (boundary case).
3. **Given** every character XML escaping treats specially — ampersand, less-than, greater-than, quote, apostrophe — **When** a value containing each is rendered, **Then** the emitted entity is byte-identical to before, and characters outside that set pass through unchanged.
4. **Given** a field name or value containing nothing escapable (the common case), **When** it is rendered, **Then** the output is identical and no scratch buffer or intermediate escaped string is created for it.
5. **Given** field **names** as well as values, **When** names containing escapable characters are rendered, **Then** they are escaped exactly as before — the name path is not overlooked.
6. **Given** an empty string, a string that is entirely escapable characters, and a string whose only escapable character is first or last (boundary cases), **When** each is rendered, **Then** output matches the reference exactly.
7. **Given** benchmark runs recorded before and after the change on an escaping-heavy case and an escape-free case, **When** compared, **Then** per-operation allocation is strictly lower in both and throughput is not worse in either.

---

### User Story 5 - Cookie restoration reads each header once (Priority: P5)

A load-test author captures a `Set-Cookie` header into the session and restores it into the cookie jar
to carry authentication across a scenario. Each cookie line is cut apart repeatedly — split into
attributes, each attribute trimmed into a new string, each attribute split again around its separator,
and the whole set collected into a temporary lookup structure consulted five times and then discarded —
for a header the parser only needs to walk once. The author needs the same parsed cookies, including
every quirk of the current attribute handling, from a single pass that builds the result directly.

**Why this priority**: Real per-invocation waste, but on an opt-in DSL step an author adds explicitly,
typically once per virtual-user journey — whereas body escaping is charged on every field of every body.

**Independent Test**: Can be fully tested by parsing the reference cookie population through the
single-pass parser and confirming equality with the reference for every input, including malformed and
duplicate-attribute cases, plus before/after benchmark runs.

**Acceptance Scenarios**:

1. **Given** a header carrying several cookies on separate lines with a mix of attributes, **When** it is parsed, **Then** the cookies, their order, and every field of each are identical to the reference.
2. **Given** attribute names in upper, lower, and mixed case, **When** parsed, **Then** they are recognised exactly as today — none becomes newly recognised and none stops being recognised.
3. **Given** the same attribute repeated on one cookie with different values (boundary case), **When** parsed, **Then** the surviving value is the one that survives today, and flag-style attributes stay set once set.
4. **Given** a rejected lifetime value repeated on one cookie, **When** parsed, **Then** exactly one warning is emitted, naming the same occurrence it names today — no warning added, dropped, or duplicated by the rewrite.
5. **Given** an attribute present with an empty value, an attribute present with no value, a trailing separator, a value containing the name/value separator, a blank line, and a line with no separator (boundary cases), **When** each is parsed, **Then** the outcome — including whether a cookie is produced at all and whether a default is substituted — matches the reference exactly.
6. **Given** benchmark runs recorded before and after on a representative multi-attribute header, **When** compared, **Then** per-operation allocation is strictly lower and throughput is not worse.

---

### User Story 6 - A requirement about a group becomes assertable, and the metric axis follows upstream (Priority: P6)

An author writes performance requirements in the OpenNFR document format and renders them into Gatling
assertions. Until now a requirement naming only a group — "the checkout journey's 95th percentile is
under 1.6 seconds" — was refused, because the format had no metric name that was true of what a group
measures. Upstream released that name. The author needs group requirements to render, needs the
retired metric name to stop being accepted silently, and needs the documentation to state plainly what
the number a group assertion judges actually is — because it is the **sum of the durations of the
operations the group encloses, not the elapsed time of the traversal**, and a run that pauses inside a
group is not charged for the pause.

**Why this priority**: Not a performance concern, and the only story here that changes what documents
render. It is included because the upstream release it waited on has shipped. It ranks last because
every other story is independent of it and it is the largest single body of work.

**Independent Test**: Can be fully tested by rendering a document whose requirement names only a group,
confirming an assertion is produced; rendering the same document with the retired metric name and
confirming a refusal that names the replacement; and confirming the whole reference document now
renders with nothing subtracted.

**Acceptance Scenarios**:

1. **Given** a requirement selecting only a group hierarchy, paired with the group-duration metric, **When** it is rendered, **Then** one assertion is produced over that group's path.
2. **Given** a requirement selecting only a group hierarchy paired with the **request**-duration metric, any other metric, or no metric at all, **When** it is rendered, **Then** it is refused — the pairing rule binds in both directions, not just the newly permitted one.
3. **Given** a requirement selecting a request, paired with the group-duration metric, **When** it is rendered, **Then** it is refused for the same reason, stated from the row that refuses it.
4. **Given** a document carrying the retired metric name, **When** it is rendered, **Then** it is refused with a message that names the retirement and the replacement to write instead — the author is told what to do, not merely that something is wrong.
5. **Given** the reference document that previously rendered ten of its eleven requirements, **When** it is rendered after this change, **Then** all eleven render and the produced set equals the deprecated builder's set exactly, with nothing subtracted by name.
6. **Given** the shipped documentation, **When** it is read, **Then** it states which quantity a group assertion judges, states that the report-only setting which switches the charts between the two group quantities never reaches an assertion — so charts and assertions disagree by design when it is set — and states the upstream release now tracked.
7. **Given** the documentation guard that today asserts a group-only requirement has no equivalent, **When** the change lands, **Then** that guard asserts the opposite and passes — a stale guard that still passes would leave the docs and the code silently disagreeing.

---

### Edge Cases

- The benchmark harness must remain unable to leak its tooling into the published artifact's declared dependencies; the fix must be to how benchmarks are run, not to what consumers are told to download.
- A benchmark class whose name does not carry the project's benchmark marker is silently counted in the coverage denominator **and** shipped in the published artifact — naming is load-bearing, not cosmetic.
- A field name or value needing no escaping — the dominant case — must produce identical output while paying no per-field buffer.
- Every control character below the printable range, at both ends, must keep its exact fixed-width lowercase hexadecimal escape; the boundary between "has a dedicated short escape" and "falls through to the hexadecimal form" must not shift by one character.
- Escaping applies to field names as well as values; a rewrite that optimizes only the value path leaves half the allocations in place.
- Cookie duplicate attributes: which occurrence wins, and that flag-style attributes cannot be un-set by a later occurrence.
- A cookie attribute present with an empty value keeps its current meaning, which is **not** the same as the attribute being absent — an absent domain falls back to the default; an empty one does not.
- Trailing and repeated attribute separators, and a trailing newline, keep their current effect on the produced list.
- A cookie value containing the name/value separator keeps the whole remainder as its value; a line with no separator produces no cookie; a blank line is skipped.
- A line consisting only of the attribute separator currently raises an error rather than being skipped. That behaviour is undocumented and untested today, and the single-pass rewrite MUST preserve it exactly: the obvious defensive rewrite silently turns the error into an empty result, which is a behaviour change disguised as robustness. Changing it is a separate decision, not this feature's.
- The single warning for an unusable lifetime value fires exactly once per cookie, for the same occurrence as today, and never for values accepted today.
- The locale fixes must not be smuggled into the single-pass rewrite: that commit changes nothing observable, each locale commit changes exactly one thing, and a reviewer must be able to tell them apart.
- Only a **capital** `I` triggers the locale defect — text already written in lower case is unaffected. A fix must therefore be justified by the capitalised spellings, and tests must use them; a test written in mixed case would pass before the fix and prove nothing.
- Secret masking may only ever widen under this fix. A key masked today that became visible would be a disclosure introduced by a disclosure fix, so the change is one-directional by construction.
- The masking decision is made in more than one place — word splitting, the separator-less safety floor, and the normalisation of user-supplied extra sensitive keys all consume the same lowering. Fixing only the first leaves the others wrong.
- Benchmark **metadata** and generated directory entries are not compiled classes, so an exclusion written for classes alone does not remove them from the artifact.
- A group path that is also the full path of a recorded request resolves ambiguously in Gatling itself, by hash order. This is a property of the target, not of this library, and the documentation records it rather than legislating around it.
- After the group metric is minted, a group requirement and the old "call the group a request" workaround render to the **identical** assertion value. The prohibition on the workaround therefore survives only as a documentation rule, and the documentation must keep saying it.
- A performance change that is behaviour-neutral **and** measurement-neutral ships nothing, and its issue closes on the recorded evidence.

## Requirements *(mandatory)*

### Functional Requirements

#### The measurement gate

- **FR-001**: The benchmark harness MUST be made to run. The published artifact's declared dependencies MUST NOT gain the benchmark tooling as a result.
- **FR-002**: Every performance change in this feature MUST be accompanied by benchmark runs recorded **before and after** it, on the path it targets, reporting per-operation allocation as well as throughput. A performance change without both recordings MUST NOT ship.
- **FR-003**: The benchmark set MUST cover the cases the changes are about: escaping-heavy and escape-free body assembly, and multi-attribute cookie parsing. The pre-existing escape-free body cases MUST be kept alongside the new escaping-heavy ones.
- **FR-004**: Every benchmark class MUST follow the project's benchmark naming convention, so it is excluded from both the coverage denominator and the published artifact.
- **FR-005**: No benchmark output of any kind — compiled classes, generated metadata, or generated directory entries — may reach the published artifact. Packaging MUST produce identical contents whether or not benchmarks were run beforehand.
- **FR-006**: Where before/after measurement shows no improvement, the change MUST NOT ship and its issue MUST close on the recorded evidence instead. Speed claims are settled by measurement, never by inspection.

#### Locale-independent matching

- **FR-007**: Deciding whether a configuration key is secret-bearing MUST be independent of the host's default locale, in **every** place that decision is made — the splitting of a key into words, the safety net for separator-less keys, and the normalisation of user-supplied extra sensitive keys.
- **FR-008**: The masking fix MUST be one-directional: no configuration key masked before the change may be visible after it. Masking may widen, never narrow.
- **FR-009**: Cookie attribute-name matching MUST be independent of the host's default locale.
- **FR-010**: The two locale fixes MUST each be delivered as their own commit, separate from each other and from the single-pass cookie rewrite, and each MUST carry a test that fails before it and passes after under an affected host locale.

#### Behaviour parity

- **FR-011**: A reference-equivalence suite MUST pin the current observable output of body assembly and cookie parsing, and compare the live implementation against it over a generated input population plus the enumerated boundary set, failing with the offending input and both outputs on any difference.
- **FR-012**: Every performance change MUST produce observably identical output — same escape sequences, entities, parsed cookie fields, ordering, defaulting, warning text and warning count — such that all pre-existing tests pass unchanged.
- **FR-013**: Where an optimization would change any observable behaviour, behaviour parity wins and the optimization MUST be narrowed or dropped. Output fidelity is never traded for library speed.

#### The optimizations

- **FR-014**: Escaping MUST write directly into the body text being assembled, rather than producing a separate intermediate escaped string per field name and per field value.
- **FR-015**: When a field name or value contains no character requiring escaping, escaping MUST NOT allocate a per-field buffer or copy the string.
- **FR-016**: The escape form for control characters without a dedicated short escape MUST remain the identical fixed-width four-digit lowercase hexadecimal sequence, produced without general-purpose text-formatting machinery.
- **FR-017**: Escaping MUST NOT introduce shared mutable per-thread scratch buffers. Such a buffer is retained for the lifetime of every load-generator worker thread and couples nested body assembly to buffer reuse, while delivering nothing that writing directly into the target text does not already deliver.
- **FR-018**: Cookie parsing MUST produce each parsed cookie from a single traversal of its line's attributes, without materialising an intermediate attribute lookup structure, while preserving case-insensitive attribute matching, duplicate-attribute precedence, flag-attribute stickiness, empty-value-versus-absent semantics, and exactly-once warning behaviour for the same occurrence chosen today.
- **FR-019**: The rewritten parsing and escaping MUST stay within the project's established code-style rules for string handling. Any construct those rules disallow MUST be recorded as a justified exemption in the plan's Complexity Tracking table rather than introduced silently.

#### The OpenNFR metric axis

- **FR-020**: The renderer MUST address the two metric names the upstream release mints — one for the duration of a recorded operation, one for the duration of a group — and MUST enforce their pairing with selection **in both directions**: the group metric only under a selection naming a group hierarchy with no request, and the operation metric under every selection except that one.
- **FR-021**: A selection naming only a group hierarchy MUST render, where today it is refused.
- **FR-022**: The retired metric name MUST be refused, not accepted and not aliased, and the refusal MUST name both the retirement and the replacement to write instead. Upstream retired it outright and defined no acceptance window; this renderer transcribes the published tables and invents no rule of its own.
- **FR-023**: Rendering the reference document MUST produce assertions equal to the deprecated builder's whole set, with nothing subtracted by name — the parity comparison becomes a whole-set comparison.
- **FR-024**: The shipped documentation MUST state which quantity a group assertion judges — the sum of the enclosed operations' durations, not the elapsed time of the traversal — and MUST state that the report-only setting switching the charts between the two group quantities never reaches an assertion, so charts and assertions disagree by design when it is set.
- **FR-025**: The tracked upstream release MUST be updated everywhere it is stated, and the documentation guards that today assert the group-only case has no equivalent MUST be inverted so that a stale guard cannot pass while the docs and code disagree.
- **FR-026**: Every fixture, example and document in the repository carrying the retired metric name MUST be migrated.

#### Compatibility and closure

- **FR-027**: Apart from the OpenNFR rendering behaviour, which is an explicitly experimental surface outside the binary-compatibility guarantee, and the two locale corrections, the changes MUST be purely internal: no public API signature, DSL behaviour, serialized format, or session variable name may change (Constitution II), and the binary-compatibility check MUST report zero new issues.
- **FR-028**: Each tracked issue MUST end up covered at the unit/functional layer by regression tests asserting exact output parity with at least one negative or boundary case — including any issue closed on refuted evidence, whose closure MUST be recorded in writing rather than left implicit.

### Key Entities

- **Reference output**: The captured, currently-shipping result of a body assembly or a cookie parse for a given input — the answer every optimized implementation must reproduce exactly.
- **Input population**: The generated spread of field names, field values, and raw cookie headers, plus the enumerated boundary set, over which equivalence is demonstrated.
- **Measurement pair**: The before and after benchmark recordings for one path, including per-operation allocation, that together justify or refute one performance change.
- **Parsed cookie**: A cookie's name, value, domain, path, lifetime, and its secure and http-only flags, as produced from one line of a raw header.
- **Requirement selection**: Which recorded operations or group a performance requirement is about, and which metric name may be paired with it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The benchmark harness starts, lists every benchmark the project defines, and runs one to completion reporting a per-operation allocation figure — where today it fails to start.
- **SC-002**: The published artifact has byte-identical contents whether or not benchmarks were run before packaging, and contains no benchmark class, metadata file, or generated directory entry.
- **SC-003**: Under a host locale with locale-specific letter casing, a capitalised secret-bearing configuration key is masked and a capitalised cookie domain attribute is honoured — both of which fail today. Across the full set of configuration keys the project's masking tests exercise, no key masked before the change is visible after it.
- **SC-004**: 100% of pre-existing tests pass unchanged — zero behavioural drift on any path this feature optimizes.
- **SC-005**: The reference-equivalence suite reports zero differences across the full generated input population and the complete enumerated boundary set, for body assembly and for cookie parsing.
- **SC-006**: Every shipped performance change carries a recorded before/after measurement pair showing strictly lower per-operation allocation and no throughput regression; every performance change without such a pair is not shipped.
- **SC-007**: A name or value containing nothing escapable completes with no per-field buffer allocated, and a cookie line is parsed with no intermediate attribute lookup structure allocated.
- **SC-008**: The reference OpenNFR document renders all eleven of its requirements, and the rendered set equals the deprecated builder's set exactly, with nothing subtracted.
- **SC-009**: A document carrying the retired metric name is refused with a message naming the replacement; a group metric under a request selection, and an operation metric under a group-only selection, are both refused.
- **SC-010**: All four open milestone issues (#126, #127, #137, #328) are closed — those whose premise measurement confirms, by a change carrying its own parity regression test and its measurement pair; those whose premise measurement refutes, by recorded evidence. The two locale defects and the packaging defect are closed by their own commits.
- **SC-011**: The binary-compatibility report shows zero new findings, and the published artifact's declared dependencies are unchanged.

## Assumptions

- Scope is the four open issues in milestone 13 ("v1.27.0 — Perf: Templates & cookies") — [#126](https://github.com/galax-io/gatling-picatinny/issues/126), [#127](https://github.com/galax-io/gatling-picatinny/issues/127), [#137](https://github.com/galax-io/gatling-picatinny/issues/137), [#328](https://github.com/galax-io/gatling-picatinny/issues/328) — **plus three defects found while scoping and pulled into this feature at the maintainer's direction rather than filed for later**: the secret-masking locale defect, the cookie-domain locale defect, and the benchmark-output packaging defect.
- Those three are tracked by [#333](https://github.com/galax-io/gatling-picatinny/issues/333), a single tracking issue on this milestone, which is what the commits for them reference under the repository's PR↔issue linkage gate. It enumerates all four unfiled work items — the benchmark harness, the packaging leak, and the two locale defects — each with its own acceptance criteria.
- **The benchmarks the gate depends on are in this milestone.** [#143](https://github.com/galax-io/gatling-picatinny/issues/143) (body assembly) and [#144](https://github.com/galax-io/gatling-picatinny/issues/144) (cookie parsing) were moved from v1.29.0 into v1.27.0 at the maintainer's direction, because under the new measurement policy they are prerequisites for #126 and #137 rather than later baseline work. They are the benchmark half of User Story 1 and land before the optimizations they measure.
- **#143's premise is partly stale and its scope is wider than its text.** Body-assembly benchmarks already exist covering nested, many-field, large-array, mixed and interpolated payloads; what is genuinely absent is the escaping-heavy coverage this feature requires (FR-003), without which a #126 regression is invisible. Both issues also name the test source tree as their location, whereas benchmarks in this project live on the production classpath — which is load-bearing for how they are discovered and for the naming rule in FR-004.
- The secret-masking defect is the most severe item in this milestone and is the reason its story outranks every performance concern here: the masking feature exists to prevent disclosure, and this defect makes it disclose.
- The `327` in the branch name is the branch script's own sequential number and carries no issue reference (it collides numerically with an unrelated merged pull request); the spec directory is numbered independently as `014`.
- **Each issue's suggested fix is a hypothesis, not a specification.** Three are amended here on evidence gathered while scoping, all read from the artefacts this project pins:
  - **#126** suggests a shared per-thread scratch buffer, and separately suggests a "single-pass char loop into a pre-sized buffer" that **already landed**. What remains true is the headline, and it is worse than the issue states: escaping runs on the field **name** on every branch as well as on the value, so a plain five-field payload pays roughly twenty short-lived objects. The chosen algorithm is to write escaped text straight into the body under construction plus a fast path for text with nothing to escape — strictly more allocation removed than the suggested fix, no memory retained between calls, no cross-call coupling (FR-012).
  - **#127** asserts a fresh field sequence is allocated per request. **It is not.** The request-builder body entry points assemble the body text once, when the scenario is defined, and reuse it for every request; the per-request cost of this path is zero. The issue's suggested overload is also not expressible — a variable-argument parameter and a sequence parameter are indistinguishable to the compiler at this language version, so any sequence-taking entry point would need a different name, which is new public API for a path measurement says is not hot. **#127 closes as invalid on recorded evidence, with no code change.** *(This supersedes an earlier draft of this spec, which proposed adding that entry point as a convenience; under the before/after measurement gate it cannot be justified, and a convenience addition belongs in its own issue outside a performance milestone.)*
  - **#137**'s premise holds, but the cost is repeated array and map allocation rather than pattern compilation — the split calls take the single-character fast path and compile no pattern.
- **#328 is scoped from the released upstream README, not from the issue text**, as the issue itself directs. Two things differ from the issue's expectation, and the released text governs: the retired metric name has **no deprecation window** — it is retired outright and its reach row reads "cannot"; and the pairing between the two new metric names and selection is **bidirectional**, not merely a newly permitted row.
- The upstream README describes the group statistic in a form that reads like a code call but is the name of the statistic, not of a builder. A literal transcription into the local contract would describe code that does not exist; the contract must transcribe the statistic.
- **Rendering the group metric needs no new call into the host framework.** The framework exposes exactly one time-metric entry point and its assertion value has no field to record a group-versus-request distinction; the statistic is chosen entirely by path resolution at validation time. The change is therefore in the renderer's *validation* rules, not in what it emits — and the largest real code change is that the selection and the metric must be resolved **together**, where today they are resolved independently.
- A consequence to document rather than fix: because the framework has one time-metric name, a group assertion prints as a response-time assertion in the framework's own report. The metric distinction lives only in the requirement document.
- Verified against the versions this project pins. The upstream README sources part of its tables to a newer framework release that is not available locally; that half is **not** independently re-verified here, and the spec claims nothing about it.
- The OpenNFR surface ships experimental and outside the binary-compatibility guarantee, which is what allows its rendering behaviour to change within a minor release. The rest of the milestone is internal, so the version stays MINOR.
- The equivalence suite uses the project's existing test and property-generation tooling; no new dependency is expected. If planning finds one is required, it needs explicit authorisation before adoption (Constitution IV).
- House delivery rules apply: one issue = one semantic commit, each independently green. The benchmark-harness fix, the packaging fix, the benchmark additions, the masking locale fix and the cookie locale fix are each their own concern and each their own commit — nine commits in all, counting the three optimization/rendering changes.

## Out of Scope

- Any change to what cookie attributes are recognised, what the cookie-restore DSL propagates into the cookie jar, or how the body DSL renders values. Recognising an attribute under a locale that today mangles it is a correction to *how* matching is done, not a widening of *what* is matched.
- Any widening of what counts as a secret-bearing configuration key. The masking fix restores the matching the existing word lists already intend; it adds no new terms and changes no rule.
- Adding a sequence-taking convenience entry point to the request-builder body DSL — see the #127 amendment above; if wanted, it belongs in its own issue.
- Resolving the framework's own group-versus-request path ambiguity. It is recorded as a property of the target, upstream records it the same way, and it applies identically to the deprecated builder.
- Migrating benchmarks to the benchmark tooling's own source directory. They live on the production classpath today and that is load-bearing for how they are discovered; moving them is a behaviour change, not a cleanup.
- A general audit of every locale-sensitive text operation in the library. The sites that decide something security- or correctness-relevant were enumerated during scoping and are fixed here; the remainder were checked and found unreachable because the tokens they compare carry no affected letter.
