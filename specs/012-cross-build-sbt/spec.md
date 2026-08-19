# Feature Specification: Cross-build on sbt 1 and sbt 2

**Feature Branch**: `012-cross-build-sbt`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "https://github.com/galax-io/gatling-picatinny/pull/319 нужен кросс билд на оба сбт"

## Context

PR [#319](https://github.com/galax-io/gatling-picatinny/pull/319) is a Scala Steward proposal to move the build tool from sbt `1.12.15` to sbt `2.0.6` — a major version step. Taken as-is it is a one-way door: it swaps the pinned build-tool version in `project/build.properties` (root) and `examples/scala-sbt-example/project/build.properties`, and the project is on sbt 2 semantics from that moment with no verified way back.

To be precise about what is *not* at stake: nobody is blocked today. The sbt launcher is
version-agnostic — it reads `project/build.properties` and fetches whichever sbt the build asks for
— so a contributor with an sbt 2 launcher builds this repository on sbt 1 without noticing. The
published artifact is a Scala 2.13 library whose coordinates and contents do not encode the build
tool, so consumers are unaffected either way. The real cost of the status quo is **optionality**:
sbt 1 will eventually reach end of life, the bot will keep proposing this bump, and today the choice
is binary — stay, or jump without knowing what breaks.

This feature replaces the either/or with **both**: the repository must be buildable, testable, and releasable under sbt 1.x *and* sbt 2.x from a single source tree, with CI proving both continuously. Once that holds, PR #319 stops being a risky cutover and becomes a routine change of which version is the default. The decision taken for this feature is to keep sbt 1.x as the default pin and carry sbt 2.x as a fully verified secondary major, so PR #319 is superseded rather than merged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintainer builds and gates the library under either sbt major (Priority: P1)

A maintainer clones the repository and runs the standard verification command. It succeeds whether their environment resolves sbt 1.x or sbt 2.x. Compilation, unit tests, integration tests, formatting checks, and the lint gate all behave identically — same pass/fail verdict, same set of reported violations — regardless of which build-tool major ran them.

**Why this priority**: This is the whole point. Without it, every contributor is forced onto one specific build-tool major, and PR #319 cannot land without breaking the other half of the contributor base. Everything else in this feature depends on the single source tree being loadable by both majors.

**Independent Test**: Check out the branch, run the full verification command twice — once forced onto sbt 1.x, once onto sbt 2.x — and confirm both runs reach the same verdict with no edits to tracked files in between.

**Acceptance Scenarios**:

1. **Given** a clean checkout, **When** the build is loaded with the sbt 1.x major, **Then** the build definition resolves without errors and all declared build capabilities (compile, test, integration test, format check, lint, coverage, benchmark, binary-compatibility report, publish) are available.
2. **Given** a clean checkout, **When** the build is loaded with the sbt 2.x major, **Then** the build definition resolves without errors and every build capability that the project declares as required-on-both is available.
3. **Given** the same clean checkout, **When** the unit and integration test suites are run under sbt 1.x and then under sbt 2.x, **Then** both runs execute the same set of tests and report the same pass/fail outcome.
4. **Given** the same clean checkout, **When** the formatting check and lint gate are run under both majors, **Then** both report the identical set of violations (empty on a clean tree).
5. **Given** a source file with a deliberate lint violation, **When** the lint gate runs under either major, **Then** it fails in both — the gate is not silently inert on one of them.
6. **Given** the compiled artifact produced under sbt 1.x and the one produced under sbt 2.x from the same commit, **When** their published coordinates, dependency metadata, and class content are compared, **Then** they are equivalent — the choice of build tool does not leak into what consumers download.

---

### User Story 2 - sbt 2 breakage is caught early, without taxing every pull request (Priority: P2)

A scheduled job runs the full gate suite under sbt 2 on a regular cadence. If a change breaks the
secondary major, it surfaces within a day, attributed and actionable — but it does not block
unrelated work in the meantime. Pull requests that touch the build definition itself additionally
get the sbt 2 check before merge, because those are the changes that actually break it.

**Why this priority**: Dual support that is never re-verified decays within weeks — the first
contributor who uses a construct available in only one major silently breaks the other. But this
capability is insurance, not a shipping feature: sbt 1 remains the default and the publishing major,
and no consumer is affected by an sbt 2 regression. Continuous per-pull-request verification would
double CI compute permanently to protect something nothing currently depends on. A daily run
catches the same decay at a fraction of the cost, and the build-file path filter puts the check
exactly where breakage originates.

**Independent Test**: Introduce a construct valid under exactly one sbt major, wait for (or manually
trigger) the scheduled run, and confirm it fails with the major named. Separately, open a pull
request touching `project/build.properties` and confirm the sbt 2 check runs on it.

**Acceptance Scenarios**:

1. **Given** the default branch, **When** the scheduled verification runs, **Then** the full gate
   suite executes under sbt 2 and the result is separately identified as an sbt 2 run.
2. **Given** a change that breaks only sbt 2, **When** the scheduled run executes, **Then** it fails,
   names sbt 2, and notifies rather than failing silently.
3. **Given** a pull request that changes a pinned build-tool or plugin version, or any build
   definition file, **When** CI runs, **Then** the sbt 2 verification runs on that pull request.
4. **Given** an sbt 2 failure, **When** a maintainer reads the result, **Then** they can tell which
   gate failed and under which major without opening logs.
5. **Given** a scheduled run that exercises no tests, **When** it completes, **Then** it fails rather
   than reporting success — a vacuous green is treated as a defect, not a pass.

---

### User Story 3 - Downstream example project builds under either sbt major (Priority: P3)

The Scala/sbt example overlay that ships with the project — the one a new user copies to start their own load test — builds and runs its end-to-end Gatling scenario under both sbt majors against the published library artifact.

**Why this priority**: The example overlay is the project's consumer-facing contract and the end-to-end test layer. If it only builds under one major, users on the other major get a broken starting point even though the library itself is fine. It is P3 because the library remaining releasable (P1) and continuously verified (P2) are prerequisites — a consumer-facing example is worthless if the artifact behind it is not.

**Independent Test**: With the library published locally, build and run the example overlay's end-to-end scenario under each sbt major and confirm both complete with passing Gatling assertions.

**Acceptance Scenarios**:

1. **Given** a locally published library artifact, **When** the Scala/sbt example overlay is built under sbt 1.x, **Then** it compiles and its end-to-end scenario runs to completion with passing assertions.
2. **Given** the same artifact, **When** the overlay is built under sbt 2.x, **Then** it compiles and its end-to-end scenario runs to completion with passing assertions.
3. **Given** the overlay's documented setup instructions, **When** a user follows them on either sbt major, **Then** the instructions are accurate for the major they are using — no step is silently major-specific.

---

### Edge Cases

- **A required build capability has no sbt 2 counterpart.** The dependency-hygiene report is provided by a tool with no sbt 2 release published. Its absence must not block the sbt 2 build; it must be recorded as a known, single-major capability with the reason, and its report must keep running on the major that supports it.
- **A capability exists on both majors but behaves differently.** Coverage thresholds, lint rule sets, and strict-compiler diagnostics must produce the same verdict on both. A rule that is enforced on one major and inert on the other is a defect, not an acceptable difference.
- **A contributor has only one sbt major installed.** Local development must remain possible with a single installed major; only CI is required to exercise both. The contributing instructions must state which major a bare local run uses and how to run the other.
- **A pinned plugin version exists for one major but not the other.** The project must not silently diverge to different plugin versions per major without recording it; version skew between majors is a tracked exemption, not a default.
- **A new plugin or build capability is proposed.** The change must state its availability on both majors before it is accepted, so dual support is not eroded one addition at a time.
- **A release is cut.** Publication must be reproducible from either major, and the release process must state which major performs the official publish so the same artifact is produced every time.
- **Cached build state from one major is present when the other runs.** Switching majors must not require manual cache cleanup to get a correct result, and must not produce a stale-cache false pass.
- **The default major changes.** Flipping which major a bare clone uses must be a one-line, reversible change with no other source edits — that is the property that makes PR #319 routine.

## Requirements *(mandatory)*

### Functional Requirements

**Dual-major build**

- **FR-001**: The repository MUST load and build from a single, unmodified source tree under both sbt 1.x and sbt 2.x. Switching majors MUST NOT require editing tracked files beyond the single declared default-version pin.
- **FR-002**: The project MUST declare exactly one sbt major as the default that a bare clone uses, and MUST document how to run the build under the other major.
- **FR-003**: The project MUST maintain an explicit, discoverable list of the sbt majors it supports, including the minimum tested version of each.
- **FR-004**: Changing the default major for a given build MUST be achievable by changing only that build's declared default-version pin, with no other source edits. Note there are **three** such pins in the repository, not one: `project/build.properties` (the library build), `examples/scala-sbt-example/project/build.properties` (the overlay), and the `--set SbtVersion` value rendered into the CI-generated template project by `scripts/test-scala-sbt-template.sh`. "One line" is per build; a repository-wide flip is three coordinated edits.

**Capability parity**

- **FR-005**: Compilation, unit tests, integration tests, formatting check, lint gate, coverage measurement, and artifact publication MUST all be available and functional under both supported majors.
- **FR-006**: Any build capability that is unavailable on one major MUST be recorded as an explicit exemption naming the capability, the unsupported major, and the reason. Undocumented gaps are a defect.
- **FR-007**: Quality gates that are available on both majors MUST reach the same verdict on the same source tree — same pass/fail, same violation set. A gate MUST NOT be inert on one major while enforcing on the other.
- **FR-008**: The published artifact — its coordinates, dependency metadata, and compiled content — MUST be equivalent regardless of which major produced it.
- **FR-009**: The library's own compilation target and language level MUST remain unchanged by this feature; consumers MUST see no difference in what they depend on.

**Continuous verification**

- **FR-010**: The default major MUST be verified by the existing gate suite on every pull request, unchanged from today. The secondary major MUST be verified on a schedule of at least once per day against the default branch.
- **FR-011**: A secondary-major failure MUST be separately identified as such, MUST name the failing gate and major without requiring log inspection, and MUST notify a maintainer. It MUST NOT block pull requests that did not touch the build definition.
- **FR-012**: A pull request that changes `project/build.properties`, `project/plugins.sbt`, `project/Dependencies.scala`, any root `*.sbt` file, or the example overlay's build files MUST additionally run the secondary-major verification before merge — that is where breakage originates.
- **FR-013**: The scheduled secondary-major run MUST exercise the **full** gate suite — compilation, unit tests, integration tests, formatting check, lint gate, coverage, and the binary-compatibility report. A reduced subset is not acceptable: a cheap run that proves little is worse than no run, because it reads as coverage that does not exist.

**Example overlay**

- **FR-014**: The `examples/scala-sbt-example` overlay is in scope for this feature and MUST support both majors alongside the library build.
- **FR-015**: The Scala/sbt example overlay MUST build and run its end-to-end scenario under both supported majors against the published library artifact, and its setup instructions MUST be accurate for both.
- **FR-016**: The Java/Maven and Kotlin/Gradle overlays are unaffected by this feature and MUST continue to build unchanged.

**Governance and disposition**

- **FR-017**: sbt 1.x MUST remain the declared default that a bare clone uses, including for official release publication. sbt 2.x MUST be supported and continuously verified as the secondary major. Promoting sbt 2.x to default is a separate, later decision, enabled by FR-004.
- **FR-018**: PR #319 MUST be closed as superseded by this feature, with a comment stating that sbt 2.x is now supported as a verified secondary major and that sbt 1.x remains the default pin. Dependency automation MUST be configured so the same single-major sbt bump is not re-proposed.
- **FR-019**: Contributor-facing documentation MUST state the supported majors, which one a bare local run uses, and how to run the build under the other.
- **FR-020**: The release process documentation MUST state which major performs the official publication.
- **FR-021**: Adding a new build plugin or build capability MUST state its availability on both supported majors as part of the proposing change.

### Key Entities

- **Supported sbt major**: A build-tool major line the project commits to keeping green (currently: 1.x and 2.x). Attributes: major line, minimum tested version, default-or-secondary role.
- **Build capability**: A named thing the build can do — compile, test, integration test, format check, lint, coverage, benchmark, binary-compatibility report, publish. Attributes: name, availability per supported major, gate-or-report status.
- **Capability exemption**: A recorded, reviewable statement that a specific capability is unavailable on a specific major. Attributes: capability, unsupported major, reason, revisit condition.
- **Verification run**: One execution of the gate suite bound to one supported major. Attributes: major, capability set exercised, verdict.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A contributor can build, test, and gate the project on either supported sbt major from an unmodified checkout, with zero manual file edits required to switch.
- **SC-002**: On identical source, both majors produce the same gate verdict and the same violation set — 100% agreement, measured on a clean tree and on a tree seeded with a deliberate violation of each enforced gate.
- **SC-003**: 100% of the project's declared build capabilities are either available on both supported majors or covered by a recorded exemption; zero undocumented gaps.
- **SC-004**: The secondary major is verified against the default branch at least once per day, each run separately identified; and any pull request touching a build definition file receives a secondary-major result before merge.
- **SC-005**: An artifact published from one major is equivalent to one published from the other at the same commit — identical coordinates and dependency metadata, equivalent compiled content.
- **SC-006**: Changing which major a bare clone uses is a single-line change **per build** (three pins repository-wide — see FR-004), verified by making the change and confirming the full gate suite still passes on both majors. The verification MUST also confirm the documented way back: once the pin names 2.x, `--sbt-version 1.12.15` is a silent no-op while an sbt server is live, so `sbt shutdown` or `SBT_NATIVE_CLIENT=false` is required first.
- **SC-007**: A change that breaks exactly one major is detected and attributed by automated verification within 24 hours, demonstrated at least once against a deliberately introduced break — including proof that a run executing zero tests fails rather than passing.
- **SC-008**: Contributor and release documentation state the supported majors, the default, and how to switch — verified by a reader following the instructions on a major they do not have configured as default.

## Assumptions

- **sbt 1.x remains the default; sbt 2.x is the verified secondary.** Both majors stay supported until explicitly retired. Dual support is not time-boxed in this feature; dropping a major is a separate, explicit decision requiring its own change. Revisit when the sbt 1.x line reaches end of life.
- **The library's own compilation target is out of scope.** This feature changes how the project is built, not what it produces. The Scala language version, the Java release target, and the Gatling dependency scope stay exactly as they are.
- **Plugin availability is broadly favourable.** A survey of the eleven build plugins the project pins found sbt 2 builds published at the same pinned version for ten of them; one — the dependency-hygiene reporter — has no sbt 2 release at all. That reporter is already documented in the build as report-only and never CI-gated, so it is assumed to become a single-major capability under FR-006 rather than a blocker. Exact per-plugin version alignment is a planning-phase concern.
- **The end-to-end Gatling scenario is the overlay's proof.** Overlay verification means the existing end-to-end scenario running to completion with passing assertions, not a new test layer.
- **Local development stays single-major.** Contributors are not expected to install both majors; only automated verification must exercise both.
- **CI cost rises by roughly one extra full run per day, not by 2x per pull request.** The secondary major is verified on a schedule plus on build-definition pull requests (FR-010, FR-012), not on every change. This reflects the standing decision (2026-08-20) that dual-major support is **insurance against a future forced migration**, not a capability anything currently depends on: sbt 1 stays the default and the publishing major, and no consumer is affected by an sbt 2 regression. Should the project decide to actually migrate, promoting the scheduled run to a per-pull-request matrix is a workflow change, not a rework of this feature.
- **Milestone**: the active milestone, **#13 `v1.26.0 — Perf: Templates & cookies`** (maintainer decision 2026-08-20). AGENTS.md defines the active milestone as the lowest-numbered open one; re-check at PR time in case it closes first.
