import Dependencies.*

def UtilsModule(id: String) = Project(id, file(id))
lazy val IntegrationTest    = config("it") extend Test

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
    libraryDependencies ++= circeDeps,
    libraryDependencies ++= junit,
    // Coverage floor — data-driven (measured unit+it: 69.69% stmt / 63.37% branch on 2026-06-21).
    // Set just under the measured level to lock in the gain and INTRODUCE a branch floor (none existed).
    coverageMinimumStmtTotal            := 65,
    coverageMinimumBranchTotal          := 60,
    coverageFailOnMinimum               := true,
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
