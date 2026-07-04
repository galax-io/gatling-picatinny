# Contract: Build Gates (contributor-facing)

The feature's external interface is the set of build commands and their failure semantics. Public library API is contractually UNCHANGED (enforced by the binary-compatibility gate itself).

## Gate commands

| Gate | Local check | Local fix | Failure semantics | Escape hatch |
|------|------------|-----------|-------------------|--------------|
| Format (existing) | `sbt scalafmtCheckAll scalafmtSbtCheck` | `sbt scalafmtAll scalafmtSbt` | CI fails listing files | none |
| Lint | `sbt "scalafixAll --check"` | `sbt scalafixAll scalafmtAll` (fix, then format) | Fails naming rule + file + line | `// scalafix:ok <Rule>` + justification comment |
| Compiler diagnostics | `sbt compile Test/compile IntegrationTest/compile` (`-Werror` always on) | fix the code | Compile error naming diagnostic + site | `@nowarn("cat=…")` per-site + justification comment |
| Coverage | `sbt coverage test "IntegrationTest / test" coverageReport` | add real tests (padding forbidden) | Build fails below stmt/branch floor | none — floor changes require authorized ratchet doc update |
| Binary compatibility (advisory) | `sbt mimaReportBinaryIssues \|\| true` | restore API, or acknowledge intentional break | NEVER blocks: the `\|\| true` absorbs `mimaReportBinaryIssues`'s non-zero exit while keeping its human-readable `[error] * method …` output (its sibling `mimaFindBinaryIssues` is a silent internal task — returns problems as a value without printing, do not run it directly); CI runs `mimaReportBinaryIssues` under `continue-on-error: true` and re-emits findings as `::warning::` annotations vs baseline — workflow stays green; warnings reviewed at release | `mimaBinaryIssueFilters` entry + justification + version bump (constitution II) — keeps warning stream clean |
| Dependency hygiene (report-only) | `sbt undeclaredCompileDependencies unusedCompileDependencies` | adjust `project/Dependencies.scala` | Prints findings; NEVER fails CI | n/a — manual triage, run pre-release |

## Pipeline order (verification workflow)

format check → lint check → compile (with `-Werror`) → MiMa (advisory, `continue-on-error`) → unit tests → coverage floor → (separate job) integration tests.

Fail-fast: earlier gate failure skips later stages — except the MiMa step, which annotates warnings and never blocks.

## Invariants

1. Every gate is runnable locally with one documented command (FR-021). The single normative "one place" (SC-013) is `TESTING.md`'s gates section (seeded from the table above); `AGENTS.md` Commands is a mirror. Release-checklist entries (MiMa baseline bump, warning review, dependency-hygiene run) live in `AGENTS.md` Release Process — where releases are executed from — referenced from `TESTING.md`.
2. A gate is introduced only at zero findings — no red-at-birth, no blanket exclusions (spec edge case).
3. Shared exclusion: benchmark sources are invisible to coverage, lint, and diagnostics alike (FR-022).
4. Bot (Scala Steward) PRs always carry the "maintenance" milestone; human PRs carry a real milestone + linked issue (clarification Q2).
5. `release.yml` and the publish path are outside this contract and unchanged.
