# gatling-picatinny — Agent Guide

Gatling DSL extension library: config, feeders, transactions, assertions, JWT, Redis, diagnostics, profiles, and shared utilities, with a Java/Kotlin-facing facade. Published library — treat all public Scala/Java APIs, DSL behavior, and serialized config/profile formats as compatibility-sensitive.

## Role

Principal Engineer: Scala 2.13, Gatling DSL, Java/Kotlin facade design, HTTP/Redis, load testing. Prefer small, clear, backward-compatible changes unless the task explicitly requires otherwise.

## Stack

Scala 2.13.18, sbt, Java 17 (compile target; CI runs on Temurin 21), Gatling 3.13.5 (`Provided`). PureConfig, Circe, json4s, Jackson, Scala Logging, Generex, JWT, fast-uuid. ScalaTest + JUnit (sbt-jupiter-interface), Testcontainers (Redis integration), JMH (benchmarks).

## Commands

```bash
sbt scalafmtAll scalafmtSbt                                          # format
sbt scalafmtCheckAll scalafmtSbtCheck compile test "IntegrationTest / test"  # verify
sbt compile test                                                    # CI (unit only)
sbt "IntegrationTest / test"                                        # integration (Docker / Redis)
sbt Jmh/run                                                         # benchmarks
```

## Structure

`config/` simulation config loading, masking, defaults, typed params. `feeders/` random/faker/HTTP/CSV/Vault feeders + feeder syntax. `transactions/` protocol, actors, trackers, actions, builders, Java helpers. `templates/`, `utils/jwt/`, `assertions/`, `profile/` templating, JWT generation, NFR/assertion builders, profile DSL/runtime. `redis/`, `storage/`, `diagnostics/`, `utils/` Redis actions, session storage, startup diagnostics, cross-cutting utils. `src/main/java/.../javaapi` thin Java/Kotlin facade. `examples/` overlay projects (Scala sbt, Java Maven, Kotlin Gradle).

## Architecture

Scala DSL/runtime is the source of truth; Java/Kotlin facades stay thin and delegate. Facade reimplementing core logic = wrong:

```scala
// ✅ thin facade delegates to Scala core
object JHttpFeeder { def apply(url: String): FeederBuilder = ScalaHttpFeeder(url) }
// ❌ duplicates logic already in Scala core
object JHttpFeeder { def apply(url: String): FeederBuilder = { /* duplicate logic */ } }
```

Review runtime-sensitive behavior carefully: transaction boundaries, feeder determinism, JWT generation, Redis side effects, startup diagnostics, masking, profile expansion.

## Boundaries

**Always:** format before commit, branch from `main`, keep commits semantic and green, preserve backward compat for published Scala/Java APIs and example overlays. `build.sbt`/`project/` = dependency truth, `.github/workflows/` = CI/release truth.

**Ask first:** new deps or upgrades, changing public API signatures / DSL behavior / serialized config/profile formats, editing another repo, release/publish workflow changes.

**Never:** force-push or commit to `main`, merge commits in PR branches (rebase only), commit broken code, opportunistic refactors outside scope, mock Gatling runtime where a real integration path exists.

## Release Process (MANDATORY)

Trunk-based with release branches. Trunk is `main`; `release/*` branches are cut from `main` for stabilization. Pushing a `vX.Y.Z` tag on `main` or a `release/*` branch publishes to Maven Central (via sbt-ci-release / dynver) and creates a GitHub Release with git-cliff notes.

### Minor/Major release (e.g. 1.2.0, 2.0.0)

1. `git checkout -b release/X.Y.0 main` — cut release branch from `main`
2. `git push -u origin release/X.Y.0`
3. `git tag vX.Y.0` on the release branch
4. `git push origin vX.Y.0` — triggers release workflow

### Patch release (e.g. 1.2.1)

1. Fix lands on `main` first (via PR as usual)
2. `git cherry-pick <fix-sha>` onto `release/X.Y.0`
3. `git tag vX.Y.1` on the release branch
4. `git push origin vX.Y.1` — triggers release workflow

### Rules

- **Every minor version gets its own `release/X.Y.0` branch** — no exceptions
- **Tags ONLY on `release/*` branches or `main`** — release.yml validates this
- **Branch name must match tag version**: `release/1.2.0` → `v1.2.0`, `v1.2.1`, etc.
- **Never delete a release tag** after Sonatype deployment starts — creates stuck deployments
- **Never reuse a version number** — Sonatype Central rejects duplicates permanently
