# Data Model: Coverage & Test Infra Hardening (008)

No runtime/domain data changes — this feature's "entities" are build-time configuration objects and governance records. Serialized config/profile formats of the library are untouched (constitution II).

## Entities

### Coverage Gate
- **Fields**: statement floor (int %), branch floor (int %), excluded-files pattern, excluded-packages pattern, fail-on-minimum flag, measurement date.
- **Rules**: floors data-driven — set just under measured value (≤5 points slack, SC-002); never decrease below 65/60; exclusions derive from the shared Benchmark Exclusion Set. Measurement date + values recorded in TESTING.md on every ratchet.
- **State transitions**: `measured → ratcheted → documented`; re-entered on every future ratchet.

### Benchmark Exclusion Set (shared, FR-022)
- **Fields**: one build-level definition of benchmark sources (`*Benchmark*` files + `org.galaxio.gatling.jmh` package).
- **Relationships**: consumed by Coverage Gate (scoverage patterns), Lint Gate (scalafix source filter), Diagnostics Gate (source filter). Single source of truth — divergence between consumers is a defect.

### Lint Gate
- **Fields**: rule set (DisableSyntax options, RemoveUnused, OrganizeImports), covered scopes (Compile, Test, IntegrationTest), check command, fix command, per-site suppression syntax + justification requirement.
- **Rules**: zero findings at introduction; suppressions carry a justification comment; overlays excluded (clarification Q5).

### Binary-Compatibility Check (advisory — clarification 2026-07-04)
- **Fields**: baseline artifact (`org.galaxio %% gatling-picatinny % <latest release>`), issue filters (each: filter expression, justification, linked version bump), CI warning annotations.
- **Rules**: NEVER fails the build — findings surface as CI warnings and local report output; reviewing outstanding warnings is a mandatory release-checklist step; baseline bumped by release checklist each release; filters are per-change acknowledgements (never wildcard) keeping the warning stream clean; direction = backward (consumer protection).
- **State transitions**: baseline `1.23.0 → 1.24.0` at next release; finding `warned → acknowledged (filter+bump) | fixed`.

### Diagnostics Gate
- **Fields**: curated flag set (`-Xlint:_` minus documented exclusions, `-Wunused:…`, `-Wdead-code`), escalation flag (`-Werror`, always on), per-site suppression (`@nowarn("cat=…")` + justification).
- **Rules**: category-level downgrades forbidden; scope = all library compile scopes.

### Dependency Hygiene Report (report-only, clarification Q4)
- **Fields**: undeclared-deps task, unused-deps task, run cadence (manual, pre-release), last-run findings count.
- **Rules**: not CI-gated; zero findings at feature completion; findings triaged manually.

### Update Automation
- **Fields**: bot identity (Scala Steward), schedule (weekly), config file, milestone assignment (current active — lowest-numbered open; amended 2026-07-19).
- **Relationships**: every bot PR → active milestone (satisfies linkage rule, clarification Q2 as amended 2026-07-19).

### Milestone Ledger
- **Fields**: milestone 10 (v1.24.0) ← existing issues #80 #81 #108 #109 #110 #121 #210 #211 + 4 new gate issues; bot PRs ← current active milestone (standing "maintenance" milestone retired 2026-07-19).
- **Rules**: 1 issue = 1 commit; every PR carries a milestone before merge.

### Test-Fix Records (per-issue, no schema — tracked as issues)
- #108 temp-dir isolation; #109 latch determinism (already fixed — verify + close); #110 typed extraction; #121 property-check config; #211 five de-tautologization/failure-path items; #81 template-pipeline render test (JDBC IT already exists).
