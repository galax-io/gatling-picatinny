# Implementation Plan: Coverage & Test Infra Hardening

**Branch**: `008-coverage-test-infra` | **Date**: 2026-07-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-coverage-test-infra/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Milestone v1.24.0: make the quality gates of this published library honest and mechanized. Four work streams: (1) coverage truth — exclude benchmark sources from the scoverage denominator and ratchet the floor to the re-measured honest value, documenting the ratchet policy; (2) tests that can fail — replace tautological tests (template discovery, NIF/NIR/PSRNSP check digits, Cyrillic smoke patterns) with independently-verified assertions and cover the untested failure branches of `RedisAction` and `THttpClient`; (3) close the integration gap — end-to-end template-pipeline test (JDBC Postgres IT already exists and conforms); (4) static-analysis guardrails — Scalafix lint gate, MiMa binary-compatibility check in advisory (warning-only) mode, curated `-Werror` compiler diagnostics on all scopes, Scala Steward dependency automation with active-milestone auto-assignment (amended 2026-07-19; originally a standing "maintenance" milestone), and a report-only dependency-hygiene task. Technical decisions and verified tool versions in [research.md](research.md).

## Technical Context

**Language/Version**: Scala 2.13.18, sbt 1.12.13, Java 17 compile target (`--release 17`), CI on Temurin 21

**Primary Dependencies**: Gatling 3.13.5 (`Provided`); existing test stack ScalaTest + ScalaCheck (scalatestplus) + JUnit 5 + Testcontainers 0.44.1 + Postgres driver 42.7.11 (`it`). New build-time only: sbt-scalafix 0.14.7, sbt-mima-plugin 1.1.6, sbt-explicit-dependencies 0.3.1, scala-steward-action v2 (GitHub Actions). Zero new runtime dependencies.

**Storage**: PostgreSQL 17 (Testcontainers, existing `JdbcStorageIntegrationSpec`), Redis container (existing IT); JSON file backend tests move to per-run temp dirs

**Testing**: six-layer model per `TESTING.md`; this feature touches Unit/Functional, DSL/Action Component, External Integration (container + non-container `it`), Full Gatling e2e (examples smoke), Compile Guard

**Target Platform**: JVM library published to Maven Central (`org.galaxio %% gatling-picatinny`); consumers on JDK 17+

**Project Type**: single sbt library project + `examples/` overlay projects (overlays NOT gated — clarification Q5)

**Performance Goals**: verification pipeline wall-clock increase ≤ ~25% after SemanticDB + scalafix + MiMa (measured once at implementation, recorded in PR)

**Constraints**: Gatling stays `Provided`; `release.yml` untouched; no runtime deps added; `-Werror` + lint on all library compile scopes (Compile/Test/IntegrationTest); every gate lands green (zero findings before enforcement); floors never decrease from 65/60

