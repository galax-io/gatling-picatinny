# Quickstart: Validating Coverage & Test Infra Hardening (008)

Runnable end-to-end validation scenarios. Commands and failure semantics are normative in [contracts/build-gates.md](contracts/build-gates.md); entities in [data-model.md](data-model.md).

## Prerequisites

- JDK 17+ (CI parity: Temurin 21), sbt 1.12.x
- Docker running (Redis/Postgres/Vault Testcontainers for the `it` scenarios)
- Network access to Maven Central (MiMa baseline resolution)

## V1. Honest coverage (US1 / FR-001, FR-002, FR-022)

```bash
sbt clean coverage test "IntegrationTest / test" coverageReport
```

**Expected**: report contains NO `*Benchmark*` or `jmh` sources; build passes at the ratcheted floors. Then open `target/scala-2.13/scoverage-report/index.html` and search for "Benchmark" — zero hits.

**Negative probe**: temporarily raise `coverageMinimumStmtTotal` above the measured value → same command fails with the scoverage minimum error. Revert.

## V2. Lint gate (US5 / FR-016, FR-017)

```bash
sbt "scalafixAll --check"          # zero findings
```

**Seeded violation**: add `if (x == null)` and an unused import to any spec → check fails naming rule/file/line. Fix flow: `sbt scalafixAll scalafmtAll` removes the unused import; run both again → zero diff (convergence). Revert the null seed manually (DisableSyntax reports, does not rewrite).

## V3. Binary compatibility — advisory (US6 / FR-018)

```bash
sbt mimaFindBinaryIssues           # reports clean vs org.galaxio %% gatling-picatinny % 1.23.0, always exits green
```

**Seeded violation**: comment out any public method in `src/main` → command PRINTS the missing-method problem but still succeeds (assert both: warning text present, exit code 0). In CI the same finding appears as a `::warning::` annotation on the PR while the workflow stays green. Revert.

## V4. Strict diagnostics (US7 / FR-019)

```bash
sbt compile Test/compile IntegrationTest/compile
```

**Seeded violation**: add a non-exhaustive match over a sealed trait → compilation fails (`-Werror`). Add `@nowarn("cat=other-match-analysis")` at that site → compiles (escape hatch is per-site). Revert.

## V5. Test-fix verification (US2, US4 / FR-004..FR-008, FR-011..FR-014)

```bash
sbt "testOnly org.galaxio.gatling.templates.TemplatesSpec org.galaxio.gatling.feeders.*"   # de-tautologized suites green
for i in $(seq 20); do sbt "testOnly org.galaxio.gatling.transactions.TransactionsSpec" || break; done  # SC-006 flake probe
sbt "IntegrationTest / testOnly *RedisIntegrationSpec *THttpClientTransportSpec *JdbcStorageIntegrationSpec"
```

**Expected**: all green; 20/20 transaction runs pass; Redis IT failures (if forced) show typed messages, not ClassCastException.

**Mutation probes (tautology check, SC-003)**: temporarily break production template discovery (e.g. change the resource directory name it scans) → `TemplatesSpec` fails; corrupt a check-digit constant in a known-valid NIF sample → feeder validity test fails. Revert both.

**Property shrink (FR-014)**: temporarily invert one property assertion → failure output shows a shrunk minimal counter-example. Revert.

## V6. Template pipeline e2e (US3 / FR-010)

```bash
sbt "testOnly org.galaxio.gatling.templates.*"
```

**Expected**: render test asserts exact output from a real resource template via the production path; missing-variable case yields the documented failure outcome.

## V7. Dependency hygiene + automation (US8 / FR-020)

```bash
sbt undeclaredCompileDependencies unusedCompileDependencies   # report-only, zero findings expected
```

**Automation**: after merge, trigger `scala-steward.yml` manually (workflow_dispatch) once → any opened PR carries the "maintenance" milestone.

## V8. Full regression chain (FR-015)

```bash
sbt scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" compile mimaFindBinaryIssues test "IntegrationTest / test"
```

**Expected**: green end-to-end; wall-clock delta vs pre-feature baseline ≤ ~25% (record once in PR description).

## V9. Docs (FR-003, FR-021)

Open `TESTING.md` (normative gates doc) → ratchet policy + floor values + date + "Static analysis & gates" section present; `AGENTS.md` Commands block mirrors every command used above, and its Release Process carries the MiMa baseline-bump / warning-review / hygiene-report entries. Execute each documented command verbatim once — all succeed.

Mechanical SC-007 sweep (zero hits expected, bounded probes and justified suppressions excepted):

```bash
grep -rn "/tmp" src/test src/it; grep -rn "Thread.sleep" src/test src/it; grep -rn "asInstanceOf" src/test src/it
```
