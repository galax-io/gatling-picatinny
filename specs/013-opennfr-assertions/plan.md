# Implementation Plan: OpenNFR assertions

**Branch**: `013-opennfr-assertions` | **Date**: 2026-08-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-opennfr-assertions/spec.md`

## Summary

Read an OpenNFR `RequirementSet` (upstream release `v0.6.0`) and produce Gatling `Assertion`s, beside
the deprecated `assertionFromYaml` which is not touched. A pure function from a document to either a
list of assertions or a list of reasons: no I/O beyond reading the file, no network, nothing dropped
or approximated. Correctness is anchored on the deprecated builder as an oracle — the same
requirements written both ways must build **equal** assertion sets over the ten of eleven the format
can state.

Three layers: a decoded document model, a reach resolver that maps one predicate to one assertion or
one reason, and an entry point that accumulates every reason. The facade re-issues the same decision
through the Java DSL because the Java `Assertion` wrapper cannot be constructed from a core one — a
constraint the deprecated path already documents and works around the same way.

## Technical Context

**Language/Version**: Scala 2.13.18, targeting Java 17. Facade in Java 17.

**Primary Dependencies**: Gatling 3.13.5 (`Provided`). `circe-core` / `circe-parser` / `circe-yaml` —
**already in `Dependencies.scala`**, so parsing and decoding need nothing new. A JSON-Schema
validator for FR-012 is **NEEDS CLARIFICATION** — see `research.md` R1.

**Storage**: N/A. A local file is read once, at simulation start-up.

**Testing**: ScalaTest (`AnyWordSpec` + `Matchers`) for the core; JUnit 5 via sbt-jupiter-interface
for facade delegation; a real `Simulation` in the `examples/` overlay against WireMock for e2e.

**Target Platform**: JVM library, consumed inside a Gatling simulation.

**Project Type**: Published library with a thin Java/Kotlin facade.

**Performance Goals**: None beyond "runs once per simulation, at set-up". Documents are tens of
predicates; no hot path.

**Constraints**: No network (FR-014). Threshold conversion is exact decimal arithmetic, never binary
floating point (FR-008). Nothing may be dropped, reordered or approximated (FR-007).

**Scale/Scope**: Three source files plus a facade; one fixture pair; ~20 core tests, ~4 facade tests,
one e2e simulation.

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | A team points a simulation at `nfr.yaml` and gets assertions back. | Unit / Functional | Load a one-criterion document through the entry point and assert the exact assertion value produced — scope, statistic, condition and target, compared by equality, not by field spot-checks. Negative: the deprecated entry point is called in the same test class and still returns its own eleven, proving the two coexist. |
| FR-002 | A document written for a future release is copied into a project. | Unit / Functional | Feed documents whose `apiVersion` is `opennfr.io/v2`, whose `kind` is misspelled, and whose `requirements` list is empty. Assert each is refused and that the message names both the value found and the value supported. Boundary: the exact supported string is accepted. |
| FR-003 | An engineer's first document has three unrelated mistakes. | Unit / Functional | Feed one document carrying a bad operator, an unreachable aggregation and an unaddressable selector across two requirements. Assert the refusal carries exactly three reasons, and that each names its requirement and its predicate identity. Negative: a single-defect document yields exactly one. |
| FR-004 | An author writes `metric` beside `bad`, which the schema forbids and nothing here validates. | Unit / Functional | Feed the three schema-forbidden combinations — `metric` with `bad`, `metric` with `good`, `bad` with `good`. Assert each is refused with the rule's own wording. Boundary: each key alone is accepted. |
| FR-005 | The four selection shapes a real NFR file uses, plus the shapes it must not. | Unit / Functional | For `{}`, a bare request name, a hierarchy of depth one, a hierarchy of depth three, and the quantified `"*"`, assert the exact scope produced. Negative: a hierarchy-only selector, a `"*"` hierarchy element, `"*"` beside a hierarchy, a non-string path part and an unaddressable attribute are each refused with that row's stated reason. |
| FR-006 | The three statistic shapes the deprecated format produces. | Unit / Functional | Assert response-time percentile, max, min, mean and stddev; the failed-request share and count; and the request count and throughput. Boundary: a fractional share target survives as a fraction rather than being truncated. Negative: a metric that is not response time is refused naming the metric. |
| FR-007 | A document whose author expects every line to be checked. | Unit / Functional | Assert the count of assertions equals the count of criteria plus guards for a document carrying both, and that two identical predicates produce two assertions rather than one. Negative: no input produces fewer assertions than predicates without a refusal. |
| FR-008 | A latency budget written in seconds with millisecond precision. | Unit / Functional | Assert `1.001 s` yields a target of exactly `1001`, which binary floating point would compute as `1000.9999…` and reject. Boundary: `0.5 s` is 500 and accepted; `0.5 ms` is refused with a message saying rounding would move the bar; a share in `1` converts to percent. |
| FR-009 | An author reaches for a shape the format allows and Gatling cannot run. | Unit / Functional | Assert `neq`, `sum`, a body-size metric, a narrowed `bad` and an unfitting unit are each refused with the reason its reach row gives. Negative — the direction that matters — assert every shape the reach tables list as reachable **is** accepted, so the allowlist does not over-refuse. |
| FR-010 | A maintainer looks for where a rule came from. | Compile Guard | A test fixture asserts the renderer's documented source is the upstream README section, and a check asserts the string `gatling-reach.md` appears nowhere in the feature's sources — that path is a redirect carrying no rule. |
| FR-011 | Upstream leaves a question open and the library answers it anyway. | Unit / Functional | Assert every locally-decided rule is reachable from one named place, and that its refusal message carries the upstream issue number. Boundary: a rule the tables do decide carries no local marker. |
| FR-012 | A document with a misspelled key that decodes but is not valid OpenNFR. | Unit / Functional | Assert a document the published schema rejects is refused here, for every rule class the schema carries that the decoder alone would let through — an unknown key, an empty `criteria`, a malformed `name`. Negative: every document in the upstream corpus is accepted. |
| FR-013 | The vendored schema is edited locally, making the library more permissive than the format. | Compile Guard | A test asserts the carried schema is byte-identical to the recorded upstream release, and fails on any difference — including one that only widens what validates. |
| FR-014 | A build runs on a machine with no route to the internet. | Unit / Functional | Assert a document validates and renders with no network available, exercised by asserting the resolver is given no remote reference to follow. Negative: a schema `$ref` to an absolute URL is refused rather than fetched. |
| FR-015 | An existing user upgrades and their `nfr.yml` keeps working. | Unit / Functional | Run the existing deprecated-path suite unmodified and assert it still builds its eleven assertions including the group-only one, per the enumeration in `contracts/parity.md`. Boundary: no signature in the deprecated surface changes, asserted by the compile guard and MiMa. |
| FR-016 | A Kotlin team migrates the same document their Scala colleagues use. | Facade Delegation | Load one document through the Scala entry point and through the facade and assert the two assertion lists are equal field by field. Negative: an unrenderable document fails through the facade with the same reasons, proving the decision was not re-made in the facade. |
| FR-017 | A user whose `nfr.yml` no longer fully translates reads the docs to find out why. | Compile Guard | Assert the migration table names every recognised NFR-YAML key — the six in `contracts/parity.md` § 1 — that each row is referenced by a test, and that the group-only row is present with its reason. Negative: a key added to the deprecated builder without a table row fails the check. |
| FR-018 | A requirement is found that the format cannot state. | Unit / Functional | Assert the repository carries a runnable reproduction for each such case — the deprecated input, the assertion it builds today, and the point the translation stops — and that no local attribute or extension exists to close it. |

**Layer choice, and what is deliberately absent.** This feature is a pure function over a file, so
layer 1 carries almost all of it. There is no external service, so **no Testcontainers layer**: adding
one would be a component test where none is needed. There is no actor, no feeder determinism and no
transaction boundary, so **no DSL/action component layer**. A full Gatling e2e in `examples/` is
listed in `quickstart.md` as the end-to-end proof that the assertions a document denotes are the ones
a real run evaluates — it is the one place the Gatling runtime is exercised for real rather than
described.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — every rule (decode, selection, statistic, unit, operator, refusal wording) lives in the Scala core. The facade re-issues the resolved decision through the Java DSL and adds no decision of its own. **Bent, declared** — see Complexity Tracking: the facade cannot literally delegate because the Java `Assertion` wrapper cannot be built from a core one.
- [x] **II. Backward Compatibility** — purely additive. `assertionFromYaml`, its exception type and its test seam are unchanged (FR-015). New public types are new, so MiMa reports nothing. The new surface is documented as experimental and outside the compatibility guarantee (spec, Assumptions).
- [x] **III. Test Discipline** — the Test Model above is filled, one row per FR, each naming a real case, a layer from `TESTING.md` and a code-free sketch with a negative or boundary assertion. Work is test-first. No Testcontainers and no component layer, for the reasons stated above. Coverage must stay at or above the enforced floor (75 % / 66 %).
- [ ] **IV. Small, Focused Changes** — **BLOCKED on one point.** FR-012 needs a JSON-Schema validator, which is a new dependency. AGENTS.md requires new dependencies to be agreed first. Everything else in the feature is unblocked; see Complexity Tracking and `research.md` R1.
- [x] **V. Release Integrity** — not a release PR.

## Project Structure

### Documentation (this feature)

```text
specs/013-opennfr-assertions/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── parity.md        # every deprecated key, scope and assertion, mapped
│   ├── public-api.md    # what this library exposes, and its refusal contract
│   └── reach.md         # the upstream rows this renderer implements, as data
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
src/main/scala/org/galaxio/gatling/assertions/
├── AssertionsBuilder.scala              # deprecated, UNCHANGED
└── opennfr/
    ├── Model.scala                      # document model + decoders
    ├── Reach.scala                      # one predicate -> one assertion, or one reason
    └── OpenNfrAssertions.scala          # entry point; accumulates every reason

