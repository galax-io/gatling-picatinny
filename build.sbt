import Dependencies.*
import com.typesafe.tools.mima.core.*

def UtilsModule(id: String) = Project(id, file(id))

// Single shared definition of "benchmark sources", consumed by every static-analysis gate
// (coverage here; scalafix / -Werror source filters reuse it). JMH benchmarks sit on the
// production classpath but only ever run via `sbt Jmh/run`, so counting them in any gate's
// denominator distorts the signal (#210).
lazy val benchmarkPackage        = "org.galaxio.gatling.jmh"
lazy val benchmarkFilePattern    = ".*Benchmark.*"
// Both spellings derive from `benchmarkPackage` so renaming it cannot leave one gate excluding the
// benchmarks and another shipping them.
lazy val benchmarkPackagePattern = benchmarkPackage.replace(".", "\\.") + "\\..*"
lazy val benchmarkPackagePath    = benchmarkPackage.replace('.', '/')

// One shared answer to "is this archive-relative path present ONLY because of the benchmarks?".
// Consumed by BOTH `packageBin` and `packageSrc`: benchmarks are not API, so neither the compiled
// classes nor the sources belong in a published artifact.
// `Jmh / classDirectory` IS the Compile class directory, so `Jmh/compile` writes three shapes of
// artefact straight into what `packageBin` reads, and only the first was ever filtered (#333):
//   1. generated classes            — `*Benchmark*.class`, incl. `<pkg>/jmh_generated/*_jmhTest.class`
//   2. generated METADATA resources — `META-INF/BenchmarkList`, `META-INF/CompilerHints`
//   3. bare DIRECTORY mappings      — `<benchmarkPackagePath>` itself (no trailing slash, so a
//      `startsWith(path + "/")` test alone misses it) and `<pkg>/jmh_generated`
// Measured 2026-09-01: a clean jar holds 668 entries, one built after `Jmh/compile` holds 671 — the
// two metadata files plus the `META-INF/` directory entry created for them.
// The directory entry and the package CONTENTS are two conditions, deliberately not collapsed into
// one slash-less prefix: `startsWith(benchmarkPackagePath)` alone would also swallow a future
// sibling package such as `…gatling.jmhutils`, silently dropping production classes from the jar.
// `jmh_generated` is JMH's hardcoded generated-package name, matched as a whole path segment; no
// production package uses it.
lazy val isBenchmarkArtifact: String => Boolean = path =>
  path.matches(".*" + benchmarkFilePattern + "\\.(class|scala|java)") ||
    path == benchmarkPackagePath ||
    path.startsWith(benchmarkPackagePath + "/") ||
    path.split("/").contains("jmh_generated") ||
    path == "META-INF/BenchmarkList" ||
    path == "META-INF/CompilerHints"

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
    mimaPreviousArtifacts                  := Set("org.galaxio" %% "gatling-picatinny" % "1.26.0"),
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
    // ...and out of the published artifact. Benchmarks live on the production classpath so `Jmh/run`
    // can see them, but they are not API: shipping them also drags jmh-core and the jmh-generator
    // artifacts into the POM at compile scope, i.e. transitively onto every consumer. Same shared
    // benchmark definition every other gate uses (#210).
    Compile / packageBin / mappings        := (Compile / packageBin / mappings).value.filterNot { case (_, path) =>
      isBenchmarkArtifact(path)
    },
    // ...and out of the SOURCES artifact too. `packageSrc` was never filtered, so every release so
    // far shipped the benchmark sources; the same predicate answers for both archives.
    Compile / packageSrc / mappings        := (Compile / packageSrc / mappings).value.filterNot { case (_, path) =>
      isBenchmarkArtifact(path)
    },
    // JmhPlugin adds jmh-core / jmh-generator-* unscoped, so they land as `compile` dependencies in
    // an Apache-2.0 POM. They are build-time only — never needed by a consumer.
    libraryDependencies                    := libraryDependencies.value.map { m =>
      if (m.organization == "org.openjdk.jmh") m % Provided else m
    },
    // ...but `Jmh / run` FORKS, and a forked run reads `Jmh / fullClasspathAsJars`, which is built
    // from `Jmh / dependencyClasspathAsJars` — a key JmhPlugin never touches (it only appends the
    // Compile classpath to the non-forked `Jmh / dependencyClasspath`). So the fork drops exactly
    // the modules the rewrite above just demoted and dies with
    // `ClassNotFoundException: org.openjdk.jmh.Main` (#333). This applies the plugin's own line to
    // the key the fork actually reads. Scoped to `Jmh`: it feeds `Jmh / fullClasspathAsJars` and
    // nothing else, so `makePom` is byte-identical with and without it — jmh stays `provided`,
    // never `compile` (1.23.0-1.25.0 shipped it at compile scope; the rewrite above is what fixed
    // that, and no remedy here may touch `libraryDependencies`).
    // Sourced from the `AsJars` variant, not from `Compile / dependencyClasspath`: the two keys are
    // separate precisely because only the former promises packaged jars to a forked process. Today
    // every entry is a Coursier jar either way, but a future `dependsOn` would contribute a class
    // DIRECTORY that the fork would receive as though it were a jar.
    Jmh / dependencyClasspathAsJars ++= (Compile / dependencyClasspathAsJars).value,
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
    // Root's `Provided` bundles are deliberately NOT re-declared here: `test->test` already puts
    // them on this project's Test classpath (verified — all four specs compile without them on both
    // majors). Re-declaring them made the dependency-hygiene report emit 8 permanently unfixable
    // "declared but not needed" findings on this project.
    libraryDependencies ++= integrationTesting,
  )

// sbt 2 defaults `exportJars` to true, putting the packaged jar rather than the class directory on
// the Test and inter-project classpaths. Several specs resolve real files through the classpath
// (JWT key loading), which cannot work from inside a jar. Pin sbt 1's shape on both majors.
// NOTE: the `Compile/Test products` pin that used to live here is gone — it existed only to hide an
// absolute-path bug in templates/Templates.scala, which is now fixed at source (classpath-relative
// ElFileBody + jar-safe discovery), so the build no longer has to reshape the classpath for it.
ThisBuild / exportJars := false

ThisBuild / com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit := true
