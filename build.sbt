import Dependencies.*

def UtilsModule(id: String) = Project(id, file(id))

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning)
  .settings(
    name                     := "gatling-picatinny",
    scalaVersion             := "2.13.18",
    libraryDependencies ++= gatlingCore,
    libraryDependencies ++= gatling,
    libraryDependencies ++= fastUUID,
    libraryDependencies ++= json4s,
    libraryDependencies ++= pureConfig,
    libraryDependencies ++= jackson,
    libraryDependencies ++= scalaTesting,
    libraryDependencies ++= generex,
    libraryDependencies ++= jwt,
    libraryDependencies ++= circeDeps,
    libraryDependencies ++= junit,
    coverageMinimumStmtTotal := 45,
    coverageFailOnMinimum    := true,
    javacOptions ++= Seq("--release", "17"),
    scalacOptions            := Seq(
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
