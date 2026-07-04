# The gatling-picatinny Test Model

> The single authoritative description of how this project tests. It mirrors how
> [`gatling/gatling`](https://github.com/gatling/gatling) tests itself. Every change —
> and every speckit feature — maps its work to the layers below. The constitution
> (`.specify/memory/constitution.md`, Principle III) enforces this on every PR.

## Principle

Test the **real** thing, **test-first**. Follow the TDD loop: write the failing test
before the code (red → green → refactor); cover unit + integration + e2e as the change
demands; assert **observable behavior**, not internals; keep tests isolated; commit no
skipped/disabled tests; keep coverage above the enforced floor (≥75% statement /
≥66% branch — see "Coverage ratchet" below). Use real
components (real `ActorSystem`, real Redis container, real clock or a controllable
`TestClock`) and small fakes **only at the edges** so the test can observe outputs.
Mocking is **plain ScalaMock**, leaf collaborators only — never the Gatling runtime.
Every test asserts **exact real values** and includes at least one **negative/boundary**
case. No empty test bodies. No mock asserted against a mock.

## Layers

A change maps to the layer(s) that fit it — layers are **not** all mandatory per change.
The DSL-component harness is a shared **test fixture** (not a layer). WireMock is used **only**
in the e2e layer (layer 4, in the example overlays), where Gatling `check` validates responses;
it is never used in the library's `src/test/`/`src/it/` (HTTP-emitting code there is ScalaMock-
unit-tested, real external systems use Testcontainers).

### 1. Unit / Functional — `Test` config, no Docker
- **For**: pure functions/utilities (converters, parsers, generators) **and**
  HTTP-emitting code (`HttpJsonFeeder`, `THttpClient`). HTTP code is unit-tested by
  mocking the HTTP collaborator with **ScalaMock** — no real server.
- **Harness**: ScalaTest `AnyWordSpec`/`AnyFlatSpec` + Matchers; ScalaCheck for
  properties; **plain ScalaMock** (`org.scalamock.scalatest.MockFactory`) to stub leaf
  collaborators and HTTP seams (`HttpGetter`, the `THttpClient` transport function).
- **Assert**: exact return values + ≥1 boundary/negative case. For HTTP code: the
  value the feeder/client returns after mock-response injection **and** that the mock
  expectation (the request it issued) was satisfied.
- **Reference**: `utils/IntensityConverterTest.scala` (pure);
  `feeders/HttpJsonFeederSpec.scala`, `utils/THttpClientSpec.scala` (ScalaMock HTTP).

### 2. DSL / Action Component *(conditional)* — `Test` config
- **When it applies**: ONLY when the change introduces/modifies a Gatling DSL piece with
  runtime behavior (actions, trackers, transactions, stateful builders). A pure
  function, doc, or config change does NOT need this layer — do not force it.
- **For**: actions, builders, trackers, transactions, templates, profiles, assertions —
  driven without launching an app.
- **Harness** (shared fixture): real `CoreComponents` + `ScenarioContext` +
  `RecordingStatsEngine` via `transactions/Mocks.scala`; edge fakes `FakeEventLoop`,
  `TestClock`, `noAction`, `latchAction` from `transactions/fixtures.scala`. Drive with
  `action ! session`; assert on recorded stats messages / probed next action.
- **Assert**: recorded `StatsEngine` message fields, session mutations, next-action
  receipt; deterministic timing via `TestClock`.
- **Forbidden**: mocking `StatsEngine`/runtime; `Thread.sleep` races (use a latch).
- **Reference**: `transactions/TransactionsSpec.scala`.

### 3. External Integration (Testcontainers) — `it` config, Docker
- **For**: container-backed real backends — Redis side effects/session state, Vault
  feeders, JDBC storage. (Non-container external state — JWT, startup diagnostics — also
  lives in `it` but needs no container.)
- **Harness**: `testcontainers-scala-scalatest` (`ForAllTestContainer` +
  `GenericContainer`); start a real container; exercise the real path; read back from
  the container.
- **Assert**: exact stored/read values from the real backend.
- **Forbidden**: embedded fakes / recording proxies (e.g. `RecordingJdbcDriver`) as the
  integration target — that is mock-vs-mock.
- **Reference**: `src/it/.../redis/RedisIntegrationSpec.scala` (redis:7-alpine),
  `src/it/.../feeders/VaultIntegrationSpec.scala` (vault:1.17).

