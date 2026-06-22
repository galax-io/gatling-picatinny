# Specification Quality Checklist: Assertions Correctness (NFR YAML → Gatling assertions)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-06-22
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **Resolved**: FR-003 unknown-key handling → **warn + skip only** (backward-compatible,
  no new config, existing `AssertionsBuilderSpec`/`nfr.yml` stay green). No
  [NEEDS CLARIFICATION] markers remain.
- "No implementation details": the spec names the public entry point `assertionFromYaml`
  and the facade exception type `AssertionBuilderException` for traceability to the
  milestone issues. These are the user-facing API surface (compatibility-sensitive per
  constitution II), not internal implementation — acceptable. Recognized-metric semantics
  (`all`→global, `lt` threshold) describe existing observable behavior, not new
  implementation. Item passes.
- Issue traceability: US1/FR-001 ← #202 exponential-dup; US2/FR-003 ← #71; US2/FR-004 ←
  #72; US3/FR-005 ← #202 null-super; US3/FR-006 ← #202 toUtf; US4/FR-007 ← #89.
- **Version**: target is **v1.18.0** (next available minor) — `v1.17.0`/`v1.17.1` are
  already published (the milestone title is stale); no version reuse (constitution V).
- **Deprecation** (US5/FR-012/SC-010): the NFR-YAML assertions feature
  (`assertionFromYaml`, Scala + Java) is deprecated in v1.18.0 with a "replacement
  coming" message; additive/backward-compatible, removal deferred to a future major.
- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`.
