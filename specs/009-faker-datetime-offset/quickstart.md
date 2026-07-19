# Quickstart Validation: Discrete Date-Time Offset Generation (009)

Prerequisites: JDK 17+, sbt. All commands from the repository root. Contract details: [contracts/faker-date-api.md](contracts/faker-date-api.md); entity rules: [data-model.md](data-model.md).

## V1. Core generator behavior (FR-001..005, 007, US1)

```bash
sbt "testOnly org.galaxio.gatling.feeders.faker.GeneratedFeederSpec"
```

**Expected**: new datetime-offset cases green — whole-unit grid & inclusive bounds (incl. a 0..1-day both-endpoints check), zero-spanning range, deterministic equal bounds, formatted composition, and the two rejection cases (inverted bounds, unsupported unit); pre-existing `between`/`offset(LocalDate)` cases untouched and green (FR-005 control).

## V2. Facade delegation (FR-006, US2)

```bash
sbt "testOnly org.galaxio.gatling.javaapi.JavaFeedersTest"
```

**Expected**: Java-side `dateOffset` usage (with and without explicit unit) produces in-bounds grid values; inverted bounds surface the same error.

## V3. Doc-parity migration mapping (FR-009, US3)

```bash
sbt "testOnly org.galaxio.gatling.feeders.faker.GeneratedFeederSpec"   # doc-parity case included in V1 run
```

**Expected**: the documented replacement for legacy `(P=30, N=0, days, "yyyy-MM-dd")` yields only base+0..29-day formatted values; the naive upper bound (base+30) is asserted absent. `docs/faker-api.md` example and `docs/migration.md` mapping table match the tested expression verbatim.

## V4. Compatibility gate (FR-008)

```bash
sbt "mimaReportBinaryIssues || true"
```

**Expected**: zero incompatibilities vs the 1.24.0 baseline (additive-only change); no new `mimaBinaryIssueFilters` entries.

## V5. Full verification chain (release-readiness)

```bash
sbt scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" compile test "IntegrationTest / test"
```

**Expected**: formatting, lint, strict diagnostics (`-Werror`), unit + integration suites all green; coverage floor 75/66 holds.