### 4. Full Gatling e2e (in `examples/`, via `sbt Gatling/test` + WireMock)
- **For**: proving picatinny's DSL works inside a **real Gatling runtime** driving **real HTTP**
  end-to-end — feeders, JWT generation, transactions, converters exercised in a real `Simulation`
  whose requests carry picatinny-generated values and whose responses are validated with Gatling
  `check`. Real consumer usage in the `examples/` overlays, NOT the library (`Provided`/non-runnable).
- **Harness**: a real `SimulationWithTransactions` in an overlay (`HttpIntegrationCoverage`,
  decomposed `scenarios/`→`cases/`→`feeders/`), against a **WireMock** server that echoes request
  values back via response templating. Picatinny features each via their picatinny method: a
  feeder (`CurrentDateFeeder`) value in the URL, `setJwt` in the `Authorization` header,
  `startTransaction`/`endTransaction` grouping, `IntensityConverter` (`.rpm`) for the injection
  rate. Run by the overlay's NATIVE Gatling task — `sbt Gatling/test` / `mvn gatling:test` /
  `gradle gatlingRun` — under `template-tests`. WireMock is overlay-test-scope only (injected by
  the script); never in the library.
- **Assert**: Gatling **`check`** on the RESPONSES — `status.is(200)`, `jsonPath("$.ts").is("#{ts}")`
  (feeder value round-tripped), `jsonPath("$.auth").is("Bearer #{jwt}")` (JWT round-tripped), plus
  `.assertions(global.failedRequests.count.is(0), details("api-call")…)`. **Check the responses,
  NOT what the mock received** (`WireMock.verify`) and NOT by re-decoding the request — that would
  be mock-testing-mock. The JWT's crypto correctness is unit-tested separately in `JwtSpec`.
- **Feeder-validation e2e**: a second overlay sim `FeederValidationCoverage` feeds EACH picatinny
  feeder (uuid/string/regex/INN/SNILS/PAN/OGRN/KPP/passport/date), sends `v` to WireMock, and
  `check`s the echoed value against that feeder's **expected pattern** (regex) — proving every
  feeder generates a contract-shaped value over real HTTP. Deep checksum honesty is unit-tested in
  `RandomFeedersSpec`.
- **Note**: the overlay isn't compiled by the library build; it is compiled+run by the
  `template-tests` CI gate (the picatinny+Gatling DSL it uses is verified against source).

### 5. Compile Guard — `Test` config
- **For**: locking public DSL signatures (compatibility, Constitution II).
- **Harness**: compile-only specs / `*CompileTest` that must compile.
- **Reference**: `javaapi/assertions/JavaAssertionsCompileTest.java`,
  `javaapi/JavaTemplateSyntaxTest.java`.

### 6. Facade Delegation — `Test` config
- **For**: the Java/Kotlin facade.
- **Harness**: JUnit 5 (jupiter-interface) + AssertJ.
- **Assert**: facade output equals Scala-core output for identical inputs
  (delegation/parity); no facade-only logic.
- **Reference**: `javaapi/JavaFeedersTest.java`, `javaapi/JavaUtilsTest.java`.

## Shared fixtures (NOT layers)

- **DSL-component harness** (`transactions/Mocks.scala` + `transactions/fixtures.scala`):
  real `CoreComponents`/`RecordingStatsEngine` + edge fakes (`FakeEventLoop`,
  `TestClock`). Reused by all layer-2 component tests — never reinvented.

