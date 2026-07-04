import Dependencies.*
import com.typesafe.tools.mima.core.*

def UtilsModule(id: String) = Project(id, file(id))
lazy val IntegrationTest    = config("it") extend Test

// Single shared definition of "benchmark sources", consumed by every static-analysis gate
// (coverage here; scalafix / -Werror source filters reuse it). JMH benchmarks sit on the
// production classpath but only ever run via `sbt Jmh/run`, so counting them in any gate's
// denominator distorts the signal (#210).
lazy val benchmarkFilePattern    = ".*Benchmark.*"
lazy val benchmarkPackagePattern = "org\\.galaxio\\.gatling\\.jmh\\..*"

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, JmhPlugin)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.testSettings))
  .settings(
    name                                   := "gatling-picatinny",
    scalaVersion                           := "2.13.18",
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
    // Coverage floor — data-driven ratchet (policy: TESTING.md "Coverage ratchet").
    // Measured unit+it with benchmarks excluded: 77.75% stmt / 68.29% branch on 2026-07-04.
    // Floors sit just under measured; they only ever move UP (#80).
    coverageMinimumStmtTotal               := 75,
    coverageMinimumBranchTotal             := 66,
    coverageFailOnMinimum                  := true,
    coverageExcludedFiles                  := benchmarkFilePattern,
    coverageExcludedPackages               := benchmarkPackagePattern,
    IntegrationTest / parallelExecution    := false,
    IntegrationTest / unmanagedResourceDirectories ++= Seq((Test / resourceDirectory).value),
    // Dependency-hygiene report (#276, report-only — never CI-gated, clarification Q4). Filters
    // document the accepted findings; everything else is declared explicitly in Dependencies.scala.
    undeclaredCompileDependenciesFilter -= moduleFilter(
      "com.chuusai",
      "shapeless*",
    ), // pureconfig-generic macro-expansion artifact, version governed by pureconfig
    undeclaredCompileDependenciesFilter -= moduleFilter(
      "org.typelevel",
      "cats*",
    ), // pureconfig API surface leak, version governed transitively
    undeclaredCompileDependenciesFilter -= moduleFilter(
      "net.debasishg",
      "redisclient*",
    ), // version deliberately pinned by the gatling-redis umbrella (Provided host runtime)
    unusedCompileDependenciesFilter -= moduleFilter(
      "io.gatling",
      "gatling-redis*",
    ), // kept: pins redisclient to Gatling's own version
    unusedCompileDependenciesFilter -= moduleFilter(
      "org.openjdk.jmh",
      "jmh-generator*",
    ), // sbt-jmh code-generation-time dependency
    // Binary-compatibility ADVISORY check (#274): never fails the build. Local: `mimaReportBinaryIssues
    // || true` (the `|| true` is required — mimaReportBinaryIssues exits non-zero on findings;
    // mimaFindBinaryIssues is a silent internal task that returns problems as a value without
    // printing them, so running it bare looks clean even when it is not). CI runs
    // mimaReportBinaryIssues under continue-on-error with ::warning:: annotations. Baseline =
    // latest published release; bumped by the release checklist (AGENTS.md). Intentional breaks:
    // mimaBinaryIssueFilters entry + justification + version bump (constitution II).
    mimaPreviousArtifacts                 := Set("org.galaxio" %% "gatling-picatinny" % "1.23.0"),
    // Intentional break, requires the NEXT release to be MAJOR (constitution II): the implicit
    // GatlingConfiguration parameter on SeparatedValuesFeeder.apply(Seq[String], ...) and
    // apply(Seq[Map], ...) was dead in the method body (flagged by -Wunused during #275) and
    // inconsistent with the third apply(String, ...) overload, which never had it. Maintainer-
    // authorized removal; Gatling resolves the implicit from ambient Predef.configuration at the
    // call site regardless, so no caller needs to change source — only the erasure changes.
    mimaBinaryIssueFilters ++= Seq(
      "org.galaxio.gatling.feeders.SeparatedValuesFeeder.apply",
    ).map(ProblemFilters.exclude[DirectMissingMethodProblem](_)),
    // Scalafix lint gate (#273): semantic rules need SemanticDB; RemoveUnused feeds on -Wunused.
    semanticdbEnabled                     := true,
    semanticdbVersion                     := scalafixSemanticdb.revision,
    // Benchmark sources are invisible to the lint gate too — same shared definition as coverage (FR-022).
    Compile / scalafix / unmanagedSources := (Compile / scalafix / unmanagedSources).value
      .filterNot(_.getName.matches(benchmarkFilePattern)),
    javacOptions ++= Seq("--release", "17"),
    scalacOptions                         := Seq(
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
      // Strict diagnostics (#275): curated compiler lints escalated to errors on ALL scopes,
      // always on (local == CI, no drift). Tolerated diagnostics get a per-site
      // @nowarn("cat=...") with a justification — never a category-wide downgrade.
      // Documented -Xlint exclusion: infer-any — heterogeneous Map[String, Any] records ARE the
      // library's core feeder domain type; the lint would demand a type ascription on every
      // record literal for zero defect-finding value.
      "-Xlint:_,-infer-any",
      "-Wdead-code",
      "-Werror",
    ),
  )
  .settings(inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)))

ThisBuild / com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit := true