**Scale/Scope**: 22 FRs; 8 existing open issues (#80, #81, #108, #109, #110, #121, #210, #211) + 4 new issues to file into milestone 10; ~10 test files touched, 2 production files gain failure-path coverage (no behavior change), `build.sbt`/`project/plugins.sbt`/2 workflows/2 docs

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

Build-configuration and governance FRs (coverage, lint, MiMa, `-Werror`, docs) are validated at the build level; their rows use the Compile Guard layer (build-time verification via seeded violations per [quickstart.md](quickstart.md)) since no runtime test layer applies.

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-001 | Benchmark sources in coverage denominator | Compile Guard | Generate coverage report; assert no `*Benchmark*`/`jmh` file appears in it. Negative/boundary: with exclusion patterns removed, the same report DOES list benchmark files (proves the exclusion, not luck, removes them). |
| FR-002 | Floor enforcement after re-measure | Compile Guard | Run full verify with new floors: passes at measured value. Negative: floor temporarily raised above measured → build fails with the scoverage minimum-coverage error naming stmt/branch percentages. |
| FR-003 | Contributor looks up ratchet policy | Compile Guard | `TESTING.md` contains ratchet rule (floor tracks measured upward, never padded), current floor values, measurement date. Negative: policy explicitly forbids floor decrease — a PR lowering the floor cites no authorized exception → rejected in review per documented rule. |
| FR-004 | Template discovery breaks silently today | Unit/Functional | Drive the production `Templates` discovery (no local re-implementation); assert the exact discovered set contains `test_json` and `test_xml` with no extension in names. Negative: missing/nonexistent templates directory yields the documented empty/absent result. Mutation check: breaking production discovery turns the test red. |
| FR-005 | NIF/NIR/PSRNSP check-digit validity | Unit/Functional | Feed fixed externally-verified valid document numbers → validator accepts each; corrupt one check digit per sample → validator rejects (negative). No production formula reuse in the test. |
| FR-006 | Cyrillic feeder output asserted loosely (`.+` / length-only) | Unit/Functional + Facade Delegation | Scala feeder spec (`feeders/faker/GeneratedFeederSpec.scala:402-405`) asserts `[Ѐ-ӿ]{10}` content+length instead of length-only; Java facade smoke (`javaapi/JavaApiExampleSmokeTest.java:255`, JUnit 5) replaces `.+` with `[Ѐ-ӿ]{6}`. Negative: a Latin sample string demonstrably fails each pattern (pattern pinned by test data, not `.*`). |
| FR-007 | Redis operation fails mid-scenario | DSL/Action Component | Mocks/ActorSystem harness + stubbed always-failing Redis client: assert exact KO status recorded in stats engine, error message propagated, session marked failed, and `next` action still invoked (no hang). Negative/boundary: success path still records OK with same harness. |
| FR-008 | HTTP feeder endpoint unreachable/slow/misconfigured TLS | External Integration (non-container `it`) | Loopback-only scenarios: closed ephemeral port → connection-refused error surfaced with cause; accepting-but-silent socket → timeout error at configured bound; plaintext socket answering HTTPS → TLS handshake failure; JDK `HttpServer` 302→200 → BOTH redirect modes pinned (`Redirect.NEVER` — the client default — returns the 302 itself; follow mode returns exact final body). Each asserts exact error type/outcome, never a generic failure. |
| FR-009 | JDBC session storage round-trip | External Integration (Testcontainers) | Existing `JdbcStorageIntegrationSpec` against real Postgres 17: save records → load returns exact values in insertion order; `clear()` empties; boundary: fresh table loads empty. Verify conformance, keep green. |
| FR-010 | Template rendered end-to-end | Unit/Functional | Real resource template through the production template path, resolved against a real Gatling session (test-bootstrap config, real code path): assert exact rendered output byte-for-byte. Negative: template referencing a missing session variable produces the documented failure outcome, not silent empty output. |
| FR-011 | Parallel test runs collide on /tmp | Unit/Functional | Storage backend specs create per-run temp directories; two concurrently-created backends write/read without interference; cleanup verified after run. Negative/boundary: nonexistent-file case uses a path inside a fresh temp dir (never a fixed absolute path) and still asserts the documented empty/error behavior. |
| FR-012 | Slow CI runner delays actor completion | DSL/Action Component | Transactions suite awaits a deterministic latch fired by the terminal action (already on `main`); assert exact recorded transaction events after latch. Negative/boundary: latch not fired within bound → test fails with timeout diagnostic, never hangs or false-passes. Verified by 20 consecutive suite runs (SC-006). |
| FR-013 | Redis IT gets unexpected result shape | External Integration (Testcontainers) | Typed extraction helper replaces all 5 `asInstanceOf` sites: on shape mismatch the test fails with message naming expected vs. actual runtime type. Positive: exact TTL/list/set values asserted as before. Negative: deliberately extracting wrong type produces the descriptive message, not ClassCastException. |
| FR-014 | Noisy counter-example on property failure | Unit/Functional | Property suites run under explicit check configuration (min successful, discard bound). Negative: a deliberately-failing property reports a shrunk minimal counter-example (e.g. boundary length/empty), not a long random sample; assertion inspects the reported counter-example form. |
| FR-015 | Regression-free delivery | Compile Guard | Full chain `sbt scalafmtCheckAll scalafmtSbtCheck compile test "IntegrationTest / test"` green; MiMa (FR-018) confirms zero public-API deltas vs 1.23.0; examples smoke unchanged. Negative: any suite failure blocks the PR. |
| FR-016 | Idiom violation enters codebase | Compile Guard | `scalafixAll --check` passes with zero findings on the branch. Negative: seeded `== null` comparison (and one unused import) in a scratch commit → check fails naming rule, file, line for each scope (main/test/it). |
| FR-017 | Contributor fixes lint findings locally | Compile Guard | Seed auto-fixable violations; run documented fix command (`sbt scalafixAll scalafmtAll`); findings gone; running fix+format a second time produces zero diff (idempotence/convergence with formatter). |
| FR-018 | Accidental public API removal | Compile Guard | Advisory MiMa check reports clean vs `org.galaxio %% gatling-picatinny % 1.23.0`. Negative: scratch removal of a public method → CI emits a visible warning naming the incompatibility while the build still succeeds (warning present AND exit green are both asserted). Acknowledgement path: filter entry with justification + version-bump rule; release checklist reviews outstanding warnings (documented). |
| FR-019 | Defect-class warnings ignored | Compile Guard | Clean compile under curated `-Xlint`/`-Wunused`/`-Wdead-code` + `-Werror` on all scopes. Negative: seeded non-exhaustive match on a sealed trait fails compilation naming the diagnostic; per-site `@nowarn` with justification compiles (escape hatch verified narrow). |
| FR-020 | Dependencies drift stale/undeclared | Compile Guard | Steward workflow + config present; first scheduled run opens PRs auto-assigned to the current active milestone (amended 2026-07-19; verified on live run). Report tasks run on demand: zero undeclared, zero unused compile deps at feature completion. Negative: scratch-added unused library dependency is flagged by the report. |
| FR-021 | Contributor can't operate the gates | Compile Guard | Docs (TESTING.md + AGENTS.md) list every gate with local command, fix flow, escape hatch; each documented command executed once verbatim during validation and succeeds. Negative: no gate exists without a documented local invocation. |
| FR-022 | Gates disagree on excluded code | Compile Guard | Single benchmark-exclusion definition in the build drives coverage AND lint/diagnostic exclusions: a benchmark file is absent from the coverage report and a seeded lint violation inside it does not fail the lint check (proving shared exclusion), while the same violation in production code does (negative control). |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Scala DSL as Source of Truth** — No facade production changes. Only the Java facade smoke TEST (`src/test/java/.../javaapi/JavaApiExampleSmokeTest.java`) tightens its Cyrillic assertion, mirroring the Scala feeder-spec tightening (both test-only).
- [x] **II. Backward Compatibility** — Zero public API/DSL/format changes; production files touched only for failure-path *coverage* (no behavior change), surfaced by the new advisory MiMa check against v1.23.0 (clean report expected). Advisory mode means principle II enforcement stays with review + release checklist; tooling adds visibility, not a blocker. New deps are build-time only; Gatling stays `Provided`.
- [x] **III. Test Discipline** — Test Model above: one row per FR, real case + layer + code-free sketch, ≥1 negative/boundary each. Layer choices follow TESTING.md: JDBC via existing Testcontainers IT; Redis failure branches at DSL/Action Component (Mocks harness); template render via real code path (no mocked Gatling runtime); THttpClient transport at non-container `it` (justified in Complexity Tracking); tautological tests replaced test-first (red via mutation check → green). Coverage floor ratchets up, never padded.
- [x] **IV. Small, Focused Changes** — New build deps explicitly authorized (maintainer follow-up request + clarification session, recorded in spec). No opportunistic refactors: source edits limited to gate remediation (unused imports, `@nowarn`/`scalafix:ok` with justification) and issue-scoped test fixes. Complexity bends justified below.
- [x] **V. Release Integrity** — Not a release PR. Release checklist gains two entries (bump MiMa baseline; run dependency-hygiene report) — process docs only, `release.yml` untouched.

## Project Structure

### Documentation (this feature)

```text
specs/008-coverage-test-infra/
├── plan.md              # This file
├── research.md          # Phase 0 — decisions R1–R9, verified versions
├── data-model.md        # Phase 1 — gate/config entities
├── quickstart.md        # Phase 1 — seeded-violation validation guide
├── contracts/
│   └── build-gates.md   # Phase 1 — contributor-facing gate contract
├── baseline.md          # created by T004 — pre-feature coverage % + wall-clock (rides in the coverage PR)
└── tasks.md             # Phase 2 (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
build.sbt                                  # coverage exclusions + re-ratcheted floors; scalac flags (-Xlint/-Wunused/-Werror);
                                           # shared benchmark-exclusion val; MiMa baseline; scalafix source filters
project/plugins.sbt                        # + sbt-scalafix 0.14.7, sbt-mima-plugin 1.1.6, sbt-explicit-dependencies 0.3.1
.scalafix.conf                             # NEW — rule config (DisableSyntax, RemoveUnused, OrganizeImports)
.scala-steward.conf                        # NEW — update policy
.github/workflows/
├── ci.yml                                 # + scalafix --check step, + advisory MiMa step (continue-on-error)
└── scala-steward.yml                      # EXISTS (weekly cron + dispatch, action@v2) — extend with active-milestone post-step (amended 2026-07-19)
.scala-steward.conf                        # NEW — update policy
src/main/scala/org/galaxio/gatling/
├── redis/RedisAction.scala                # unchanged behavior; failure branches gain coverage (tests only)
└── utils/THttpClient.scala                # unchanged behavior; transport failures gain coverage (tests only)
src/test/scala/org/galaxio/gatling/
├── templates/TemplatesSpec.scala          # de-tautologize (drive production discovery) + pipeline render test (FR-010)
├── feeders/RandomFeedersSpec.scala        # known-sample check digits (~L403–413) + explicit property-check config
├── feeders/faker/GeneratedFeederSpec.scala # known-sample check digits (NIR/NINO ~L872–887, NIF ~L897) + Cyrillic [Ѐ-ӿ]{10} (~L402–405)
├── redis/RedisActionSpec.scala            # EXISTS — extend with failure-branch component tests (FR-007)
├── storage/StorageBackendSpec.scala       # temp-dir isolation (drop /tmp)
└── transactions/TransactionsSpec.scala    # verify latch determinism (#109 — already fixed; evidence + close)
src/test/java/org/galaxio/gatling/javaapi/
└── JavaApiExampleSmokeTest.java           # Cyrillic pattern `.+` → [Ѐ-ӿ]{6} (~L255; facade test, JUnit 5)
src/it/scala/org/galaxio/gatling/
├── redis/RedisIntegrationSpec.scala       # typed extraction replaces 5 asInstanceOf sites
├── storage/JdbcStorageIntegrationSpec.scala # exists & conforms — verify green (FR-009)
└── utils/THttpClientTransportSpec.scala   # NEW — loopback transport failures + BOTH redirect modes (NEVER default / follow)
TESTING.md                                 # ratchet policy + "Static analysis & gates" section
AGENTS.md                                  # Commands block gains gate commands
```

**Structure Decision**: single-project sbt library layout is unchanged; the feature adds build/CI configuration, two dot-config files, one new `it` spec, and edits confined to the test files named by the issues. `examples/` overlays are untouched entirely (clarification Q5: overlays not gated; the "Cyrillic smoke checks" turned out to live in the library's own test tree, not the overlays — analysis finding I1).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| THttpClient transport tests use real loopback sockets in non-container `it` (constitution III says HTTP-emitting code is unit-tested with ScalaMock, no server in the library) | Connection-refused, timeout, and TLS-handshake failures originate below the mockable HTTP-collaborator seam — a ScalaMock stub cannot produce them, only simulate them | Stubbing the client to *throw* transport exceptions asserts the stub, not the client (mock-testing-mock, forbidden); constitution's non-container `it` sub-class explicitly permits real-state tests without containers; loopback-only, no external network, JDK-built-in server, zero new deps |
| Three new build-time dependencies (scalafix, MiMa, explicit-dependencies plugins) + one GitHub Action (constitution IV: new deps need explicit authorization) | The four static-analysis gates are the feature (US5–US8) | Authorization granted: direct maintainer request + clarification session recorded in spec Assumptions; all build-time only, zero runtime footprint (constitution II unaffected) |
| `SeparatedValuesFeeder.apply(Seq[String], ...)` / `apply(Seq[Map], ...)` lose a dead, unused `implicit GatlingConfiguration` parameter — a genuine public-API removal — but ships in the milestone's **1.24.0 (MINOR)**, not the MAJOR release constitution II otherwise mandates for any removal | Code-review discussion during #276/#275 remediation surfaced the dead param (inconsistent with the third `apply(String, ...)` overload, which never had it); milestone is titled and tagged v1.24.0, and holding the whole coverage/test-infra milestone for one removed dead parameter was judged not worth a MAJOR/2.0.0 release cycle | Splitting the removal into its own MAJOR-targeted PR was offered and explicitly declined by the maintainer (2026-07-05) in favor of shipping as-is in 1.24.0; real-world impact assessed as near-zero — Gatling resolves the param from ambient `Predef.configuration` at every call site, so no known caller ever passed it explicitly and no source change is needed downstream, only the binary erasure changes (`mimaBinaryIssueFilters` entry in `build.sbt` documents the accepted MiMa finding) |
