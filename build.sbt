import Dependencies.*
import com.typesafe.tools.mima.core.*

def UtilsModule(id: String) = Project(id, file(id))

// Single shared definition of "benchmark sources", consumed by every static-analysis gate
// (coverage here; scalafix / -Werror source filters reuse it). JMH benchmarks sit on the
// production classpath but only ever run via `sbt Jmh/run`, so counting them in any gate's
// denominator distorts the signal (#210).
lazy val benchmarkFilePattern    = ".*Benchmark.*"
lazy val benchmarkPackagePattern = "org\\.galaxio\\.gatling\\.jmh\\..*"

// Strict diagnostics (#275): curated compiler lints escalated to errors on ALL scopes, always on
// (local == CI, no drift). Tolerated diagnostics get a per-site @nowarn("cat=...") with a
// justification — never a category-wide downgrade.
// Documented -Xlint exclusion: infer-any — heterogeneous Map[String, Any] records ARE the
// library's core feeder domain type; the lint would demand a type ascription on every record
// literal for zero defect-finding value.
lazy val strictScalacOptions = Seq(
  "-encoding",
  "UTF-8",
  "-release:17",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-Wunused:imports,privates,locals,patvars",
  "-Xlint:_,-infer-any",
  "-Wdead-code",
  "-Werror",
)

// Applied to BOTH projects so the relocated integration sources keep exactly the compiler contract
// they had under the old `it` configuration (spec 012, FR-007/FR-009). Deliberately NOT
// `ThisBuild / scalacOptions` — a plugin doing `scalacOptions ++=` at project scope would silently
// win over a delegated value.
lazy val commonSettings = Seq(
  scalaVersion      := "2.13.18",
  // Scalafix lint gate (#273): semantic rules need SemanticDB; RemoveUnused feeds on -Wunused.
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  javacOptions ++= Seq("--release", "17"),
  scalacOptions     := strictScalacOptions,
)

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, JmhPlugin)
  // Aggregation is what keeps scalafmtAll / scalafixAll / Test-compile / clean reaching the
  // `integration` subproject; without it those gates go silently inert on its sources — the exact
  // FR-007 "gate inert / false green" defect. `LocalProject(...)` is required: a bare forward
  // reference to `integration` fails the load.
  .aggregate(LocalProject("integration"))
  .settings(commonSettings)
  .settings(
    name                                   := "gatling-picatinny",
    libraryDependencies ++= gatlingCore,
    libraryDependencies ++= gatlingShared,
    libraryDependencies ++= gatling,
    // TypeTag-based config extraction references scala-reflect directly (hygiene report #276)
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    libraryDependencies ++= fastUUID,
    libraryDependencies ++= json4s,
    libraryDependencies ++= pureConfig,
    libraryDependencies ++= jackson,
    libraryDependencies ++= scalaLogging,
    libraryDependencies ++= scalaTesting,
    libraryDependencies ++= generex,
    libraryDependencies ++= jwt,
    libraryDependencies ++= idValidation,
    libraryDependencies ++= idValidationTest,
    libraryDependencies ++= circeDeps,
    libraryDependencies ++= junit,
    // The unit gate (`sbt Test/testOnly`, CI, .githooks/pre-commit) must stay Docker-free, so the
    // test tasks specifically do NOT aggregate into `integration`. Everything else still does.
    // `Test / testFull` is sbt-2-only and nothing invokes it; left aggregating deliberately.
    Test / test / aggregate                := false,
    Test / testOnly / aggregate            := false,
    Test / testQuick / aggregate           := false,
    // Pin sbt 1's product shape on both majors — see the exportJars/products note at the bottom
    // of this file for why this reaches runtime behaviour rather than being a build detail.
    // One `val _` per block, not two: Scala 2.12 (sbt 1's build compiler) rejects a second
    // `val _` in the same scope with "_ is already defined as value _", while Scala 3 (sbt 2)
    // accepts it. The tuple keeps a single binding and compiles on both.
    Compile / products                     := {
      val _ = ((Compile / compile).value, (Compile / copyResources).value)
      Seq((Compile / classDirectory).value)
    },
    Test / products                        := {
      val _ = ((Test / compile).value, (Test / copyResources).value)
      Seq((Test / classDirectory).value)
    },
    // Coverage floor — data-driven ratchet (policy: TESTING.md "Coverage ratchet").
    // Measured unit+integration with benchmarks excluded, 2026-08-20: 81.40-81.44% stmt /
    // 75.33-75.49% branch. Within any single run the two sbt majors agree EXACTLY; across runs the
    // figure varies by ~0.04 stmt / ~0.16 branch even with `clean`, so it is a range, not a
    // constant — do not gate on an exact value or the check will flap.
    // The previous comment recorded 77.75/68.29 (2026-07-04); that was already stale at HEAD before
    // this feature. The `integration` subproject migration is coverage-NEUTRAL, not improving.
    // Floors sit just under measured; they only ever move UP (#80).
    coverageMinimumStmtTotal               := 75,
    coverageMinimumBranchTotal             := 66,
    coverageFailOnMinimum                  := true,
    coverageExcludedFiles                  := benchmarkFilePattern,
    coverageExcludedPackages               := benchmarkPackagePattern,
    // Binary-compatibility ADVISORY check (#274): never fails the build. Local: `mimaReportBinaryIssues
    // || true` (the `|| true` is required — mimaReportBinaryIssues exits non-zero on findings;
    // mimaFindBinaryIssues is a silent internal task that returns problems as a value without
    // printing them, so running it bare looks clean even when it is not). CI runs
    // mimaReportBinaryIssues under continue-on-error with ::warning:: annotations. Baseline =
    // latest published release; bumped by the release checklist (AGENTS.md). Intentional breaks:
    // mimaBinaryIssueFilters entry + justification + version bump (constitution II).
    mimaPreviousArtifacts                  := Set("org.galaxio" %% "gatling-picatinny" % "1.25.0"),
    // Intentional break: the implicit GatlingConfiguration parameter on
    // SeparatedValuesFeeder.apply(Seq[String], ...) and apply(Seq[Map], ...) was dead in the
    // method body (flagged by -Wunused during #275) and inconsistent with the third
    // apply(String, ...) overload, which never had it. Maintainer-authorized removal; Gatling
    // resolves the implicit from ambient Predef.configuration at the call site regardless, so no
    // real caller ever passed it explicitly or needs to change source — only the erasure changes.
    // EXCEPTION to constitution II's MAJOR-bump default, explicitly authorized 2026-07-05:
    // ships in 1.24.0 (MINOR) rather than 2.0.0 — see plan.md Complexity Tracking (008).
    mimaBinaryIssueFilters ++= Seq(
      "org.galaxio.gatling.feeders.SeparatedValuesFeeder.apply",
    ).map(ProblemFilters.exclude[DirectMissingMethodProblem](_)),
    // Benchmark sources are invisible to the lint gate too — same shared definition as coverage (FR-022).
    Compile / scalafix / unmanagedSources  := (Compile / scalafix / unmanagedSources).value
      .filterNot(_.getName.matches(benchmarkFilePattern)),
  )

