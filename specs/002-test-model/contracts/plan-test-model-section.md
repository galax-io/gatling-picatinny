# Contract: speckit "Test Model" planning gate (FR-005/006)

> Defines what gets added to `.specify/templates/plan-template.md`,
> `.specify/templates/spec-template.md`, and the spec-quality checklist so every
> future feature mechanically articulates real cases + code-free test sketches.

## Template addition (plan-template.md, after Technical Context)

A mandatory section:

```markdown
## Test Model *(mandatory — real cases + test sketches, NO implementation)*

| Req | Real case to test | Layer | Test sketch (no code) |
|-----|-------------------|-------|-----------------------|
| FR-xxx | <concrete real-world case> | <one of the 6 model layers> | <prose: what is asserted, incl. the negative/boundary case; for HTTP, what `verify` checks> |
```

Rules embedded as guidance text:
- One row per functional requirement (group only if genuinely identical).
- `Layer` MUST be one of the model layers (see TESTING.md / test-model contract).
  Pick the layer that fits the change — layers are NOT all mandatory per change.
  The DSL-component layer applies only when a DSL/action component with runtime
  behavior is involved; a requirement with no fitting layer may state the closest
  real layer (e.g. pure unit) — never invent a component test where none is needed.
- `Test sketch` describes assertions in prose ONLY — no code, no language syntax,
  no class/method bodies.

## spec-template.md addition

A short hook under "User Scenarios & Testing" reminding the author that each
acceptance scenario must name the real case and intended layer, to be expanded
into the plan's Test Model table.

## Checklist gate items (added to the requirements checklist)

```markdown
## Test Model Gate
- [ ] A "Test Model" section exists in the plan
- [ ] Every functional requirement has a row (real case + layer + sketch)
- [ ] Each `Layer` value is one of the defined model layers
- [ ] No test sketch contains implementation/code (no code fences/syntax)
- [ ] Each sketch names at least one negative/boundary or exact-value assertion
- [ ] HTTP-touching rows specify a `verify` of the received request
```

## Pass/Fail semantics

- **FAIL** (blocks `/speckit-tasks` and `/speckit-implement`) if: section missing;
  any FR lacks a row; a `Layer` is invalid; any sketch contains code; any sketch is
  empty or names no real case.
- **PASS** when all rows are present, layer-valid, code-free, and assertion-bearing.

## Acceptance (how this contract is itself tested)

- A fixture plan with an empty/code-containing Test Model section → checklist
  evaluation reports FAIL.
- A fixture plan with a complete table → reports PASS.
- This plan's own Test Model section (see plan.md) serves as the positive example.
