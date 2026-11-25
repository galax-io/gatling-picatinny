# gatling-picatinny — Agent Guide

Gatling DSL extension library: config, feeders, transactions, assertions, JWT, Redis, diagnostics, profiles, and shared utilities, with a Java/Kotlin-facing facade. Published library — treat all public Scala/Java APIs, DSL behavior, and serialized config/profile formats as compatibility-sensitive.

## Role

Principal Engineer: Scala 2.13, Gatling DSL, Java/Kotlin facade design, HTTP/Redis, load testing. Prefer small, clear, backward-compatible changes unless the task explicitly requires otherwise.

## Stack

Scala 2.13.18, sbt, Java 17 (compile target; CI runs on Temurin 21), Gatling 3.13.5 (`Provided`). PureConfig, Circe, json4s, Jackson, Scala Logging, Generex, JWT, fast-uuid. ScalaTest + JUnit (sbt-jupiter-interface), Testcontainers (Redis integration), JMH (benchmarks).

## Commands

Supported sbt majors: **1.12.15** (default — what a bare `sbt` uses, pinned in
`project/build.properties`) and **2.0.6** (secondary). Run any task on the secondary with
`sbt --sbt-version 2.0.6 <task>`. Use `testOnly`, never `test`: on sbt 2 `test` is `testQuick` and
reports success having run zero tests.

```bash
# install   sbt update Test/compile scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" mimaReportBinaryIssues coverage update coverageOff
# test      sbt compile "Test/testOnly"
sbt scalafmtAll scalafmtSbt                                          # format
sbt scalafixAll scalafmtAll                                          # lint fix (then format; they converge)
sbt "scalafixAll --check"                                            # lint gate (CI-enforced; TESTING.md "Static analysis & gates")
sbt scalafmtCheckAll scalafmtSbtCheck compile "Test/testOnly" integration/testOnly  # verify
sbt compile "Test/testOnly"                                         # CI (unit only)
sbt integration/testOnly                                            # integration (Docker / Redis)
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

## Test Model

Authoritative: **[TESTING.md](TESTING.md)** (constitution §III). Test-first; assert exact values + ≥1 negative case; ScalaMock (not Mockito) for leaf deps only. Six layers, apply what fits:

1. Unit/functional (`Test`) — pure fns + HTTP (`HttpJsonFeeder`/`THttpClient`) via ScalaMock; no server in the library.
2. DSL/action component (conditional, `Test`) — `transactions/Mocks` ActorSystem harness; feeder-determinism + tx boundaries.
3. External integration (`integration` subproject) — Testcontainers Redis/Vault/JDBC; JWT/diagnostics non-container.
4. E2e — real `Simulation` driving picatinny DSL (feeders/JWT/transactions/converters) over real HTTP vs **WireMock** in `examples/`, `sbt 'Gatling/testOnly *'`. Assert RESPONSES with Gatling `check` (values round-trip via the mock echo); never `WireMock.verify`/re-decode the request (mock-testing-mock). WireMock overlay-only.
5. Compile guard (`Test`).
6. Facade delegation (`Test`, JUnit 5).

Coverage floor 75/66 (stmt/branch; data-driven ratchet — TESTING.md "Coverage ratchet"). Every `/speckit-plan` fills the code-free "Test Model" table (gate).

## Boundaries

**Always:** format before commit, branch from `main`, keep commits semantic and green, preserve backward compat for published Scala/Java APIs and example overlays. `build.sbt`/`project/` = dependency truth, `project/build.properties` = the single default-sbt-major pin, `.github/workflows/` = CI/release truth.

**Ask first:** new deps or upgrades, changing public API signatures / DSL behavior / serialized config/profile formats, editing another repo, release/publish workflow changes. A new sbt plugin or build capability MUST state its availability on **both** supported majors; a single-major capability needs a recorded exemption (TESTING.md).

**Never:** force-push or commit to `main`, merge commits in PR branches (rebase only), commit broken code, opportunistic refactors outside scope, mock Gatling runtime where a real integration path exists.

## Milestones (ALWAYS)

A product repository has no milestones: a release is a business milestone of `galax-io/galaxio-sdlc-backlog` (see "Feature milestones"), and a PR here carries none.

- **Issues are for important tasks**, not technical ones. A PR that completes one writes `Closes owner/repo#N` (the issue closes when the PR lands on `main`); one PR of several writes `Part of owner/repo#N`. A technical PR (sync, pins, docs, CI) needs no issue: its commit already records it.
- **Spec work** (`specs/BCKLG-<N>-*/`) lives in the backlog and travels in the feature's backlog PR together with its work, never in a PR of its own.

