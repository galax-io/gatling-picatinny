val picatinnyVersion  = sys.env.getOrElse("PICATINNY_VERSION", "0.0.0-ci-local")
val gatlingVersion    = "3.13.5"

resolvers += Resolver.mavenLocal

ThisBuild / scalaVersion := "2.13.18"

enablePlugins(GatlingPlugin)

// Simulations live in src/test/scala (galaxio-gatling-pro layout)
Gatling / scalaSource := (Test / scalaSource).value

libraryDependencies ++= Seq(
  "org.galaxio"           %% "gatling-picatinny"         % picatinnyVersion % Test,
  "io.gatling.highcharts"  % "gatling-charts-highcharts"  % gatlingVersion   % Test,
  "io.gatling"             % "gatling-test-framework"      % gatlingVersion   % Test,
  "org.wiremock"           % "wiremock"                   % "3.13.2"         % Test,
)
