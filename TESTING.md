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
it is never used in the library's `src/test/`/`integration/src/test/` (HTTP-emitting code there is ScalaMock-
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

### 3. External Integration (Testcontainers) — `integration` subproject, Docker
- **For**: container-backed real backends — Redis side effects/session state, Vault
  feeders, JDBC storage. (Non-container external state — JWT, startup diagnostics — also
  lives in the `integration` subproject but needs no container.)
- **Harness**: `testcontainers-scala-scalatest` (`ForAllTestContainer` +
  `GenericContainer`); start a real container; exercise the real path; read back from
  the container.
- **Assert**: exact stored/read values from the real backend.
- **Forbidden**: embedded fakes / recording proxies (e.g. `RecordingJdbcDriver`) as the
  integration target — that is mock-vs-mock.
- **Reference**: `integration/src/test/scala/.../redis/RedisIntegrationSpec.scala` (redis:7-alpine),
  `integration/src/test/scala/.../feeders/VaultIntegrationSpec.scala` (vault:1.17).

### 4. Full Gatling e2e (in `examples/`, via `sbt 'Gatling/testOnly *'` + WireMock)
- **For**: proving picatinny's DSL works inside a **real Gatling runtime** driving **real HTTP**
  end-to-end — feeders, JWT generation, transactions, converters exercised in a real `Simulation`
  whose requests carry picatinny-generated values and whose responses are validated with Gatling
  `check`. Real consumer usage in the `examples/` overlays, NOT the library (`Provided`/non-runnable).
- **Harness**: a real `SimulationWithTransactions` in an overlay (`HttpIntegrationCoverage`,
  decomposed `scenarios/`→`cases/`→`feeders/`), against a **WireMock** server that echoes request
  values back via response templating. Picatinny features each via their picatinny method: a
  feeder (`CurrentDateFeeder`) value in the URL, `setJwt` in the `Authorization` header,
  `startTransaction`/`endTransaction` grouping, `IntensityConverter` (`.rpm`) for the injection
  rate. Run by the overlay's NATIVE Gatling task — `sbt 'Gatling/testOnly *'` / `mvn gatling:test` /
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
| Unit/component | `sbt "Test/testOnly"` | layers 1 (ScalaMock for HTTP), 2, 5, 6 | no |
| Integration | `sbt integration/testOnly` | layer 3 | yes |
| Full Gatling e2e (in `examples/`) | `sbt 'Gatling/testOnly *'` in the overlay, under `template-tests` | layer 4 | no |
| Coverage | `sbt clean coverage "Test/testOnly" "integration/testOnly" coverageOff coverageReport coverageAggregate` (≥75%/66%) | breadth | yes |

### Supported sbt majors and per-major gate availability

The build is cross-built on **sbt 1.12.15 (default pin, `project/build.properties`)** and
**sbt 2.0.6 (secondary, `sbt --sbt-version 2.0.6 <task>`)**. Every gate above runs on both majors,
with exactly one recorded exception.

**Capability exemption — Dependency hygiene, sbt 2.x**

| Field | Value |
|---|---|
| Capability | Dependency-hygiene report (`undeclaredCompileDependencies` / `unusedCompileDependencies`) |
| Unsupported major | **2.x** |
| Reason | `com.github.cb372:sbt-explicit-dependencies` publishes no `_sbt2_3` artifact at any version. Worse, `build.sbt` referenced its keys symbolically, so the build failed to **compile** under sbt 2 with `Not found: undeclaredCompileDependenciesFilter`. The plugin and its five filters therefore moved out of the always-loaded build into `project/hygiene/`, attached on demand with `--addPluginSbtFile` |
| Revisit condition | `sbt-explicit-dependencies_sbt2_3` appears on Maven Central → fold the plugin and filters back into `build.sbt` and delete this entry |

`Jmh/run` was flagged as a second possible gap (it is also a plugin-contributed config axis) but
was verified working on both majors on 2026-08-20 — no exemption needed.

This is the **only** permitted single-major capability. Any new build plugin must state its
availability on both majors as part of the change that proposes it; a second undocumented gap is a
defect, not a precedent.

**sbt 2 action-cache corruption — always publish from a fresh cache.** sbt 2.0.6 stores the
compiled class directory as a packed blob in `<sbt-cache>/v2/cas/`, and restores individual class
files as symlinks into it. If a blob is lost or truncated — an interrupted sbt mid-write, two sbt 2
processes sharing one `--sbt-cache`, or manual pruning of `~/.cache/sbt` / `~/Library/Caches/sbt` —
`DiskActionCacheStore.syncBlobs` skips it **silently** and returns the cached value anyway. Two
symptoms follow, and the second is the dangerous one:

| | symptom | caught by |
|---|---|---|
| directory gone entirely | sbt fails with `NoSuchFileException` / `file referenced by the build does not exist` | sbt itself, loudly |
| individual files dangling | `packageBin` skips them and `publishLocal` reports **`[success]` with classes missing** — reproduced at 617 → 616 jar entries | **nothing, by default** |

**`clean` does not clear the poisoned state** — only a fresh `--sbt-cache` (and, if the dangling
symlinks are already on disk, removing the output tree) does, so any guidance of the form "just run
clean" is wrong. The CI job that publishes under sbt 2 therefore uses a per-run `--sbt-cache`, and
the artifact-equivalence step compares jar entry sets rather than POMs alone, since a truncated jar
still has a perfectly correct POM.

