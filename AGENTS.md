# gatling-picatinny — Agent Guide

Gatling DSL extension library: config, feeders, transactions, assertions, JWT, Redis, diagnostics, profiles, and shared utilities. Published library — treat all public APIs as compatibility-sensitive.

## Role
Principal Engineer in software development and performance testing. Strong Scala, Java, Kotlin, Gatling DSL, HTTP/Redis, and load-testing expertise. Prefer small, clear, backward-compatible changes unless the task explicitly requires otherwise.

## Stack
- Scala 2.13, SBT, Gatling 3.13.x, Java 17+
- PureConfig, Circe, Jackson, Scala Logging, Testcontainers, JMH
- Java API facade with Kotlin-compatible usage and example overlays for Scala, Java, and Kotlin projects
- GitHub Actions, Scala Steward, Codecov, Sonatype

## Structure
- `config/`: simulation config loading, masking, defaults, typed parameter access
- `feeders/`: random data generators, faker integration, HTTP/CSV/Vault feeders, feeder syntax
- `transactions/`: transaction protocol, actors, trackers, actions, builders, Java helpers
- `templates/`, `utils/jwt/`, `assertions/`, `profile/`: templating, JWT generation, NFR/assertion builders, profile DSL/runtime
- `redis/`, `storage/`, `diagnostics/`, `utils/`: Redis actions, session storage helpers, startup diagnostics, cross-cutting utilities
- `src/main/java/.../javaapi`: Java/Kotlin-facing facade and builders
- `examples/`: overlay projects for Scala SBT, Java Maven, and Kotlin Gradle

## Commands

Format:
```
sbt scalafmtAll scalafmtSbt
```

Verify:
```
sbt scalafmtCheckAll scalafmtSbtCheck compile test "IntegrationTest / test"
```

## Design Rules

Scala DSL/runtime is the source of truth; Java/Kotlin facades stay thin and compatible. Treat Scala DSL, Java builders, Kotlin examples, defaults, serialized config/profile formats as compatibility-sensitive.

Review runtime-sensitive behavior carefully: transaction boundaries, feeder determinism, JWT generation, Redis side effects, startup diagnostics, masking, and profile expansion.

```scala
// ✅ — thin facade delegates to Scala core
object JHttpFeeder { def apply(url: String): FeederBuilder = ScalaHttpFeeder(url) }
// ❌ — reimplements logic already in Scala core
object JHttpFeeder { def apply(url: String): FeederBuilder = { /* duplicate logic */ } }
```

## Boundaries

✅ Always:
- Run `sbt scalafmtAll` before committing
- Branch from `main`; keep commits semantic and green
- Preserve backward compatibility for published Scala and Java APIs and example overlays
- Treat `build.sbt`, `project/Dependencies.scala`, `project/plugins.sbt` as source of truth
- Treat `.github/workflows/` as source of truth for formatting, compile, tests, coverage, and release

⚠️ Ask first:
- Adding or upgrading dependencies
- Changing public API signatures, DSL behavior, or serialized config/profile formats
- Editing another repository
- Any change to release or publish workflow

🚫 Never:
- Force-push or commit directly to `main`
- Add merge commits to PR branches (rebase-oriented history)
- Commit knowingly broken code to `main`
- Add opportunistic refactors outside task scope
- Mock Gatling runtime behavior where a real integration path exists

## PR Workflow
1. Branch from `main`.
2. Run verify commands before commit.
3. Keep commits semantic and green.
4. Prefer rebase-oriented history; avoid merge commits in PR branches.
5. CI in `.github/workflows/` is the source of truth for formatting, compile, tests, coverage, template validation, integration coverage, and release behavior.
6. Releases are driven from `main` and `v*` tags; align with existing workflows rather than inventing a parallel path.

## Repo Notes
- `examples/` are overlays, not standalone projects; keep them aligned with the documented developer flow.
- Changes in transactions, feeders, templates, config, Redis, diagnostics, or Java API wrappers affect correctness and adoption across Scala, Java, and Kotlin consumers.
- Real Gatling runtime behavior is usually more valuable than mocks here.
