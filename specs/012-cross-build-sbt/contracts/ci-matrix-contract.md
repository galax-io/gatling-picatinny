# Contract: CI Verification of the Secondary Major

**Feature**: [012-cross-build-sbt](../spec.md) | **Phase**: 1
**Scope decision**: 2026-08-20 — dual-major support is insurance, not a shipped capability. Verified
on a schedule plus a build-definition path filter, **not** by a per-pull-request matrix.

Defines what CI must guarantee so that "sbt 2 still works" is a fact rather than a claim — at a cost
proportionate to what actually depends on it.

## M-1: Composite action input

`.github/actions/sbt-setup` gains one input:

| Input | Required | Default | Meaning |
|---|---|---|---|
| `sbt-version` | no | *(empty — use the repository pin)* | When set, every `sbt` invocation in the job runs against this version |
| `java-version` | no | `21` | unchanged |

**Backward compatibility**: all nine existing callers pass only `java-version` and MUST keep working
unchanged, resolving to the repository's default pin. This is why the default is empty rather than
`'1.12.15'`.

**Selection mechanism**: the action MUST export the launcher flag as a **dedicated** env var
(`SBT_VERSION_FLAG`). It MUST NOT append `-Dsbt.version=` to `SBT_OPTS` — `ci.yml` sets `SBT_OPTS` at
workflow level and again at step level in `binary-compat`, so an `SBT_OPTS`-borne selection is
clobbered and the sbt 2 job silently runs sbt 1 and passes. Independently, at the launcher level
`SBT_OPTS` strictly outranks the `--sbt-version` flag.

**Cache key**: MUST include the resolved sbt major in both `key` and `restore-keys`, and the `path:`
list MUST add sbt 2's `~/.config/sbt` and `~/.cache/sbt` (the current `~/.sbt` is sbt 1 only).

## M-2: Where the secondary major runs

| Trigger | Scope | Blocking |
|---|---|---|
| `schedule` — at least daily, against the default branch | **Full** gate suite under sbt 2: compile, unit, integration, format, lint, coverage, MiMa, publishLocal + POM comparison, overlay e2e | no — notifies |
| `pull_request` with a build-definition path filter | Same full suite | **yes** |
| `workflow_dispatch` | Same full suite | no |

**Path filter** (FR-012) — the sbt 2 job runs on a pull request touching any of:

```text
project/build.properties
project/plugins.sbt
project/Dependencies.scala
project/hygiene/**
*.sbt
examples/scala-sbt-example/**
.github/actions/sbt-setup/**
scripts/test-scala-sbt-template.sh
```

**Everything else is unchanged.** Ordinary pull requests keep exactly today's jobs at today's cost:
`format`, `lint`, `test` (Java 17/21), `coverage`, `redis-integration`, `binary-compat`,
`publish-local`, `template-tests` — all on sbt 1 only.

**Cost**: roughly one extra full run per day (~2700s compute) plus an occasional pull-request leg.
Compare with the rejected full-matrix option: ~2x compute on **every** pull request, permanently.

## M-3: Failure attribution

- The scheduled job MUST be named so the major is visible without opening it (e.g.
  `sbt 2 compatibility (scheduled)`).
- A failure MUST notify a maintainer — a red run on a branch nobody watches is not a signal.
  Filing or updating an issue is acceptable; silence is not.
- A scheduled failure MUST NOT block unrelated pull requests. A path-filtered pull-request run MUST.
- `binary-compat` stays advisory per #274 in both contexts.

## M-4: Anti-vacuity requirement — the load-bearing rule of this contract

A once-a-day job that passes without running anything is **worse than no job**: it reads as coverage
that does not exist, and it is cheap enough that nobody scrutinises it. Every defence below is
mandatory, and they only work together:

1. **`testOnly`, never `test`.** On sbt 2 `test` IS `testQuick` and returns `[success]` having run
   zero tests, off a global cache that survives `clean` and `rm -rf target`.
2. **Report globs** must be `**/test-reports/*.xml` — the literal `target/test-reports` segment does
   not exist under sbt 2's `target/out/jvm/...` layout.
3. **`if-no-files-found: error`** on uploads and **`action_fail_on_inconclusive: true`** on the
   test-results publisher.
4. **Positive assertions**: the job MUST assert `sbt $SBT_VERSION_FLAG "show sbtVersion"` prints `2.`
   (not `--numeric-version` or `--version`, which ignore the flag; not `print`, which is sbt-2-only),
   **and** that the reported test count is non-zero.

Before this feature is considered done, demonstrate at least once (SC-007): introduce a change valid
under sbt 1 and invalid under sbt 2, observe the sbt 2 job fail and name the major, then revert.
A job never seen to fail has not been shown to work.

## M-5: Artifact equivalence

The scheduled job MUST `publishLocal` under sbt 2 and diff the resulting POM against the sbt 1
artifact — coordinates, dependency set, scopes, asserting every `io.gatling` entry is still
`provided` (FR-008, SC-005). Use `/usr/bin/diff`; the rtk-proxied `diff` present in some
environments returns false "identical" results.

## M-6: Promotion path

If the project later decides to actually migrate to sbt 2, this contract is superseded by a
per-pull-request matrix over `sbt-major: [1, 2]`. That is a workflow change only — the build-side
work (the `integration` subproject, `exportJars`, `License.Apache2`, the hygiene overlay,
`testOnly` convergence) is identical either way and does not need revisiting.
