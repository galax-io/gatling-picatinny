# Feature Specification: OpenNFR assertions

**Feature Branch**: `013-opennfr-assertions` · **Issue**: [#236](https://github.com/galax-io/gatling-picatinny/issues/236) · **Milestone**: v1.26.0 — Assertions API replacement

**Created**: 2026-08-24 · **Rewritten**: 2026-08-30 · **Status**: Draft

**Input**: User description: "необходимо предложить новый формат согласно спецификации, обязательно сделать тесты проверки на схему и соответствие с версией. Старую версию не убирай, обязательно тест на соответствие assert старого формата. Если что то не сходится … не придумывай ничего нового, собери информацию и кейсы для воспроизведения и надо будет завести issue в оригинальном проекте"

---

## Context

`assertionFromYaml` (deprecated since 1.18.0) reads a tool-private YAML whose metric keys are Russian
prose and whose scopes are `" / "`-joined strings. It travels to no other tool and its vocabulary is
arbitrary.

**[OpenNFR](https://github.com/galax-io/opennfr)** is the replacement format: a published JSON Schema
(Draft 2020-12), a validated corpus, and reach tables stating what Gatling can and cannot assert.
This feature reads an OpenNFR document and produces Gatling assertions, additively, beside the
deprecated path — which is not touched.

This library is the format's **first consumer in any language**. A requirement the deprecated format
expresses and OpenNFR cannot is evidence about the format, and is reported upstream rather than
worked around: a picatinny-local extension would fork the format on its first adoption. Tracked
against upstream release **`v0.6.0`**.

> **What informs this spec.** Six upstream releases were audited from here and 38 findings filed;
> that history lives in [the upstream issues](https://github.com/galax-io/opennfr/issues), not in
> this document. Thirteen remain open and **exactly one (`opennfr#74`) would change this library's
> behaviour** — it would add four accepted units. The rest are the format's own hygiene. A throwaway
> spike then confirmed the design end to end, which is why the assumptions below are stated firmly
> rather than hedged; the spike was deleted and is not the deliverable.

---

## User Scenarios & Testing

### User Story 1 — A requirement written once becomes Gatling assertions (P1)

A performance engineer writes thresholds in an OpenNFR `RequirementSet` — a document naming no tool —
and points the simulation at it. The same document is readable by a product owner, reviewable in a
pull request, and survives a move to another load generator.

**Independent Test**: point the entry point at a one-criterion document; assert the exact assertion
produced — scope, statistic, condition, target.

**Acceptance Scenarios**

1. **Given** `selector: {}` with `{metric: http.client.request.duration, aggregation: p95, op: lte, threshold: 500, unit: ms}`, **Then** one assertion is produced, globally scoped, over the response-time 95th percentile, condition `lte`, target `500`.
2. **Given** `{loadtest.request.name: X}`, **Then** the scope is the root-anchored one-part path `X` — it resolves only to a request with no enclosing group.
3. **Given** `{loadtest.group.name: [G₁…Gₙ], loadtest.request.name: X}`, **Then** the scope is the ordered path `G₁ / … / Gₙ / X`, at any depth, matched whole and never as a prefix.
4. **Given** `{loadtest.request.name: "*"}`, **Then** the scope is Gatling's per-request one.
5. **Given** `{bad: {error.type: "*"}, aggregation: rate}`, **Then** the failed-request share, with a fractional target preserved (`5.5` stays `5.5`).
6. **Given** a requirement carrying `guards`, **Then** every guard renders as well as every criterion — Gatling has one outcome, and an unrendered guard would pass silently.

### User Story 2 — A wrong document is refused before the run, not after it (P1)

An engineer misspells a key, writes a unit that does not fit, or copies a document written against a
different release. They are told at load time, in one message naming every problem, before a load
test consumes an environment for twenty minutes and reports green.

**Independent Test**: feed documents each carrying one defect; assert the load fails and the message
names the offending location and value.

**Acceptance Scenarios**

1. **Given** several independent defects, **Then** *every* reason is reported, not the first.
2. **Given** an `apiVersion` this library does not read, a wrong `kind`, or no requirements, **Then** loading fails naming both the value found and the value supported.
3. **Given** `threshold: 0.5, unit: ms` over response time, **Then** it is refused rather than rounded — rounding moves the bar the author wrote, so the document states one limit and the run enforces another.
4. **Given** `threshold: 1.001, unit: s`, **Then** the target is exactly `1001` ms. Binary floating point yields `1000.9999999999999` and would refuse it.
5. **Given** a shape outside the reach tables — `neq`, `sum`, a non-addressable metric, a `bad` narrower than `{error.type: "*"}`, a `"*"` hierarchy element, a unit that does not fit its statistic — **Then** it is refused with that row's reason.
6. **Given** a path that does not exist, **Then** the failure names the path.

### User Story 3 — Migrating off NFR-YAML changes nothing that can be kept (P2)

A team rewrites `nfr.yml` as an OpenNFR document and needs certainty the run is judged by the same
bar — not "equivalent", the same assertions.

**Independent Test**: build both ways from the fixture and its translation, and compare the sets.

**Acceptance Scenarios**

1. **Given** `nfr.yml` and its translation, **Then** the two build **equal** assertion sets over the ten of eleven OpenNFR can state — same scopes, statistics, conditions, targets, no extras either side. All eleven are enumerated in [`contracts/parity.md`](contracts/parity.md); ten match and the eleventh never will.
2. **Given** the eleventh — the group-only `myGroup: '1600'` — **Then** it is excluded by name and by a test asserting *why*, never by silently shrinking the expected set.
3. **Given** the deprecated path after this ships, **Then** its existing suite passes unmodified: same signatures, same behaviour, nothing removed.
4. **Given** a published migration table from every recognised NFR-YAML key, **Then** each row is exercised by a test, so the documentation cannot drift from the behaviour.

### User Story 4 — Java and Kotlin reach the same capability (P3)

The deprecated path exists in the facade; a replacement that skips it strands those users on a
deprecated API.

**Acceptance Scenarios**

1. **Given** one document, **Then** the Scala entry point and the facade produce the same assertions.
2. **Given** an unrenderable document, **Then** both fail for the same reason.
3. **Given** the facade, **Then** every rule of the format is decided once in the shared core.

---

## Requirements

### Reading and refusing

- **FR-001**: An entry point MUST take the path of an OpenNFR document and return the Gatling assertions it denotes, without altering, wrapping or further deprecating `assertionFromYaml`.
- **FR-002**: A document whose `apiVersion` is not `opennfr.io/v1`, whose `kind` is wrong, or which carries no requirements MUST be refused, naming both the value found and the value supported.
- **FR-003**: Every violation MUST be reported at once — each naming the requirement, the predicate and the reason — never the first alone.
- **FR-004**: The rules the schema states but this library does not validate against — `metric` with `bad`, `metric` with `good`, `bad` with `good` — MUST be enforced by the renderer instead.

### Rendering

- **FR-005**: Scopes MUST render per the upstream Selection table: `{}` global; a bare request name root-anchored; a hierarchy plus a name as an ordered path at any depth; `"*"` on the request name as the per-request scope. Any other shape MUST be refused with that row's reason.
- **FR-006**: `http.client.request.duration` MUST render as the response-time statistic; `bad: {error.type: "*"}` as the failed-request share or count per its aggregation; a predicate carrying neither as the request count or throughput per its aggregation.
- **FR-007**: Every criterion **and every guard** MUST produce exactly one assertion. Nothing is dropped, deduplicated, reordered or approximated.
- **FR-008**: A threshold MUST be canonicalised to the target's native unit by **exact decimal arithmetic on the literal as written**, and MUST be refused rather than rounded where the target needs a whole number.
- **FR-009**: Any predicate outside the reach tables MUST be refused, naming the predicate and the specific reason. The rejected set is an allowlist: a shape not listed as reachable is refused, never accepted by default.
- **FR-010**: Reach rules MUST be taken from upstream `README.md` § *What any tool can actually run*, which is their only home. `specs/004-strip-to-schema/contracts/gatling-reach.md` is a redirect and MUST NOT be cited.
- **FR-011**: Where this library must decide something the published text leaves open, the decision MUST be recorded in one place with its reason and the upstream issue named. It MUST NOT silently follow either side.

### Validation and pinning

- **FR-012**: Documents MUST be validated against the pinned OpenNFR JSON Schema before any assertion is produced. *This is the only part of the feature needing a dependency the project does not have; it is subject to the AGENTS.md rule that new dependencies are agreed first.*
- **FR-013**: The repository MUST carry the schema at a recorded upstream release and commit, MUST state that release in user-facing documentation, and a gate MUST fail the build if the carried copy differs from it.
- **FR-014**: The library MUST NOT reach the network to validate a document.

### Not removing anything

- **FR-015**: `assertionFromYaml` in both the Scala and Java surfaces, the exception type it throws, and the internal test seam MUST remain with unchanged signatures and behaviour, and their existing tests MUST pass unmodified.
- **FR-016**: The new surface MUST be reachable from Java and Kotlin, producing the same assertions from the same document, with every rule decided once in the shared core.

### Documenting the boundary

- **FR-017**: User-facing documentation MUST state that this path is experimental, name the upstream release it tracks, carry the 1:1 migration table from every recognised NFR-YAML key, and say plainly which requirements do not survive the move **and why**.
- **FR-018**: A requirement the deprecated format expresses and OpenNFR cannot MUST be carried as a minimal reproduction and reported upstream. No local extension, private attribute or workaround may be added for it.

---

## Success Criteria

- **SC-001**: The translated `nfr.yml` and the original build equal assertion sets over the ten OpenNFR can state — verified by comparing sets, not by inspection, against the row-by-row enumeration in [`contracts/parity.md`](contracts/parity.md).
- **SC-002**: The deprecated suite passes unmodified and no published signature changes.
- **SC-003**: No accepted document produces fewer assertions than it has criteria and guards; no run is green on a check that was dropped.
- **SC-004**: A threshold whose exact conversion is whole is accepted; one that is not is refused, never rounded.
- **SC-005**: Every document the format's own schema rejects is rejected here.
- **SC-006**: A reader can rewrite a deprecated document from the published migration table alone, and every row of it is exercised by a test.
- **SC-007**: The supported upstream release is stated in one place, and a drifted schema copy fails the build.

---

## Parity: ten of eleven, and where the eleventh is blocked

`nfr.yml` builds eleven assertions. Ten translate. The eleventh — `myGroup: '1600'`, a group-only
scope — is refused. Upstream's Selection table records it as a `cannot` at `v0.6.0`, and this renderer
follows that row.

**The blocker is the format's metric axis, not Gatling's model.** Read from the Gatling bytecode:
`AssertionValidator.resolvePath` sends a `StatsPath.Group` to
`groupCumulatedResponseTimeGeneralStats`, so Gatling asserts on a group path perfectly well — it
answers with the group's **cumulated** response time, the sum of the durations of the requests one
traversal encloses. What is missing is a *name*: OpenNFR admits one metric,
`http.client.request.duration`, and a sum over several requests is not an HTTP request's duration, so
**no metric name in the format is true of the quantity Gatling would return**. Upstream's own row says
as much — *"which no name in § Names is true of"*.

That gap is filed at `opennfr#89` and tracked here at `#328`; the same gap leaves Kafka, JDBC and
bracketed `startTransaction` spans unnameable. **This document states the cause, not the outcome:**
how upstream answers is not settled, so nothing here promises what happens when it does.

**FR-017** must carry this, because a user told only "your file no longer translates" deserves the
reason — and deserves to be told that the deprecated path still covers the case today.

The obvious workaround — `{loadtest.request.name: myGroup}`, which renders the identical Gatling call
— is forbidden by **FR-007**: it is a document that says *request* about a group.

---

## Assumptions

- **The new surface is experimental and outside the binary-compatibility guarantee**, documented as such. The format is pre-1.0 and its schema has changed materially between consecutive releases. The deprecated path keeps its guarantees in full. *This is the one assumption most worth a stakeholder overturning; doing so changes the labelling and the compatibility gate, not the design.*
- **Removing the deprecated path is not in this feature** — a separate breaking change under `v2.0.0`, after a deprecation window.
- **Parsing needs no new dependency**: `circe-yaml` and `circe-core` are already in `Dependencies.scala`. **FR-012 is the only part that does.**
- **The upstream reach tables are the starting point, not the last word.** Where they and Gatling disagree, the disagreement is a finding, reported upstream and decided locally under **FR-011**.
- **Documents are read from a local path**, as the deprecated entry point does. Classpath resources, URLs and inline documents are out of scope.
- **No renderer exists upstream to compare against**, so correctness is anchored on the deprecated path as an oracle (User Story 3) and on the published corpus.

## Out of Scope

- Removing, changing or further deprecating any part of the NFR-YAML path.
- Extending, subsetting or profiling OpenNFR — including inventing `loadtest.*` attributes, adding a picatinny-only field, or narrowing the schema locally to what Gatling runs.
- Constructs the format does not have: measurement windows, baselines and tolerances, severities, shared defaults, a result document, load profiles.
- A result or report document: nothing upstream produces one.
