# Agents

Local-only instructions for agents working in `gatling-picatinny`.

## Role
- Act as a Principal Engineer in software development and performance testing.
- Bring strong Scala, Java, Kotlin, Gatling DSL, and performance testing expertise.
- Prefer small, clear, backward-compatible changes unless the task explicitly requires otherwise.

## Stack
- Scala 2.13.18 core on SBT, Gatling 3.13.5 on `main`, Java 17+.
- HTTP and Redis Gatling protocols (`gatling-http`, `gatling-redis`).
- Java API facade with Kotlin-compatible usage and tests.
- PureConfig 0.17.x + circe 0.15.x + json4s 4.1.x for config and template rendering.
- jwt-scala 11.x for JWT generation (RSA/EC + HMAC), generex for regex-based data generation.
- ScalaTest 3.2.x, ScalaCheck 1.19.x, ScalaMock 7.x, JUnit Jupiter 6.x, AssertJ 3.x.
- Testcontainers (Redis, Vault) for integration tests, JMH for microbenchmarks.
- GitHub Actions, Scala Steward, Codecov, Sonatype.

## Installed Skills
- Use the installed Scala, Java, Kotlin, TDD, and unit-test skills for tasks in this repo when they apply.
- Default skill set for this repository: `scala-pro`, `java-best-practices`, `kotlin-patterns`, `kotlin-testing`, `tdd-workflow`, `unit-test-utility-methods`.
- When working on Scala core code, prefer Scala skill guidance first.
- When changing `src/main/java/.../javaapi`, apply Java best practices explicitly.
- When reviewing Kotlin tests or examples, apply Kotlin and Kotlin testing guidance.
- For bug fixes and new behavior, prefer TDD and focused regression/unit coverage instead of ad hoc changes.

## Structure
- `config/`: SimulationConfig — base URL, intensity, ramp duration, custom param readers.
- `feeders/`: legacy feeders, Faker-based GeneratedFeeder, FakerApi facade, feeder DSL ops (zip, rename, select, etc.).
- `jwt/`: JWT builder DSL, ClaimsBuilder, SigningKey ADT, PEM loaders, Java/Kotlin API.
- `redis/`: RedisAction, RedisCommand sealed trait, 27 commands across 6 data types, saveAs/requestName.
- `templates/`: Mustache template rendering, makeJson/makeXml helpers, Java API.
- `transactions/`: TransactionsActor, startTransaction/endTransaction DSL, Java wrapper.
- `assertion/`: response-time percentile assertion helpers.
- `storage/`: SessionStorage — cross-scenario auth token management, pluggable backends (JSON, Redis, JDBC).
- `src/main/java/.../javaapi`: Java/Kotlin-facing facade for all modules.
- `src/test/scala`, `src/test/java`, `src/test/kotlin`: unit, integration, and usage coverage.
- `examples/`: source overlays applied over a galaxio CLI-generated project — not standalone.

## Design Rules
- Keep architecture simple: SimulationConfig provides shared settings, module DSLs wrap Gatling actions, checks map results back into Gatling sessions.
- Treat Scala DSL, Java builders, feeder APIs, and plugin defaults as compatibility-sensitive.
- Feeder and session state can be stateful and concurrency-sensitive: review thread safety, iterator exhaustion, and Gatling session isolation carefully.
- Apply SOLID when it improves clarity and testability.
- Prefer KISS and DRY, but avoid premature abstraction in public APIs.

## Working Rules
- Do not commit or publish this file unless the user explicitly asks.
- Keep changes scoped to this repo; preserve existing user changes.
- Prefer `rg` for search and `apply_patch` for edits.
- Confirm before editing another repo.
- Avoid opportunistic refactors; prefer real runtime validation over heavy mocking for Gatling/Redis/Vault behavior.

## Quality
- Format before publishing:
  - minimum: `sbt scalafmtAll scalafmtSbt`
  - preferred for Scala/test changes: `sbt --batch "Test / compile" scalafmtAll scalafmtCheckAll`
- Run `sbt test` for unit suite; `sbt IntegrationTest/test` for Redis and Vault integration tests.
- Coverage minimum is 45% statement — `sbt coverage test coverageReport` to verify.
- Follow TDD where practical and add focused regression tests for behavior changes.
- Prefer integration tests against real services (Testcontainers) when validating runtime behavior.
- Preserve backward compatibility for published Scala and Java/Kotlin APIs.

## PR Workflow
1. Branch from `main`.
2. Run the real repo checks before commit and push.
3. Keep commits semantic and green; do not knowingly break the target branch.
4. Prefer rebase-oriented history; avoid merge commits in PR branches.
5. CI lives in `.github/workflows/ci.yml` and checks formatting, compile, tests, coverage, and template tests.
6. Releases are driven by pushes to `main` and tags `v*`, with versioning and publishing handled by the same workflow.

## Branch Policy
- Treat `main` as the only active long-lived branch for plugin development.
- Do not plan work around stale maintenance branches.
- Prefer deleting stale branches instead of keeping parallel inactive lines around.

## Repo Notes
- `build.sbt`, `project/Dependencies.scala`, and `project/plugins.sbt` are the source of truth for build and dependency behavior.
- Changes in feeder state, session variable naming, or action execution can affect both correctness and observability under load.
- JMH benchmarks live under `src/test` and run via `sbt "Jmh/run .*"`.
- `IntegrationTest` scope (`it`) is separate from `test` — Redis and Vault tests live there.
- Real Redis/Vault behavior is usually more valuable than mocks here.