- **WireMock** (e2e overlay only): an in-process HTTP server the e2e Simulation drives. The
  golden rule that keeps it from being mock-testing-mock: assert on the **RESPONSE** with Gatling
  `check` (the picatinny values round-trip via the mock's echo), never on what the mock received
  (`WireMock.verify`) and never by re-decoding the request. Real external systems (Redis/Vault/JDBC)
  use **Testcontainers** (real backends); HTTP-emitting library code is ScalaMock-unit-tested.

## Mock-vs-real boundary

| Allowed real | Allowed fake (edge only) | Mock library (leaf collaborators only) |
|--------------|--------------------------|----------------------------------------|
| ActorSystem, Redis/Vault/JDBC (Testcontainers), clock, real Gatling runtime + real HTTP vs WireMock (e2e) | next-action probe, `FakeEventLoop`, `RecordingStatsEngine`, `TestClock`, ScalaMock'd `HttpGetter`/transport | **plain ScalaMock** for awkward, non-runtime leaf deps and HTTP seams (no Mockito) |

The Gatling runtime/DSL is **never** in the mock column.

## CI gates

| Gate | Command | Covers | Docker |
|------|---------|--------|--------|
| Unit/component | `sbt Test/test` | layers 1 (ScalaMock for HTTP), 2, 5, 6 | no |
| Integration | `sbt "IntegrationTest / test"` | layer 3 | yes |
| Full Gatling e2e (in `examples/`) | `sbt Gatling/test` in the overlay, under `template-tests` | layer 4 | no |
| Coverage | `sbt clean coverage test "IntegrationTest/test" coverageReport` (≥75%/66%) | breadth | yes |

## Coverage ratchet

The floors (`coverageMinimumStmtTotal` / `coverageMinimumBranchTotal` in `build.sbt`) are
**data-driven**: after a change that raises real coverage, re-measure and reset each floor
just under the measured value (≤5 points slack). Rules:

- Floors only move **up** — lowering one requires explicit maintainer authorization in the PR.
- Never satisfy the floor by padding: no low-value tests on generated or benchmark code.
- Benchmark sources (`*Benchmark*`, `org.galaxio.gatling.jmh`) are excluded from the
  denominator via the shared exclusion definition in `build.sbt` — the same definition all
  static-analysis gates use, so every gate sees the same code.
- Record the measured value and date in the `build.sbt` comment on every ratchet.

Current: **75% stmt / 66% branch**, set 2026-07-04 (measured 77.75% / 68.29%, unit+it,
benchmarks excluded; history: 65/60 on 2026-06-21 at measured 69.69%/63.37%).

## Static analysis & gates

This section is the **normative** "one place" for every gate: local command, fix flow,
escape hatch. `AGENTS.md` Commands is a mirror. A gate lands only at zero findings — no
red-at-birth, no blanket exclusions. Benchmark sources (`*Benchmark*`,
`org.galaxio.gatling.jmh`) are invisible to every gate via the shared exclusion definition
in `build.sbt`.

| Gate | Local check | Local fix | Escape hatch |
|------|------------|-----------|--------------|
| Format | `sbt scalafmtCheckAll scalafmtSbtCheck` | `sbt scalafmtAll scalafmtSbt` | none |
| Lint (scalafix, #273) | `sbt "scalafixAll --check"` | `sbt scalafixAll scalafmtAll` (fix, then format — they converge) | `// scalafix:ok <Rule>` on the offending line, or a bare `// scalafix:off <Rule>` … `// scalafix:on <Rule>` block, ALWAYS with a `// Justification:` line |
| Binary compatibility (ADVISORY, #274) | `sbt mimaFindBinaryIssues` — prints findings, never fails; CI annotates PRs with `::warning::` and stays green | restore the API, or acknowledge an intentional break | `mimaBinaryIssueFilters` entry in `build.sbt` + justification comment + constitution-II version bump; reviewing outstanding warnings is a mandatory release-checklist step (AGENTS.md) |
| Compiler diagnostics (#275) | `sbt compile Test/compile "IntegrationTest / compile"` — `-Xlint:_,-infer-any` + `-Wunused` + `-Wdead-code` under `-Werror`, always on | fix the diagnostic | per-site `@nowarn("cat=…"/"msg=…")` + justification comment — never a category-wide downgrade; documented category exclusions live in the `build.sbt` flag comment (currently: `infer-any` — heterogeneous feeder records are the domain type) |

If a local run seems to miss a fresh finding, the scalafix incremental cache is stale — re-run
as `sbt "Test/scalafix --no-cache <Rule>"` (CI always runs cold-cache). Note: `scalafix:off/on`
and `scalafix:ok` directives take a BARE rule list — prose on the directive line voids it; put
the justification on its own comment line.

Lint rules (`.scalafix.conf`): `DisableSyntax` (no `return`, no `asInstanceOf`/`isInstanceOf`
outside justified guarded extraction, no `finalize`, no null comparisons — wrap in
`Option(...)`), `ProcedureSyntax`, `RemoveUnused` (needs the `-Wunused` flags in `build.sbt`),
`OrganizeImports` (deterministic layout).

## Per-feature gate (speckit)

Every `/speckit-plan` MUST fill a **Test Model** section: for each functional requirement,
(a) the real case to test, (b) the chosen layer above, (c) a prose test sketch with the
assertions — **no implementation/code**. The planning checklist FAILS if the section is
missing, empty, names no real case, or contains code. See
`.specify/templates/plan-template.md`.