## Feature milestones (ALWAYS)

A feature is an issue in `galax-io/galaxio-sdlc-backlog`. It belongs to exactly one backlog milestone whose description has the line `Release: business`: that milestone is its release.

- **Trunk-based.** `main` is the trunk: every PR, a feature's, a fix, a bot's or CI, is opened into `main` from a short-lived branch. Unfinished behaviour is not merged or stays behind a flag. The PR of a feature writes `Part of galax-io/galaxio-sdlc-backlog#<feature>`, which records it in the feature.
- **Work enters through the backlog.** A bug, an audit finding or a request is not worked on until it belongs to a feature: grouped into one or given one. An issue from outside stays in its repository and carries the label `status:needs-feature` until it is linked to a feature in the backlog.
- **Release.** The backlog's `scripts/release-build.sh <release>` gives this repository the next minor, `vX.Y.0`, when `main` has commits after its last tag, and runs the phase `prepare` in every such repository: `release/X.Y.0` is cut from `main` and this repository's checks run on it. Only when every repository is green does it run `publish` everywhere: the tag is set on the head `prepare` checked and the GitHub Release is made; `main` is not touched and the branch stays. A red check in one repository publishes nothing anywhere. The backlog's release of the milestone lists each repository's version and links its GitHub Release.
- **Patch.** A fix of a released version is a PR into `main` first, then `git cherry-pick -x` onto `release/X.Y.0` by a PR into that branch; `prepare` and `publish` with `vX.Y.Z` tag it there. A release branch is never merged into `main`.
- **Feature done.** The feature's documents PR in the backlog writes `Closes galax-io/galaxio-sdlc-backlog#<feature>` and is merged last; the work's PRs here write `Part of`.
- **Published releases are not edited.** A fix after the release is a patch on the release branch or the next release; a tag is never moved.

## Labels (ALWAYS)

One label scheme for every repository of the organization: `scripts/labels.json` in `galax-io/galaxio-sdlc-backlog`. A label says what the milestone and the feature link do not: the kind of work, its topic, how bad it is, where it stands.

- **Names.** Every label is `group:value`. Labels a tool owns (GitHub's `good first issue` and `help wanted`, Dependabot's, and those code reads, listed as `reserved`) are left alone.
- **Exactly one `type:*` on every open issue:** `bug`, `enhancement`, `docs`, `maintenance`, `question`; in the backlog `type:feature` (an issue of a business milestone) or `type:standing` (the standing feature of an older milestone). An issue without a type is not triaged.
- **At most one each:** `severity:*` (`critical`, `high`, `medium`, `low`: the impact, not the order of work), `status:*` (`needs-triage`, `needs-info`, `needs-feature`, `ready-for-agent`, `ready-for-human`, `blocked`), `tier:*`.
- **Any number:** `area:*` (topics shared by every repository), `module:*` (parts of one repository, declared in the scheme), `source:external`, `source:audit`.
- **Not labels.** The order of work is the milestone: no `priority:*`. The feature is the `Part of` link: no `feature:*`. A rejected or duplicate issue is closed with that reason and a comment: no `wontfix`, `invalid`, `duplicate`.
- **The engine's labels** `sdlc:*` live in the backlog: only the engine sets `sdlc:gate`, `sdlc:done` and `sdlc:failed`. The label of a run's workflow, `workflow:<pipeline name>`, is the maintainer's to set, at most one on an intent; the engine never sets or removes it.
- **A new label is a change to the scheme**, a PR to `scripts/labels.json`, not a label made in one repository. `scripts/sync-labels.sh` applies the scheme; `--check` lists what breaks it.

## Task number (ALWAYS)

A task of the backlog has one number across the organization: `BCKLG-<N>`, where `<N>` is its issue number in `galax-io/galaxio-sdlc-backlog`. It is the only number a spec gets, and it names the task wherever `#N` alone would be read as an issue or PR of another repository.

