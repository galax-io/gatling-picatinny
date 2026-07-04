import Dependencies.*

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
    name                                  := "gatling-picatinny",
    scalaVersion                          := "2.13.18",
    libraryDependencies ++= gatlingCore,
    libraryDependencies ++= gatling,
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
    coverageMinimumStmtTotal              := 75,
    coverageMinimumBranchTotal            := 66,
    coverageFailOnMinimum                 := true,
    coverageExcludedFiles                 := benchmarkFilePattern,
    coverageExcludedPackages              := benchmarkPackagePattern,
    IntegrationTest / parallelExecution   := false,
    IntegrationTest / unmanagedResourceDirectories ++= Seq((Test / resourceDirectory).value),
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
    ),
  )
  .settings(inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)))

ThisBuild / com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit := true
