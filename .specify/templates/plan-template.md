# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]

**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]

**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]

**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]

**Project Type**: [e.g., library/cli/web-service/mobile-app/compiler/desktop-app or NEEDS CLARIFICATION]

**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]

**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]

**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Test Model *(mandatory — real cases + test sketches, NO implementation)*

<!--
  GATE (Constitution III / FR-005/006): one row per functional requirement. This is
  filled BEFORE implementation — think of the real case and the test before the code.
  - `Layer` MUST be one of the six model layers in `TESTING.md`: Unit/Functional |
    DSL/Action Component (conditional) | External Integration (Testcontainers /
    non-container `it`) | Full Gatling e2e (examples) | Compile Guard | Facade
    Delegation. Pick the layer(s) that FIT — they are not all mandatory per change;
    never invent a component test where none is needed.
  - `Test sketch` is PROSE ONLY — no code, no language syntax, no class/method bodies.
    Each sketch names ≥1 negative/boundary or exact-value assertion. HTTP-emitting code
    is unit-tested with ScalaMock (assert the value returned + the request issued); the
    full HTTP wire is the e2e layer (WireMock in the overlay), where the sketch states
    what `WireMock.verify` checks.
  The planning checklist FAILS if this section is missing, empty, names no real case,
  has an FR without a row, uses an invalid layer, or contains code.
-->

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-xxx | [concrete real-world case] | [one of the 6 model layers] | [prose: what is asserted, incl. the negative/boundary case; for HTTP units what the mock expectation checks, for e2e what `WireMock.verify` checks] |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [ ] **I. Scala DSL as Source of Truth** — Java/Kotlin facade changes delegate to Scala core; no logic duplication in facade layer.
- [ ] **II. Backward Compatibility** — No public API, DSL behavior, or serialized config/profile format broken without MAJOR bump and explicit authorization.
- [ ] **III. Test Discipline** — The Test Model section above is filled (real case + layer + code-free sketch per FR); work is test-first; layer choices follow `TESTING.md` (Testcontainers only for container-backed Redis/Vault/JDBC; JWT/diagnostics are non-container `it`; feeder-determinism/transaction-boundary are DSL-component; HTTP units use ScalaMock, e2e uses WireMock in the overlay); Gatling runtime not mocked where a real path exists; coverage ≥ the enforced floor (65%/60%).
- [ ] **IV. Small, Focused Changes** — No opportunistic refactors; new deps / API signature / config format changes explicitly authorized; complexity justified in table below if principle bent.
- [ ] **V. Release Integrity** *(release PRs only)* — Correct branch strategy; tag placement; no version reuse.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