- **Spec.** Its directory is `specs/BCKLG-<N>-<name>/`, `<N>` its main task. Its `spec.md`, `plan.md` and `tasks.md` carry the line `**Задачи**: BCKLG-<N>[, BCKLG-<M>…]` under the title, or `**Tasks**: …` in a repository whose files are English only, read alike: every task it carries, the main one first. A task has one spec: before writing one, find the spec whose tasks line names the task, check it against the task as its issue says now, and update it rather than write another. A spec directory from before the number keeps its name.
- **Branch.** A task's documents branch in the backlog is `spec/BCKLG-<N>-<name>`, whether made by hand or by the engine, which works on it and opens no other branch in the backlog; the engine's run is `BCKLG-<N>-<time>`, and its branches in the code repositories are `sdlc/BCKLG-<N>-<time>`.
- **Commit.** A commit that does a task's work carries the trailer `Refs: BCKLG-<N>`; its subject stays a Conventional Commit.
- **PR.** Its body carries the line `Refs: BCKLG-<N>` beside `Part of galax-io/galaxio-sdlc-backlog#<N>`, or beside `Closes galax-io/galaxio-sdlc-backlog#<N>` in the task's documents PR.
- **Checked.** The backlog's `scripts/check-linkage.sh --specs` checks every spec there.

## Agents & Models (ALWAYS)

Route work by the reasoning it needs, not by habit; parallelize only where agents would not rebuild the same context. Every model is the newest of its class that the accounts can run; an older one is never used.

| Tier | Claude (effort) | Codex (effort) | Google (effort) | Work |
|---|---|---|---|---|
| deep | `claude-opus-5-5` (xhigh) | `gpt-6.2-sol` (xhigh) | `gemini-4-neon` (xhigh) | complex code, review and verification (every critic), research, root-cause analysis, specs/plans, architecture |
| light | `claude-sonnet-5-5` (high) | `gpt-6.1-terra` (high) | `gemini-4-hydrogen` (high) | simpler work a plan already spells out: task/issue generation, mechanical infra (CI config, version pins, renames, doc fixes) |
| trivial | `claude-haiku-5-5` (high) | `gpt-6-luna` (high) | `gemini-4-hydrogen` (low) | very simple edits, gathering context, finding files, bookkeeping |
| exceptional | `claude-fable-5-2` (medium) | `gpt-6.2-astra` (xhigh) | `gemini-4-carbon` (xhigh) | only when the maintainer names the chosen model for a task; no automatic selection or vendor fallback |

- **Newest only.**
  - When a newer model of a class appears, this table and every pin of it move to it in the same change: `.claude/settings.json`, `.claude/agents/`, `setup-speckit.sh` (in spec-kit-galaxio-bootstrap), and any code that routes models.
  - Terra stays at 6.1 and Luna at 6 because Codex refuses `gpt-6.2-terra` and `gpt-6.2-luna` to a ChatGPT account.
  - `gpt-6.2-sol` and `gpt-6.2-astra` need Codex CLI 0.167.2 or later (0.165 refuses them); `gpt-6.1-terra` needs 0.159 or later.
  - Claude Code's own aliases and its background model are pinned to the table in `.claude/settings.json` (`env`: `ANTHROPIC_DEFAULT_FABLE_MODEL`, `ANTHROPIC_DEFAULT_OPUS_MODEL`, `…_SONNET_MODEL`, `…_HAIKU_MODEL`, `ANTHROPIC_SMALL_FAST_MODEL`). Without that pin its `haiku` alias means an older Haiku.
- **Codex models by tier.** Deep work runs Sol on xhigh effort, light work runs Terra on high effort, and trivial work runs Luna on high effort. A stage keeps its tier through a vendor fallback; task labels do not switch the model inside its executor session.
- **Google models by tier.** Gemini CLI (`gemini`) and Antigravity (`agy`) follow the same tiers: deep runs `gemini-4-neon` at xhigh, light runs `gemini-4-hydrogen` at high, trivial runs `gemini-4-hydrogen` at low, and `gemini-4-carbon` at xhigh is exceptional (explicit maintainer choice only). Select the model explicitly with `--model <id>` (e.g. `agy --model gemini-4-neon` / `gemini --model gemini-4-neon`) and matching thinking depth.
- **Exceptional is explicit.** Fable, Astra and Carbon run only on their own vendor's harness, with no cross-vendor fallback. Token cost keeps them opt-in: the maintainer names the model on the chosen stage, never as defaultModel. The engine runs Fable at medium; interactive Claude Code uses `claude --model claude-fable-5-2 --effort medium`. Raising effort needs an explicit instruction.
- **Reviews are deep.** Every critic and reviewer runs at the deep tier, on the other vendor than the work it judges.
- **Research is adversarial.** Deep research gets a second opinion from the other vendor (`deep-researcher` does this via `codex exec --sandbox read-only`). A claim only one model supports is unverified, not a fact.
- **Every task carries its tier.** `tasks.md` lines add `[tier:deep]`, `[tier:light]` or `[tier:trivial]` next to `[P]`, and issues carry the matching `tier:*` label where the project labels them. When unsure, it is deep.
- **Parallelize by context, not by file.**
  - Every sub-agent builds its own context from scratch. Agents that must read the same code, spec, or repository repeat that cost and leave integration work behind.
  - `[tier:deep]` work that shares a context runs in one agent, task after task: the main session or a single deep sub-agent. This holds even when `[P]` marks the tasks as parallelizable.
  - Fan deep work out only where each agent's context differs: separate repositories, or subsystems an agent can learn without the others.
  - `[tier:light]` mechanical work (`infra-worker`) and `[tier:trivial]` work (`trivial-worker`) may fan out freely.
  - Dependent tasks stay sequential.
