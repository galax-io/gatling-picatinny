import Dependencies.*

def UtilsModule(id: String) = Project(id, file(id))
lazy val IntegrationTest    = config("it") extend Runtime

lazy val root: Project = (project in file("."))
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
    libraryDependencies ++= scalaTesting,
    libraryDependencies ++= generex,
    libraryDependencies ++= jwt,
    libraryDependencies ++= circeDeps,
    libraryDependencies ++= junit,
    coverageMinimumStmtTotal            := 45,
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

lazy val exampleCommonSettings = Seq(
  scalaVersion    := "2.13.18",
  libraryDependencies ++= gatlingCore,
  libraryDependencies ++= gatling,
  javacOptions ++= Seq("--release", "17"),
  publish / skip  := true,
  coverageEnabled := false,
  Test / test     := {},
)

lazy val exampleScala = (project in file("examples/scala-sbt-example"))
  .dependsOn(root % "test->compile")
  .settings(exampleCommonSettings)
  .settings(
    name                                := "example-scala-compile-check",
    Test / unmanagedSourceDirectories   := Seq(baseDirectory.value / "src" / "test" / "scala"),
    Test / unmanagedResourceDirectories := Seq(baseDirectory.value / "src" / "test" / "resources"),
    scalacOptions                       := (root / scalacOptions).value,
  )

lazy val exampleJava = (project in file("examples/java-maven-example"))
  .dependsOn(root % "test->compile")
  .settings(exampleCommonSettings)
  .settings(
    name                                := "example-java-compile-check",
    Test / unmanagedSourceDirectories   := Seq(baseDirectory.value / "src" / "test" / "java"),
    Test / unmanagedResourceDirectories := Seq(baseDirectory.value / "src" / "test" / "resources"),
    autoScalaLibrary                    := false,
    crossPaths                          := false,
  )