// Testcontainers-backed integration tests. Replaces the custom `it` configuration, which sbt 2
// refuses to resolve as a key axis (`Not a valid key: it`) — a plain subproject is the construct
// both sbt majors implement identically (spec 012, research D-07). No `scalafixConfigSettings` is
// needed any more: ScalafixPlugin injects Compile/Test scopes into every project by itself, and the
// explicit `inConfig` call only existed because `it` was a custom configuration.
// Run with `sbt integration/testOnly` — NOT `integration/test`: on sbt 2 `test` is `testQuick` and
// reports success having run nothing.
lazy val integration = (project in file("integration"))
  .dependsOn(root % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name                     := "gatling-picatinny-integration",
    // Never published. Aggregation means `ci-release` reaches this project, so this setting is the
    // only thing keeping a second artifact off Maven Central — verify with publishLocal, not by
    // reading the setting.
    publish / skip           := true,
    // No previous artifact exists for a project that is never published; without this the whole
    // binary-compat run aborts on it.
    mimaFailOnNoPrevious     := false,
    // Testcontainers specs (Redis, Vault, Postgres) must not race for ports and containers.
    Test / parallelExecution := false,
    // `Provided` scope does NOT propagate across `dependsOn`, so root's host-runtime bundles must
    // be re-declared here or the relocated specs fail to compile against io.gatling / com.redis
    // types. `publish / skip` keeps them out of any POM regardless.
    libraryDependencies ++= gatlingCore,
    libraryDependencies ++= gatlingShared,
    libraryDependencies ++= fastUUID,
    libraryDependencies ++= integrationTesting,
  )

// Two build-tool defaults changed in sbt 2 and both reach the library's RUNTIME behaviour, so both
// are pinned to sbt 1's shape on every major (spec 012, D-16).
//
// 1. `exportJars` defaults to true in sbt 2, putting a jar rather than a class directory on the
//    Test classpath.
// 2. sbt 2 does not copy unmanaged resources into `classDirectory`; it puts `src/{main,test}/resources`
//    directly on the classpath. Verified: sbt 1 `Test/products` = [test-classes]; sbt 2 =
//    [test-classes, src/test/resources], and sbt 2's test-classes has no `templates/` directory.
//
// Why this is not cosmetic: `templates/Templates.scala` builds its registry from
// `getResource("templates")` and hands Gatling `ElFileBody(f.getCanonicalPath)` — an ABSOLUTE path.
// Gatling rejects an absolute path that resolves inside its configured resources directory:
//   "Your resource's path .../src/test/resources/templates/test_json.json is incorrect. It should
//    not be an absolute path pointing to a directory that belongs to your classpath."
// Under sbt 1 the path is the copied `test-classes/templates/...` and Gatling accepts it. Without
// the `products` pin above, 2 TemplatesSpec tests fail under sbt 2 — and the natural misdiagnosis
// is to blame the `integration` subproject migration and narrow what the sbt 2 gate runs.
ThisBuild / exportJars := false

ThisBuild / com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit := true