- **Pinned, not remembered.**
  - The Spec Kit kit (`.claude/skills/speckit-*`, `.agents/skills/speckit-*`, `.specify/` but `.specify/memory/`) is the same in every repository: `scripts/build-speckit-kit.sh` of spec-kit-galaxio-bootstrap generates it, pins the Claude Code skills to their tier (override with `SPECKIT_DEEP_MODEL` / `SPECKIT_LIGHT_MODEL`), and it arrives with `copier update` (or `scripts/adopt.sh --speckit`). `scripts/check-speckit.sh` fails when this repository's kit differs; do not edit it here and do not run `specify extension`, `preset` or `integration` commands here. The constitution (`.specify/memory/constitution.md`) is this repository's own.
  - `.claude/agents/` defines the tiered sub-agents, each with its tier's model and `effort`.
  - Where a project routes models in code, it routes by this table: a task or stage names its tier, and each harness runs its own vendor's model of that tier. A model outside the table is an error.

## Commits & PRs

- **Spec-first.** `specs/BCKLG-<N>-<feature>/` artifacts → `docs(speckit): add BCKLG-<N>-<feature> spec/plan/tasks` as the first commit of the backlog PR that carries the feature's work, BEFORE any `feat`/`fix`. Never folded into an implementation commit, never a spec-only PR; that PR merges together with the feature's PRs in the other repositories (backlog constitution, Principle II).
- **1 issue = 1 commit.** Each tracked GitHub issue maps to one semantic commit (`feat(scope): … (#NNN)`), green on its own (`sbt compile "Test/testOnly"` on the default major). Docs, tweaks, and out-of-scope improvements go in separate PRs — never mixed with issue commits.
- **Intent, not path.** No add-then-remove within a PR. Squash churn before review.
- **1 concern per PR.** Feature ≠ docs/README. Stack dependent PRs; update with `--force-with-lease`.
- **Idiomatic Scala.** `Try`/`Option`/`Either`, pattern matching, `flatMap`/`collect`. No `try/catch` for control flow, `== null`, `indexOf`/`substring`, or double conversions.

## Release Process (MANDATORY)

Trunk-based with release branches. Trunk is `main`; `release/*` branches are cut from `main` for stabilization. Pushing a `vX.Y.Z` tag on `main` or a `release/*` branch publishes to Maven Central (via sbt-ci-release / dynver) and creates a GitHub Release with git-cliff notes.

A release is a business milestone of the backlog (see "Feature milestones"). Choose the next version by what `main` holds since the last tag, then tag it as below.

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
- **Before tagging**: confirm the full gate suite is green on **both** supported majors (`sbt <task>` and `sbt --sbt-version 2.0.6 <task>`)
- **Before tagging**: review outstanding MiMa binary-compatibility warnings (`sbt mimaReportBinaryIssues || true` — `mimaFindBinaryIssues` is silent and looks clean even when it is not) — each must be fixed or acknowledged via a justified `mimaBinaryIssueFilters` entry with the constitution-mandated version bump
- **Before tagging**: run the dependency-hygiene report and triage findings (report-only, **sbt 1 only** — see TESTING.md "Static analysis & gates" for why):
  `sbt --batch --addPluginSbtFile=project/hygiene/plugins.sbt undeclaredCompileDependencies unusedCompileDependencies`
- **After releasing**: bump `mimaPreviousArtifacts` in `build.sbt` to the just-published version