src/main/java/org/galaxio/gatling/javaapi/
├── Assertions.java                      # deprecated, UNCHANGED
└── OpenNfrAssertions.java               # facade (FR-016)

src/main/resources/opennfr/
└── requirementset.schema.json           # vendored at a recorded release (FR-013)

src/test/resources/
├── nfr.yml                              # deprecated fixture, UNCHANGED
└── opennfr/                             # translation + refusal fixtures
src/test/scala/org/galaxio/gatling/assertions/opennfr/
src/test/java/org/galaxio/gatling/javaapi/assertions/
examples/                                # e2e simulation (overlay only)
docs/                                    # migration table (FR-017)
```

**Structure Decision**: the new code sits in an `opennfr` sub-package beside the deprecated builder,
so the two are visibly siblings and the deprecated file is never opened. The facade mirrors the
existing `javaapi` layout. Fixtures mirror the source layout.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **Principle I bent**: the Java facade re-issues the DSL call rather than delegating to the Scala core's result | `io.gatling.javaapi.core.Assertion`'s wrapping constructor is package-private, so there is no public way to turn a core `Assertion` into a Java one. `Assertions.java` already documents this and does the same. | Returning the core type from the facade would leak a Scala-side type into the Java surface. Reflection into a package-private constructor would be worse than the duplication and would break on any Gatling upgrade. **Mitigation**: every *decision* stays in the core and is exposed as a resolved value the facade only re-issues, so the facade carries no rule — which is the part of Principle I that matters. |
| **Principle IV**: FR-012 needs a new dependency | The format's authority is a JSON Schema; without a validator the library enforces a hand-copied subset (FR-004) and drifts from the schema on every upstream release. | Hand-rolling schema validation is the drift this requirement exists to prevent. **This is the one item requiring authorization before implementation, and it is scoped to FR-012 alone** — the rest of the feature is unblocked and does not wait on it. |
