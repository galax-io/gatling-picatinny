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
    name                                := "gatling-picatinny",
    scalaVersion                        := "2.13.18",
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
    // Coverage floor — data-driven (measured unit+it: 69.69% stmt / 63.37% branch on 2026-06-21).
    // Set just under the measured level to lock in the gain and INTRODUCE a branch floor (none existed).
    coverageMinimumStmtTotal            := 65,
    coverageMinimumBranchTotal          := 60,
    coverageFailOnMinimum               := true,
    coverageExcludedFiles               := benchmarkFilePattern,
    coverageExcludedPackages            := benchmarkPackagePattern,
    IntegrationTest / parallelExecution := false,
    IntegrationTest / unmanagedResourceDirectories ++= Seq((Test / resourceDirectory).value),
    javacOptions ++= Seq("--release", "17"),
    scalacOptions                       := Seq(
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
    ),
  )

ThisBuild / com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit := true