An earlier revision of this section described an `assertMaterialised` guard on `Compile/Test
products`. That guard was removed: it only detected a *dangling symlink*, not a class file the CAS
never materialised at all, so it did not cover the case it was written for — and the `products` pin
it lived in is itself gone now that `templates/Templates.scala` resolves classpath resources
correctly.

Official publication runs on sbt 1 (AGENTS.md Release Process), which has no action cache and cannot
hit this at all. That is now a load-bearing reason for the pin, not merely a conservative default:
Sonatype Central permanently rejects a reused version, so a silently truncated jar published under
version X could never be fixed by republishing X.

**On `test` vs `testOnly`**: on sbt 2, `test` IS `testQuick` — it reports `[success]` having run
zero tests, off a global disk cache that survives `clean` and `rm -rf target`. Every gate command in
this document uses `testOnly` for that reason. Do not "simplify" one back to `test`.

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

Current: **75% stmt / 66% branch**. Measured **81.40-81.44% / 75.33-75.49%** on 2026-08-20,
unit+integration, benchmarks excluded. Within a single run the two sbt majors agree **exactly**;
across runs the figure moves by ~0.04 stmt / ~0.16 branch even with `clean`. Treat it as a range:
gate on "both majors agree", never on an exact constant, or the check will flap.
History: 77.75/68.29 recorded 2026-07-04 (already stale at HEAD by 2026-08-20, i.e. before the
`integration` migration — that migration is coverage-neutral); 69.69/63.37 on 2026-06-21 at floors 65/60.

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
| Binary compatibility (ADVISORY, #274) | `sbt mimaReportBinaryIssues \|\| true` — the `\|\| true` is required: `mimaReportBinaryIssues` alone exits non-zero on findings (its sibling `mimaFindBinaryIssues` is a silent internal task that returns problems as a value without printing them — do not use it directly, it looks clean even when it is not); CI annotates PRs with `::warning::` and stays green | restore the API, or acknowledge an intentional break | `mimaBinaryIssueFilters` entry in `build.sbt` + justification comment + constitution-II version bump; reviewing outstanding warnings is a mandatory release-checklist step (AGENTS.md) |
| Compiler diagnostics (#275) | `sbt compile Test/compile integration/Test/compile` — `-Xlint:_,-infer-any` + `-Wunused` + `-Wdead-code` under `-Werror`, always on | fix the diagnostic | per-site `@nowarn("cat=…"/"msg=…")` + justification comment — never a category-wide downgrade; documented category exclusions live in the `build.sbt` flag comment (currently: `infer-any` — heterogeneous feeder records are the domain type) |
| Dependency hygiene (REPORT-ONLY, #276) | `sbt --batch --addPluginSbtFile=project/hygiene/plugins.sbt undeclaredCompileDependencies unusedCompileDependencies` — **sbt 1 only** (see "Supported sbt majors and per-major gate availability" above); never CI-gated; run manually before each release (AGENTS.md Release Process). `--batch` is mandatory: without it a non-TTY run is SIGKILLed after "done compiling" and looks like a hang | declare the dependency in `project/Dependencies.scala` or remove it | accepted findings carry a justified `…DependenciesFilter` entry in `project/hygiene/HygieneFilters.scala` (macro artifacts, umbrella version pins, codegen-time deps) |

Dependency updates are automated by Scala Steward (`.github/workflows/scala-steward.yml`,
weekly + manual dispatch; policy in `.scala-steward.conf`). Every bot PR is auto-assigned by
the workflow post-step to the current **active** milestone (the lowest-numbered open one,
same definition as `scripts/check-linkage.sh`), so the
every-PR-needs-a-milestone rule holds for bots too and dependency updates are tracked
with the release they ship in. Gatling itself is Steward-ignored —
host-runtime bumps are a deliberate maintainer decision.

If a local run seems to miss a fresh finding, the scalafix incremental cache is stale — re-run
as `sbt "Test/scalafix --no-cache <Rule>"` (CI always runs cold-cache). Note: `scalafix:off/on`
and `scalafix:ok` directives take a BARE rule list — prose on the directive line voids it; put
the justification on its own comment line.

Lint rules (`.scalafix.conf`): `DisableSyntax` (no `return`, no `asInstanceOf`/`isInstanceOf`
outside justified guarded extraction, no `finalize`, no XML literals, no null comparisons —
wrap in `Option(...)`, no `Thread.sleep` synchronization — latch or bounded probe, no
`println` — use the logger), `ProcedureSyntax`, `RemoveUnused` (needs the `-Wunused` flags in
`build.sbt`), `OrganizeImports` (deterministic layout), `NoValInForComprehension`,
`RedundantSyntax`, `ExplicitResultTypes` (public members carry explicit types).
`LeakingImplicitClassVal` is deliberately NOT enabled — its auto-fix would privatize published
implicit-class accessors (binary break; see the note in `.scalafix.conf`).

## Per-feature gate (speckit)

Every `/speckit-plan` MUST fill a **Test Model** section: for each functional requirement,
(a) the real case to test, (b) the chosen layer above, (c) a prose test sketch with the
assertions — **no implementation/code**. The planning checklist FAILS if the section is
missing, empty, names no real case, or contains code. See
`.specify/templates/plan-template.md`.
