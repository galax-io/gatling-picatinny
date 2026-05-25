# Repository Instructions

- Before any `git commit` or `git push`, always run `sbt scalafmtAll scalafmtSbt`.
- If formatting changes files, review them and include the relevant formatted changes before publishing.

## Stack

- Scala 2.13.18, Java 17
- Gatling 3.13.5 — HTTP, Redis protocols (`gatling-http`, `gatling-redis`)
- PureConfig 0.17.x + circe 0.15.x + json4s 4.1.x — config and template rendering
- jwt-scala 11.x — JWT generation (RSA/EC + HMAC)
- generex — regex-based data generation
- ScalaTest 3.2.x + ScalaCheck 1.19.x + ScalaMock 7.x + JUnit Jupiter 6.x + AssertJ 3.x
- Testcontainers (Redis, Vault) for integration tests
- JMH — microbenchmarks (Faker API, feeder throughput)

## Working Rules

- Keep changes scoped to this repository.
- Prefer `rg` for search and `apply_patch` for edits.
- Preserve existing user changes unless explicitly asked otherwise.
- If a task spans multiple repos, confirm the target repo before editing.
- Follow existing architecture and avoid unrelated refactors.

## Quality

- Run `sbt scalafmtAll scalafmtSbt` before every commit — CI enforces this.
- Run `sbt test` for unit suite; `sbt IntegrationTest/test` for Redis/Vault integration tests.
- Prefer integration tests against real services (Testcontainers) over mocks for runtime behavior.
- Coverage minimum is 45% statement coverage — `sbt coverage test coverageReport` to verify.
- Preserve backward compatibility for all published DSL/API surfaces (Scala + Java + Kotlin).

## Repo Notes

- Public DSLs, feeder builders, and Gatling protocol wrappers are compatibility-sensitive.
- Java API lives in `javaapi` package — keep parity with Scala DSL when adding features.
- Example overlays in `examples/` are source-only; they are applied over a galaxio CLI-generated project, not run standalone.
- JMH benchmarks live under `src/test` with `@State` and run via `sbt "Jmh/run .*"`.
- `IntegrationTest` scope (`it`) is separate from `test` — Redis and Vault tests live there.
